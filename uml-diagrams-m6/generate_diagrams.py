import math
import os
from xml.sax.saxutils import escape

from PIL import Image, ImageDraw, ImageFont


OUT_DIR = os.path.dirname(os.path.abspath(__file__))

BG = "#e7e7e7"
TEXT = "#2d333b"
LINE = "#222222"
LIGHT_BLUE = "#d7e5f7"
HEADER_BLUE = "#d5e2f3"
CLASS_FILL = "#eee1cf"
CLASS_HEAD = "#f1dec5"
ALT = "#f4b35f"

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
    r"C:\Windows\Fonts\simsun.ttc",
]
FONT_PATH = next((p for p in FONT_CANDIDATES if os.path.exists(p)), None)


def get_font(size, scale=1):
    if FONT_PATH:
        return ImageFont.truetype(FONT_PATH, int(size * scale))
    return ImageFont.load_default()


def text_size(text, size):
    font = get_font(size, 1)
    return font.getsize(text)


def wrap_text(text, max_width, size):
    parts = []
    for raw in str(text).split("\n"):
        if not raw:
            parts.append("")
            continue
        line = ""
        for ch in raw:
            candidate = line + ch
            if line and text_size(candidate, size)[0] > max_width:
                parts.append(line)
                line = ch
            else:
                line = candidate
        parts.append(line)
    return parts


def hex_to_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


