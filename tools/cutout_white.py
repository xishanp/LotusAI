#!/usr/bin/env python3
"""
把「纯白底」的立绘抠成透明底 —— 一次性离线处理，App 里零开销。

为什么需要它：生成的立绘都是"白纸上一张画"，白边是真的白色像素、不是透明。
App 背景换成苹果风冷白 (#F6F6F9) 之后，那张纯白纸就会读成一块**方块**（用户原话：
「由于我们不是纯白的，但是生成的有些绘图它背景是纯白的，看上去会有割裂感」）。

做法（比按亮度整体去白好得多）：
  从四条边往里 flood fill「和边角同色 ±thresh」的连通区域 —— 只吃掉**外部**的白纸，
  角色内部的白色/浅灰（脸、衣服、淡阴影）原样保留。
  最后给 alpha 一点高斯模糊，边缘不留锯齿。

用法：
    python3 tools/cutout_white.py app/src/main/res/drawable-nodpi/*.png
原图会先备份到 design/mascot/originals/。
"""
import os
import sys
import shutil

from PIL import Image, ImageDraw, ImageFilter

# 只有"离纯白 14 以内"的像素算背景纸；调大就会啃进角色的浅灰阴影，调小会留白圈。
THRESH = 14
BLUR = 1.0

BACKUP_DIR = os.path.join(os.path.dirname(__file__), "..", "design", "mascot", "originals")


def cut(path: str) -> bool:
    im = Image.open(path)
    if im.mode == "RGBA" and im.getchannel("A").getextrema()[0] == 0:
        print(f"  skip (already transparent): {path}")
        return False
    im = im.convert("RGB")
    w, h = im.size

    sentinel = (255, 0, 255)
    marked = im.copy()
    seeds = [(0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)]
    seeds += [(w // 4, 0), (w // 2, 0), (3 * w // 4, 0)]
    seeds += [(w // 4, h - 1), (w // 2, h - 1), (3 * w // 4, h - 1)]
    seeds += [(0, h // 4), (0, h // 2), (0, 3 * h // 4)]
    seeds += [(w - 1, h // 4), (w - 1, h // 2), (w - 1, 3 * h // 4)]
    for pt in seeds:
        if marked.getpixel(pt) != sentinel:
            ImageDraw.floodfill(marked, pt, sentinel, thresh=THRESH)

    mp = marked.load()
    mask = Image.new("L", (w, h), 255)
    mk = mask.load()
    cleared = 0
    for y in range(h):
        for x in range(w):
            if mp[x, y] == sentinel:
                mk[x, y] = 0
                cleared += 1

    ratio = cleared / (w * h)
    if ratio > 0.90:
        print(f"  ABORT (>90% cleared, flood leaked): {path}")
        return False

    if BLUR > 0:
        mask = mask.filter(ImageFilter.GaussianBlur(BLUR))

    out = im.convert("RGBA")
    out.putalpha(mask)

    os.makedirs(BACKUP_DIR, exist_ok=True)
    backup = os.path.join(BACKUP_DIR, os.path.basename(path))
    if not os.path.exists(backup):
        shutil.copy2(path, backup)
    out.save(path)
    print(f"  cut  {path}  (cleared {ratio:.1%}, backup -> {backup})")
    return True


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    for p in sys.argv[1:]:
        if not os.path.isfile(p):
            print(f"  missing: {p}")
            continue
        print(f"- {p}")
        cut(p)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
