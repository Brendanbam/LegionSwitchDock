"""Generate a support-free, watertight Switch-style Portrait Dock Bridge STL
paired with the UGREEN 9-in-1 Foldable Dock (B0CR6JND4M, 160x115x40 mm)
for the Lenovo Legion Y700 (2025 / Gen 3).
"""

import os
import struct


def add_box(tris: list, x0: float, x1: float, y0: float, y1: float, z0: float, z1: float) -> None:
    if x1 <= x0 or y1 <= y0 or z1 <= z0:
        return
    p000, p100, p110, p010 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    p001, p101, p111, p011 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    tris.extend([
        (p000, p010, p110), (p000, p110, p100),
        (p001, p101, p111), (p001, p111, p011),
        (p000, p100, p101), (p000, p101, p001),
        (p010, p011, p111), (p010, p111, p110),
        (p000, p001, p011), (p000, p011, p010),
        (p100, p110, p111), (p100, p111, p101),
    ])


def normal_of(tri):
    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = tri
    ux, uy, uz = bx - ax, by - ay, bz - az
    vx, vy, vz = cx - ax, cy - ay, cz - az
    nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
    length = (nx * nx + ny * ny + nz * nz) ** 0.5 or 1.0
    return (nx / length, ny / length, nz / length)


def write_binary_stl(path: str, tris: list) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    header = b"UGREEN B0CR6JND4M + Legion Y700 Switch Dock" + b"\0" * (80 - 43)
    with open(path, "wb") as f:
        f.write(header)
        f.write(struct.pack("<I", len(tris)))
        for tri in tris:
            f.write(struct.pack("<3f", *normal_of(tri)))
            for v in tri:
                f.write(struct.pack("<3f", *v))
            f.write(struct.pack("<H", 0))


def build_ugreen_dock() -> list:
    tris: list = []

    # Tablet portrait slot (Legion Y700 2025: 129.5 x 7.8 mm)
    slot_w = 131.0
    slot_d = 9.2
    side_wall = 12.5        # Brings outer width to 156 mm (matches 160 mm UGREEN dock)
    front_wall = 6.0
    back_wall = 8.0

    base_w = slot_w + 2 * side_wall     # 156.0 mm
    x0, x1 = -base_w / 2.0, base_w / 2.0
    sx0, sx1 = -slot_w / 2.0, slot_w / 2.0

    # Y layout: front lip at y0=-22, tablet slot y=[-16, -6.8], rear UGREEN alignment shelf up to y=+48
    sy0, sy1 = -16.0, -16.0 + slot_d    # -16.0 .. -6.8
    y0 = sy0 - front_wall               # -22.0
    y_cradle_back = sy1 + back_wall     # +1.2
    y_rear_shelf = 48.0                 # Rear shelf with side stops for UGREEN B0CR6JND4M

    pedestal_h = 32.0                   # Extra height for UGREEN's 90-deg USB-C plug bend
    back_h = pedestal_h + 64.0          # 96.0 mm
    front_h = pedestal_h + 26.0         # 58.0 mm

    # Centered USB-C plug pocket & rear cable tunnel (18 mm wide x 18 mm tall)
    usb_x0, usb_x1 = -9.0, 9.0
    ch_z_top = 22.0

    # 1. Base plate (4 mm floor)
    add_box(tris, x0, x1, y0, y_rear_shelf, 0.0, 4.0)

    # 2. Rear UGREEN alignment side rails (keeps the 160 mm UGREEN dock tucked flush behind)
    add_box(tris, x0, x0 + 6.0, y_cradle_back, y_rear_shelf, 4.0, 12.0)
    add_box(tris, x1 - 6.0, x1, y_cradle_back, y_rear_shelf, 4.0, 12.0)

    # 3. Pedestal under tablet (z = 4 .. 32), split around centered USB-C tunnel
    add_box(tris, x0, usb_x0, y0, y_cradle_back, 4.0, pedestal_h)
    add_box(tris, usb_x1, x1, y0, y_cradle_back, 4.0, pedestal_h)
    add_box(tris, usb_x0, usb_x1, y0, sy0, 4.0, pedestal_h)
    add_box(tris, usb_x0, usb_x1, sy1, y_cradle_back, ch_z_top, pedestal_h)

    # 4. Left & Right cradle walls
    add_box(tris, x0, sx0, y0, y_cradle_back, pedestal_h, back_h)
    add_box(tris, sx1, x1, y0, y_cradle_back, pedestal_h, back_h)

    # 5. Back wall with cooling cutout (x in [-34, +34], z in [pedestal_h+14, back_h-10])
    add_box(tris, sx0, sx1, sy1, y_cradle_back, pedestal_h, pedestal_h + 14.0)
    add_box(tris, sx0, -34.0, sy1, y_cradle_back, pedestal_h + 14.0, back_h)
    add_box(tris, 34.0, sx1, sy1, y_cradle_back, pedestal_h + 14.0, back_h)
    add_box(tris, -34.0, 34.0, sy1, y_cradle_back, back_h - 10.0, back_h)

    # 6. Front Switch-style lip with center finger notch
    add_box(tris, sx0, -24.0, y0, sy0, pedestal_h, front_h)
    add_box(tris, 24.0, sx1, y0, sy0, pedestal_h, front_h)
    add_box(tris, -24.0, 24.0, y0, sy0, pedestal_h, pedestal_h + 10.0)

    return tris


if __name__ == "__main__":
    out_path = os.path.join(os.path.dirname(__file__), "UGREEN_B0CR6JND4M_Y700_Dock.stl")
    tris = build_ugreen_dock()
    write_binary_stl(out_path, tris)
    print(f"Wrote {len(tris)} triangles to {out_path}")
