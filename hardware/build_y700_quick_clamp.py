"""Generate a Quick-Release Rubber-Band/Cord Tensioned Clamp Pair with Switch 2
Joy-Con 2 snap-locking rails for the Lenovo Legion Y700 (2025 / Gen 3).

No full backplate — two lightweight end-cap C-clamps hook onto the left and right
short edges of the Y700, held together across the back by two standard rubber
bands or elastic shock cords for 1-second dock/undock removal.
"""

import os
import zipfile
import xml.etree.ElementTree as ET
import numpy as np
import trimesh
import manifold3d as m3d

JC2_3MF = r"C:\Users\majsz\.gemini\antigravity\brain\b9ff5005-3f23-4f12-b350-57a55aa3e919\.user_uploaded\media_1794670889624_558ad4a7.3mf"
OUT_DIR = os.path.dirname(__file__)


def load_3mf_object(path: str, obj_path: str) -> trimesh.Trimesh:
    with zipfile.ZipFile(path) as z:
        root = ET.fromstring(z.read(obj_path))
    ns = {"m": "http://schemas.microsoft.com/3dmanufacturing/core/2015/02"}
    verts = np.array(
        [[float(v.attrib["x"]), float(v.attrib["y"]), float(v.attrib["z"])] for v in root.findall(".//m:vertex", ns)],
        dtype=np.float32,
    )
    tris = np.array(
        [[int(t.attrib["v1"]), int(t.attrib["v2"]), int(t.attrib["v3"])] for t in root.findall(".//m:triangle", ns)],
        dtype=np.uint32,
    )
    return trimesh.Trimesh(vertices=verts, faces=tris)


def to_manifold(mesh: trimesh.Trimesh) -> m3d.Manifold:
    m = m3d.Mesh(vert_properties=mesh.vertices.astype(np.float32), tri_verts=mesh.faces.astype(np.uint32))
    m.merge()
    mf = m3d.Manifold(m)
    if mf.status() != m3d.Error.NoError:
        raise RuntimeError(f"Manifold error: {mf.status()}")
    return mf


def from_manifold(mf: m3d.Manifold) -> trimesh.Trimesh:
    mesh = mf.to_mesh()
    return trimesh.Trimesh(vertices=mesh.vert_properties[:, :3], faces=mesh.tri_verts, process=False)