class Canvas:
    def __init__(self, width, height, bg=BG, scale=2):
        self.width = width
        self.height = height
        self.bg = bg
        self.scale = scale
        self.image = Image.new("RGB", (width * scale, height * scale), hex_to_rgb(bg))
        self.draw = ImageDraw.Draw(self.image)
        self.svg = [
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
            f'<rect width="100%" height="100%" fill="{bg}"/>',
            '<style>text{font-family:"Microsoft YaHei","SimHei",Arial,sans-serif;}</style>',
        ]

    def p(self, value):
        return int(round(value * self.scale))

    def xy(self, x, y):
        return (self.p(x), self.p(y))

    def rect(self, x, y, w, h, fill=None, outline=LINE, width=2, radius=0, dash=None):
        box = [self.p(x), self.p(y), self.p(x + w), self.p(y + h)]
        dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
        if radius and hasattr(self.draw, "rounded_rectangle"):
            self.draw.rounded_rectangle(
                box,
                radius=self.p(radius),
                fill=hex_to_rgb(fill) if fill else None,
                outline=hex_to_rgb(outline) if outline else None,
                width=self.p(width) if outline else 1,
            )
            self.svg.append(
                f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" ry="{radius}" '
                f'fill="{fill or "none"}" stroke="{outline or "none"}" stroke-width="{width}"'
                f'{dash_attr}/>'
            )
        else:
            self.draw.rectangle(
                box,
                fill=hex_to_rgb(fill) if fill else None,
                outline=hex_to_rgb(outline) if outline else None,
                width=self.p(width) if outline else 1,
            )
            self.svg.append(
                f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{fill or "none"}" '
                f'stroke="{outline or "none"}" stroke-width="{width}"'
                f'{dash_attr}/>'
            )

    def ellipse(self, x, y, w, h, fill=None, outline=LINE, width=2):
        box = [self.p(x), self.p(y), self.p(x + w), self.p(y + h)]
        self.draw.ellipse(
            box,
            fill=hex_to_rgb(fill) if fill else None,
            outline=hex_to_rgb(outline) if outline else None,
            width=self.p(width) if outline else 1,
        )
        self.svg.append(
            f'<ellipse cx="{x + w / 2}" cy="{y + h / 2}" rx="{w / 2}" ry="{h / 2}" '
            f'fill="{fill or "none"}" stroke="{outline or "none"}" stroke-width="{width}"/>'
        )

    def line(self, x1, y1, x2, y2, fill=LINE, width=2, dash=None):
        if dash:
            self._dashed_line(x1, y1, x2, y2, fill, width, dash)
            self.svg.append(
                f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{fill}" '
                f'stroke-width="{width}" stroke-dasharray="{dash}"/>'
            )
        else:
            self.draw.line([self.xy(x1, y1), self.xy(x2, y2)], fill=hex_to_rgb(fill), width=self.p(width))
            self.svg.append(
                f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{fill}" stroke-width="{width}"/>'
            )

    def polyline(self, points, fill=LINE, width=2, dash=None):
        for a, b in zip(points, points[1:]):
            self.line(a[0], a[1], b[0], b[1], fill=fill, width=width, dash=dash)
        pts = " ".join(f"{x},{y}" for x, y in points)
        dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
        self.svg.append(
            f'<polyline points="{pts}" fill="none" stroke="{fill}" stroke-width="{width}"'
            f'{dash_attr}/>'
        )

    def polygon(self, points, fill=LINE, outline=None, width=1):
        pts_scaled = [self.xy(x, y) for x, y in points]
        self.draw.polygon(pts_scaled, fill=hex_to_rgb(fill))
        if outline:
            self.draw.line(pts_scaled + [pts_scaled[0]], fill=hex_to_rgb(outline), width=self.p(width))
        pts = " ".join(f"{x},{y}" for x, y in points)
        self.svg.append(
            f'<polygon points="{pts}" fill="{fill}" stroke="{outline or fill}" stroke-width="{width}"/>'
        )

    def text(self, x, y, text, size=22, fill=TEXT, anchor="lt", align="left", max_width=None, line_gap=4):
        lines = wrap_text(text, max_width, size) if max_width else str(text).split("\n")
        font = get_font(size, self.scale)
        base_font = get_font(size, 1)
        line_h = base_font.getsize("国Hg")[1] + line_gap
        widths = [base_font.getsize(line)[0] for line in lines]
        block_w = max(widths) if widths else 0
        block_h = len(lines) * line_h - line_gap if lines else 0
        if anchor in ("mt", "mm", "mb"):
            x0 = x - block_w / 2
        elif anchor in ("rt", "rm", "rb"):
            x0 = x - block_w
        else:
            x0 = x
        if anchor in ("lm", "mm", "rm"):
            y0 = y - block_h / 2
        elif anchor in ("lb", "mb", "rb"):
            y0 = y - block_h
        else:
            y0 = y
        for i, line in enumerate(lines):
            w = widths[i]
            if align == "center":
                tx = x0 + (block_w - w) / 2
                svg_anchor = "middle"
                svg_x = x0 + block_w / 2
            elif align == "right":
                tx = x0 + block_w - w
                svg_anchor = "end"
                svg_x = x0 + block_w
            else:
                tx = x0
                svg_anchor = "start"
                svg_x = tx
            ty = y0 + i * line_h
            self.draw.text(self.xy(tx, ty), line, font=font, fill=hex_to_rgb(fill))
            self.svg.append(
                f'<text x="{svg_x}" y="{ty}" font-size="{size}" fill="{fill}" '
                f'text-anchor="{svg_anchor}" dominant-baseline="text-before-edge">{escape(line)}</text>'
            )
        return block_w, block_h

    def _dashed_line(self, x1, y1, x2, y2, fill, width, dash):
        pattern = [float(x) for x in str(dash).replace(",", " ").split() if x]
        if not pattern:
            pattern = [8, 6]
        dx, dy = x2 - x1, y2 - y1
        length = math.hypot(dx, dy)
        if length == 0:
            return
        ux, uy = dx / length, dy / length
        dist = 0
        idx = 0
        draw_on = True
        while dist < length:
            seg = pattern[idx % len(pattern)]
            end = min(length, dist + seg)
            if draw_on:
                ax, ay = x1 + ux * dist, y1 + uy * dist
                bx, by = x1 + ux * end, y1 + uy * end
                self.draw.line([self.xy(ax, ay), self.xy(bx, by)], fill=hex_to_rgb(fill), width=self.p(width))
            dist = end
            idx += 1
            draw_on = not draw_on

    def arrow(self, x1, y1, x2, y2, label=None, dashed=False, fill=LINE, width=2, label_size=20, max_label_width=None):
        dash = "9 7" if dashed else None
        self.line(x1, y1, x2, y2, fill=fill, width=width, dash=dash)
        self.arrow_head(x1, y1, x2, y2, fill=fill)
        if label:
            max_w = max_label_width or max(110, min(abs(x2 - x1) - 24, 360))
            tx = (x1 + x2) / 2
            ty = y1 - 28
            bw, bh = self.text(tx, ty, label, size=label_size, fill=TEXT, anchor="mb", align="center", max_width=max_w)
            pad = 4
            self.rect(tx - bw / 2 - pad, ty - bh - pad, bw + pad * 2, bh + pad * 2, fill=BG, outline=None, width=0)
            self.text(tx, ty, label, size=label_size, fill=TEXT, anchor="mb", align="center", max_width=max_w)

    def arrow_head(self, x1, y1, x2, y2, fill=LINE):
        angle = math.atan2(y2 - y1, x2 - x1)
        size = 14
        p1 = (x2, y2)
        p2 = (x2 - size * math.cos(angle - math.pi / 7), y2 - size * math.sin(angle - math.pi / 7))
        p3 = (x2 - size * math.cos(angle + math.pi / 7), y2 - size * math.sin(angle + math.pi / 7))
        self.polygon([p1, p2, p3], fill=fill)

    def save(self, filename):
        png_path = os.path.join(OUT_DIR, filename + ".png")
        svg_path = os.path.join(OUT_DIR, filename + ".svg")
        img = self.image.resize((self.width, self.height), Image.LANCZOS)
        img.save(png_path)
        self.svg.append("</svg>")
        with open(svg_path, "w", encoding="utf-8") as f:
            f.write("\n".join(self.svg))
        return png_path, svg_path


