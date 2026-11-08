"""Renders the This Is Meat cover images.

cover.png         1280x720  - main cover for the MC百科 entry
cover-square.png   800x800  - square fallback, in case the thumbnail gets cropped

Flat typographic layout, no gradients, no generated art: Chinese text rendered by a
generative model is always garbled, and mod pages get judged on that.
"""

from PIL import Image, ImageDraw, ImageFont
import os

BG = (242, 237, 228)        # warm paper
INK = (28, 26, 23)          # near black
MUTED = (122, 114, 104)     # warm grey
BODY = (64, 58, 51)
ACCENT = (176, 58, 42)      # muted brick red
HAIRLINE = (210, 201, 188)

FONT_DIR = "C:/Windows/Fonts"


def pick(*names):
    for name in names:
        path = os.path.join(FONT_DIR, name)
        if os.path.exists(path):
            return path
    raise SystemExit(f"none of these fonts exist: {names}")


LATIN_BOLD = pick("seguisb.ttf", "segoeuib.ttf", "arialbd.ttf")
LATIN_REG = pick("segoeui.ttf", "arial.ttf")
CN_BOLD = pick("msyhbd.ttc", "msyhbd.ttf", "simhei.ttf")
CN_REG = pick("msyh.ttc", "msyh.ttf", "simhei.ttf")


def font(path, size):
    return ImageFont.truetype(path, size)


def bullets(d, x, y, items, size=24, gap=22):
    f = font(CN_REG, size)
    dot = font(CN_REG, size)
    for i, text in enumerate(items):
        d.text((x, y), text, font=f, fill=MUTED)
        x += d.textlength(text, font=f) + gap
        if i < len(items) - 1:
            d.text((x - gap / 2 - 4, y), "·", font=dot, fill=(190, 181, 168))


# ---------------------------------------------------------------------------
# 16:9 main cover
# ---------------------------------------------------------------------------
W, H, M = 1280, 720, 96
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

d.rectangle([M, 92, W - M, 93], fill=HAIRLINE)
d.text((M, 118), "MINECRAFT 1.20.1   ·   FORGE 47.x", font=font(LATIN_REG, 21), fill=MUTED)
d.text((M - 4, 170), "This Is Meat", font=font(LATIN_BOLD, 116), fill=INK)
d.rectangle([M, 330, M + 84, 338], fill=ACCENT)
d.text((M, 372), "这是肉食", font=font(CN_BOLD, 62), fill=INK)
d.text((M, 486), "让「只能吃肉」的起源，认出所有模组的肉", font=font(CN_REG, 34), fill=BODY)
bullets(d, M, 596, ["无前置模组", "装了就生效", "服务端可用"])
d.rectangle([M, H - 92, W - M, H - 91], fill=HAIRLINE)

S = 250
d.rectangle([W - M - S, 176, W - M, 176 + S], fill=ACCENT)

here = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs")
os.makedirs(here, exist_ok=True)
img.save(os.path.join(here, "cover.png"), "PNG")
print("cover.png", img.size)

# ---------------------------------------------------------------------------
# 1:1 square fallback - everything centred so any crop keeps the title
# ---------------------------------------------------------------------------
W2 = 800
img2 = Image.new("RGB", (W2, W2), BG)
d2 = ImageDraw.Draw(img2)

d2.rectangle([0, 0, W2, 14], fill=ACCENT)

f_kicker = font(LATIN_REG, 22)
f_title = font(LATIN_BOLD, 92)
f_cn = font(CN_BOLD, 54)
f_tag = font(CN_REG, 27)

title = "This Is Meat"
cn = "这是肉食"
tag = "认出所有模组的肉"

for text, f, y, fill in [
    ("MINECRAFT 1.20.1 · FORGE 47.x", f_kicker, 132, MUTED),
    (title, f_title, 186, INK),
    (cn, f_cn, 330, INK),
    (tag, f_tag, 430, BODY),
]:
    d2.text(((W2 - d2.textlength(text, font=f)) / 2, y), text, font=f, fill=fill)

d2.rectangle([(W2 - 84) / 2, 312, (W2 + 84) / 2, 320], fill=ACCENT)

f_b = font(CN_REG, 23)
line = "无前置模组  ·  装了就生效  ·  服务端可用"
d2.text(((W2 - d2.textlength(line, font=f_b)) / 2, 640), line, font=f_b, fill=MUTED)
d2.rectangle([0, W2 - 14, W2, W2], fill=ACCENT)

img2.save(os.path.join(here, "cover-square.png"), "PNG")
print("cover-square.png", img2.size)
