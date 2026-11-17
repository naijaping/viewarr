"""Viewarr "Play Stack" brand pictures (2026-11-17): the Android TV banner (320 x 180 and 640 x 360).

The mark is three rounded play buttons (opacity 0.3 / 0.6 / 1), emerald #34D399 -> cyan #06B6D4, beside a lowercase
"viewarr" in Plus Jakarta Sans ExtraBold. The same shapes as ui/common/Logo.kt (a 200 x 200 square).

Run where Pillow is installed, with this folder and the font beside it:
  python stack.py <font.ttf> <out-dir>
"""
import sys
from PIL import Image, ImageDraw, ImageFont

FROM = (0x34, 0xD3, 0x99)
TO = (0x06, 0xB6, 0xD4)
BG = (0x0B, 0x0D, 0x14)


def quad(p0, c, p1, n=12):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * p1[0],
             (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * p1[1]) for t in (i / n for i in range(n + 1))]


def tri(l, t, tip, b):
    mid = (t + b) / 2
    pts = quad((l, t + 10), (l, t), (l + 9, t + 5))
    pts += quad((tip - 8, mid - 8), (tip, mid), (tip - 8, mid + 8))
    pts += quad((l + 9, b - 5), (l, b), (l, b - 10))
    return pts


SHAPES = [(tri(76, 34, 178, 166), 0.3), (tri(52, 40, 146, 160), 0.6), (tri(26, 46, 112, 154), 1.0)]


def gradient(size):
    w, h = size
    g = Image.new('RGB', size)
    px = g.load()
    for y in range(h):
        for x in range(w):
            f = (x / max(w - 1, 1) + y / max(h - 1, 1)) / 2
            px[x, y] = tuple(round(FROM[i] + (TO[i] - FROM[i]) * f) for i in range(3))
    return g


def mark(px):
    """The mark, px square, on transparent."""
    s = px / 200
    out = Image.new('RGBA', (px, px), (0, 0, 0, 0))
    grad = gradient((px, px))
    for pts, a in SHAPES:
        m = Image.new('L', (px, px), 0)
        ImageDraw.Draw(m).polygon([(x * s, y * s) for x, y in pts], fill=round(255 * a))
        layer = Image.new('RGBA', (px, px), (0, 0, 0, 0))
        layer.paste(grad, (0, 0), m)
        out = Image.alpha_composite(out, layer)
    return out


def banner(font_path, w, h):
    k = 4  # drawn 4x, then scaled down: smooth edges
    W, H = w * k, h * k
    img = Image.new('RGBA', (W, H), BG + (255,))
    m = mark(int(H * 0.42))
    font = ImageFont.truetype(font_path, int(H * 0.25))
    try:
        font.set_variation_by_axes([800])
    except Exception:
        pass
    d = ImageDraw.Draw(img)
    box = d.textbbox((0, 0), 'viewarr', font=font)
    tw, th = box[2] - box[0], box[3] - box[1]
    gap = int(m.width * 0.06)
    total = m.width + gap + tw
    x0 = (W - total) // 2
    img.alpha_composite(m, (x0, (H - m.height) // 2))
    d.text((x0 + m.width + gap - box[0], (H - th) // 2 - box[1]), 'viewarr', font=font, fill=(255, 255, 255, 255))
    return img.resize((w, h), Image.LANCZOS).convert('RGB')


def icon(px):
    """The app icon for the README / GitHub: the mark on the dark rounded square."""
    k = 4
    P = px * k
    img = Image.new('RGBA', (P, P), (0, 0, 0, 0))
    ImageDraw.Draw(img).rounded_rectangle((0, 0, P - 1, P - 1), radius=int(P * 0.22), fill=BG + (255,))
    m = mark(int(P * 0.62))
    img.alpha_composite(m, ((P - m.width) // 2 + int(P * 0.02), (P - m.height) // 2))
    return img.resize((px, px), Image.LANCZOS)


if __name__ == '__main__':
    font, out = sys.argv[1], sys.argv[2]
    banner(font, 320, 180).save(f'{out}/banner_xhdpi.png')
    banner(font, 640, 360).save(f'{out}/banner_xxxhdpi.png')
    # GitHub: docs/images/banner.png (shown 640 wide, drawn at twice that) and docs/images/icon.png.
    banner(font, 1280, 720).save(f'{out}/banner_readme.png')
    icon(512).save(f'{out}/icon.png')
    print('ok')
