"""Convert Y700_2025(Gen3)_Joycon_holder.3mf (Switch 1 rails) to Switch 2 Joy-Con 2
snap-locking rails using exact CSG boolean operations from joy-con2_grip.3mf.
Includes a clean finger-access cutout for Power & Volume buttons and repairs all
non-manifold edges / face winding before export.
"""

import os
import zipfile
import xml.etree.ElementTree as ET
import numpy as np
import trimesh
import manifold3d as m3d

Y700_3MF = r"C:\Users\majsz\.gemini\antigravity\brain\b9ff5005-3f23-4f12-b350-57a55aa3e919\.user_uploaded\media_1794670581848_62b579db.3mf"
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


def main():
    # 1. Load Y700 holder and trim off Switch 1 rails beyond |Y| = 106.5 mm
    y700_tm = load_3mf_object(Y700_3MF, "3D/Objects/object_2.model")
    y700_mf = to_manifold(y700_tm)
    trim_box = make_box(-100.0, 100.0, -106.5, 106.5, -25.0, 25.0)
    y700_trimmed = y700_mf ^ trim_box

    # Widen the Power + Volume button cutout on the +X wall (Y in [30, 88], Z in [-1.5, 10.0])
    # so buttons are easy to press with a fingertip
    button_cutout = make_box(63.0, 72.0, 30.0, 88.0, -1.5, 10.0)
    y700_trimmed = y700_trimmed - button_cutout

    # 2. Load Joy-Con 2 grip body
    jc2_tm = load_3mf_object(JC2_3MF, "3D/Objects/object_1.model")

    # Right rail: rotate -15 deg around Y so +X is flat outward rail normal
    jc2_r = jc2_tm.copy()
    jc2_r.apply_transform(trimesh.transformations.rotation_matrix(np.radians(-15.0), [0, 1, 0]))
    rail_r_mf = to_manifold(jc2_r) ^ make_box(16.5, 35.0, -10.0, 10.0, -65.0, 70.0)
    rail_r_tm = from_manifold(rail_r_mf)
    z_mid_r = (rail_r_tm.bounds[0][2] + rail_r_tm.bounds[1][2]) / 2.0
    # Map (x, y, z) -> (-(z - z_mid_r), x - 16.5 + 105.0, -y)
    # Puts + / - and 4 LED holes on the FRONT (+Z screen side), + on Right, - on Left [det = +1]
    vr = rail_r_tm.vertices.copy()
    rail_r_tm.vertices = np.column_stack([-(vr[:, 2] - z_mid_r), vr[:, 0] - 16.5 + 105.0, -vr[:, 1]])
    rail_r_final = to_manifold(rail_r_tm)

    # Left rail: rotate +15 deg around Y so -X is flat outward rail normal
    jc2_l = jc2_tm.copy()
    jc2_l.apply_transform(trimesh.transformations.rotation_matrix(np.radians(15.0), [0, 1, 0]))
    rail_l_mf = to_manifold(jc2_l) ^ make_box(-35.0, -16.5, -10.0, 10.0, -65.0, 70.0)
    rail_l_tm = from_manifold(rail_l_mf)
    z_mid_l = (rail_l_tm.bounds[0][2] + rail_l_tm.bounds[1][2]) / 2.0
    # Map (x, y, z) -> (-(z - z_mid_l), x + 16.5 - 105.0, -y)
    vl = rail_l_tm.vertices.copy()
    rail_l_tm.vertices = np.column_stack([-(vl[:, 2] - z_mid_l), vl[:, 0] + 16.5 - 105.0, -vl[:, 1]])
    rail_l_final = to_manifold(rail_l_tm)

    # 3. Boolean union all three parts, then plane-cut Z >= -6.95 and shift to Z = 0.0
    # so the entire backplate and both rails sit 100% flush on the build plate
    combined = (y700_trimmed + rail_r_final + rail_l_final) ^ make_box(-100.0, 100.0, -130.0, 130.0, -6.95, 25.0)
    combined = combined.translate([0.0, 0.0, 6.95])
    out_tm = from_manifold(combined)

    # Export as indexed 3MF (zero STL float32 vertex-pinch) and binary STL
    out_3mf = os.path.join(OUT_DIR, "Y700_2025_Switch2_JoyCon_Holder.3mf")
    out_tm.export(out_3mf)
    out_stl = os.path.join(OUT_DIR, "Y700_2025_Switch2_JoyCon_Holder.stl")
    out_tm.export(out_stl)
    print(f"Exported {out_3mf} & {out_stl}: watertight={out_tm.is_watertight}, is_volume={out_tm.is_volume}, faces={len(out_tm.faces)}")

    # 4. Export two snap-locking bars lying flat for printing
    bar_tm = load_3mf_object(JC2_3MF, "3D/Objects/object_7.model")
    bar1 = bar_tm.copy()
    bar1.apply_translation([-12.0, 0.0, -bar1.bounds[0][2]])
    bar2 = bar_tm.copy()
    bar2.apply_translation([12.0, 0.0, -bar2.bounds[0][2]])
    bars = trimesh.util.concatenate([bar1, bar2])
    bars_stl = os.path.join(OUT_DIR, "JoyCon2_Locking_Bar_x2.stl")
    bars.export(bars_stl)
    print(f"Exported {bars_stl}: watertight={bars.is_watertight}")


if __name__ == "__main__":
    main()