def draw_actor(c, x, y, label, size=1.0):
    head_r = 18 * size
    c.ellipse(x - head_r, y - 76 * size, head_r * 2, head_r * 2, fill="#fff3df", outline="#8a8a8a", width=2)
    c.line(x, y - 58 * size, x, y + 8 * size, fill="#777777", width=3)
    c.line(x - 42 * size, y - 24 * size, x + 42 * size, y - 24 * size, fill="#777777", width=3)
    c.line(x, y + 8 * size, x - 38 * size, y + 70 * size, fill="#777777", width=3)
    c.line(x, y + 8 * size, x + 38 * size, y + 70 * size, fill="#777777", width=3)
    c.text(x, y + 90 * size, label, size=int(26 * size), anchor="mt", align="center")


def draw_use_case_ellipse(c, cx, cy, title):
    w, h = 430, 112
    c.ellipse(cx - w / 2, cy - h / 2, w, h, fill=LIGHT_BLUE, outline="#858585", width=2)
    c.text(cx, cy - 22, "<<UseCase>>", size=24, anchor="mm", align="center")
    c.text(cx, cy + 20, title, size=27, anchor="mm", align="center", max_width=w - 44)
    return (cx - w / 2, cy - h / 2, w, h)


def draw_m6_use_case():
    c = Canvas(1850, 1080)
    c.text(75, 48, "用例图", size=30, fill=TEXT)
    c.text(720, 84, "智能行程", size=24, fill="#555555", anchor="mt", align="center")
    c.text(1180, 84, "社区互动", size=24, fill="#555555", anchor="mt", align="center")

    draw_actor(c, 190, 540, "用户", size=1.05)
    draw_actor(c, 1660, 330, "AI服务", size=0.9)
    draw_actor(c, 1660, 790, "官方/管理员", size=0.9)

    left = [
        ("manual", "手动规划行程", 700, 180),
        ("ai_plan", "AI生成个性化行程", 700, 320),
        ("share", "分享/导出行程", 700, 460),
        ("reminder", "接收行前提醒", 700, 600),
        ("local", "当地玩乐推荐", 700, 740),
        ("auto_post", "游记自动生成", 700, 880),
    ]
    right = [
        ("review", "发布订单评价", 1180, 250),
        ("post", "发布游记", 1180, 390),
        ("interact", "点赞/评论/收藏", 1180, 530),
        ("qa", "问答社区", 1180, 670),
        ("sentiment", "评论情感分析与自动回复", 1180, 810),
        ("bot", "智能问答机器人", 1180, 950),
    ]
    boxes = {}
    for key, title, x, y in left + right:
        boxes[key] = draw_use_case_ellipse(c, x, y, title)

    # User links use the trunk style seen in the existing diagrams.
    active = ["manual", "ai_plan", "share", "reminder", "local", "auto_post", "review", "post", "interact", "qa", "bot"]
    trunk_x = 410
    c.line(260, 540, trunk_x, 540, fill=LINE, width=3)
    c.line(trunk_x, 180, trunk_x, 950, fill=LINE, width=3)
    for key in active:
        x, y, w, h = boxes[key]
        c.line(trunk_x, y + h / 2, x, y + h / 2, fill=LINE, width=3)

    # Service-side links.
    ai_links = ["ai_plan", "auto_post", "sentiment", "bot"]
    ai_trunk_x = 1495
    c.line(1590, 330, ai_trunk_x, 330, fill=LINE, width=2)
    c.line(ai_trunk_x, 320, ai_trunk_x, 950, fill=LINE, width=2)
    for key in ai_links:
        x, y, w, h = boxes[key]
        c.line(x + w, y + h / 2, ai_trunk_x, y + h / 2, fill=LINE, width=2)

    admin_links = ["qa", "sentiment"]
    admin_trunk_x = 1515
    c.line(1590, 790, admin_trunk_x, 790, fill=LINE, width=2)
    c.line(admin_trunk_x, 670, admin_trunk_x, 810, fill=LINE, width=2)
    for key in admin_links:
        x, y, w, h = boxes[key]
        c.line(x + w, y + h / 2, admin_trunk_x, y + h / 2, fill=LINE, width=2)

    c.save("01_M6_use_case")


