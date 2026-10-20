"""Side / top / front previews of a ship's voxel hull, coloured by material: python preview.py sloop"""
import importlib
import sys

from PIL import Image

COL = {"bottom": (60, 52, 44), "planks": (150, 100, 60), "trim": (110, 50, 32), "deck": (200, 160, 100),
       "keel": (40, 30, 20), "gilt": (220, 180, 60), "canvas": (235, 230, 215), "post": (176, 132, 86)}
S = 4


def main():
    ship = importlib.import_module(sys.argv[1])
    g = ship.build_hull()
    vox = [(x * 4 + a, y * 4 + b, z * 4 + c, m) for (x, y, z), cell in g.cells().items() for (a, b, c), m in cell.items()]
    xs, ys, zs = [v[0] for v in vox], [v[1] for v in vox], [v[2] for v in vox]
    x0, y0, z0 = min(xs), min(ys), min(zs)
    W, H, D = (max(zs) - z0 + 1) * S, (max(ys) - y0 + 1) * S, (max(xs) - x0 + 1) * S

    def view(key_h, key_v, depth, w, h, flip_v=True):
        img = Image.new("RGB", (w, h), (170, 200, 230))
        best = {}
        for v in vox:
            k = (key_h(v), key_v(v))
            if k not in best or depth(v) > best[k][0]:
                best[k] = (depth(v), v[3])
        for (hh, vv), (_, m) in best.items():
            for i in range(S):
                for j in range(S):
                    px = hh * S + i
                    py = (h - 1 - (vv * S + j)) if flip_v else vv * S + j
                    if 0 <= px < w and 0 <= py < h:
                        img.putpixel((px, py), COL[m])
        return img

    side = view(lambda v: v[2] - z0, lambda v: v[1] - y0, lambda v: v[0], W, H)
    top = view(lambda v: v[2] - z0, lambda v: v[0] - x0, lambda v: v[1], W, D, flip_v=False)
    front = view(lambda v: v[0] - x0, lambda v: v[1] - y0, lambda v: v[2], D, H)
    out = Image.new("RGB", (W + D + 10, H + D + 10), (30, 30, 30))
    out.paste(side, (0, 0))
    out.paste(top, (0, H + 10))
    out.paste(front, (W + 10, 0))
    out.save(f"../../shots/{sys.argv[1]}_voxels.png")


if __name__ == "__main__":
    main()
