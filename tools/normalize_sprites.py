#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""立绘归一化：把立绘画布统一，让「靠遮挡取景」的几何对每个角色都成立。

背景（见 docs/ds-adventrue/立绘取景与CG层级-需求定稿.md §1.4 / S2）：
素材画布实测 15 种宽 / 9 种高（w/h 0.389~0.818），同一个节点框里做等比适配，
不同角色的头顶高度与人物大小都会不一样（实测 ds 与 xiguan 头顶差 188px）。
归一化之后所有立绘同画布、同身高、同脚线、同中心 —— 框在哪里，人就落在哪里。

规则 v1
  1) 按 alpha 通道裁边（阈值 8），取角色真实包围盒（透明留白不计）
  2) 贴到统一画布 1280×1536：
     · 全身类：bbox 高 → 1460，bbox 底(脚线) → y=1500，bbox 水平中心 → x=640
     · 宽图类：按身高缩放后宽度 > 1200 时改为按宽限幅（≤1200），
       并把 bbox 垂直中心放到 y=760 —— 否则矮宽的角色整个落在取景带之外
  3) 只处理地图实际引用的立绘；WIP 目录（_legacy* / _old_pipeline_backup / t2i_alt / official）
     永不触碰（那些是美术侧未定稿的东西）
  4) 幂等：归一化后再跑一次 scale=1.0，画面不变（--check 校验）

用法
  python tools/normalize_sprites.py                  # 干跑：只打印计划，不写盘
  python tools/normalize_sprites.py --apply          # 落盘（先备份到仓库外）
  python tools/normalize_sprites.py --check          # 校验已归一化（CI/回归用）
  python tools/normalize_sprites.py --apply --limit 2  # 只处理前 2 个（试水）