def draw_sequence_base(c, title, subtitle, participants, messages, alt_frames=None, note=None):
    alt_frames = alt_frames or []
    c.text(80, 50, title, size=30, fill=TEXT)
    c.text(80, 98, subtitle, size=26, fill=TEXT)
    if note:
        c.rect(c.width - 420, 48, 330, 58, fill="#f4eadb", outline="#d1a56d", width=1, radius=4)
        c.text(c.width - 255, 77, note, size=19, fill="#6b4c23", anchor="mm", align="center", max_width=300)

    top = 180
    header_w = 190 if len(participants) >= 6 else 220
    header_h = 72
    left_margin = 190
    right_margin = 190
    xs = {}
    step_x = (c.width - left_margin - right_margin) / (len(participants) - 1)
    for i, name in enumerate(participants):
        x = left_margin + i * step_x
        xs[name] = x
        c.rect(x - header_w / 2, top, header_w, header_h, fill=HEADER_BLUE, outline="#9a9a9a", width=2)
        c.text(x, top + header_h / 2, name, size=22, anchor="mm", align="center", max_width=header_w - 18)

    y0 = 315
    msg_step = 66
    y_for = [y0 + i * msg_step for i in range(len(messages))]
    bottom = y_for[-1] + 105 if messages else 760

    for name, x in xs.items():
        c.line(x, top + header_h, x, bottom, fill="#9a9a9a", width=1, dash="6 8")
        c.rect(x - 8, y0 - 18, 16, bottom - y0 + 22, fill="#d9e5f4", outline="#555555", width=1)

    for frame in alt_frames:
        start = y_for[frame["start"]] - frame.get("top_pad", 45)
        end = y_for[frame["end"]] + frame.get("bottom_pad", 45)
        x1 = xs[frame["from"]] - 150
        x2 = xs[frame["to"]] + 150
        x1 = max(95, x1)
        x2 = min(c.width - 95, x2)
        draw_alt_frame(c, x1, start, x2 - x1, end - start, frame)

    for i, msg in enumerate(messages):
        if msg.get("type") == "gap":
            continue
        y = y_for[i]
        if msg.get("type") == "self":
            x = xs[msg["actor"]]
            draw_self_message(c, x, y, msg["label"])
            continue
        x1 = xs[msg["from"]]
        x2 = xs[msg["to"]]
        offset = 13
        start_x = x1 + (offset if x2 > x1 else -offset)
        end_x = x2 - (offset if x2 > x1 else -offset)
        c.arrow(
            start_x,
            y,
            end_x,
            y,
            label=msg["label"],
            dashed=msg.get("dashed", False),
            label_size=19,
            max_label_width=msg.get("max_label_width"),
        )


def draw_alt_frame(c, x, y, w, h, frame):
    c.rect(x, y, w, h, fill=None, outline=ALT, width=2)
    tag_w, tag_h = 120, 46
    c.polygon(
        [(x, y), (x + tag_w, y), (x + tag_w - 30, y + tag_h), (x, y + tag_h)],
        fill=BG,
        outline=ALT,
        width=2,
    )
    c.text(x + 36, y + 14, frame.get("title", "alt"), size=20, fill=TEXT)
    for split in frame.get("splits", []):
        sy = y + split
        c.line(x, sy, x + w, sy, fill=ALT, width=2, dash="10 8")
    for label, rel_y in frame.get("conditions", []):
        c.text(x + 34, y + rel_y, label, size=20, fill="#5a4a33")


def draw_self_message(c, x, y, label):
    loop_w = 125
    c.line(x + 10, y, x + loop_w, y, width=2)
    c.line(x + loop_w, y, x + loop_w, y + 34, width=2)
    c.arrow(x + loop_w, y + 34, x + 12, y + 34, label=None, width=2)
    c.text(x + loop_w / 2 + 8, y - 25, label, size=19, anchor="mb", align="center", max_width=180)


def sequence_canvas_height(message_count):
    return 315 + max(0, message_count - 1) * 66 + 180


