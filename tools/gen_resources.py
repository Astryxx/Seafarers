"""Writes the mod's blockstates, block/item models, lang and physics data.

python tools/gen_resources.py
"""
import json
import os
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
A = "assets/seafarers"
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
FACES = ["north", "south", "east", "west", "up", "down"]


def w(path, obj):
    p = ROOT / path
    os.makedirs(p.parent, exist_ok=True)
    p.write_text(json.dumps(obj, indent=2) + "\n")


def tex(name):
    return name if ":" in name else f"seafarers:block/{name}"


def fit_uv(a0, a1):
    """A face edge's texture span kept inside its texture: Minecraft takes a face's texture coordinates from where it
    sits in the block, so a part reaching past the block (a long table top, a cannon's muzzle) would read the
    neighbouring textures in the atlas. Longer than a block: the whole texture, stretched."""
    if a1 - a0 >= 16:
        return 0.0, 16.0
    shift = (a0 // 16) * 16
    a0, a1 = a0 - shift, a1 - shift
    if a1 > 16:
        a0, a1 = 16 - (a1 - a0), 16.0
    return a0, a1


def face_uv(d, f, t):
    """The texture coordinates Minecraft would give face d, kept inside the texture (see fit_uv)."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    if d in ("north", "south"):
        u, v = (x0, x1) if d == "south" else (16 - x1, 16 - x0), (16 - y1, 16 - y0)
    elif d in ("east", "west"):
        u, v = (z0, z1) if d == "west" else (16 - z1, 16 - z0), (16 - y1, 16 - y0)
    else:
        u, v = (x0, x1), (z0, z1) if d == "up" else (16 - z1, 16 - z0)
    u0, u1 = fit_uv(*u)
    v0, v1 = fit_uv(*v)
    return [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)]


def el(f, t, texture="#all", rot=None, tint=False, **faces):
    """A cuboid; faces can override textures per side, e.g. up='#top'."""
    e = {"from": f, "to": t, "faces": {}}
    outside = any(c < 0 or c > 16 for c in list(f) + list(t))
    for d in FACES:
        face = {"texture": faces.get(d, texture)}
        if outside:
            face["uv"] = face_uv(d, f, t)
        if tint:
            face["tintindex"] = 0
        e["faces"][d] = face
    if rot:
        e["rotation"] = rot
    return e


# every block model's boxes, so a fitting's collision and outline can be made from exactly what it looks like
MODEL_BOXES = {}


def model(name, textures, elements, parent="minecraft:block/block", render=None):
    for e in elements:   # Minecraft refuses a model with a box outside -16..32 px (the whole model goes missing)
        assert all(-16 <= v <= 32 for v in e["from"] + e["to"]), f"{name}: element outside -16..32: {e['from']} {e['to']}"
    MODEL_BOXES[name] = [e["from"] + e["to"] for e in elements if "rotation" not in e]
    m = {"parent": parent, "textures": textures, "elements": elements}
    if render:
        m["render_type"] = render
    w(f"{A}/models/block/{name}.json", m)


def cube(name, all_tex=None, **kw):
    if kw:
        w(f"{A}/models/block/{name}.json", {"parent": "minecraft:block/cube_bottom_top" if "bottom" in kw else "minecraft:block/cube_column",
                                            "textures": {k: tex(v) for k, v in kw.items()}})
    else:
        w(f"{A}/models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex(all_tex or name)}})


def single(name, model_name=None):
    w(f"{A}/blockstates/{name}.json", {"variants": {"": {"model": f"seafarers:block/{model_name or name}"}}})


def multi_facing(name, parts, model_name=None):
    """A fitting several blocks big: part 0 draws the whole model, the other parts draw nothing."""
    w(f"{A}/models/block/empty_part.json", {"textures": {"particle": "seafarers:block/helm_wood"}})
    w(f"{A}/blockstates/{name}.json", {"variants": {
        f"facing={d},part={p}": ({"model": f"seafarers:block/{model_name or name}", "y": r} if p == 0
                                 else {"model": "seafarers:block/empty_part"})
        for d, r in ROT.items() for p in range(6)}})


def facing(name, model_name=None):
    w(f"{A}/blockstates/{name}.json", {"variants": {f"facing={d}": {"model": f"seafarers:block/{model_name or name}", "y": r}
                                                    for d, r in ROT.items()}})


def stairs(name, texture):
    t = {"bottom": tex(texture), "top": tex(texture), "side": tex(texture)}
    for kind in ("", "_inner", "_outer"):
        w(f"{A}/models/block/{name}{kind}.json", {"parent": f"minecraft:block/{'stairs' if not kind else kind[1:] + '_stairs'}", "textures": t})
    variants = {}
    base_y = {"east": 0, "south": 90, "west": 180, "north": 270}
    for f, y in base_y.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                m = name + ("_inner" if shape.startswith("inner") else "_outer" if shape.startswith("outer") else "")
                yy = y
                if shape in ("inner_left", "outer_left"):
                    yy = (y + 270) % 360 if half == "bottom" else y
                if shape in ("inner_right", "outer_right") and half == "top":
                    yy = (y + 90) % 360
                v = {"model": f"seafarers:block/{m}"}
                if half == "top":
                    v["x"] = 180
                if yy:
                    v["y"] = yy
                if half == "top" or yy:
                    v["uvlock"] = True
                variants[f"facing={f},half={half},shape={shape}"] = v
    w(f"{A}/blockstates/{name}.json", {"variants": variants})


def fence(name, texture):
    t = {"texture": tex(texture)}
    w(f"{A}/models/block/{name}_post.json", {"parent": "minecraft:block/fence_post", "textures": t})
    w(f"{A}/models/block/{name}_side.json", {"parent": "minecraft:block/fence_side", "textures": t})
    w(f"{A}/models/block/{name}_inventory.json", {"parent": "minecraft:block/fence_inventory", "textures": t})
    parts = [{"apply": {"model": f"seafarers:block/{name}_post"}}]
    for d, y in ROT.items():
        a = {"model": f"seafarers:block/{name}_side", "uvlock": True}
        if y:
            a["y"] = y
        parts.append({"when": {d: "true"}, "apply": a})
    w(f"{A}/blockstates/{name}.json", {"multipart": parts})


def main():
    # ---------------- hull ----------------
    for name in ("hull_planks", "hull_bottom"):
        cube(name)
        cube(f"{name}_flooded")
        w(f"{A}/blockstates/{name}.json", {"variants": {
            "flooded=false": {"model": f"seafarers:block/{name}"},
            "flooded=true": {"model": f"seafarers:block/{name}_flooded"}}})
    stairs("hull_bottom_stairs", "hull_bottom")
    w(f"{A}/models/block/hull_trim.json", {"parent": "minecraft:block/cube_column",
                                           "textures": {"side": tex("hull_trim"), "end": tex("hull_trim_top")}})
    single("hull_trim")
    cube("deck_planks")
    single("deck_planks")
    stairs("deck_stairs", "deck_planks")
    fence("railing", "railing")
    w(f"{A}/models/block/keel.json", {"parent": "minecraft:block/cube_column", "textures": {"side": tex("keel"), "end": tex("keel_end")}})
    w(f"{A}/models/block/keel_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal",
                                                 "textures": {"side": tex("keel"), "end": tex("keel_end")}})
    w(f"{A}/blockstates/keel.json", {"variants": {
        "axis=y": {"model": "seafarers:block/keel"},
        "axis=z": {"model": "seafarers:block/keel_horizontal", "x": 90},
        "axis=x": {"model": "seafarers:block/keel_horizontal", "x": 90, "y": 90}}})
    model("spar", {"s": tex("mast"), "e": tex("keel_end"), "particle": tex("mast")},
          [el([5, 0, 5], [11, 16, 11], "#s", up="#e", down="#e")])
    w(f"{A}/blockstates/spar.json", {"variants": {
        "axis=y": {"model": "seafarers:block/spar"},
        "axis=z": {"model": "seafarers:block/spar", "x": 90},
        "axis=x": {"model": "seafarers:block/spar", "x": 90, "y": 90}}})
    t = {"bottom": tex("deck_planks"), "top": tex("deck_planks"), "side": tex("deck_planks")}
    w(f"{A}/models/block/deck_slab.json", {"parent": "minecraft:block/slab", "textures": t})
    w(f"{A}/models/block/deck_slab_top.json", {"parent": "minecraft:block/slab_top", "textures": t})
    w(f"{A}/blockstates/deck_slab.json", {"variants": {
        "type=bottom": {"model": "seafarers:block/deck_slab"}, "type=top": {"model": "seafarers:block/deck_slab_top"},
        "type=double": {"model": "seafarers:block/deck_planks"}}})
    w(f"{A}/models/block/rope_ladder.json", {"parent": "minecraft:block/ladder", "render_type": "minecraft:cutout",
                                             "textures": {"texture": tex("rope_ladder"), "particle": tex("rope_ladder")}})
    facing("rope_ladder")
    model("bowsprit", {"w": tex("hull_trim_top"), "b": tex("cannon_iron"), "particle": tex("mast")},
          [el([4, 0, 4], [12, 6, 12], "#w"), el([3.5, 2, 3.5], [12.5, 3, 12.5], "#b")])
    facing("bowsprit")
    model("mast", {"all": tex("mast"), "band": tex("mast_band"), "particle": tex("mast")},
          [el([3, 0, 3], [13, 16, 13], up="#band", down="#band"), el([2.5, 6, 2.5], [13.5, 8, 13.5], "#band")])
    w(f"{A}/blockstates/mast.json", {"multipart": [
        {"apply": {"model": "seafarers:block/mast"}},
        {"when": {"deck": "true"}, "apply": {"model": "seafarers:block/deck_plate"}}]})
    for bottom in (False, True):
        n = "hull_hole_bottom" if bottom else "hull_hole"
        w(f"{A}/models/block/{n}.json", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout",
                                         "textures": {"all": tex(n)}})
    w(f"{A}/blockstates/hull_hole.json", {"variants": {"bottom=false": {"model": "seafarers:block/hull_hole"},
                                                       "bottom=true": {"model": "seafarers:block/hull_hole_bottom"}}})

    # bilge water: eight heights of tinted still water
    variants = {}
    for lvl in range(1, 9):
        model(f"bilge_water_{lvl}", {"w": "minecraft:block/water_still", "particle": "minecraft:block/water_still"},
              [el([0, 0, 0], [16, lvl * 2, 16], "#w", tint=True)], render="minecraft:cutout")   # (Sable hides translucent faces in the dry hold)
        variants[f"level={lvl}"] = {"model": f"seafarers:block/bilge_water_{lvl}"}
    w(f"{A}/blockstates/bilge_water.json", {"variants": variants})

    # ---------------- stations ----------------
    # helm: the pedestal stands on the bow side (model north) and the wheel faces aft, toward the helmsman, as on
    # a real ship's wheel; HelmRenderer spins the wheel about its fore-and-aft axle at (8, 9.5)
    model("helm", {"w": tex("helm_wood"), "b": tex("brass"), "particle": tex("helm_wood")},
          [el([6, 0, 3.5], [10, 1.5, 7.5], "#w"), el([6.5, 1.5, 4], [9.5, 9, 7], "#w"),
           el([6, 8, 3.5], [10, 11, 7.5], "#w"), el([7.25, 8.75, 7.5], [8.75, 10.25, 9.5], "#b")])
    wheel = []
    ring = [  # (from, to) for a blocky octagonal rim, z 8.4..9.4
        ([5.5, 14.5], [10.5, 15.5]), ([5.5, 3.5], [10.5, 4.5]), ([2.5, 6.5], [3.5, 12.5]), ([12.5, 6.5], [13.5, 12.5]),
        ([3.5, 12.5], [5.5, 14.5]), ([10.5, 12.5], [12.5, 14.5]), ([3.5, 4.5], [5.5, 6.5]), ([10.5, 4.5], [12.5, 6.5])]
    for (x0, y0), (x1, y1) in ring:
        wheel.append(el([x0, y0, 8.4], [x1, y1, 9.4], "#w"))
    wheel += [el([7.5, 1.5, 8.5], [8.5, 17.5, 9.3], "#s"), el([0, 9, 8.5], [16, 10, 9.3], "#s"),
              el([7, 9, 8.2], [9, 10, 9.6], "#b")]
    model("helm_wheel", {"w": tex("helm_wood"), "s": tex("helm_spoke"), "b": tex("brass"), "particle": tex("helm_wood")}, wheel)
    facing("helm")

    model("sail_yard", {"particle": tex("mast")}, [])
    facing("sail_yard")

    # length cleat (hoist): a post with rope belayed round it; angle pulley (brace): a block and tackle
    model("sail_rope", {"p": tex("helm_wood"), "r": tex("rope"), "particle": tex("rope")},
          [el([6.5, 0, 6.5], [9.5, 13, 9.5], "#p"), el([5.5, 3, 5.5], [10.5, 9, 10.5], "#r"),
           el([4, 10, 7], [12, 11.5, 9], "#p"), el([7.5, 9, 7.5], [8.5, 16, 8.5], "#r"),
           # the hauling part, flaked down in a coil on deck beside the cleat
           el([1.5, 0, 10], [6.5, 1, 15], "#r"), el([2.5, 1, 11], [5.5, 1.8, 14], "#r")])
    model("brace_rope", {"p": tex("helm_wood"), "r": tex("rope"), "b": tex("brass"), "particle": tex("rope")},
          [el([6.5, 0, 6.5], [9.5, 10, 9.5], "#p"), el([5, 10, 6], [11, 15, 10], "#p"),
           el([6, 11, 5.5], [10, 14, 10.5], "#b"), el([7.5, 14, 7.5], [8.5, 16, 8.5], "#r"),
           el([5.5, 2, 5.5], [10.5, 6, 10.5], "#r"),
           el([9.5, 0, 10], [14.5, 1, 15], "#r"), el([10.5, 1, 11], [13.5, 1.8, 14], "#r")])
    facing("sail_rope")
    facing("brace_rope")

    # capstan: drum on a base with two pegs, like the Sloop's
    # capstan: the plinth is the block model; CapstanRenderer draws the turning head, cable and anchor
    model("anchor", {"w": tex("helm_wood"), "b": tex("brass"), "particle": tex("helm_wood")},
          [el([1.5, 0, 1.5], [14.5, 2, 14.5], "#w"), el([2.5, 2, 2.5], [13.5, 3.2, 13.5], "#b")])
    facing("anchor")
    # under a deck, the lantern block also draws the deck planking it hangs from
    model("deck_plate", {"d": tex("deck_planks"), "particle": tex("deck_planks")}, [el([0, 12, 0], [16, 16, 16], "#d")])
    def lantern(name, y0):
        body = {"from": [5, y0, 5], "to": [11, y0 + 7, 11], "faces": {
            f: {"uv": [0, 9, 6, 15] if f in ("up", "down") else [0, 2, 6, 9], "texture": "#lantern", "tintindex": 0} for f in FACES}}
        cap = {"from": [6, y0 + 7, 6], "to": [10, y0 + 9, 10], "faces": {
            f: {"uv": [1, 10, 5, 14] if f in ("up", "down") else [1, 0, 5, 2], "texture": "#lantern", "tintindex": 0} for f in FACES}}
        top = y0 + 9
        handle = [{"from": [6.5, top + (1 if y0 else 0), 8], "to": [9.5, top + (5 if y0 else 2), 8],
                   "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45}, "shade": False,
                   "faces": {"north": {"uv": [14, 1, 11, 5], "texture": "#lantern"}, "south": {"uv": [11, 1, 14, 5], "texture": "#lantern"}}},
                  {"from": [8, top, 6.5], "to": [8, top + (6 if y0 else 2), 9.5],
                   "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45}, "shade": False,
                   "faces": {"west": {"uv": [14, 6, 11, 12], "texture": "#lantern"}, "east": {"uv": [11, 6, 14, 12], "texture": "#lantern"}}}]
        w(f"{A}/models/block/{name}.json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                            "textures": {"lantern": "minecraft:block/lantern", "particle": "minecraft:block/lantern"},
                                            "elements": [body, cap] + handle})

    # standing: on the floor with a small loop handle (y0 0); hanging: a block lower in its space with a chain (y0 1)
    lantern("ship_lantern_unlit", 0)
    lantern("ship_lantern_hanging_unlit", 1)
    w(f"{A}/blockstates/ship_lantern.json", {"multipart": [
        {"when": {"hanging": "false", "lit": "true"}, "apply": {"model": "minecraft:block/lantern"}},
        {"when": {"hanging": "true", "lit": "true"}, "apply": {"model": "minecraft:block/lantern_hanging"}},
        {"when": {"hanging": "false", "lit": "false"}, "apply": {"model": "seafarers:block/ship_lantern_unlit"}},
        {"when": {"hanging": "true", "lit": "false"}, "apply": {"model": "seafarers:block/ship_lantern_hanging_unlit"}},
        {"when": {"deck": "true"}, "apply": {"model": "seafarers:block/deck_plate"}}]})
    w(f"{A}/models/item/ship_lantern.json", {"parent": "minecraft:item/lantern"})

    # cannon, Sea of Thieves sized: an iron gun on a two-cheek carriage with four trucks, the whole thing two blocks
    # long (part 1 is the cell ahead, toward the gunport). The barrel is aimed by CannonRenderer about its trunnions
    # at (8, 8.5, 8) and points north; its muzzle is 1.5 blocks ahead of them.
    model("cannon", {"w": tex("carriage"), "i": tex("cannon_iron"), "particle": tex("carriage")},
          [el([3, 1.5, -6], [5, 8, 13], "#w"), el([11, 1.5, -6], [13, 8, 13], "#w"),          # cheeks
           el([5, 1.5, -4], [11, 3, 11], "#w"), el([5, 3, 9], [11, 5, 12], "#w"),               # bed and quoin
           el([1.5, 0, -5], [3, 3.5, -1], "#i"), el([13, 0, -5], [14.5, 3.5, -1], "#i"),       # front trucks
           el([1.5, 0, 8], [3, 3.5, 12], "#i"), el([13, 0, 8], [14.5, 3.5, 12], "#i"),         # rear trucks
           el([3, 1, -4], [13, 2, -2], "#i"), el([3, 1, 9], [13, 2, 11], "#i")])                # axles
    model("cannon_barrel", {"i": tex("cannon_iron"), "d": "minecraft:block/black_concrete", "particle": tex("cannon_iron")},
          [el([4.5, 5, -14], [11.5, 12, 13], "#i", north="#d"),                                 # the gun
           el([4, 4.5, -16], [12, 12.5, -13], "#i", north="#d"),                                # muzzle swell
           el([4, 4.5, -5], [12, 12.5, -3], "#i"), el([4, 4.5, 5], [12, 12.5, 7], "#i"),        # reinforcing rings
           el([4, 4.5, 11], [12, 12.5, 13], "#i"),                                              # base ring
           el([6.5, 7, 13], [9.5, 10, 15.5], "#i"),                                             # cascabel
           el([2.5, 7.5, 7], [13.5, 9.5, 9], "#i")])                                            # trunnions
    multi_facing("cannon", 2)
    facing("cannon")

    # supply barrels: a round barrel (three crossed boxes and two hoops) with what's in it showing at the top
    def round_barrel(h=14, side="#side", top="#top", hoop="#hoop"):
        return [el([4, 0, 1], [12, h, 15], side, up=top, down=top), el([1, 0, 4], [15, h - 0.05, 12], side, up=top, down=top),
                el([2, 0, 2], [14, h - 0.1, 14], side, up=top, down=top)] + [
            e for y in (2, h - 3) for e in (el([3.8, y, 0.8], [12.2, y + 1, 15.2], hoop), el([0.8, y, 3.8], [15.2, y + 1, 12.2], hoop),
                                             el([1.8, y, 1.8], [14.2, y + 1, 14.2], hoop))]
    barrel = round_barrel()
    bt = {"side": tex("barrel_side"), "top": tex("barrel_top"), "hoop": tex("cannon_iron"), "particle": tex("barrel_side")}
    model("supply_barrel_cannonballs", {**bt, "c": tex("cannon_iron")},
          barrel + [el([4, 14, 4], [7, 16, 7], "#c"), el([8, 14, 5], [11, 16, 8], "#c"), el([5, 14, 9], [8, 16, 12], "#c"),
                    el([9, 14, 9.5], [12, 16, 12.5], "#c"), el([6.5, 15.5, 6.5], [9.5, 17, 9.5], "#c")])
    model("supply_barrel_planks", {**bt, "p": tex("deck_planks")},
          barrel + [el([4, 10, 5], [6, 21, 7], "#p"), el([7, 10, 8], [9, 23, 10], "#p"), el([10, 10, 4.5], [12, 20, 6.5], "#p"),
                    el([5, 10, 10], [7, 19, 12], "#p")])
    model("supply_barrel_food", {**bt, "y": tex("banana")},
          barrel + [el([4, 14, 4], [8, 16, 9], "#y"), el([8, 14, 7], [12, 16.5, 11], "#y"), el([5, 14, 9], [9, 15.5, 12], "#y")])
    # the grog barrel lies on a cradle with a brass tap, as on the Sloop's lower deck
    model("supply_barrel_grog", {**bt, "c": tex("helm_wood"), "b": tex("brass")},
          [el([0.5, 3, 4], [15.5, 13, 12], "#side", east="#top", west="#top"),
           el([0.5, 4, 3], [15.5, 12, 13], "#side", east="#top", west="#top"),
           el([0.5, 3.5, 3.5], [15.5, 12.5, 12.5], "#side", east="#top", west="#top"),
           el([3, 2.7, 2.7], [4, 13.3, 13.3], "#hoop"), el([12, 2.7, 2.7], [13, 13.3, 13.3], "#hoop"),
           el([2, 0, 2], [4, 4, 14], "#c"), el([12, 0, 2], [14, 4, 14], "#c"),
           el([7, 6, 1.5], [9, 8, 3], "#b"), el([7.5, 4.5, 1.5], [8.5, 6, 2.5], "#b")])
    w(f"{A}/blockstates/supply_barrel.json", {"variants": {f"supply={s}": {"model": f"seafarers:block/supply_barrel_{s}"}
                                                           for s in ("cannonballs", "planks", "food", "grog")}})
    # bucket rack: a short post with two buckets hanging off a peg
    model("bucket_rack", {"w": tex("helm_wood"), "b": tex("barrel_side"), "i": tex("cannon_iron"), "particle": tex("barrel_side")},
          [el([7, 0, 12], [9, 12, 14], "#w"), el([4, 9, 9], [12, 10, 13], "#w"),
           el([3, 2, 4], [8, 8, 9], "#b", up="#i"), el([9, 2, 4], [14, 8, 9], "#b", up="#i")])
    facing("bucket_rack")

    # ---------------- fittings ----------------
    # harpoon gun: a stout post on an iron plate is the block; HarpoonRenderers swings the gun about (8, 11.5, 2)
    # and draws the harpoon in it until it's fired (the same bolt model flies as the harpoon entity, tip at z -19).
    # Drawn 6 px forward of its block, so it can stand hard against the bulwark where the hull's planking runs
    # through the cell ahead (as the Sloop's harpoons do)
    hp = {"w": tex("helm_wood"), "i": tex("cannon_iron"), "r": tex("rope"), "particle": tex("cannon_iron")}
    model("harpoon", hp, [el([4, 0, -2], [12, 1.5, 6], "#i"),
        el([5.5, 1.5, -0.5], [10.5, 9.5, 4.5], "#w"),
        el([5, 9.5, -1], [11, 10.5, 5], "#i")])
    model("harpoon_gun", hp, [el([6, 10.5, -6], [10, 14, 9], "#w"),
        el([4.5, 10, 2.5], [11.5, 15, 6.5], "#r"),
        el([11.5, 12, 4], [13, 13, 5], "#i"),
        el([13, 9.5, 4], [14, 13, 5], "#i"),
        el([6.5, 8, 7], [9.5, 11, 10], "#w"),
        el([5.5, 11, -6], [10.5, 13.5, -4], "#i")])
    # the bolt isn't shifted (it would reach past what a model may): HarpoonRenderers moves it with the gun
    model("harpoon_bolt", hp, [el([7.25, 11.25, -9], [8.75, 12.75, 3], "#i"), el([6, 10.5, -11], [10, 13.5, -9], "#i"),
                               el([7, 11.5, -13], [9, 12.5, -11], "#i"), el([7.5, 9.5, -10], [8.5, 14.5, -9], "#i")])
    facing("harpoon")
    facing("harpoon")
    # rowboat: the rowing bench (HelmRenderer draws the oars) and the bow post with its docking hook
    model("oars", {"w": tex("deck_planks"), "p": tex("helm_wood"), "particle": tex("deck_planks")},
          [el([0, 6, 5], [16, 8, 11], "#w"), el([1, 0, 6], [3, 6, 10], "#p"), el([13, 0, 6], [15, 6, 10], "#p")])
    facing("oars")
    model("rowboat_hook", {"w": tex("helm_wood"), "i": tex("cannon_iron"), "particle": tex("helm_wood")},
          [el([6.5, 0, 6.5], [9.5, 12, 9.5], "#w"), el([7.25, 9, 3.5], [8.75, 10.5, 6.5], "#i"),
           el([7.25, 6, 2.5], [8.75, 10.5, 4], "#i"), el([7.25, 5, 4], [8.75, 6.5, 5.5], "#i")])
    facing("rowboat_hook")
    # flag box: a chest of flags by the helm
    model("flag_box", {"w": tex("helm_wood"), "b": tex("brass"), "f": tex("flag"), "particle": tex("helm_wood")},
          [el([2, 0, 3], [14, 8, 13], "#w"), el([2, 8, 3], [14, 9, 13], "#b"), el([4, 9, 5], [12, 10, 11], "#f"),
           el([7, 4, 2.5], [9, 7, 3], "#b")])
    facing("flag_box")
    # galley stove: an iron box with a fire door and a flue; the door glows while something cooks
    for lit, door in ((False, "minecraft:block/black_concrete"), (True, "minecraft:block/campfire_fire")):
        # the frying pan on top (the food cooks in it: GalleyRenderers.Stove): a black iron pan, a rim, a wooden handle
        pan = [el([2.5, 13.5, 2], [10.5, 14, 10], "#p"),
               el([2, 13.5, 1.5], [11, 15, 2], "#p"), el([2, 13.5, 10], [11, 15, 10.5], "#p"),
               el([2, 13.5, 2], [2.5, 15, 10], "#p"), el([10.5, 13.5, 2], [11, 15, 10], "#p"),
               el([11, 14.2, 5.5], [15.5, 14.8, 6.5], "#h")]
        model("stove_lit" if lit else "stove", {"i": tex("cannon_iron"), "d": door, "b": tex("brass"), "particle": tex("cannon_iron"),
                                                "p": "minecraft:block/coal_block", "h": tex("helm_wood")},
              [el([1, 0, 1], [15, 12, 15], "#i"), el([0.5, 12, 0.5], [15.5, 13.5, 15.5], "#i"),
               el([4, 2, 0.5], [12, 8, 1], "#d"), el([3.5, 8, 0.4], [12.5, 8.6, 1], "#b"),
               el([11, 13.5, 11], [14, 16, 14], "#i")] + pan)
    w(f"{A}/blockstates/stove.json", {"variants": {f"facing={d},lit={str(l).lower()}": {
        "model": "seafarers:block/stove_lit" if l else "seafarers:block/stove", "y": r} for d, r in ROT.items() for l in (False, True)}})
    # the Sloop's map table: three blocks across the gallery and two deep (part 0 is the middle of the back row; the
    # front row is model z -16..0); the chart itself is drawn on top by MapTableScreen.TableRenderer
    model("map_table", {"w": tex("helm_wood"), "d": tex("carriage"), "r": tex("rope"), "b": tex("brass"),
                        "particle": tex("helm_wood")},
          [el([-15, 12, -15], [31, 13.5, 15], "#w"), el([-15.5, 11, -15.5], [31.5, 12, 15.5], "#d"),
           el([-14, 0, -14], [-11, 11, -11], "#d"), el([27, 0, -14], [30, 11, -11], "#d"),
           el([-14, 0, 11], [-11, 11, 14], "#d"), el([27, 0, 11], [30, 11, 14], "#d"),
           el([-11, 3, -13], [27, 4.5, -12], "#d"), el([-11, 3, 12], [27, 4.5, 13], "#d"),
           el([-13, 3, -11], [-12, 4.5, 11], "#d"), el([28, 3, -11], [29, 4.5, 11], "#d"),
           el([-13.5, 13.3, -14.2], [-11.5, 14.3, 14.2], "#r"), el([27.5, 13.3, -14.2], [29.5, 14.3, 14.2], "#r"),
           el([20, 13.8, 9], [23, 15, 12], "#b"), el([-8, 13.8, -10], [-6, 15.5, -8], "#b")])
    multi_facing("map_table", 6)
    # the quest table: two blocks long against the cabin's front wall, books and papers on it (part 0 is the west end)
    model("voyage_table", {"w": tex("helm_wood"), "d": tex("carriage"), "p": "minecraft:block/white_wool",
                           "k": "minecraft:block/red_wool", "n": "minecraft:block/blue_wool", "particle": tex("helm_wood")},
          [el([1, 12, 2], [31, 13.5, 14], "#w"), el([0.5, 11, 1.5], [31.5, 12, 14.5], "#d"),
           el([2, 0, 3], [5, 11, 6], "#d"), el([27, 0, 3], [30, 11, 6], "#d"),
           el([2, 0, 10], [5, 11, 13], "#d"), el([27, 0, 10], [30, 11, 13], "#d"),
           el([5, 3, 4], [27, 4.5, 5], "#d"),
           el([3, 13.5, 4], [9, 15, 9], "#k"), el([3.5, 15, 4.5], [8.5, 16.5, 8.5], "#n"),
           el([11, 13.5, 5], [19, 13.8, 11], "#p"), el([21, 13.5, 3.5], [29, 15, 5.5], "#p")])
    multi_facing("voyage_table", 2)
    model("binnacle", {"w": tex("helm_wood"), "b": tex("brass"), "g": "minecraft:block/glass", "particle": tex("brass")},
          [el([5, 0, 5], [11, 10, 11], "#w"), el([4.5, 10, 4.5], [11.5, 13, 11.5], "#b"), el([5.5, 13, 5.5], [10.5, 15, 10.5], "#g"),
           el([3, 11, 7], [4.5, 12.5, 9], "#b"), el([11.5, 11, 7], [13, 12.5, 9], "#b")], render="minecraft:cutout")
    facing("binnacle")
    # a bunk two blocks long (part 0 is the head end, part 1 the foot, to the south)
    model("mattress", {"w": tex("helm_wood"), "m": tex("mattress"), "p": "minecraft:block/white_wool",
                       "b": "minecraft:block/red_wool", "particle": tex("mattress")},
          [el([0, 0, 0], [16, 12, 1.5], "#w"), el([0, 0, 30.5], [16, 8, 32], "#w"),
           el([0, 2, 1.5], [1.5, 6, 30.5], "#w"), el([14.5, 2, 1.5], [16, 6, 30.5], "#w"),
           el([0, 0, 1.5], [2, 3, 3.5], "#w"), el([14, 0, 1.5], [16, 3, 3.5], "#w"),
           el([0, 0, 28.5], [2, 3, 30.5], "#w"), el([14, 0, 28.5], [16, 3, 30.5], "#w"),
           el([1.5, 3, 1.5], [14.5, 4, 30.5], "#w"), el([1.5, 4, 1.5], [14.5, 8, 30.5], "#m"),
           el([3, 8, 2.5], [13, 10, 7], "#p"), el([1.2, 7.5, 12], [14.8, 8.6, 30.8], "#b")])
    multi_facing("mattress", 2)
    model("figurehead", {"w": tex("figurehead"), "b": tex("brass"), "particle": tex("figurehead")},
          [el([5, 0, 6], [11, 9, 14], "#w"), el([4.5, 9, 4], [11.5, 15, 12], "#w"), el([5.5, 15, 3], [10.5, 18, 9], "#w"),
           el([6, 4, 2], [10, 9, 6], "#w"), el([6.5, 15.5, 2], [9.5, 17, 3], "#b"), el([3, 10, 7], [5, 13, 11], "#w"),
           el([11, 10, 7], [13, 13, 11], "#w")])
    facing("figurehead")
    model("flag", {"p": tex("mast"), "f": tex("flag"), "particle": tex("flag")},
          [el([7.25, 0, 7.25], [8.75, 16, 8.75], "#p"), el([8, 8, 7.75], [18, 15, 8.25], "#f"),
           el([18, 9, 7.75], [22, 14, 8.25], "#f")], render="minecraft:cutout")
    model("flag_staff", {"p": tex("mast"), "particle": tex("mast")}, [el([7.25, 0, 7.25], [8.75, 16, 8.75], "#p")])
    w(f"{A}/blockstates/flag.json", {"variants": {f"facing={d},flying={str(f).lower()}": {
        "model": "seafarers:block/flag_staff" if f else "seafarers:block/flag", "y": r} for d, r in ROT.items() for f in (False, True)}})

    # sea chest: a banded wooden chest with a raised lid, brass lock and iron handles
    model("sea_chest", {"w": tex("helm_wood"), "i": tex("cannon_iron"), "b": tex("brass"), "particle": tex("helm_wood")},
          [el([2, 0, 3], [14, 8, 13], "#w"), el([2, 8, 3], [14, 10, 13], "#w"), el([3, 10, 4], [13, 11.5, 12], "#w"),
           el([1.8, 0, 5], [14.2, 10.2, 6], "#i"), el([1.8, 0, 10], [14.2, 10.2, 11], "#i"),
           el([2.8, 10, 5], [13.2, 11.7, 6], "#i"), el([2.8, 10, 10], [13.2, 11.7, 11], "#i"),
           el([7, 6, 2.5], [9, 9, 3], "#b"), el([1, 5, 7], [2, 6, 9], "#i"), el([14, 5, 7], [15, 6, 9], "#i")])
    facing("sea_chest")
    # cask: the same round barrel as the supply barrels, lidded
    model("cask", {"side": tex("barrel_side"), "top": tex("barrel_top"), "hoop": tex("cannon_iron"), "particle": tex("barrel_side")},
          round_barrel(15))
    facing("cask")
    # the ship's bell: vanilla's wall bell (the bell itself is vanilla's, drawn by its block entity), its beam
    # three pixels longer so it reaches into the mast (10 pixels wide) behind it; facing is the way to the mast
    # the bowsprit's walkable top: invisible (the spar is drawn by the heel's renderer)
    w(f"{A}/blockstates/bowsprit_spar.json", {"variants": {"": {"model": "minecraft:block/air"}}})
    w(f"{A}/models/block/ship_bell.json", {"textures": {"particle": "minecraft:block/bell_bottom", "bar": "minecraft:block/dark_oak_planks"},
        "elements": [{"from": [3, 13, 7], "to": [19, 15, 9], "faces": {
            "north": {"uv": [2, 2, 14, 4], "texture": "#bar"}, "east": {"uv": [5, 4, 7, 6], "texture": "#bar"},
            "south": {"uv": [2, 3, 14, 5], "texture": "#bar"}, "west": {"uv": [5, 4, 7, 6], "texture": "#bar"},
            "up": {"uv": [2, 3, 14, 5], "texture": "#bar"}, "down": {"uv": [2, 3, 14, 5], "texture": "#bar"}}}]})
    w(f"{A}/blockstates/ship_bell.json", {"variants": {f"facing={d}": {"model": "seafarers:block/ship_bell", **({"y": r} if r else {})}
                                                       for d, r in (("east", 0), ("south", 90), ("west", 180), ("north", 270))}})

    # water barrel: an open-topped barrel (walls, hoops, a floor) with the water showing at its level (tint 0: water)
    wb = {"side": tex("barrel_side"), "top": tex("barrel_top"), "hoop": tex("cannon_iron"), "w": "minecraft:block/water_still",
          "particle": tex("barrel_side")}
    walls = [el([3, 0, 1.5], [13, 15, 3], "#side"), el([3, 0, 13], [13, 15, 14.5], "#side"),
             el([1.5, 0, 3], [3, 15, 13], "#side"), el([13, 0, 3], [14.5, 15, 13], "#side"),
             el([2, 0, 2], [4, 15, 4], "#side"), el([12, 0, 2], [14, 15, 4], "#side"),
             el([2, 0, 12], [4, 15, 14], "#side"), el([12, 0, 12], [14, 15, 14], "#side"),
             el([3, 0, 3], [13, 1, 13], "#top")]
    for y in (2, 11):   # hoops: thin bands round the outside (not slabs across it, or they'd lid the barrel)
        walls += [el([3, y, 1.2], [13, y + 1, 1.6], "#hoop"), el([3, y, 14.4], [13, y + 1, 14.8], "#hoop"),
                  el([1.2, y, 3], [1.6, y + 1, 13], "#hoop"), el([14.4, y, 3], [14.8, y + 1, 13], "#hoop")]
    for lvl in range(4):
        water = []
        if lvl:
            h = 1 + lvl * 4.3
            water = [{"from": [3, 1, 3], "to": [13, h, 13], "faces": {"up": {"texture": "#w", "tintindex": 0}}}]
        # cutout, not translucent: Sable drops translucent faces inside a ship's dry hold (it takes them for sea)
        model(f"water_barrel_{lvl}", wb, walls + water, render="minecraft:cutout")
    w(f"{A}/blockstates/water_barrel.json", {"variants": {f"facing={d},level={l}": {"model": f"seafarers:block/water_barrel_{l}", "y": r}
                                                          for d, r in ROT.items() for l in range(4)}})
    w(f"{A}/models/item/water_barrel.json", {"parent": "seafarers:block/water_barrel_3"})

    # ---------------- items ----------------
    w(f"{A}/models/item/rope_ladder.json", {"parent": "minecraft:item/generated", "textures": {"layer0": tex("rope_ladder")}})
    blocks = ["hull_planks", "hull_bottom", "hull_bottom_stairs", "hull_trim", "deck_planks", "deck_stairs", "keel", "mast",
              "spar", "deck_slab", "bowsprit",
              "helm", "sail_rope", "brace_rope", "anchor", "cannon", "harpoon", "map_table", "voyage_table", "binnacle",
              "mattress", "figurehead", "flag", "flag_box", "stove", "oars", "rowboat_hook", "sea_chest", "cask"]
    w(f"{A}/models/item/ship_bell.json", {"parent": "minecraft:item/bell"})
    big = {"map_table": 0.3, "voyage_table": 0.38, "mattress": 0.38, "cannon": 0.4}
    for b in blocks:
        item = {"parent": f"seafarers:block/{b}"}
        if b in big:
            k = big[b]
            item["display"] = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [k, k, k]},
                               "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [k / 2, k / 2, k / 2]},
                               "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [k, k, k]},
                               "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [k / 1.5] * 3},
                               "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [k / 1.5] * 3}}
        w(f"{A}/models/item/{b}.json", item)
    w(f"{A}/models/item/railing.json", {"parent": "seafarers:block/railing_inventory"})
    w(f"{A}/models/item/supply_barrel.json", {"parent": "seafarers:block/supply_barrel_cannonballs"})
    w(f"{A}/models/item/sail_yard.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "seafarers:block/sail_cloth"}})
    w(f"{A}/models/item/bucket_rack.json", {"parent": "seafarers:block/bucket_rack"})
    # ship fire: crossed flames (vanilla's fire frames), taller as it grows
    for age in range(4):
        h = 6 + age * 3
        w(f"{A}/models/block/ship_fire_{age}.json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "ambientocclusion": False, "textures": {"f": "minecraft:block/fire_0", "particle": "minecraft:block/fire_0"},
            "elements": [{"from": [0, 0, 8], "to": [16, h, 8], "shade": False,
                          "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
                          "faces": {"north": {"texture": "#f", "uv": [0, 16 - h, 16, 16]}, "south": {"texture": "#f", "uv": [0, 16 - h, 16, 16]}}},
                         {"from": [8, 0, 0], "to": [8, h, 16], "shade": False,
                          "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
                          "faces": {"east": {"texture": "#f", "uv": [0, 16 - h, 16, 16]}, "west": {"texture": "#f", "uv": [0, 16 - h, 16, 16]}}}]})
    w(f"{A}/blockstates/ship_fire.json", {"variants": {f"age={a}": {"model": f"seafarers:block/ship_fire_{a}"} for a in range(4)}})
    # the brig's door: vanilla's iron door, barred window and all (its models, turned and hinged as vanilla's are)
    door = {}
    for side, y0 in {"east": 0, "south": 90, "west": 180, "north": 270}.items():
        for half, part in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for opened in (False, True):
                    v = {"model": f"minecraft:block/iron_door_{part}_{hinge}" + ("_open" if opened else "")}
                    y = (y0 + ((90 if hinge == "left" else 270) if opened else 0)) % 360
                    if y:
                        v["y"] = y
                    door[f"facing={side},half={half},hinge={hinge},open={str(opened).lower()}"] = v
    w(f"{A}/blockstates/brig_door.json", {"variants": door})
    w(f"{A}/models/item/brig_door.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/iron_door"}})
    for i in ["cannonball", "chain_shot", "banana", "sloop_deed", "brigantine_deed", "galleon_deed", "ship_bucket", "ship_bucket_water", "ship_bucket_vomit", "grog",
              "rowboat", "rowboat_cannon", "rowboat_harpoon", "charred_food", "grog_bucket",
              "firebomb", "anchor_ball", "rigging_ball", "ballast_ball", "grog_ball", "limp_ball", "venom_ball", "weary_ball", "jig_ball"]:
        w(f"{A}/models/item/{i}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"seafarers:item/{i}"}})
    # the Ship's Compass: vanilla's compass faces (tinted brass in code), the needle picked by the "angle" property
    # just as vanilla's compass model does it
    w(f"{A}/models/item/ships_compass.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/compass_16"},
        # (vanilla's frame 16 is the plain "item/compass" model; there's no compass_16 model)
        "overrides": [{"predicate": {"angle": 0.0}, "model": "minecraft:item/compass"}]
        + [{"predicate": {"angle": (2 * j + 1) / 64}, "model": "minecraft:item/compass" if (17 + j) % 32 == 16
            else f"minecraft:item/compass_{(17 + j) % 32:02d}"} for j in range(32)]})

    import sys; sys.path.insert(0, str(pathlib.Path(__file__).parent)); from lang_captains import LANG as captains_lang; from gen_advancements import LANG as advancement_lang; from lang_ships import LANG as ships_lang
    sound_lang = json.loads((ROOT.parent.parent.parent / "tools" / "sound_lang.json").read_text()) \
        if (ROOT.parent.parent.parent / "tools" / "sound_lang.json").exists() else {}
    w(f"{A}/lang/en_us.json", {**sound_lang, **captains_lang, **advancement_lang, **ships_lang,
        "itemGroup.seafarers": "Seafarers",
        "block.seafarers.hull_planks": "Hull Planks", "block.seafarers.hull_bottom": "Tarred Hull Planks",
        "block.seafarers.hull_bottom_stairs": "Tarred Hull Stairs", "block.seafarers.hull_trim": "Gunwale Trim",
        "block.seafarers.deck_planks": "Deck Planks", "block.seafarers.deck_stairs": "Deck Stairs",
        "block.seafarers.railing": "Ship Railing", "block.seafarers.spar": "Spar", "block.seafarers.deck_slab": "Deck Slab",
        "block.seafarers.rope_ladder": "Rope Ladder", "block.seafarers.bowsprit": "Bowsprit", "block.seafarers.keel": "Keel Timber", "block.seafarers.mast": "Mast",
        "block.seafarers.hull_hole": "Hull Hole", "block.seafarers.bilge_water": "Bilge Water",
        "block.seafarers.helm": "Ship's Wheel", "block.seafarers.sail_yard": "Sail and Yard",
        "block.seafarers.sail_rope": "Sail Length Cleat", "block.seafarers.brace_rope": "Sail Angle Pulley",
        "block.seafarers.anchor": "Capstan", "block.seafarers.cannon": "Cannon",
        "block.seafarers.supply_barrel": "Supply Barrel", "block.seafarers.harpoon": "Harpoon Gun",
        "block.seafarers.map_table": "Map Table", "block.seafarers.voyage_table": "Quest Table",
        "block.seafarers.binnacle": "Compass Binnacle", "block.seafarers.mattress": "Bunk",
        "block.seafarers.figurehead": "Figurehead", "block.seafarers.flag": "Pennant",
        "item.seafarers.cannonball": "Cannonball", "item.seafarers.cannonball.tip": "Use on a cannon to load it",
        "item.seafarers.chain_shot": "Chain Shot", "item.seafarers.chain_shot.tip": "Tears sails and breaks masts; bounces off hulls",
        "item.seafarers.banana": "Banana",
        "item.seafarers.sloop_deed": "Sloop Deed",
        "entity.seafarers.cannonball": "Cannonball",
        "block.seafarers.voxel_hull": "Hull", "block.seafarers.ship_lantern": "Ship's Lantern",
        "message.seafarers.rope_no_ship": "This rope isn't rigged to a ship with a helm.",
        "message.seafarers.capstan_no_ship": "This capstan isn't on a ship with a helm.",
        "block.seafarers.bucket_rack": "Bucket Rack",
        "item.seafarers.ship_bucket": "Bucket", "item.seafarers.ship_bucket_water": "Bucket of Water",
        "item.seafarers.ship_bucket_vomit": "Bucket of Vomit",
        "item.seafarers.ship_bucket.tip.empty": "Use aboard a flooding ship to bail, or on the sea to fill",
        "item.seafarers.ship_bucket.tip.water": "Use to throw: puts out fires",
        "item.seafarers.ship_bucket.tip.vomit": "Use to throw: whoever it hits throws up",
        "item.seafarers.grog": "Bottle of Grog", "item.seafarers.grog_bucket": "Bucket of Grog",
        "item.seafarers.grog.tip": "%s drink(s). Each one makes you a little more unsteady; too many and the world spins",
        "effect.seafarers.drunk": "Drunk", "effect.seafarers.jig": "Jig", "block.seafarers.ship_fire": "Ship Fire",
        "message.seafarers.cannon_climb_in": "You climb into the barrel. Left click to fire yourself, sneak to climb out.",
        "item.seafarers.firebomb": "Firebomb", "item.seafarers.firebomb.tip": "Sets ships ablaze; put fires out with a thrown bucket of water",
        "item.seafarers.anchor_ball": "Anchorball", "item.seafarers.anchor_ball.tip": "Cursed: drops the anchor of the ship it hits",
        "item.seafarers.rigging_ball": "Riggingball", "item.seafarers.rigging_ball.tip": "Cursed: hauls up every sail of the ship it hits",
        "item.seafarers.ballast_ball": "Ballastball", "item.seafarers.ballast_ball.tip": "Cursed: the ship it hits sits heavy and slow for a while",
        "item.seafarers.grog_ball": "Grogball", "item.seafarers.grog_ball.tip": "Cursed: everyone it hits is roaring drunk",
        "item.seafarers.limp_ball": "Limpball", "item.seafarers.limp_ball.tip": "Cursed: everyone it hits can barely walk",
        "item.seafarers.venom_ball": "Venomball", "item.seafarers.venom_ball.tip": "Cursed: poisons everyone it hits",
        "item.seafarers.weary_ball": "Wearyball", "item.seafarers.weary_ball.tip": "Cursed: everyone it hits is too sleepy to see straight",
        "item.seafarers.jig_ball": "Jigball", "item.seafarers.jig_ball.tip": "Cursed: everyone it hits dances a jig",

        "block.seafarers.oars": "Rowing Bench", "block.seafarers.rowboat_hook": "Rowboat Hook",
        "block.seafarers.flag_box": "Flag Box", "block.seafarers.stove": "Galley Stove", "entity.seafarers.harpoon": "Harpoon",
        "item.seafarers.rowboat": "Rowboat",
        "item.seafarers.rowboat.tip": "Rowboats are found along the coasts. This one (Creative) launches on open water",
        "item.seafarers.charred_food": "Charred Food",
        "item.seafarers.rowboat_cannon": "Cannon Rowboat",
        "item.seafarers.rowboat_cannon.tip": "A rowboat with a cannon in the bow. Found along the coasts; this one (Creative) launches on open water",
        "item.seafarers.rowboat_harpoon": "Harpoon Rowboat",
        "item.seafarers.rowboat_harpoon.tip": "A rowboat with a harpoon gun in the bow. Found along the coasts; this one (Creative) launches on open water",
        "message.seafarers.oars_hint": "You take the oars. W/S to row, A/D to turn, sneak to let go.",
        "message.seafarers.harpoon_hint": "Look to aim, left click to fire (again to cut the line), hold W to reel in, sneak to step away.",
        "message.seafarers.docked": "Rowboat docked.", "message.seafarers.undocked": "Cast off.",
        "message.seafarers.no_dock": "No ship's ladder close enough to dock at.",
        "message.seafarers.no_flagstaff": "This ship has no flagstaff.",
        "message.seafarers.flag_hint": "Use a banner on the flag box to hoist it at the masthead.",
        "message.seafarers.flag_raised": "Hoisted %s.", "message.seafarers.flag_lowered": "Flag struck.",
        "message.seafarers.stove_hint": "Put raw food on the stove. Take it off when the smoke turns white, before it goes black.",
        "map.seafarers.hint": "Drag to move the chart, scroll to zoom. Click to mark, right click to rub out. C: back to the ship", "map.seafarers.blocks": "%s blocks",
        "map.seafarers.you_are_here": "Your ship",
        "message.seafarers.voyage_table_full": "There's no room for another voyage on the table.",
        "message.seafarers.voyage_hint": "Put a treasure map down to propose a voyage; use the table empty handed to vote, sneak to take yours back.",
        "message.seafarers.voyage_votes": "%s: %s of %s agree",
        "message.seafarers.voyage_started": "Voyage started: %s",
        "message.seafarers.hull_painted": "Hull painted %s.", "message.seafarers.sails_dyed": "Sails dyed %s.",
        "block.seafarers.sea_chest": "Sea Chest", "block.seafarers.cask": "Cask", "block.seafarers.ship_bell": "Ship's Bell",
        "message.seafarers.bell": "%s rings the ship's bell!",
        "block.seafarers.water_barrel": "Water Barrel",
        "container.seafarers.supply_barrel.cannonballs": "Cannonballs", "container.seafarers.supply_barrel.planks": "Planks",
        "container.seafarers.supply_barrel.food": "Food", "container.seafarers.supply_barrel.grog": "Grog",
        "message.seafarers.grog_hint": "Hold out a glass bottle or a bucket to pour some grog.",
        "message.seafarers.water_barrel_level": "Water: %s of 3. Use a bucket, bottle or ship's bucket.",
        "message.seafarers.water_barrel_empty": "Empty. It fills itself again, slowly.",
        "message.seafarers.voyage_hold_one": "Hold a voyage (a treasure or explorer map) in your hand to put it on the table.",
        "message.seafarers.not_your_ship": "Only her crew can rename her.",
        "ship.seafarers.sloop": "Sloop", "ship.seafarers.rowboat": "Rowboat", "ship.seafarers.unknown": "Ship",
        "voyage.seafarers.tab.voyages": "Voyages", "voyage.seafarers.tab.crew": "Crew", "voyage.seafarers.tab.ship": "Ship",
        "voyage.seafarers.tab.companies": "Companies", "voyage.seafarers.coming_soon": "Trading companies, reputation and emissaries are coming in a later update.",
        "voyage.seafarers.none": "No voyages on the table. Hold a treasure map or explorer map and press Propose (or use the table with it).",
        "voyage.seafarers.by_votes": "from %s - %s of %s agree", "voyage.seafarers.vote": "Vote", "voyage.seafarers.voted": "Voted",
        "voyage.seafarers.take_back": "Take back", "voyage.seafarers.propose": "Propose the map I'm holding",
        "voyage.seafarers.agree": "When everyone agrees, it starts.",
        "voyage.seafarers.crew_count": "Crew: %s of %s", "voyage.seafarers.no_crew": "She has no crew. Sleep in her bunk to join her crew.",
        "voyage.seafarers.aboard": "aboard", "voyage.seafarers.ashore": "ashore", "voyage.seafarers.offline": "away",
        "voyage.seafarers.leave_crew": "Leave crew",
        "voyage.seafarers.ship_name": "Name", "voyage.seafarers.rename": "Rename", "voyage.seafarers.class": "Class",
        "voyage.seafarers.water": "Water aboard", "voyage.seafarers.holes": "Holes", "voyage.seafarers.mast": "Mast",
        "voyage.seafarers.mast_broken": "broken", "voyage.seafarers.anchor": "Anchor",
        "voyage.seafarers.down": "down", "voyage.seafarers.up": "up", "voyage.seafarers.speed": "Speed",
        "message.seafarers.bed_set": "You'll wake here, wherever she sails.",
        "message.seafarers.mattress_ashore": "A ship's mattress only works aboard a ship. Use a bed ashore.",
        "message.seafarers.no_room": "Not enough open water here to launch a ship.",
        "message.seafarers.station_taken": "Someone else is already on it.",
        "message.seafarers.helm_hint": "You take the wheel. A/D to steer, sneak to let go.",
        "message.seafarers.cannon_hint": "Look to aim, left click to fire, sneak to step away.",
        "message.seafarers.cannon_hint_empty": "Not loaded: use ammo on the cannon first. Sneak to step away.",
        "message.seafarers.cannon_loaded": "This cannon is already loaded.",
        "message.seafarers.barrel_stock": "%s / %s left",
        "message.seafarers.barrel_empty": "This barrel is empty.",
        "message.seafarers.going_down": "She's going down!",
        "message.seafarers.sunk": "Your ship has sunk.",
        "message.seafarers.new_ship": "A new ship awaits your crew %s blocks away, at %s, %s. You will respawn aboard her.",
        "message.seafarers.mast_broken": "The mast is broken. Patch it with planks before raising the sail.",
        "message.seafarers.mast_down": "The mast is broken! Patch it with planks.",
        "message.seafarers.back_aboard": "Back aboard your ship.",
        "message.seafarers.no_ship": "You aren't in a ship's crew.",
        "message.seafarers.invite_sent": "Invited %s.",
        "message.seafarers.no_invite": "You have no crew invite (or that ship is gone).",
        "message.seafarers.crew_full": "That crew is full (%s max).",
        "message.seafarers.joined": "You joined the crew. You will respawn aboard.",
        "message.seafarers.left": "You left your crew.",
    })

    # ---------------- shapes ----------------
    # A fitting's collision and outline are its model (with the parts its renderer draws, at rest), in model
    # space facing north; the block turns them to its facing. Read by dev.seafarers.ship.block.FittingShapes.
    shaped = {
        "helm": ["helm", "helm_wheel"], "oars": ["oars"], "cannon": ["cannon", "cannon_barrel"],
        "harpoon": ["harpoon", "harpoon_gun", "harpoon_bolt"], "anchor": ["anchor"], "bucket_rack": ["bucket_rack"],
        "map_table": ["map_table"], "voyage_table": ["voyage_table"], "binnacle": ["binnacle"], "mattress": ["mattress"],
        "figurehead": ["figurehead"], "flag": ["flag_staff"], "flag_box": ["flag_box"], "stove": ["stove"],
        "rowboat_hook": ["rowboat_hook"], "sail_rope": ["sail_rope"], "brace_rope": ["brace_rope"],
        "sea_chest": ["sea_chest"], "cask": ["cask"],
    }
    # fittings several blocks big: part offsets {east, up, south} (as in MultiPart); each part gets its own cell's slice
    multi = {"map_table": [(0, 0, 0), (-1, 0, 0), (1, 0, 0), (0, 0, -1), (-1, 0, -1), (1, 0, -1)], "voyage_table": [(0, 0, 0), (1, 0, 0)],
             "cannon": [(0, 0, 0), (0, 0, -1)], "mattress": [(0, 0, 0), (0, 0, 1)]}
    # drawn by renderers, not block models: the capstan's drum
    extra = {"anchor": [[2.6, 3.2, 2.6, 13.4, 13.4, 13.4], [2.5, 11.5, 2.5, 13.5, 13.5, 13.5]]}
    shapes = {}
    for block, offsets in multi.items():
        models = shaped.get(block, [block])
        boxes = [b for m in models for b in MODEL_BOXES[m]]
        for i, (ox, oy, oz) in enumerate(offsets):
            part = []
            for b in boxes:
                c = [b[0] - 16 * ox, b[1] - 16 * oy, b[2] - 16 * oz, b[3] - 16 * ox, b[4] - 16 * oy, b[5] - 16 * oz]
                c = [max(0.0, min(16.0, v)) for v in c]
                if c[3] > c[0] and c[4] > c[1] and c[5] > c[2]:
                    part.append([round(v, 3) for v in c])
            shapes[f"{block}#{i}"] = part
    for block, models in shaped.items():
        if block in multi:
            continue
        boxes = [b for m in models for b in MODEL_BOXES[m]] + extra.get(block, [])
        # inside the block: bits that reach past it (the harpoon's tip, the oar handles) don't block the next one
        clamped = []
        for b in boxes:
            c = [max(0.0, min(16.0, v)) for v in b]
            if c[3] > c[0] and c[4] > c[1] and c[5] > c[2]:
                clamped.append([round(v, 3) for v in c])
        shapes[block] = clamped
    w(f"{A}/fitting_shapes.json", shapes)

    # ---------------- block drops ----------------
    # every block a player can place drops itself; a fitting several blocks big drops once (from its main part).
    # The hull, holes, bilge water and fire drop nothing.
    multi_blocks = {"map_table", "voyage_table", "cannon", "mattress"}
    for b in ["hull_planks", "hull_bottom", "hull_bottom_stairs", "hull_trim", "deck_planks", "deck_stairs", "railing", "keel",
              "mast", "spar", "bucket_rack", "ship_lantern", "bowsprit", "deck_slab", "rope_ladder", "helm", "sail_yard", "sail_rope",
              "brace_rope", "anchor", "cannon", "supply_barrel", "harpoon", "map_table", "voyage_table", "binnacle", "mattress",
              "figurehead", "flag", "flag_box", "stove", "oars", "rowboat_hook", "sea_chest", "cask", "ship_bell", "water_barrel",
              "brig_door"]:
        entry = {"type": "minecraft:item", "name": f"seafarers:{b}"}
        pool = {"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}
        if b in multi_blocks:
            pool["conditions"].append({"condition": "minecraft:block_state_property", "block": f"seafarers:{b}",
                                       "properties": {"part": "0"}})
        if b == "brig_door":
            pool["conditions"].append({"condition": "minecraft:block_state_property", "block": "seafarers:brig_door",
                                       "properties": {"half": "lower"}})
        if b == "supply_barrel":
            # keeps which supply it is (cannonballs, planks, food, grog)
            entry["functions"] = [{"function": "minecraft:copy_state", "block": "seafarers:supply_barrel", "properties": ["supply"]}]
        w(f"data/seafarers/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [pool]})

    # ---------------- damage ----------------
    # a cannonball's own damage, so deaths read "blasted overboard by Alex's cannonball"; it counts as a projectile
    w("data/seafarers/damage_type/cannonball.json", {"message_id": "seafarers.cannonball", "exhaustion": 0.1, "scaling": "when_caused_by_living_non_player"})
    w("data/minecraft/tags/damage_type/is_projectile.json", {"replace": False, "values": ["seafarers:cannonball"]})

    # ---------------- recipes ----------------
    def shaped(name, pattern, key, result=None, count=1, cat="misc"):
        keys = {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()}
        w(f"data/seafarers/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": cat, "pattern": pattern,
                                                 "key": keys, "result": {"id": result or f"seafarers:{name}", "count": count}})

    def shapeless(name, items, result=None, count=1, cat="misc"):
        w(f"data/seafarers/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": cat,
                                                 "ingredients": [{"tag": i[1:]} if i.startswith("#") else {"item": i} for i in items],
                                                 "result": {"id": result or f"seafarers:{name}", "count": count}})

    # ships
    # no recipe for ship deeds: a player's first ship comes from the ship choice screen (deeds are otherwise Creative-only)
    shapeless("ships_compass", ["minecraft:compass", "minecraft:copper_ingot"], cat="equipment")
    # rowboats aren't crafted: they wait along the coasts (see Rowboats); the items are for Creative and testing
    # ammunition
    shaped("cannonball", ["NNN", "N N", "NNN"], {"N": "minecraft:iron_nugget"}, count=4, cat="equipment")
    shapeless("chain_shot", ["seafarers:cannonball", "seafarers:cannonball", "minecraft:chain"], count=2, cat="equipment")
    shapeless("firebomb", ["minecraft:glass_bottle", "minecraft:fire_charge", "minecraft:gunpowder"], count=2, cat="equipment")
    for ball, extra in {"anchor_ball": "minecraft:chain", "rigging_ball": "minecraft:string", "ballast_ball": "minecraft:iron_ingot",
                        "grog_ball": "seafarers:grog", "limp_ball": "minecraft:slime_ball", "venom_ball": "minecraft:spider_eye",
                        "weary_ball": "minecraft:phantom_membrane", "jig_ball": "minecraft:note_block"}.items():
        shapeless(ball, ["seafarers:cannonball", "minecraft:soul_sand", extra], cat="equipment")
    # crew's things
    shaped("ship_bucket", ["PSP", " P "], {"P": "#minecraft:planks", "S": "minecraft:string"}, cat="equipment")
    shapeless("grog", ["minecraft:glass_bottle", "minecraft:sugar_cane", "minecraft:sweet_berries"])
    # fittings
    shaped("cannon", [" I ", "III", "P P"], {"I": "minecraft:iron_ingot", "P": "#minecraft:planks"}, cat="equipment")
    shaped("harpoon", ["I  ", " S ", "PPP"], {"I": "minecraft:iron_ingot", "S": "minecraft:string", "P": "#minecraft:planks"}, cat="equipment")
    shaped("sea_chest", ["PIP", "P P", "PPP"], {"P": "#minecraft:planks", "I": "minecraft:iron_ingot"})
    shaped("cask", ["PNP", "P P", "PNP"], {"P": "#minecraft:planks", "N": "minecraft:iron_nugget"})
    shaped("water_barrel", ["P P", "PBP", "PPP"], {"P": "#minecraft:planks", "B": "minecraft:bucket"})
    shaped("stove", ["I I", "IFI", "III"], {"I": "minecraft:iron_ingot", "F": "minecraft:furnace"})
    shaped("ship_bell", [" S ", "CCC", "C C"], {"S": "minecraft:string", "C": "minecraft:copper_ingot"})
    shapeless("ship_lantern", ["minecraft:lantern", "minecraft:string"])
    shaped("rope_ladder", ["S S", "SSS", "S S"], {"S": "minecraft:string"}, count=4)
    shaped("bucket_rack", ["S S", "PPP", "S S"], {"S": "minecraft:stick", "P": "#minecraft:planks"})
    shaped("mattress", ["WWW", "PPP", "S S"], {"W": "#minecraft:wool", "P": "#minecraft:planks", "S": "minecraft:stick"})
    shaped("map_table", ["MMM", "PPP", "S S"], {"M": "minecraft:paper", "P": "#minecraft:planks", "S": "minecraft:stick"})
    shaped("voyage_table", ["BPB", "PPP", "S S"], {"B": "minecraft:book", "P": "#minecraft:planks", "S": "minecraft:stick"})
    shaped("flag_box", ["PWP", "PPP"], {"P": "#minecraft:planks", "W": "#minecraft:wool"})

    # ---------------- data ----------------
    # Sable physics: light, buoyant hull planks stand in for the air inside a real hull
    d = "data/seafarers/physics_block_properties"
    # mass tuned so the sloop rides with the sea just below her lower-deck floor (measured with ship status)
    buoyant = {"sable:mass": 0.16, "sable:volume": 2.0}
    flooded = {"flooded=true": {"sable:volume": 0.2}}
    w(f"{d}/hull_planks.json", {"selector": "seafarers:hull_planks", "properties": buoyant, "overrides": flooded})
    w(f"{d}/hull_bottom.json", {"selector": "seafarers:hull_bottom", "properties": buoyant, "overrides": flooded})
    w(f"{d}/hull_bottom_stairs.json", {"selector": "seafarers:hull_bottom_stairs", "properties": {"sable:mass": 0.16, "sable:volume": 1.5}})
    w(f"{d}/hull_hole.json", {"selector": "seafarers:hull_hole", "properties": {"sable:mass": 0.5, "sable:volume": 0.0}})
    w(f"{d}/light_fittings.json", {"selector": "#seafarers:light_fittings", "properties": {"sable:mass": 0.16, "sable:volume": 0.0}})
    w(f"{d}/cannon.json", {"selector": "seafarers:cannon", "properties": {"sable:mass": 2.0, "sable:volume": 0.3}})
    w(f"{d}/keel.json", {"selector": "seafarers:keel", "properties": {"sable:mass": 4.0, "sable:volume": 0.5}})
    w(f"{d}/mast.json", {"selector": "seafarers:mast", "properties": {"sable:mass": 0.15, "sable:volume": 0.0}})
    w(f"{d}/deck_planks.json", {"selector": "seafarers:deck_planks", "properties": {"sable:mass": 0.3}})
    w(f"{d}/hull_trim.json", {"selector": "seafarers:hull_trim", "properties": {"sable:mass": 0.3}})
    w(f"{d}/stove.json", {"selector": "seafarers:stove", "properties": {"sable:mass": 1.0, "sable:volume": 0.0}})
    w(f"{d}/oars.json", {"selector": "seafarers:oars", "properties": {"sable:mass": 0.2, "sable:volume": 0.0}})
    w("data/seafarers/tags/item/voyages.json", {"values": []})
    w(f"{d}/bilge_water.json", {"selector": "seafarers:bilge_water", "properties": {"sable:mass": 0.0, "sable:volume": 0.0}})
    w("data/seafarers/tags/block/hull.json", {"values": [
        "seafarers:hull_planks", "seafarers:hull_bottom", "seafarers:hull_bottom_stairs", "seafarers:hull_trim",
        "seafarers:voxel_hull"]})
    w("data/seafarers/tags/block/light_fittings.json", {"values": [
        "seafarers:railing", "seafarers:sail_yard", "seafarers:sail_rope", "seafarers:brace_rope", "seafarers:flag",
        "seafarers:mattress", "seafarers:map_table", "seafarers:rope_ladder", "seafarers:bowsprit", "seafarers:spar", "seafarers:deck_slab", "seafarers:voyage_table", "seafarers:binnacle",
        "seafarers:flag_box", "seafarers:rowboat_hook", "seafarers:harpoon", "seafarers:sea_chest", "seafarers:cask",
        "seafarers:ship_bell"]})
    w("data/minecraft/tags/block/mineable/axe.json", {"replace": False, "values": [
        "seafarers:hull_planks", "seafarers:hull_bottom", "seafarers:hull_bottom_stairs", "seafarers:hull_trim",
        "seafarers:deck_planks", "seafarers:deck_stairs", "seafarers:railing", "seafarers:keel", "seafarers:mast",
        "seafarers:hull_hole", "seafarers:helm", "seafarers:sail_rope", "seafarers:brace_rope", "seafarers:anchor",
        "seafarers:supply_barrel", "seafarers:map_table", "seafarers:voyage_table", "seafarers:figurehead",
        "seafarers:harpoon", "seafarers:sail_yard", "seafarers:flag", "seafarers:voxel_hull", "seafarers:bucket_rack",
        "seafarers:flag_box", "seafarers:oars", "seafarers:rowboat_hook", "seafarers:sea_chest", "seafarers:cask",
        "seafarers:water_barrel"]})
    w("data/minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": ["seafarers:cannon", "seafarers:binnacle", "seafarers:stove", "seafarers:brig_door"]})
    w("data/seafarers/tags/item/deeds.json", {"values": ["seafarers:sloop_deed", "seafarers:brigantine_deed", "seafarers:galleon_deed"]})
    w("data/minecraft/tags/block/fences.json", {"replace": False, "values": ["seafarers:railing"]})
    w("data/minecraft/tags/block/wooden_fences.json", {"replace": False, "values": ["seafarers:railing"]})
    # rope ladders climb like ladders (vanilla and Sable both go by this tag)
    w("data/minecraft/tags/block/climbable.json", {"replace": False, "values": ["seafarers:rope_ladder"]})
    w("data/minecraft/tags/block/stairs.json", {"replace": False, "values": ["seafarers:hull_bottom_stairs", "seafarers:deck_stairs"]})


if __name__ == "__main__":
    main()
