"""Convert Y700_2025(Gen3)_Joycon_holder.3mf (Switch 1 rails) to Switch 2 Joy-Con 2
snap-locking rails using exact CSG boolean operations from joy-con2_grip.3mf.

V2 Complete Audit Upgrades:
1. Tablet Fit & Corner Radius Fix:
   - Replaces V1's R=10.0mm inner corner fillet (which pinched the Y700's R~6mm corners at 45 deg)
     with an R=5.5mm rounded-rectangle pocket (210.20 x 131.10 mm, Z = 2.60 .. 10.90 mm) so the
     Y700 + tempered glass screen protector drops all the way down to the floor without binding.
   - Preserves the full outer corner bumpers (|Y| up to 109.8 mm at |X| > 58 mm) for thick corners.
   - Adds smooth 45-degree beveled snap-retention lips along the flexible -X and +X long walls
     (W = 128.90 mm at Z = 11.45 .. 12.45 mm vs 129.46 mm tablet width) using exact convex hulls
     so the tablet clicks in securely and cannot fall out while playing, with zero flat overhangs.
2. Joy-Con 2 Flush Seating & Locking-Bar Tunnel Fix:
   - Slices joy-con2_grip.3mf at x = 15.15 mm and maps it to Y = +/-106.15 mm so the Y700 case wall
     (|Y| <= 106.50 mm) never intrudes into the 4 recessed locking-bar pockets (x = 16.10 mm) or
     the bottom stop of the tunnel (x = 17.52 mm).
   - Deepens the locking-bar channel by +0.25 mm and uses a smooth 45-degree triangular roof prism
     above the tunnel (eliminating the Z = 7.42 mm internal bridge overhang so tree supports never
     enter the tunnel and bridge sag never pinches the locking bar).
   - Trims JoyCon2_Locking_Bar_x2.stl thickness by -0.15 mm (to 1.62 mm) for smooth sliding.
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


def rounded_rect_cs(half_x: float, half_y: float, radius: float, segments: int = 64) -> m3d.CrossSection:
    r = min(radius, half_x - 0.1, half_y - 0.1)
    pts = []
    corners = [
        (half_x - r, half_y - r, 0.0, np.pi / 2.0),
        (-half_x + r, half_y - r, np.pi / 2.0, np.pi),
        (-half_x + r, -half_y + r, np.pi, 1.5 * np.pi),
        (half_x - r, -half_y + r, 1.5 * np.pi, 2.0 * np.pi),
    ]
    seg_per_corner = max(8, segments // 4)
    for cx, cy, a0, a1 in corners:
        for a in np.linspace(a0, a1, seg_per_corner, endpoint=True):
            pts.append([cx + r * np.cos(a), cy + r * np.sin(a)])
    return m3d.CrossSection([np.array(pts, dtype=np.float32)])


def build_snap_lip_and_pocket() -> tuple[m3d.Manifold, m3d.Manifold]:
    """Return (snap_lips_to_add_after_pocket, pocket_cutter_to_subtract)."""
    # 1. Full-depth R=5.5mm rounded-rectangle pocket cutter (Z = 2.60 .. 14.00 mm):
    #    half_x = 65.55 (W = 131.10 mm), half_y = 105.10 (L = 210.20 mm), R = 5.5 mm
    #    -> 45-deg corner is (63.94, 103.49) vs Y700's (62.97, 102.51): +0.97 mm/corner clearance
    #       all the way from the Z=2.60 floor through the top opening!
    cs_main = rounded_rect_cs(65.55, 105.10, 5.5)
    pocket_cutter = m3d.Manifold.extrude(cs_main, 11.40).translate([0.0, 0.0, 2.60])

    # 2. Smooth 45-degree beveled snap-retention lips on the flexible -X and +X long walls:
    #    - Bottom 45-deg print chamfer: Z = 10.60 -> 11.65 mm (1.05 mm rise for 1.05 mm inward lip = 45 deg, zero support!)
    #    - Waist retention face: Z = 11.65 .. 12.35 mm at X = +/-64.50 mm (W = 129.00 mm vs 129.46 mm tablet = 0.46 mm snap lock)
    #    - Top entry ramp: Z = 12.35 -> 13.15 mm flares back out to X = +/-65.55 mm so tablet clicks in smoothly
    lip_neg_x = (
        make_box(-66.60, -65.55, -70.0, 70.0, 10.60, 13.15)
        + make_box(-66.60, -64.50, -68.0, 68.0, 11.65, 12.35)
    ).hull()
    lip_pos_x = (
        make_box(65.55, 66.60, -70.0, 20.0, 10.60, 13.15)
        + make_box(64.50, 66.60, -68.0, 18.0, 11.65, 12.35)
    ).hull()
    snap_lips = lip_neg_x + lip_pos_x
    return snap_lips, pocket_cutter


def prepare_joycon2_rail(jc2_tm: trimesh.Trimesh, is_right: bool) -> m3d.Manifold:
    """Extract Joy-Con 2 rail with full unobstructed locking-bar tunnel and smooth 45-deg roof."""
    angle = -15.0 if is_right else 15.0
    jc2 = jc2_tm.copy()
    jc2.apply_transform(trimesh.transformations.rotation_matrix(np.radians(angle), [0, 1, 0]))
    mf = to_manifold(jc2)

    # Slice at |x| = 15.15 mm (behind the x = 16.10 mm locking-bar pockets!)
    if is_right:
        rail_mf = mf ^ make_box(15.15, 35.0, -10.0, 10.0, -65.0, 70.0)
    else:
        rail_mf = mf ^ make_box(-35.0, -15.15, -10.0, 10.0, -65.0, 70.0)

    # Extract exact 2D locking-bar tunnel cavity at midplane y = 0 in rotated rail coords
    rail_tm = from_manifold(rail_mf)
    v_rot = np.column_stack([rail_tm.vertices[:, 0], rail_tm.vertices[:, 2], -rail_tm.vertices[:, 1]])
    m_rot = m3d.Mesh(vert_properties=v_rot.astype(np.float32), tri_verts=rail_tm.faces.astype(np.uint32))
    m_rot.merge()
    mf_rot = m3d.Manifold(m_rot)

    if is_right:
        box_2d = m3d.CrossSection([np.array([[15.95, -48.6], [23.80, -48.6], [23.80, 61.0], [15.95, 61.0]])])
        raw_cav = box_2d - mf_rot.slice(0.0)
        cav_2d = raw_cav + raw_cav.translate([-0.25, -0.35])
        x0, x1 = 15.70, 22.10
    else:
        box_2d = m3d.CrossSection([np.array([[-23.80, -48.6], [-15.95, -48.6], [-15.95, 61.0], [-23.80, 61.0]])])
        raw_cav = box_2d - mf_rot.slice(0.0)
        cav_2d = raw_cav + raw_cav.translate([0.25, -0.35])
        x0, x1 = -22.10, -15.70

    # Smooth 45-degree triangular roof prism over the locking-bar tunnel (zero stair-steps!):
    # Rectangular slot from Z_rot = -1.10 to +1.05 (cuts past the +0.98 flat ceiling!),
    # then 45-deg roof peaking at x_mid, Z_rot = +1.05 + roof_ht
    x_mid = 0.5 * (x0 + x1)
    roof_ht = 0.5 * (x1 - x0)
    roof_profile_2d = m3d.CrossSection([np.array([
        [x0, -1.10],
        [x1, -1.10],
        [x1,  1.05],
        [x_mid, 1.05 + roof_ht],
        [x0,  1.05],
    ], dtype=np.float32)])
    # Extrude along Z (length 112 mm), then map (x, z_prof, y_len) -> (x, -y_len, z_prof) [det = +1]
    prism_raw = m3d.Manifold.extrude(roof_profile_2d, 112.0).translate([0.0, 0.0, -61.5])
    pm = prism_raw.to_mesh()
    pv = pm.vert_properties[:, :3]
    pv_rot = np.column_stack([pv[:, 0], -pv[:, 2], pv[:, 1]])
    pm_rot = m3d.Mesh(vert_properties=pv_rot.astype(np.float32), tri_verts=pm.tri_verts.astype(np.uint32))
    pm_rot.merge()
    roof_prism = m3d.Manifold(pm_rot)

    cav_extruded = m3d.Manifold.extrude(cav_2d, 6.0).translate([0.0, 0.0, -1.10])
    tunnel_cutter_rot = cav_extruded ^ roof_prism

    mf_rot_clean = mf_rot - tunnel_cutter_rot
    clean_mesh = mf_rot_clean.to_mesh()
    v_back = np.column_stack([clean_mesh.vert_properties[:, 0], -clean_mesh.vert_properties[:, 2], clean_mesh.vert_properties[:, 1]])
    rail_clean_tm = trimesh.Trimesh(vertices=v_back, faces=clean_mesh.tri_verts, process=False)

    z_mid = (rail_clean_tm.bounds[0][2] + rail_clean_tm.bounds[1][2]) / 2.0
    v = rail_clean_tm.vertices.copy()
    if is_right:
        rail_clean_tm.vertices = np.column_stack([-(v[:, 2] - z_mid), v[:, 0] - 15.15 + 106.15, -v[:, 1]])
    else:
        rail_clean_tm.vertices = np.column_stack([-(v[:, 2] - z_mid), v[:, 0] + 15.15 - 106.15, -v[:, 1]])

    return to_manifold(rail_clean_tm).translate([0.0, 0.0, 6.40])


def main():
    # 1. Load Y700 holder; keep full outer corners (|Y| <= 109.8 mm at |X| > 58 mm),
    # and trim Switch 1 rails at |Y| <= 106.5 mm inside |X| <= 58 mm.
    y700_tm = load_3mf_object(Y700_3MF, "3D/Objects/object_2.model")
    y700_mf = to_manifold(y700_tm).translate([0.0, 0.0, 6.40])

    trim_keep = (
        make_box(-100.0, 100.0, -109.8, 109.8, 0.0, 25.0)
        - make_box(-58.0, 58.0, 106.5, 115.0, -1.0, 25.0)
        - make_box(-58.0, 58.0, -115.0, -106.5, -1.0, 25.0)
    )
    y700_trimmed = y700_mf ^ trim_keep

    # Build flat 2.60 mm backplate using the un-chamfered Z=3.60 outer boundary combined with
    # the Z=0.05 backplate vent slots & camera hole (eliminates the Z=2.60 outer overhang step!)
    outer_ring_2d = y700_trimmed.slice(3.60) - rounded_rect_cs(63.0, 102.5, 6.0)
    backplate_2d = y700_trimmed.slice(0.05) + outer_ring_2d
    backplate_solid = m3d.Manifold.extrude(backplate_2d, 2.60)

    upper_walls = (
        (y700_trimmed ^ make_box(-100.0, 100.0, -110.0, 110.0, 2.55, 25.0))
        - make_box(-61.0, 61.0, -101.0, 101.0, 2.50, 25.0)
    )
    snap_lips, pocket_cutter = build_snap_lip_and_pocket()
    y700_clean = backplate_solid + upper_walls

    # Widen the Power + Volume button cutout on the +X wall (Y in [30, 88], Z in [2.6, 16.0])
    button_cutout = make_box(63.0, 72.0, 30.0, 88.0, 2.6, 16.0)
    y700_clean = y700_clean - button_cutout

    # 2. Load Joy-Con 2 grip body and prepare both rails with full locking-bar tunnels
    jc2_tm = load_3mf_object(JC2_3MF, "3D/Objects/object_1.model")
    rail_r_final = prepare_joycon2_rail(jc2_tm, is_right=True)
    rail_l_final = prepare_joycon2_rail(jc2_tm, is_right=False)

    # 3. Union case + rails, subtract full-depth R=5.5mm tablet pocket, add smooth 45-deg snap lips, and cut Z >= 0.0
    combined = (((y700_clean + rail_r_final + rail_l_final) - pocket_cutter) + snap_lips) ^ make_box(-100.0, 100.0, -130.0, 130.0, 0.0, 25.0)
    out_tm = from_manifold(combined)

    for base_name in ("Y700_2025_Switch2_JoyCon_Holder_V2", "Y700_2025_Switch2_JoyCon_Holder"):
        out_3mf = os.path.join(OUT_DIR, f"{base_name}.3mf")
        out_tm.export(out_3mf)
        out_stl = os.path.join(OUT_DIR, f"{base_name}.stl")
        out_tm.export(out_stl)
        print(f"Exported {out_3mf} & {out_stl}: watertight={out_tm.is_watertight}, is_volume={out_tm.is_volume}, faces={len(out_tm.faces)}")

    # 4. Export two snap-locking bars lying flat, scaled to 1.62 mm thickness (-0.15 mm Z relief)
    bar_tm = load_3mf_object(JC2_3MF, "3D/Objects/object_7.model")
    bar_tm.vertices[:, 2] *= (1.62 / 1.77)
    bar1 = bar_tm.copy()
    bar1.apply_translation([-12.0, 0.0, -bar1.bounds[0][2]])
    bar2 = bar_tm.copy()
    bar2.apply_translation([12.0, 0.0, -bar2.bounds[0][2]])
    bars = trimesh.util.concatenate([bar1, bar2])
    bars_stl = os.path.join(OUT_DIR, "JoyCon2_Locking_Bar_x2.stl")
    bars.export(bars_stl)
    print(f"Exported {bars_stl}: watertight={bars.is_watertight}, Z_height={bars.bounds[1][2]:.2f}mm")


if __name__ == "__main__":
    main()