def draw_itinerary_sequence():
    participants = ["用户", "前端行程页面", "行程服务", "AI服务", "数据库", "通知服务"]
    messages = [
        {"from": "用户", "to": "前端行程页面", "label": "填写目的地、天数、预算、偏好"},
        {"from": "前端行程页面", "to": "行程服务", "label": "提交生成行程请求"},
        {"from": "行程服务", "to": "AI服务", "label": "请求生成结构化行程"},
        {"from": "AI服务", "to": "行程服务", "label": "返回每日行程草案", "dashed": True},
        {"from": "AI服务", "to": "行程服务", "label": "返回预置 Mock 行程，不阻塞主流程", "dashed": True},
        {"type": "gap"},
        {"from": "行程服务", "to": "数据库", "label": "保存 travel_plan / travel_plan_item"},
        {"from": "数据库", "to": "行程服务", "label": "返回保存结果", "dashed": True},
        {"from": "行程服务", "to": "前端行程页面", "label": "返回行程详情", "dashed": True},
        {"from": "用户", "to": "前端行程页面", "label": "拖拽调整景点顺序"},
        {"from": "前端行程页面", "to": "行程服务", "label": "提交修改后的行程"},
        {"from": "行程服务", "to": "数据库", "label": "更新行程数据"},
        {"from": "用户", "to": "前端行程页面", "label": "分享或导出"},
        {"from": "前端行程页面", "to": "行程服务", "label": "请求分享链接 / PDF导出"},
        {"from": "行程服务", "to": "数据库", "label": "更新 share_token / 查询行程"},
        {"from": "行程服务", "to": "前端行程页面", "label": "返回分享链接或文件地址", "dashed": True},
        {"from": "行程服务", "to": "通知服务", "label": "注册出发前 24 小时 / 2 小时提醒", "max_label_width": 320},
    ]
    c = Canvas(2000, sequence_canvas_height(len(messages)))
    alt_frames = [
        {
            "from": "行程服务",
            "to": "AI服务",
            "start": 2,
            "end": 4,
            "title": "alt",
            "bottom_pad": 30,
            "splits": [154],
            "conditions": [("[AI生成成功]", 28), ("[AI服务失败]", 180)],
        }
    ]
    draw_sequence_base(c, "系统顺序图", "智能行程生成与分享", participants, messages, alt_frames)
    c.save("02_M6_itinerary_sequence")


def draw_community_sequence():
    participants = ["用户", "前端社区页面", "社区服务", "AI服务", "数据库", "通知服务"]
    messages = [
        {"from": "用户", "to": "前端社区页面", "label": "浏览游记 / 问答"},
        {"from": "前端社区页面", "to": "社区服务", "label": "查询列表"},
        {"from": "社区服务", "to": "数据库", "label": "查询 post / question / comment"},
        {"from": "数据库", "to": "社区服务", "label": "返回社区内容", "dashed": True},
        {"from": "社区服务", "to": "前端社区页面", "label": "展示列表", "dashed": True},
        {"from": "用户", "to": "前端社区页面", "label": "发布游记"},
        {"from": "前端社区页面", "to": "社区服务", "label": "提交标题、正文、图片"},
        {"from": "社区服务", "to": "数据库", "label": "保存 post"},
        {"from": "用户", "to": "前端社区页面", "label": "点赞 / 评论 / 收藏"},
        {"from": "前端社区页面", "to": "社区服务", "label": "提交互动请求"},
        {"from": "社区服务", "to": "数据库", "label": "更新 like_count / comment_count"},
        {"from": "社区服务", "to": "通知服务", "label": "通知作者有新互动"},
        {"from": "用户", "to": "前端社区页面", "label": "发布问题"},
        {"from": "前端社区页面", "to": "社区服务", "label": "提交 question"},
        {"from": "社区服务", "to": "AI服务", "label": "尝试智能问答"},
        {"from": "AI服务", "to": "社区服务", "label": "返回答案", "dashed": True},
        {"from": "社区服务", "to": "数据库", "label": "保存 answer"},
        {"from": "社区服务", "to": "通知服务", "label": "通知提问者"},
        {"from": "社区服务", "to": "数据库", "label": "问题进入待回答状态", "dashed": True},
    ]
    c = Canvas(2000, sequence_canvas_height(len(messages)))
    alt_frames = [
        {
            "from": "社区服务",
            "to": "通知服务",
            "start": 14,
            "end": 18,
            "title": "alt",
            "splits": [255],
            "conditions": [("[AI可回答]", 28), ("[AI不可回答]", 282)],
        }
    ]
    draw_sequence_base(c, "系统顺序图", "社区互动与智能问答", participants, messages, alt_frames)
    c.save("03_M6_community_sequence")


