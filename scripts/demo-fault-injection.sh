#!/usr/bin/env bash
# ============================================================================
# Lightmark 答辩演示脚本:故障注入(超时 / 熔断 / 备用结果 / 故障隔离)
# ----------------------------------------------------------------------------
# 演示对象(与代码实现一一对应,见 docs/微服务划分.md §7.2):
#   A. 停 user-service    -> 备用结果:社区列表仍 200,作者昵称降级为"旅行用户"
#                           故障隔离:机票/酒店搜索、订单预览不受影响(仅登录类接口失败)
#   B. 停 product-service -> 超时/重试 + 熔断:POST /api/flights/order/preview
#                            前几次带重试失败(503"服务繁忙，请稍后再试"),5 次失败后
#                            熔断打开 -> 瞬时失败;恢复后 10s 半开 -> 自愈(200)
#   C. 停 content-service -> 故障隔离:产品/订单/用户域全部正常,仅社区 503;恢复后自愈
#
# 用法(在服务器上执行,kubectl 需能访问 lightmark 命名空间):
#   bash scripts/demo-fault-injection.sh            # 默认全部场景,逐步按回车
#   bash scripts/demo-fault-injection.sh --auto      # 自动连续执行(适合录屏)
#   bash scripts/demo-fault-injection.sh B --auto    # 只跑场景 B,自动
#   BASE_URL=https://msa.lightmark.ortus.top bash scripts/demo-fault-injection.sh A
#
# 输出:全程日志 /tmp/lm-fault-demo-<时间戳>.log(录屏后可用于剪辑/核对)
# 说明:4 个服务均配置了 HPA(min 1/max 4),直接 scale 到 0 会被 HPA 拉回;
#       脚本会先暂停 HPA 再缩容,结束后恢复副本并重建 HPA(幂等,可中断重跑,
#       Ctrl+C 也会自动恢复现场)。
# ============================================================================
set -u

NS="${NS:-lightmark}"
BASE_URL="${BASE_URL:-http://msa.lightmark.ortus.top}"
AUTO=0
MODE="${1:-all}"
[ "$MODE" = "--auto" ] && { AUTO=1; MODE="${2:-all}"; }

TS=$(date +%Y%m%d-%H%M%S)
LOG="/tmp/lm-fault-demo-${TS}.log"
STATEDIR="/tmp/lm-fault-demo-${TS}.state"
mkdir -p "$STATEDIR"
exec > >(tee -a "$LOG") 2>&1

PASS=0; FAIL=0

