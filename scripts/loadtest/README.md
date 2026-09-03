# 压力测试脚本（云原生实验 + 性能对比）

对应《软件工程基础实践》评分项：**自动扩缩容（3 分）**、**性能对比（4 分）**。

| 脚本 | 用途 | 运行位置 |
| --- | --- | --- |
| `run-load.sh` | 单接口压测（通用） | 任意机器（需能访问被测入口） |
| `perf-compare.sh` | **单体 vs 微服务** 性能对比（同机/同数据/同脚本，各 ≥3 次） | 服务器（两版本都在 k3s 上） |
| `hpa-test.sh` | **HPA 扩缩容演示**：加压→副本数上升→降压→回落 | 服务器（需要 kubectl） |

## 依赖

- `ab`（ApacheBench）：`sudo apt-get install -y apache2-utils`（Ubuntu）或 `brew install ab`
- `perf-compare.sh` / `hpa-test.sh` 还需要 `kubectl` 与集群 metrics-server（k3s 默认自带），
  以及 CPU 指标（`kubectl top nodes` 有输出即可）

## 入口约定

单体与微服务都部署在同一 k3s（Traefik 监听 80/443），按 **Host 头**区分路由：

- 单体入口：`http://127.0.0.1` + `Host: lightmark.ortus.top`
- 微服务入口：`http://127.0.0.1` + `Host: msa.lightmark.ortus.top`

走 HTTP :80 可避免自签名证书问题。脚本默认即按此约定压测。

## 1. 单接口压测

```bash
bash scripts/loadtest/run-load.sh "http://127.0.0.1/api/flights/search?page=1&size=10" 50 5000 flights-search msa.lightmark.ortus.top
```

参数：URL、并发、总请求数、标签、Host 头（可省略）。
输出 `artifacts/load/single-<标签>-<时间>/`：ab 原始报告、summary.tsv、
metrics.log（2s 一次 CPU/内存采样，kubectl top 或 docker stats 自动选择）。

需要带登录态时：`EXTRA_AB_ARGS='-H "Authorization: Bearer <token>"' bash ...`

## 2. 单体 vs 微服务性能对比（各 3 次）

```bash
bash scripts/loadtest/perf-compare.sh
```

默认接口（都可改 `ENDPOINTS`，空格分隔）：
- `/api/flights/search?page=1&size=10`（机票搜索，读接口）
- `/api/hotel/list?page=1&size=10`（酒店列表，读接口）

默认 并发 30 × 每轮 3000 请求 × 3 轮 × 2 版本。输出 `summary.csv`：
`endpoint,version,run,conc,requests,failed,rps,avg_ms,p95_ms`，每轮 ab 原始报告与资源采样均在目录内。

> 前提：微服务库数据与单体一致（数据库拆分流程 `split-mysql.sh` 已保证）；
> 对比在**同一台服务器**上执行，避免网络差异。

## 3. HPA 自动扩缩容演示

```bash
bash scripts/loadtest/hpa-test.sh            # 默认打 product-service（min1/max4，CPU 60%）
# 换目标服务：
# SERVICE=user-service bash scripts/loadtest/hpa-test.sh
# 提高压力（若未触发扩容）：
# CONC=200 REQ=60000 bash scripts/loadtest/hpa-test.sh
```

流程：基线采样 → `ab -n 30000 -c 100` 持续加压 → 每 2s 记录 `副本数/CPU/内存` 到
`timeline.log`，观察到副本数 > min 即扩容成功 → 压测结束后等待缩容（HPA 有稳定期，
通常几分钟）。输出：时间线、压测原始报告（吞吐/平均/P95/错误率）、扩容后
`kubectl get hpa/pods` 快照。

> 建议先把单体缩容腾出资源再演示：
> `kubectl scale deploy lightmark-backend --replicas=0`
> 节点资源充足才能扩到 4 副本。

## 结果归档

所有原始数据在 `artifacts/load/`（已加入 .gitignore）。提交材料时整体拷贝到
`04_tests/load/` 目录即可。