def draw_class_box(c, x, y, w, title, fields):
    line_h = 34
    header_h = 56
    h = header_h + 24 + len(fields) * line_h
    c.rect(x + 6, y + 6, w, h, fill="#c8c8c8", outline=None, width=0, radius=8)
    c.rect(x, y, w, h, fill=CLASS_FILL, outline="#777777", width=2, radius=8)
    c.rect(x, y, w, header_h, fill=CLASS_HEAD, outline="#777777", width=2, radius=8)
    c.line(x, y + header_h, x + w, y + header_h, fill="#333333", width=2)
    c.text(x + w / 2, y + header_h / 2, title, size=23, anchor="mm", align="center")
    for i, field in enumerate(fields):
        c.text(x + 22, y + header_h + 20 + i * line_h, "+ " + field, size=20)
    return (x, y, w, h)


def assoc(c, p1, p2, label, m1, m2, label_offset=(0, -12)):
    x1, y1 = p1
    x2, y2 = p2
    c.line(x1, y1, x2, y2, fill="#333333", width=2)
    c.arrow_head(x1, y1, x2, y2, fill=BG)
    c.line(x2 - 1, y2 - 1, x2 + 1, y2 + 1, fill="#333333", width=1)
    mx, my = (x1 + x2) / 2 + label_offset[0], (y1 + y2) / 2 + label_offset[1]
    c.text(mx, my, label, size=18, anchor="mm", align="center")
    c.text(x1 + (14 if x2 > x1 else -14), y1 - 20, m1, size=18, anchor="mm", align="center")
    c.text(x2 + (-14 if x2 > x1 else 14), y2 - 20, m2, size=18, anchor="mm", align="center")