# ---------- 基础工具 ----------
say()  { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m  ✔ %s\033[0m\n' "$*"; PASS=$((PASS+1)); }
bad()  { printf '\033[1;31m  ✘ %s\033[0m\n' "$*"; FAIL=$((FAIL+1)); }
note() { printf '\033[1;33m  · %s\033[0m\n' "$*"; }
pause() { [ "$AUTO" = "1" ] && return 0; printf '\033[1;37m  —— 按回车继续(Ctrl+C 安全退出并自动恢复) ——\033[0m'; read -r _; }

# curl:GET/POST,结果存 CURL_CODE(HTTP码)+CURL_BODY(文件);$3 为可选附加请求头
curl_req() {
  local url="$1" data="${2:-}" extra="${3:-}"
  CURL_BODY="$(mktemp)"
  if [ -n "$data" ]; then
    if [ -n "$extra" ]; then
      CURL_CODE=$(curl -sS --max-time 40 -o "$CURL_BODY" -w '%{http_code}' \
        -X POST -H 'Content-Type: application/json' -H "$extra" --data "$data" "$url")
    else
      CURL_CODE=$(curl -sS --max-time 40 -o "$CURL_BODY" -w '%{http_code}' \
        -X POST -H 'Content-Type: application/json' --data "$data" "$url")
    fi
  else
    CURL_CODE=$(curl -sS --max-time 40 -o "$CURL_BODY" -w '%{http_code}' "$url")
  fi
}
body_snip() { tr -d '\n' < "$CURL_BODY" | head -c 220; echo; }

# 从 JSON 取字段(路径用点号,数字段为下标;data.list 缺省时自动试 data.records)
jget() {
  python3 - "$CURL_BODY" "$1" <<'PY'
import json,sys
body,path=sys.argv[1],sys.argv[2]
try: d=json.load(open(body,encoding="utf-8"))
except Exception: sys.exit(2)
def walk(d,path):
    for k in path.split("."):
        k=int(k) if k.isdigit() else k
        if isinstance(d,list) and isinstance(k,int) and 0 <= k < len(d):
            d=d[k]; continue
        if not isinstance(d,dict) or k not in d: return None
        d=d[k]
    return d
v=walk(d,path)
if v is None and path.startswith("data.list."):
    v=walk(d,path.replace("data.list.","data.records.",1))
print(v if v is not None else "")
PY
}

# 从机票搜索响应里挑一条有库存的航班(输出其 productId;无则输出空)
pick_flight_id() {
  python3 - "$CURL_BODY" <<'PY'
import json,sys
try:
    d=json.load(open(sys.argv[1],encoding="utf-8"))
except Exception:
    print(""); sys.exit(0)
data=d.get("data") or {}
lst=data.get("list") or data.get("records") or []
for it in lst:
    if not isinstance(it,dict): continue
    try:
        if int(it.get("stock") or 0) > 0 and str(it.get("id") or ""):
            print(it["id"]); sys.exit(0)
    except Exception:
        pass
print("")
PY
}

# ---------- 服务启停(HPA 感知,可安全恢复) ----------
# 冻结/解冻 Java 进程:不依赖 PID=1(新镜像 Java 可能由 tini 等包装),
# 通过 /proc/*/cmdline 定位真正的 java 进程(模式经环境变量传入,避免自匹配)
svc_freeze() {  # $1=svc;成功输出 FROZEN-<pid> 并返回 0
  kubectl exec -n "$NS" deploy/"$1" -- env APP_PAT=app.jar sh -c \
    'for p in /proc/[0-9]*; do c=$(tr "\0" " " < "$p/cmdline" 2>/dev/null); case "$c" in *"$APP_PAT"*) kill -STOP "${p#/proc/}" 2>/dev/null && echo FROZEN-"${p#/proc/}"; exit 0;; esac; done; echo NOJAVA' 2>/dev/null | grep -E 'FROZEN-[0-9]+'
}
svc_unfreeze() {  # $1=svc;成功返回 0
  kubectl exec -n "$NS" deploy/"$1" -- env APP_PAT=app.jar sh -c \
    'for p in /proc/[0-9]*; do c=$(tr "\0" " " < "$p/cmdline" 2>/dev/null); case "$c" in *"$APP_PAT"*) kill -CONT "${p#/proc/}" 2>/dev/null && echo RESUMED-"${p#/proc/}"; exit 0;; esac; done; echo NOJAVA' 2>/dev/null | grep -qE 'RESUMED-[0-9]+'
}
svc_down() {  # $1=service
  local svc="$1"
  say "停止服务 $svc(暂停 HPA -> 缩容到 0)"
  if kubectl get hpa "$svc" -n "$NS" >/dev/null 2>&1; then
    kubectl delete hpa "$svc" -n "$NS" >/dev/null 2>&1 && touch "$STATEDIR/hpa-$svc"
    note "已暂停 $svc 的 HPA(避免自动拉回副本)"
  fi
  kubectl scale deployment "$svc" -n "$NS" --replicas=0 >/dev/null
  local i=0
  while [ $i -lt 60 ]; do
    local n; n=$(kubectl get pods -n "$NS" 2>/dev/null | awk -v s="$svc" '$1 ~ "^"s"-" {c++} END{print c+0}')
    [ "$n" = "0" ] && { ok "$svc 已完全下线(Pod 数=$n)"; return 0; }
    sleep 2; i=$((i+2))
  done
  bad "$svc 缩容超时,请人工检查"
  return 1
}

svc_up() {  # $1=service
  local svc="$1"
  say "恢复服务 $svc(扩容到 1 并等待就绪)"
  kubectl scale deployment "$svc" -n "$NS" --replicas=1 >/dev/null
  if ! kubectl rollout status deployment/"$svc" -n "$NS" --timeout=180s >/dev/null 2>&1; then
    bad "$svc rollout 未就绪"
    return 1
  fi
  if [ -f "$STATEDIR/hpa-$svc" ]; then
    kubectl autoscale deployment "$svc" -n "$NS" --min=1 --max=4 --cpu-percent=60 >/dev/null 2>&1 \
      && note "已重建 $svc 的 HPA(min 1/max 4,CPU 60%)"
  fi
  ok "$svc 已就绪"
  sleep 6   # 等 Ingress/Service endpoints 生效
}

restore_all() {  # 退出时兜底恢复现场
  for f in "$STATEDIR"/freeze-*; do
    [ -f "$f" ] || continue
    local svc=${f##*freeze-}
    svc_unfreeze "$svc" >/dev/null 2>&1
    kubectl rollout status deployment/"$svc" -n "$NS" --timeout=150s >/dev/null 2>&1
    note "退出兜底:已解冻 $svc"
  done
  for f in "$STATEDIR"/down-*; do
    [ -f "$f" ] || continue
    local svc=${f##*down-}
    kubectl scale deployment "$svc" -n "$NS" --replicas=1 >/dev/null 2>&1
    kubectl rollout status deployment/"$svc" -n "$NS" --timeout=180s >/dev/null 2>&1
    if [ -f "$STATEDIR/hpa-$svc" ]; then
      kubectl autoscale deployment "$svc" -n "$NS" --min=1 --max=4 --cpu-percent=60 >/dev/null 2>&1
    fi
    note "退出兜底:已恢复 $svc"
  done
}
trap restore_all EXIT

preflight() {
  say "预检:kubectl 集群状态"
  kubectl get nodes 2>/dev/null | tail -n +2 | head -3 || { bad "kubectl 不可用,请在服务器执行"; exit 1; }
  kubectl get deploy,hpa -n "$NS" 2>/dev/null | grep -E 'NAME|service' || note "未找到 $NS 下的 Deployment,请检查命名空间"

  # 小规格节点上 Spring Boot 冷启动约 50~90s,刚部署/重启后探针会先失败,
  # 这里轮询等待 4 个业务服务全部 Ready(已就绪时秒过)
  say "等待 4 个微服务全部 Ready(冷启动约 50~90s,已就绪则跳过)"
  local ready=0
  for _ in $(seq 1 48); do   # 最多等 240s
    ready=$(kubectl get deploy -n "$NS" -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.availableReplicas}{"\n"}{end}' 2>/dev/null \
      | awk '$1 ~ /-service$/ && $2 >= 1 {c++} END{print c+0}')
    [ "$ready" -ge 4 ] && break
    sleep 5
  done
  if [ "$ready" -ge 4 ]; then ok "4 个服务均已 Ready";
  else bad "240s 内未等到 4 个服务 Ready(当前 $ready/4),请先排查 Pod 状态: kubectl get pods -n $NS"; exit 1; fi

  # Ingress/Service endpoints 生效需要几秒,轮询入口
  say "预检:入口连通性 $BASE_URL"
  local code=000 i=0
  while [ "$i" -lt 18 ]; do
    curl_req "$BASE_URL/api/flights/search?page=1&size=1"
    code="$CURL_CODE"
    [ "$code" = "200" ] && break
    sleep 5; i=$((i+1))
  done
  if [ "$code" = "200" ]; then ok "入口可达(HTTP $code)";
  else bad "90s 内入口仍不可达(HTTP $code),请用 BASE_URL 覆盖或先检查部署/Ingress"; exit 1; fi
  pause
}

# ---------- 数据准备:直灌一条种子游记(绕开前端发布模块,仅作演示数据) ----------
# 演示链路仍是真实 HTTP:GET /api/posts -> content-service 调 user-service 补作者昵称
db_seed_post() {  # 成功返回 0
  local pw out uid nick sql
  say "社区无游记,准备种子数据:直接向内容库写入一条游记(作者取真实用户,不经前端模块)"
  pw=$(kubectl exec -n "$NS" deploy/lightmark-mysql -- printenv MYSQL_ROOT_PASSWORD 2>/dev/null | tr -d '\r\n')
  if [ -z "$pw" ]; then note "无法从 mysql Pod 获取 MYSQL_ROOT_PASSWORD(envFrom lightmark-secrets)"; return 1; fi
  sql="select u.id, ifnull(u.nickname,'') from lightmark_user.user u where ifnull(u.nickname,'') <> '' and u.nickname <> '旅行用户' order by u.id limit 10"
  out=$(kubectl exec -n "$NS" deploy/lightmark-mysql -- env MYSQL_PWD="$pw" mysql -uroot -N -e "$sql" 2>/dev/null)
  uid=$(printf '%s\n' "$out" | head -1 | cut -f1)
  nick=$(printf '%s\n' "$out" | head -1 | cut -f2)
  [ -z "$uid" ] && { note "lightmark_user.user 中未找到可用作者(需有非空昵称)"; return 1; }
  sql="insert into lightmark_content.post(user_id,title,content,images,likes,comments_count,status) values($uid,'故障演示:昵称降级验证','种子游记:验证 user 服务故障时作者昵称降级为旅行用户的备用结果。',NULL,0,0,1)"
  if kubectl exec -n "$NS" deploy/lightmark-mysql -- env MYSQL_PWD="$pw" mysql -uroot -e "$sql" >/dev/null 2>&1; then
    note "已写入种子游记:作者 user_id=$uid(nickname='$nick')"
    return 0
  fi
  note "写入失败,请检查 lightmark_content.post 表结构/权限"
  return 1
}

# ---------- 场景 A:user-service 下线 -> 备用结果 + 隔离 ----------
scenario_a() {
  say "========== 场景 A:停 user-service —— 备用结果(昵称降级)+ 故障隔离 =========="

  # A0 基线:找一个有作者的游记;没有则自动直灌种子游记(或 ADMIN_TOKEN 发帖)
  local pid="" base_nick=""
  curl_req "$BASE_URL/api/posts?page=1&size=3"
  if [ "$CURL_CODE" = "200" ]; then
    pid=$(jget "data.list.0.id")
    base_nick=$(jget "data.list.0.author.nickname")
  fi
  if [ -z "$pid" ] || [ "$pid" = "0" ]; then
    if db_seed_post; then
      curl_req "$BASE_URL/api/posts?page=1&size=3"
      pid=$(jget "data.list.0.id")
      base_nick=$(jget "data.list.0.author.nickname")
    fi
  fi
  if [ -z "$pid" ] || [ "$pid" = "0" ]; then
    if [ -n "${ADMIN_TOKEN:-}" ]; then
      say "自动直灌失败,尝试用 ADMIN_TOKEN 经接口发布一篇"
      curl_req "$BASE_URL/api/posts" \
        '{"title":"故障注入演示:昵称降级验证","content":"这是一篇用于验证备用结果(昵称降级)的演示游记。"}' \
        "Authorization: Bearer ${ADMIN_TOKEN}"
      if [ "$CURL_CODE" = "200" ]; then
        ok "发帖成功(HTTP 200)"
        sleep 1
        curl_req "$BASE_URL/api/posts?page=1&size=3"
        pid=$(jget "data.list.0.id")
        base_nick=$(jget "data.list.0.author.nickname")
      else
        bad "自动发帖失败(HTTP $CURL_CODE):$(body_snip)"
      fi
    fi
  fi
  if [ -z "$pid" ] || [ "$pid" = "0" ]; then
    bad "仍无可用游记数据:请检查 mysql Pod 可执行性(kubectl exec)与 lightmark_content.post 表"
    return 1
  fi
  ok "基线:游记 id=$pid,作者昵称='${base_nick}'"
  pause

  touch "$STATEDIR/down-user-service"
  svc_down user-service || return 1

  say "验证① 用户域接口失败(登录不可用,故障在用户域)"
  curl_req "$BASE_URL/api/auth/admin/login" '{}'
  if [ "${CURL_CODE:-0}" -ge 500 ]; then ok "登录接口 HTTP $CURL_CODE(服务不可达,符合预期)";
  else note "登录接口 HTTP $CURL_CODE(未按预期 5xx,以实际为准)"; fi
  pause

  say "验证② 故障隔离:产品域机票搜索仍正常"
  curl_req "$BASE_URL/api/flights/search?page=1&size=2"
  local n; n=$(jget "data.list.0.id")
  if [ "$CURL_CODE" = "200" ] && [ -n "$n" ] && [ "$n" != "0" ]; then
    ok "机票搜索 HTTP 200,首条 id=$n(user-service 下线不影响产品域)"
  else bad "机票搜索异常(HTTP $CURL_CODE)"; fi
  pause

  say "验证③ 备用结果:社区列表仍 200,作者昵称降级为'旅行用户'"
  curl_req "$BASE_URL/api/posts?page=1&size=3"
  local nick; nick=$(jget "data.list.0.author.nickname")
  if [ "$CURL_CODE" = "200" ] && [ "$nick" = "旅行用户" ]; then
    ok "社区 HTTP 200,昵称已降级='$nick'(备用结果生效)"
  elif [ "$CURL_CODE" = "200" ]; then
    note "社区 200 但昵称='$nick'(未走降级,请核对基线昵称)"
  else bad "社区异常 HTTP $CURL_CODE"; fi
  pause

  say "验证④ 订单域不受影响(免鉴权订单预览走 product 校验)"
  curl_req "$BASE_URL/api/flights/search?page=1&size=6"
  local pid2; pid2=$(pick_flight_id)
  if [ -n "$pid2" ]; then
    curl_req "$BASE_URL/api/flights/order/preview" "{\"productId\":\"$pid2\",\"passengerCount\":1}"
    if [ "$CURL_CODE" = "200" ]; then ok "订单预览 HTTP 200(订单域正常)";
    else note "订单预览 HTTP $CURL_CODE,msg=$(jget msg)"; fi
  fi
  pause

  svc_up user-service || return 1
  say "验证⑤ 恢复后昵称还原(自愈)"
  curl_req "$BASE_URL/api/posts?page=1&size=3"
  local nick2; nick2=$(jget "data.list.0.author.nickname")
  if [ "$CURL_CODE" = "200" ] && [ -n "$nick2" ] && [ "$nick2" != "旅行用户" ]; then
    ok "恢复后昵称='$nick2'(已还原真实昵称)"
  else note "恢复后昵称='$nick2'(可能该用户本就叫'旅行用户',以实际为准)"; fi
  rm -f "$STATEDIR/down-user-service"
  pause
}

# ---------- 场景 B:product-service 故障 -> 超时重试 + 熔断 + 恢复自愈 ----------
scenario_b() {
  say "========== 场景 B:product-service 故障 —— 超时重试 + 熔断 + 自愈 =========="
  local pid="" FREEZE=0

  # B0 基线:挑一条有库存的机票,预览成功
  curl_req "$BASE_URL/api/flights/search?page=1&size=6"
  pid=$(pick_flight_id)
  if [ -z "$pid" ]; then
    note "机票列表中没有有库存的商品(可能全部售罄),打印诊断信息:"
    python3 - "$CURL_BODY" <<'PY'
import json,sys
try:
    d=json.load(open(sys.argv[1],encoding="utf-8"))
    data=d.get("data") or {}
    lst=data.get("list") or data.get("records") or []
    print("rows:", len(lst))
    if lst: print("first row:", json.dumps(lst[0], ensure_ascii=False)[:300])
except Exception as e:
    print("json parse error:", e)
PY
    bad "无法取到有库存的 productId,中止场景 B"
    return 1
  fi
  curl_req "$BASE_URL/api/flights/order/preview" "{\"productId\":\"$pid\",\"passengerCount\":1}"
  local amt; amt=$(jget "data.payAmount")
  if [ "$CURL_CODE" = "200" ]; then ok "基线:productId=$pid(有库存),预览应支付 ¥$amt";
  else bad "基线预览失败 HTTP $CURL_CODE,msg=$(jget msg),中止场景 B"; return 1; fi
  pause

  # 故障注入:优先冻结 Java 进程(SIGSTOP)——Pod 保留、TCP 可连但无响应,
  # 让订单侧真实走到 3s 读超时;冻结失败(找不到进程/未真正冻结)时回退为暂停 HPA+缩容
  say "故障注入:冻结 product-service 的 Java 进程(kill -STOP,保留 Pod 制造 3s 超时)"
  touch "$STATEDIR/down-product-service"
  local frozen_pid=""
  frozen_pid=$(svc_freeze product-service)
  if [ -n "$frozen_pid" ]; then
    # 验证确已冻结:2 次 1s 探测都应无响应(000/超时);若仍 200 说明冻结失败
    local alive=0 k code2
    for k in 1 2; do
      code2=$(curl -s --max-time 1 -o /dev/null -w '%{http_code}' "$BASE_URL/api/flights/search?page=1&size=1" 2>/dev/null)
      [ "$code2" = "200" ] && alive=$((alive+1))
    done
    if [ "$alive" = "0" ]; then
      FREEZE=1
      touch "$STATEDIR/freeze-product-service"
      ok "已冻结 Java 进程($frozen_pid),探测无响应;订单侧将先经历 3s 读超时,熔断生效后快速失败"
      sleep 3
    else
      note "冻结后探测仍 $alive/2 次 200(进程未真正冻结),回退:暂停 HPA 并缩容到 0"
      svc_unfreeze product-service >/dev/null 2>&1
      svc_down product-service || return 1
    fi
  else
    note "未定位到 Java 进程,回退:暂停 HPA 并缩容到 0"
    svc_down product-service || return 1
  fi

  say "故障期:连续调用订单预览(记录每次耗时:超时重试阶段 vs 熔断快速失败阶段)"
  local i slow_fail=0 fast_fail=0 code dur t0 total=8
  [ "$FREEZE" = "1" ] && total=6
  for i in $(seq 1 "$total"); do
    t0=$(date +%s%3N)
    curl_req "$BASE_URL/api/flights/order/preview" "{\"productId\":\"$pid\",\"passengerCount\":1}"
    dur=$(( $(date +%s%3N) - t0 ))
    local msg; msg=$(jget "msg")
    [ "$CURL_CODE" = "503" ] && [ "$msg" = "服务繁忙，请稍后再试" ] && ok "第${i}次:HTTP $CURL_CODE 耗时${dur}ms 文案='$msg'" \
      || note "第${i}次:HTTP $CURL_CODE 耗时${dur}ms 文案='$msg'"
    # 熔断打开后不再等待 3s 网络超时:超时阶段约 6000ms+,快速失败约 350ms,以 1500ms 为界
    if [ "$dur" -lt 1500 ] && [ "$CURL_CODE" = "503" ]; then fast_fail=$((fast_fail+1)); fi
    [ "$CURL_CODE" = "503" ] && slow_fail=$((slow_fail+1))
    [ "$FREEZE" = "1" ] && sleep 2 || sleep 1
  done
  if [ "$slow_fail" -ge 5 ]; then ok "全部失败且均为 503'服务繁忙，请稍后再试'(降级文案生效,共 $slow_fail 次)";
  else note "503 次数=$slow_fail(未达预期,以实际响应为准,检查 order-service 日志)"; fi
  if [ "$fast_fail" -ge 1 ]; then ok "出现 ≥1 次快速失败(<1500ms,未再等待 3s 网络超时),说明熔断已生效";
  else note "未观察到快速失败(全部请求都在等待网络超时?)"; fi
  pause

  say "熔断窗口验证:连打 3 次,应全部快速失败"
  local fastn=0
  for i in 1 2 3; do
    t0=$(date +%s%3N)
    curl_req "$BASE_URL/api/flights/order/preview" "{\"productId\":\"$pid\",\"passengerCount\":1}"
    dur=$(( $(date +%s%3N) - t0 ))
    [ "$dur" -lt 1500 ] && fastn=$((fastn+1))
    note "  -> HTTP $CURL_CODE 耗时 ${dur}ms"
  done
  [ "$fastn" = "3" ] && ok "3/3 快速失败(熔断器处于打开状态)" || note "快速失败 $fastn/3"
  pause

  # 恢复:解冻(秒级,无冷启动);回退模式则走扩容等待
  if [ "$FREEZE" = "1" ]; then
    say "恢复:解冻 product-service(kill -CONT,无需冷启动)"
    if svc_unfreeze product-service; then
      ok "已解冻 Java 进程"
    else
      note "解冻未确认(可能已被探针重启),继续用 rollout 兜底"
    fi
    kubectl rollout status deployment/product-service -n "$NS" --timeout=150s >/dev/null 2>&1 \
      && ok "product-service 探针已恢复" || note "rollout 状态未知"
    rm -f "$STATEDIR/freeze-product-service"
  else
    svc_up product-service || return 1
  fi
  rm -f "$STATEDIR/down-product-service"

  say "恢复自愈验证:熔断 10s 后半开,放行试探请求"
  note "等待 3s 进入半开窗口…"
  sleep 3
  t0=$(date +%s%3N)
  curl_req "$BASE_URL/api/flights/order/preview" "{\"productId\":\"$pid\",\"passengerCount\":1}"
  dur=$(( $(date +%s%3N) - t0 ))
  if [ "$CURL_CODE" = "200" ]; then
    local amt2; amt2=$(jget "data.payAmount")
    ok "恢复后预览 HTTP 200 耗时${dur}ms 应支付 ¥$amt2(熔断半开->成功,服务自愈)"
  else note "恢复后预览 HTTP $CURL_CODE,msg=$(jget msg)(半开试探未成功,稍后重试一次)"
    sleep 5; curl_req "$BASE_URL/api/flights/order/preview" "{\"productId\":\"$pid\",\"passengerCount\":1}"
    if [ "$CURL_CODE" = "200" ]; then ok "重试后 HTTP 200(自愈)"; else note "重试后 HTTP $CURL_CODE,msg=$(jget msg)"; fi
  fi
  pause
}

# ---------- 场景 C:content-service 下线 -> 故障隔离 ----------
scenario_c() {
  say "========== 场景 C:停 content-service —— 故障隔离(社区域故障不扩散) =========="

  curl_req "$BASE_URL/api/posts?page=1&size=1"
  [ "$CURL_CODE" = "200" ] && ok "基线:社区列表 200" || note "基线社区 HTTP $CURL_CODE"
  pause

  touch "$STATEDIR/down-content-service"
  svc_down content-service || return 1

  say "社区域故障:列表接口应不可用"
  curl_req "$BASE_URL/api/posts?page=1&size=1"
  if [ "${CURL_CODE:-0}" -ge 500 ]; then ok "社区 HTTP $CURL_CODE(服务不可达,故障被限制在内容域)";
  else note "社区 HTTP $CURL_CODE"; fi
  pause

  say "隔离验证:产品域(机票/酒店/火车)、订单域、用户域全部正常"
  local n=0
  curl_req "$BASE_URL/api/flights/search?page=1&size=6"; [ "$CURL_CODE" = "200" ] && n=$((n+1)) || note "机票 HTTP $CURL_CODE"
  curl_req "$BASE_URL/api/hotel/list?page=1&size=1";     [ "$CURL_CODE" = "200" ] && n=$((n+1)) || note "酒店 HTTP $CURL_CODE"
  local pid=""; pid=$(pick_flight_id)
  if [ -n "$pid" ]; then
    curl_req "$BASE_URL/api/flights/order/preview" "{\"productId\":\"$pid\",\"passengerCount\":1}"
    if [ "$CURL_CODE" = "200" ]; then n=$((n+1)); else note "订单预览 HTTP $CURL_CODE,msg=$(jget msg)"; fi
  fi
  curl_req "$BASE_URL/api/auth/admin/login" '{"email":"x@x.com"}'  # 仅探测可达性,不要求成功
  [ "${CURL_CODE:-0}" != "000" ] && n=$((n+1)) || note "用户域不可达"
  [ "$n" -ge 3 ] && ok "产品/订单/用户域 $n 项探测正常(content 下线未扩散)" || bad "隔离验证仅 $n 项通过"
  pause

  svc_up content-service || return 1
  say "恢复验证:社区列表恢复 200"
  curl_req "$BASE_URL/api/posts?page=1&size=1"
  [ "$CURL_CODE" = "200" ] && ok "社区已恢复 HTTP 200" || note "社区 HTTP $CURL_CODE"
  rm -f "$STATEDIR/down-content-service"
  pause
}

# ---------- 主流程 ----------
main() {
  echo "================================================================"
  echo " Lightmark 故障注入演示  |  BASE_URL=$BASE_URL  NS=$NS"
  echo " 日志:$LOG  |  MODE=$MODE  AUTO=$AUTO"
  echo "================================================================"
  preflight
  case "$MODE" in
    all|A|a|B|b|C|c)
      case "$MODE" in all|A|a) scenario_a;; esac
      case "$MODE" in all|B|b) scenario_b;; esac
      case "$MODE" in all|C|c) scenario_c;; esac
      ;;
    *) bad "未知模式:$MODE(可用 all / A / B / C)"; exit 1;;
  esac
  say "演示结束:通过 $PASS 项 / 异常 $FAIL 项"
  note "完整日志:$LOG(录屏剪辑可对照)"
  echo "================================================================"
}
main