def make_box(x0: float, x1: float, y0: float, y1: float, z0: float, z1: float) -> m3d.Manifold:
    size = [x1 - x0, y1 - y0, z1 - z0]
    center = [(x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0]
    return m3d.Manifold.cube(size, center=True).translate(center)


def build_end_clamp(rail_mf: m3d.Manifold, is_right: bool) -> m3d.Manifold:
    """Build one end-cap C-clamp oriented with its Joy-Con 2 rail on +Y (if is_right) or -Y."""
    # Y700 width = 129.46 mm -> half = 64.73 mm. Add ~0.47 mm/side clearance -> +/- 65.2 mm.
    inner_x = 65.2
    wall_x = 3.2
    outer_x = inner_x + wall_x  # 68.4 mm

    # Y700 thickness = 7.79 mm + ~0.5 mm tempered glass = ~8.3 mm -> 8.6 mm pocket (Z=2.6..11.2)
    z_floor = 2.6
    z_pocket_top = 11.2
    z_lip_top = 13.2

    # Rail sits at Y in [105.0, 118.56] (or [-118.56, -105.0] for left)
    # Clamp spine sits at Y in [101.6, 105.4] (overlaps rail base by 0.4 mm for clean union)
    y_spine_in = 101.6
    y_spine_out = 105.4
    y_arm_tip = 84.0  # 17.6 mm grip onto tablet corners

    def y_box(x0, x1, ya, yb, z0, z1):
        y0, y1 = (ya, yb) if is_right else (-yb, -ya)
        return make_box(x0, x1, y0, y1, z0, z1)

    # 1. Backplate (full width across clamp, Z = 0..2.6)
    backplate = y_box(-outer_x, outer_x, y_arm_tip, y_spine_out, 0.0, z_floor)

    # 2. Outer end spine (full height Z = 0..13.2, with USB-C pass-through at center)
    spine = y_box(-outer_x, outer_x, y_spine_in, y_spine_out, 0.0, z_lip_top)

    # 3. Left & Right side walls (Z = 0..13.2)
    wall_pos_x = y_box(inner_x, outer_x, y_arm_tip, y_spine_out, 0.0, z_lip_top)
    wall_neg_x = y_box(-outer_x, -inner_x, y_arm_tip, y_spine_out, 0.0, z_lip_top)

    # 4. Front retention lips (3.2 mm bezel overlap on end + side arms, leaving screen clear)
    end_lip = y_box(-outer_x, outer_x, y_spine_in - 3.0, y_spine_out, z_pocket_top, z_lip_top)
    arm_lip_pos = y_box(inner_x - 3.2, outer_x, y_arm_tip, y_spine_out, z_pocket_top, z_lip_top)
    arm_lip_neg = y_box(-outer_x, -inner_x + 3.2, y_arm_tip, y_spine_out, z_pocket_top, z_lip_top)

    # 5. Two rear tension-band anchor hooks (at X = +/- 40 mm, Y extending slightly inward)
    hooks = []
    for hx in (-40.0, 40.0):
        stem = y_box(hx - 4.0, hx + 4.0, y_arm_tip - 5.5, y_arm_tip + 1.0, 0.0, 2.8)
        lip = y_box(hx - 5.5, hx + 5.5, y_arm_tip - 6.5, y_arm_tip - 3.5, 0.0, 4.6)
        hooks.append(stem + lip)

    # 6. Combine clamp body + Joy-Con 2 rail, then ensure flat Z >= 0.0
    body = backplate + spine + wall_pos_x + wall_neg_x + end_lip + arm_lip_pos + arm_lip_neg + hooks[0] + hooks[1]
    combined = (body + rail_mf) ^ make_box(-100.0, 100.0, -130.0, 130.0, 0.0, 20.0)
    return combined


def main():
    jc2_tm = load_3mf_object(JC2_3MF, "3D/Objects/object_1.model")

    # Right rail (+Y)
    jc2_r = jc2_tm.copy()
    jc2_r.apply_transform(trimesh.transformations.rotation_matrix(np.radians(-15.0), [0, 1, 0]))
    rail_r_mf = to_manifold(jc2_r) ^ make_box(16.5, 35.0, -10.0, 10.0, -65.0, 70.0)
    rail_r_tm = from_manifold(rail_r_mf)
    z_mid_r = (rail_r_tm.bounds[0][2] + rail_r_tm.bounds[1][2]) / 2.0
    vr = rail_r_tm.vertices.copy()
    rail_r_tm.vertices = np.column_stack([-(vr[:, 2] - z_mid_r), vr[:, 0] - 16.5 + 105.0, -vr[:, 1]])
    rail_r_final = to_manifold(rail_r_tm).translate([0.0, 0.0, 7.0])

    # Left rail (-Y)
    jc2_l = jc2_tm.copy()
    jc2_l.apply_transform(trimesh.transformations.rotation_matrix(np.radians(15.0), [0, 1, 0]))
    rail_l_mf = to_manifold(jc2_l) ^ make_box(-35.0, -16.5, -10.0, 10.0, -65.0, 70.0)
    rail_l_tm = from_manifold(rail_l_mf)
    z_mid_l = (rail_l_tm.bounds[0][2] + rail_l_tm.bounds[1][2]) / 2.0
    vl = rail_l_tm.vertices.copy()
    rail_l_tm.vertices = np.column_stack([-(vl[:, 2] - z_mid_l), vl[:, 0] + 16.5 - 105.0, -vl[:, 1]])
    rail_l_final = to_manifold(rail_l_tm).translate([0.0, 0.0, 7.0])

    clamp_r = build_end_clamp(rail_r_final, is_right=True).translate([0.0, -72.0, 0.0])
    clamp_l = build_end_clamp(rail_l_final, is_right=False).translate([0.0, 72.0, 0.0])

    pair = clamp_r + clamp_l
    out_tm = from_manifold(pair)

    out_3mf = os.path.join(OUT_DIR, "Y700_Switch2_Quick_Clamp_Pair.3mf")
    out_stl = os.path.join(OUT_DIR, "Y700_Switch2_Quick_Clamp_Pair.stl")
    out_tm.export(out_3mf)
    out_tm.export(out_stl)
    print(
        f"Exported {out_3mf} & {out_stl}: "
        f"watertight={out_tm.is_watertight}, is_volume={out_tm.is_volume}, "
        f"faces={len(out_tm.faces)}, bounds={out_tm.bounds.tolist()}"
    )


if __name__ == "__main__":
    main()