def elbow_assoc(c, points, label, m1, m2, label_point=None):
    c.polyline(points, fill="#333333", width=2)
    c.arrow_head(points[-2][0], points[-2][1], points[-1][0], points[-1][1], fill=BG)
    c.line(points[-1][0] - 1, points[-1][1] - 1, points[-1][0] + 1, points[-1][1] + 1, fill="#333333", width=1)
    if label_point is None:
        label_point = points[len(points) // 2]
    c.text(label_point[0], label_point[1] - 14, label, size=18, anchor="mm", align="center")
    c.text(points[0][0] + 12, points[0][1] - 18, m1, size=18, anchor="mm", align="center")
    c.text(points[-1][0] - 16, points[-1][1] - 18, m2, size=18, anchor="mm", align="center")


def draw_concept_class():
    c = Canvas(1920, 1380)
    c.text(80, 58, "概念类图", size=30, fill=TEXT)
    user = draw_class_box(c, 760, 130, 330, "User", ["id", "phone", "email", "nickname", "avatar", "points", "status"])
    plan = draw_class_box(
        c,
        140,
        160,
        390,
        "TravelPlan",
        ["id", "user_id", "title", "destination", "start_date", "end_date", "plan_data", "is_public", "share_token"],
    )
    item = draw_class_box(
        c,
        140,
        625,
        430,
        "TravelPlanItem",
        ["id", "plan_id", "day_no", "start_time", "end_time", "item_type", "item_name", "location", "sort_order"],
    )
    question = draw_class_box(
        c,
        1230,
        150,
        410,
        "Question",
        ["id", "user_id", "title", "content", "answer", "answer_user_id", "status"],
    )
    post = draw_class_box(c, 710, 555, 370, "Post", ["id", "user_id", "title", "content", "images", "likes", "comments_count", "status"])
    comment = draw_class_box(
        c,
        1230,
        555,
        420,
        "Comment",
        ["id", "target_type", "target_id", "user_id", "parent_id", "content", "likes"],
    )
    order = draw_class_box(c, 725, 965, 350, "Order", ["id", "order_no", "user_id", "order_type", "status"])
    review = draw_class_box(
        c,
        1230,
        930,
        430,
        "Review",
        ["id", "order_id", "user_id", "rating", "content", "images", "sentiment_score", "reply_content"],
    )

    assoc(c, (user[0], user[1] + 120), (plan[0] + plan[2], plan[1] + 120), "creates", "1", "*")
    assoc(c, (plan[0] + plan[2] / 2, plan[1] + plan[3]), (item[0] + item[2] / 2, item[1]), "contains", "1", "*", label_offset=(45, 0))
    assoc(c, (user[0] + user[2] / 2, user[1] + user[3]), (post[0] + post[2] / 2, post[1]), "publishes", "1", "*", label_offset=(70, -16))
    assoc(c, (post[0] + post[2], post[1] + 150), (comment[0], comment[1] + 150), "has", "1", "*")
    assoc(c, (user[0] + user[2], user[1] + 95), (question[0], question[1] + 95), "asks", "1", "*")
    elbow_assoc(
        c,
        [(user[0] + user[2], user[1] + 250), (1125, user[1] + 250), (1125, comment[1] + 65), (comment[0], comment[1] + 65)],
        "writes",
        "1",
        "*",
        label_point=(1130, 470),
    )
    elbow_assoc(
        c,
        [
            (user[0] + user[2], user[1] + 250),
            (1160, user[1] + 250),
            (1160, 510),
            (1710, 510),
            (1710, review[1] + 110),
            (review[0] + review[2], review[1] + 110),
        ],
        "writes",
        "1",
        "*",
        label_point=(1425, 496),
    )
    assoc(c, (order[0] + order[2], order[1] + 95), (review[0], review[1] + 95), "has", "1", "0..1")

    # Self association for threaded comments.
    x = comment[0] + comment[2]
    y = comment[1] + 190
    c.polyline([(x, y), (x + 90, y), (x + 90, y + 105), (x, y + 105)], fill="#333333", width=2)
    c.arrow_head(x + 90, y + 105, x, y + 105, fill=BG)
    c.text(x + 112, y + 54, "replies", size=18, anchor="lm", align="left")
    c.text(x + 24, y - 16, "0..1", size=18)
    c.text(x + 8, y + 118, "*", size=18)

    c.save("04_M6_concept_class")


def draw_train_meal_sequence():
    participants = ["用户", "前端订餐页面", "服务器/订餐订单服务", "数据库", "支付模块", "消息通知服务"]
    messages = [
        {"from": "用户", "to": "前端订餐页面", "label": "进入火车餐购买页"},
        {"from": "前端订餐页面", "to": "服务器/订餐订单服务", "label": "请求车次可售餐品"},
        {"from": "服务器/订餐订单服务", "to": "数据库", "label": "查询车次、供餐时间、餐品列表"},
        {"from": "数据库", "to": "服务器/订餐订单服务", "label": "返回餐品与价格", "dashed": True},
        {"from": "服务器/订餐订单服务", "to": "前端订餐页面", "label": "展示可选餐品", "dashed": True},
        {"from": "用户", "to": "前端订餐页面", "label": "选择餐品、数量、用餐时间"},
        {"from": "前端订餐页面", "to": "服务器/订餐订单服务", "label": "提交订餐订单"},
        {"from": "服务器/订餐订单服务", "to": "数据库", "label": "校验库存并创建订单"},
        {"from": "服务器/订餐订单服务", "to": "前端订餐页面", "label": "返回待支付订单", "dashed": True},
        {"from": "用户", "to": "支付模块", "label": "支付"},
        {"from": "支付模块", "to": "服务器/订餐订单服务", "label": "支付回调"},
        {"from": "服务器/订餐订单服务", "to": "数据库", "label": "更新订单状态、扣减库存"},
        {"from": "服务器/订餐订单服务", "to": "消息通知服务", "label": "发送订餐成功通知"},
        {"from": "消息通知服务", "to": "用户", "label": "站内信/邮件通知", "dashed": True},
        {"from": "服务器/订餐订单服务", "to": "前端订餐页面", "label": "返回失败原因"},
        {"from": "支付模块", "to": "服务器/订餐订单服务", "label": "支付失败回调", "dashed": True},
        {"from": "服务器/订餐订单服务", "to": "数据库", "label": "订单保持待支付或取消"},
    ]
    c = Canvas(2050, sequence_canvas_height(len(messages)))
    alt_frames = [
        {
            "from": "服务器/订餐订单服务",
            "to": "消息通知服务",
            "start": 7,
            "end": 16,
            "title": "alt",
            "splits": [456, 590],
            "conditions": [("[库存充足且支付成功]", 28), ("[库存不足/车次不支持订餐]", 483), ("[支付失败]", 617)],
        }
    ]
    draw_sequence_base(c, "系统顺序图", "火车餐购买流程", participants, messages, alt_frames, note="火车餐为模拟/预留能力")
    c.save("05_train_meal_order_sequence")


def draw_notification_sequence():
    participants = ["业务模块", "通知服务", "数据库", "邮件/站内信服务", "用户"]
    messages = [
        {"from": "业务模块", "to": "通知服务", "label": "触发通知事件"},
        {"type": "self", "actor": "通知服务", "label": "选择通知模板"},
        {"from": "通知服务", "to": "数据库", "label": "保存通知记录"},
        {"from": "通知服务", "to": "邮件/站内信服务", "label": "发送通知"},
        {"from": "邮件/站内信服务", "to": "用户", "label": "送达通知", "dashed": True},
        {"from": "通知服务", "to": "数据库", "label": "状态改为已发送"},
        {"from": "通知服务", "to": "数据库", "label": "记录失败原因，进入重试或待处理状态"},
        {"from": "通知服务", "to": "邮件/站内信服务", "label": "仅发送站内信"},
        {"type": "gap"},
        {"from": "用户", "to": "通知服务", "label": "查看通知列表"},
        {"from": "通知服务", "to": "数据库", "label": "查询用户通知"},
        {"from": "数据库", "to": "通知服务", "label": "返回通知列表", "dashed": True},
        {"from": "通知服务", "to": "用户", "label": "展示通知", "dashed": True},
        {"from": "用户", "to": "通知服务", "label": "标记已读"},
        {"from": "通知服务", "to": "数据库", "label": "更新 read_status"},
    ]
    c = Canvas(1850, sequence_canvas_height(len(messages)))
    alt_frames = [
        {
            "from": "通知服务",
            "to": "邮件/站内信服务",
            "start": 3,
            "end": 7,
            "title": "alt",
            "bottom_pad": 70,
            "splits": [190, 322],
            "conditions": [("[发送成功]", 28), ("[发送失败]", 217), ("[用户关闭邮件通知]", 349)],
        }
    ]
    draw_sequence_base(c, "系统顺序图", "消息通知模块流程", participants, messages, alt_frames)
    c.save("06_notification_sequence")


def draw_design_community_sequence():
    participants = ["用户", "前端社区页面", "社区服务", "数据库", "AI服务", "消息通知服务"]
    messages = [
        {"from": "用户", "to": "前端社区页面", "label": "浏览游记/问答"},
        {"from": "前端社区页面", "to": "社区服务", "label": "请求社区内容"},
        {"from": "社区服务", "to": "数据库", "label": "查询 post、comment、question", "max_label_width": 430},
        {"from": "数据库", "to": "社区服务", "label": "返回内容", "dashed": True},
        {"from": "社区服务", "to": "前端社区页面", "label": "展示内容", "dashed": True},
        {"from": "用户", "to": "前端社区页面", "label": "发布游记或评论"},
        {"from": "前端社区页面", "to": "社区服务", "label": "提交内容"},
        {"from": "社区服务", "to": "数据库", "label": "保存 post/comment"},
        {"from": "社区服务", "to": "消息通知服务", "label": "通知作者有新评论"},
        {"from": "社区服务", "to": "前端社区页面", "label": "返回错误"},
        {"from": "用户", "to": "前端社区页面", "label": "点赞/收藏"},
        {"from": "前端社区页面", "to": "社区服务", "label": "提交互动"},
        {"from": "社区服务", "to": "数据库", "label": "更新互动计数"},
        {"from": "用户", "to": "前端社区页面", "label": "提出问题"},
        {"from": "前端社区页面", "to": "社区服务", "label": "提交 question"},
        {"from": "社区服务", "to": "AI服务", "label": "请求智能回答"},
        {"from": "AI服务", "to": "社区服务", "label": "返回答案", "dashed": True},
        {"from": "社区服务", "to": "数据库", "label": "保存 answer"},
        {"from": "社区服务", "to": "消息通知服务", "label": "通知提问者"},
        {"from": "社区服务", "to": "数据库", "label": "保留待回答状态", "dashed": True},
    ]
    c = Canvas(2050, sequence_canvas_height(len(messages)))
    alt_frames = [
        {
            "from": "社区服务",
            "to": "消息通知服务",
            "start": 6,
            "end": 9,
            "title": "alt",
            "splits": [190],
            "conditions": [("[内容合法]", 28), ("[内容为空/未登录/无权限]", 217)],
        },
        {
            "from": "社区服务",
            "to": "消息通知服务",
            "start": 15,
            "end": 19,
            "title": "alt",
            "splits": [255],
            "conditions": [("[AI回答成功]", 28), ("[AI回答失败]", 282)],
        },
    ]
    draw_sequence_base(c, "系统顺序图", "社区模块流程", participants, messages, alt_frames)
    c.save("07_community_design_sequence")


def main():
    draw_m6_use_case()
    draw_itinerary_sequence()
    draw_community_sequence()
    draw_concept_class()
    draw_train_meal_sequence()
    draw_notification_sequence()
    draw_design_community_sequence()


if __name__ == "__main__":
    main()
