"""Generate a watertight, support-free Switch-style Portrait Dock Bridge
paired with the UGREEN 9-in-1 Foldable Dock (B0CR6JND4M, 160x115x40 mm)
for the Lenovo Legion Y700 (2025 / Gen 3).
"""

import os
import numpy as np
import trimesh
import manifold3d as m3d


def add_box(boxes: list, x0: float, x1: float, y0: float, y1: float, z0: float, z1: float) -> None:
    if x1 <= x0 or y1 <= y0 or z1 <= z0:
        return
    size = [x1 - x0, y1 - y0, z1 - z0]
    center = [(x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0]
    boxes.append(m3d.Manifold.cube(size, center=True).translate(center))


def build_ugreen_dock() -> trimesh.Trimesh:
    boxes: list = []

    # Tablet portrait slot (Legion Y700 2025: 129.5 x 7.8 mm)
    slot_w = 131.0
    slot_d = 9.2
    side_wall = 12.5        # 156 mm outer width (matches 160 mm UGREEN dock)
    front_wall = 6.0
    back_wall = 8.0

    base_w = slot_w + 2 * side_wall     # 156.0 mm
    x0, x1 = -base_w / 2.0, base_w / 2.0
    sx0, sx1 = -slot_w / 2.0, slot_w / 2.0

    sy0, sy1 = -16.0, -16.0 + slot_d    # -16.0 .. -6.8
    y0 = sy0 - front_wall               # -22.0
    y_cradle_back = sy1 + back_wall     # +1.2
    y_rear_shelf = 48.0                 # Rear shelf for UGREEN B0CR6JND4M

    pedestal_h = 32.0                   # Clearance for UGREEN 90-deg USB-C plug
    back_h = pedestal_h + 64.0          # 96.0 mm
    front_h = pedestal_h + 26.0         # 58.0 mm

    usb_x0, usb_x1 = -9.0, 9.0
    ch_z_top = 22.0

    # 1. Base plate (4 mm floor)
    add_box(boxes, x0, x1, y0, y_rear_shelf, 0.0, 4.0)

    # 2. Rear UGREEN alignment side rails
    add_box(boxes, x0, x0 + 6.0, y_cradle_back, y_rear_shelf, 4.0, 12.0)
    add_box(boxes, x1 - 6.0, x1, y_cradle_back, y_rear_shelf, 4.0, 12.0)

    # 3. Pedestal under tablet (z = 4 .. 32), split around centered USB-C tunnel
    add_box(boxes, x0, usb_x0, y0, y_cradle_back, 4.0, pedestal_h)
    add_box(boxes, usb_x1, x1, y0, y_cradle_back, 4.0, pedestal_h)
    add_box(boxes, usb_x0, usb_x1, y0, sy0, 4.0, pedestal_h)
    add_box(boxes, usb_x0, usb_x1, sy1, y_cradle_back, ch_z_top, pedestal_h)

    # 4. Left & Right cradle walls
    add_box(boxes, x0, sx0, y0, y_cradle_back, pedestal_h, back_h)
    add_box(boxes, sx1, x1, y0, y_cradle_back, pedestal_h, back_h)

    # 5. Back wall with cooling cutout
    add_box(boxes, sx0, sx1, sy1, y_cradle_back, pedestal_h, pedestal_h + 14.0)
    add_box(boxes, sx0, -34.0, sy1, y_cradle_back, pedestal_h + 14.0, back_h)
    add_box(boxes, 34.0, sx1, sy1, y_cradle_back, pedestal_h + 14.0, back_h)
    add_box(boxes, -34.0, 34.0, sy1, y_cradle_back, back_h - 10.0, back_h)

    # 6. Front Switch-style lip with center finger notch
    add_box(boxes, sx0, -24.0, y0, sy0, pedestal_h, front_h)
    add_box(boxes, 24.0, sx1, y0, sy0, pedestal_h, front_h)
    add_box(boxes, -24.0, 24.0, y0, sy0, pedestal_h, pedestal_h + 10.0)

    combined = m3d.Manifold.compose(boxes)
    mesh = combined.to_mesh()
    return trimesh.Trimesh(vertices=mesh.vert_properties[:, :3], faces=mesh.tri_verts, process=False)


if __name__ == "__main__":
    out_dir = os.path.dirname(__file__)
    tm = build_ugreen_dock()
    out_3mf = os.path.join(out_dir, "UGREEN_B0CR6JND4M_Y700_Dock.3mf")
    out_stl = os.path.join(out_dir, "UGREEN_B0CR6JND4M_Y700_Dock.stl")
    tm.export(out_3mf)
    tm.export(out_stl)
    print(f"Exported {out_3mf} & {out_stl}: watertight={tm.is_watertight}, is_volume={tm.is_volume}, bounds={tm.bounds.tolist()}")
