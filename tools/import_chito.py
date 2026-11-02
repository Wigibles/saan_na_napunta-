"""Imports Chito poses into the app as 480 px lossless WebP drawables.

The source PNGs are 1600 x 1600 (1-2 MB each); decoding several of those per screen is slow on
budget phones. Some source files are named after a different pose than they show, so the mapping
below goes by what each picture actually contains.

usage: python tools/import_chito.py SOURCE_DIR app/src/main/res/drawable
"""
import os
import sys

from PIL import Image, ImageDraw

SIZE = 480

# source file (without _1600.png) -> drawable name
POSES = {
    "improved_default": "chito_default",
    "improved_rich": "chito_rich",
    "improved_broke": "chito_broke",
    "improved_celebrate": "chito_celebrate",
    "improved_confused": "chito_confused",
    "waving": "chito_waving",
    "thinking": "chito_thinking",
    "thumbsup": "chito_thumbsup",
    "sleeping": "chito_sleeping",
    "celebrate2": "chito_cheer",
    "facepalm": "chito_facepalm",
    "hearteyes": "chito_hearteyes",
    "laptop": "chito_laptop",
    "pointing": "chito_pointing",
    "counting": "chito_phone",      # shows Chito holding a phone with a budget app
    "detective": "chito_paying",    # shows Chito holding out cash
    "paying": "chito_confetti",     # shows Chito celebrating with confetti
    "phone": "chito_running",       # shows Chito running, money flying from his wallet
    "piggy": "chito_detective",     # shows Chito with a magnifying glass
    "running": "chito_counting",    # shows Chito counting coins with a calculator
    "shocked": "chito_piggy",       # shows Chito hugging a piggy bank
}

# Background pockets enclosed by the art (e.g. inside a curled tail), in 1600 px source coordinates
ENCLOSED_BACKGROUND = {
    "waving": [(506, 1160)],
}


def clear_edge_background(im, extra_seeds=()):
    """Makes a solid background transparent: flood-fills near-white pixels connected to the edges
    (plus any listed enclosed pockets), so light areas inside the art (eye whites, belly) stay opaque."""
    rgb = im.convert("RGB")
    marker = (1, 254, 1)  # a color the art doesn't use
    w, h = rgb.size
    for seed in [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1), *extra_seeds]:
        if rgb.getpixel(seed) != marker:
            ImageDraw.floodfill(rgb, seed, marker, thresh=40)
    out = im.copy()
    pixels_rgb = rgb.load()
    pixels_out = out.load()
    for y in range(h):
        for x in range(w):
            if pixels_rgb[x, y] == marker:
                pixels_out[x, y] = (255, 255, 255, 0)
    return out


if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    for source, name in POSES.items():
        path = os.path.join(src, f"chito_{source}_1600.png")
        if not os.path.exists(path):
            path = os.path.join(src, f"{source}_1600.png")
        im = Image.open(path).convert("RGBA")
        if im.getpixel((0, 0))[3] == 255:
            im = clear_edge_background(im, ENCLOSED_BACKGROUND.get(source, ()))
        im = im.resize((SIZE, SIZE), Image.LANCZOS)
        for ext in (".png", ".webp", ".jpg"):
            old = os.path.join(dst, name + ext)
            if os.path.exists(old):
                os.remove(old)
        out = os.path.join(dst, name + ".webp")
        im.save(out, "WEBP", lossless=True, quality=100, method=6)
        print(f"{name}.webp {os.path.getsize(out) // 1024} KB  <- {os.path.basename(path)}")