"""

import argparse
import os
import re
import shutil
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    print("✗ 需要 Pillow：pip install pillow")
    sys.exit(2)

# Windows 控制台默认 GBK：中文与 ⚠ 会直接抛 UnicodeEncodeError，强制 UTF-8 输出
for _s in (sys.stdout, sys.stderr):
    if hasattr(_s, "reconfigure"):
        _s.reconfigure(encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCENARIO = os.path.join(ROOT, "maps", "story", "scenario.txt")
RES = os.path.join(ROOT, "src", "main", "resources")
DEFAULT_BACKUP = os.path.join(os.path.dirname(ROOT), "_sprite_backup_before_normalize")

CANVAS_W, CANVAS_H = 1280, 1536
CHAR_H = 1460          # 全身类：角色身高
FEET_Y = 1500          # 全身类：脚线
CENTER_X = 640         # 所有类：水平中心
MAX_W = 1200           # 宽图类：限幅宽度
WIDE_CENTER_Y = 760    # 宽图类：bbox 垂直中心
ALPHA_MIN = 8          # 裁边阈值
WIDE_RATIO = 0.9       # w/h 超过这个值算"宽图类"

WIP = re.compile(r"(_legacy|_old_pipeline_backup|t2i_alt|/official/)")


def referenced_sprites():
    """地图实际引用的立绘（相对 src/main/resources 的路径，去重保序）"""
    text = open(SCENARIO, encoding="utf-8").read()
    found = re.findall(r"^path = (assets/sprites/.+?\.png)$", text, re.M)
    out = []
    for rel in found:
        if rel.startswith("assets/sprites/ui/") or rel.startswith("assets/sprites/backgrounds/"):
            continue
        if rel not in out:
            out.append(rel)
    return out


def alpha_bbox(img):
    a = img.getchannel("A").point(lambda v: 255 if v > ALPHA_MIN else 0)
    return a.getbbox()


def plan_for(path):
    """算出这个文件该怎么变（不写盘）"""
    img = Image.open(path)
    img = img.convert("RGBA")
    w0, h0 = img.size
    box = alpha_bbox(img)
    if not box:
        return dict(err="整图透明（alpha 全空）")
    bw, bh = box[2] - box[0], box[3] - box[1]
    ratio = bw / bh
    cropped = img.crop(box)

    scale = CHAR_H / bh
    wide = ratio > WIDE_RATIO
    if bw * scale > MAX_W:
        scale = MAX_W / bw
        wide = True
    nw, nh = max(1, round(bw * scale)), max(1, round(bh * scale))
    x = CENTER_X - nw // 2
    y = (FEET_Y - nh) if not wide else (WIDE_CENTER_Y - nh // 2)
    # 真正缩放（曾经漏掉这一步：只算了几何值却贴了原尺寸，被 --check 抓出来）
    scaled = img.crop(box).resize((nw, nh), Image.Resampling.LANCZOS)
    return dict(img=scaled, before=(w0, h0), bbox=(bw, bh), scale=scale,
                after=(nw, nh), at=(x, y), wide=wide, ratio=ratio,
                canvas=(CANVAS_W, CANVAS_H), box=box)


def compose(p, dest):
    canvas = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    src = p["img"]
    x, y = p["at"]
    sx0, sy0 = max(0, -x), max(0, -y)
    dx0, dy0 = max(0, x), max(0, y)
    w = min(src.width - sx0, CANVAS_W - dx0)
    h = min(src.height - sy0, CANVAS_H - dy0)
    if w <= 0 or h <= 0:
        return canvas
    canvas.alpha_composite(src.crop((sx0, sy0, sx0 + w, sy0 + h)), (dx0, dy0))
    canvas.save(dest, "PNG", optimize=True)
    return canvas


def check_one(path):
    """校验一个文件是否已符合规格"""
    img = Image.open(path).convert("RGBA")
    if img.size != (CANVAS_W, CANVAS_H):
        return f"画布 {img.size[0]}x{img.size[1]} ≠ {CANVAS_W}x{CANVAS_H}"
    box = alpha_bbox(img)
    if not box:
        return "整图透明"
    bw, bh = box[2] - box[0], box[3] - box[1]
    cx = (box[0] + box[2]) / 2
    if abs(cx - CENTER_X) > 2:
        return f"中心 x={cx:.1f} ≠ {CENTER_X}"
    if bw >= MAX_W - 2 and abs((box[1] + box[3]) / 2 - WIDE_CENTER_Y) > 2:
        return f"宽图类中心 y={(box[1] + box[3]) / 2:.1f} ≠ {WIDE_CENTER_Y}"
    if bw < MAX_W - 2:
        if abs(bh - CHAR_H) > 2:
            return f"身高 {bh} ≠ {CHAR_H}"
        if abs(box[3] - FEET_Y) > 2:
            return f"脚线 {box[3]} ≠ {FEET_Y}"
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="真正写盘（默认只干跑）")
    ap.add_argument("--check", action="store_true", help="只校验已归一化的结果")
    ap.add_argument("--restore", action="store_true", help="从备份还原（撤销归一化，回滚用）")
    ap.add_argument("--limit", type=int, default=0, help="只处理前 N 个（试水）")
    ap.add_argument("--backup", default=DEFAULT_BACKUP, help="备份目录（默认仓库外）")
    args = ap.parse_args()

    rels = referenced_sprites()
    if args.limit:
        rels = rels[: args.limit]

    if args.restore:
        n = miss = 0
        for rel in rels:
            src = os.path.join(args.backup, rel.replace("/", os.sep))
            dst = os.path.join(RES, rel.replace("/", os.sep))
            if os.path.exists(src):
                shutil.copy2(src, dst)
                n += 1
            else:
                print(f"  ✗ 备份里没有：{rel}")
                miss += 1
        print(f"已从 {args.backup} 还原 {n} 个立绘 | 缺失 {miss}")
        return 1 if miss else 0

    if args.check:
        bad = 0
        for rel in rels:
            p = os.path.join(RES, rel.replace("/", os.sep))
            if not os.path.exists(p):
                print(f"  ✗ 缺文件 {rel}")
                bad += 1
                continue
            err = check_one(p)
            if err:
                print(f"  ✗ {rel}: {err}")
                bad += 1
        print(f"校验 {len(rels)} 个立绘 | 不合规 {bad}")
        print("✅ S2 校验通过" if bad == 0 else "❌ S2 校验未通过")
        return 0 if bad == 0 else 1

    print(f"引用立绘 {len(rels)} 个 | 目标画布 {CANVAS_W}x{CANVAS_H} "
          f"身高 {CHAR_H} 脚线 {FEET_Y} 中心 x={CENTER_X} | 模式={'落盘' if args.apply else '干跑'}")
    if args.apply:
        print(f"备份目录：{args.backup}")
    print(f"{'文件':<44}{'原画布':>12}{'bbox':>13}{'缩放':>8}{'新bbox':>12}{'落点':>12}  类")
    done = upscaled = failed = 0
    for rel in rels:
        p = os.path.join(RES, rel.replace("/", os.sep))
        if WIP.search(rel.replace(os.sep, "/")):
            print(f"  ⚠ WIP 目录，跳过 {rel}")
            continue
        if not os.path.exists(p):
            print(f"  ✗ 缺文件 {rel}")
            failed += 1
            continue
        try:
            pl = plan_for(p)
        except Exception as e:  # 坏图不静默
            print(f"  ✗ {rel}: {e}")
            failed += 1
            continue
        if pl.get("err"):
            print(f"  ✗ {rel}: {pl['err']}")
            failed += 1
            continue
        note = "宽图" if pl["wide"] else "全身"
        warn = ""
        if pl["scale"] > 1.3:
            warn = "  ⚠放大素材（建议美术侧重出更高分辨率）"
            upscaled += 1
        print(f"{rel:<44}{pl['before'][0]:>6}x{pl['before'][1]:<5}"
              f"{pl['bbox'][0]:>6}x{pl['bbox'][1]:<6}{pl['scale']:>7.3f}"
              f"{pl['after'][0]:>6}x{pl['after'][1]:<5}"
              f"{pl['at'][0]:>6},{pl['at'][1]:<5}  {note}{warn}")
        if args.apply:
            dst = os.path.join(args.backup, rel.replace("/", os.sep))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            if not os.path.exists(dst):
                shutil.copy2(p, dst)
            compose(pl, p)
        done += 1

    print(f"\n合计 {done} 个（宽图类另行锚点） | 需放大素材 {upscaled} 个 | 失败 {failed} 个")
    if not args.apply:
        print("（干跑，未写盘。确认无误后加 --apply）")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
