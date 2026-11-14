"""Generate a watertight, support-free 3D-printable Switch-style dock STL
for the Lenovo Legion Y700 (2025 / Gen 3) in portrait orientation.

Short-edge USB-C 3.2 Gen 2 (DP Alt Mode) port sits centered at the bottom.
"""

import os
import struct


def add_box(tris: list, x0: float, x1: float, y0: float, y1: float, z0: float, z1: float) -> None:
    """Append 12 outward-facing triangles for an axis-aligned box [x0,x1] x [y0,y1] x [z0,z1]."""
    if x1 <= x0 or y1 <= y0 or z1 <= z0:
        return
    p000 = (x0, y0, z0)
    p100 = (x1, y0, z0)
    p110 = (x1, y1, z0)
    p010 = (x0, y1, z0)
    p001 = (x0, y0, z1)
    p101 = (x1, y0, z1)
    p111 = (x1, y1, z1)
    p011 = (x0, y1, z1)

    faces = [
        # Bottom (-Z)
        (p000, p010, p110), (p000, p110, p100),
        # Top (+Z)
        (p001, p101, p111), (p001, p111, p011),
        # Front (-Y)
        (p000, p100, p101), (p000, p101, p001),
        # Back (+Y)
        (p010, p011, p111), (p010, p111, p110),
        # Left (-X)
        (p000, p001, p011), (p000, p011, p010),
        # Right (+X)
        (p100, p110, p111), (p100, p111, p101),
    ]
    tris.extend(faces)


def normal_of(tri):
    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = tri
    ux, uy, uz = bx - ax, by - ay, bz - az
    vx, vy, vz = cx - ax, cy - ay, cz - az
    nx = uy * vz - uz * vy
    ny = uz * vx - ux * vz
    nz = ux * vy - uy * vx
    length = (nx * nx + ny * ny + nz * nz) ** 0.5 or 1.0
    return (nx / length, ny / length, nz / length)


def write_binary_stl(path: str, tris: list) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    header = b"Legion Y700 2025 Switch Dock (P2S)" + b"\0" * (80 - 34)
    with open(path, "wb") as f:
        f.write(header)
        f.write(struct.pack("<I", len(tris)))
        for tri in tris:
            nx, ny, nz = normal_of(tri)
            f.write(struct.pack("<3f", nx, ny, nz))
            for vx, vy, vz in tri:
                f.write(struct.pack("<3f", vx, vy, vz))
            f.write(struct.pack("<H", 0))


def build_dock() -> list:
    tris: list = []

    # --- Parameters (mm) ---
    # Legion Y700 2025: 129.5 mm wide x 7.8 mm thick (short edge)
    slot_w = 131.0       # +1.5 mm total width clearance
    slot_d = 9.2         # +1.4 mm thickness clearance (fits thin screen protector)
    wall = 7.5           # Side/back wall thickness
    front_wall = 5.5     # Front Switch-style lip thickness

    base_w = slot_w + 2 * wall          # 146.0 mm
    base_d = 88.0                       # Deep anti-tip footprint
    pedestal_h = 28.0                   # Clearance underneath for USB-C plug + cable bend
    back_h = pedestal_h + 62.0          # 90.0 mm total height
    front_h = pedestal_h + 26.0         # 54.0 mm low Switch-style front lip

    # Y coordinates: tablet slot centered around y = -10.0 so more base mass sits behind
    slot_y0 = -14.6
    slot_y1 = slot_y0 + slot_d          # -5.4
    base_y0 = slot_y0 - front_wall      # -20.1
    base_y1 = base_y0 + base_d          # +67.9

    x_left = -base_w / 2.0
    x_right = base_w / 2.0
    slot_x0 = -slot_w / 2.0
    slot_x1 = slot_w / 2.0

    # USB-C plug pocket & rear cable channel (centered at x = 0)
    usb_pocket_w = 16.0
    usb_x0 = -usb_pocket_w / 2.0
    usb_x1 = usb_pocket_w / 2.0
    cable_ch_h = 15.0

    # 1. Bottom base plate (solid 5mm floor, leaving USB cable exit open above z=5 at rear)
    add_box(tris, x_left, x_right, base_y0, base_y1, 0.0, 5.0)

    # 2. Pedestal blocks under tablet (z = 5 .. pedestal_h), split around centered USB-C pocket & rear cable tunnel
    # Left pedestal block
    add_box(tris, x_left, usb_x0, base_y0, base_y1, 5.0, pedestal_h)
    # Right pedestal block
    add_box(tris, usb_x1, x_right, base_y0, base_y1, 5.0, pedestal_h)
    # Front bridge in front of USB-C pocket
    add_box(tris, usb_x0, usb_x1, base_y0, slot_y0, 5.0, pedestal_h)
    # Rear bridge above cable channel (z = 5 + cable_ch_h .. pedestal_h)
    add_box(tris, usb_x0, usb_x1, slot_y1, base_y1, 5.0 + cable_ch_h, pedestal_h)

    # 3. Left & Right cradle side walls (z = pedestal_h .. back_h)
    add_box(tris, x_left, slot_x0, base_y0, slot_y1 + wall, pedestal_h, back_h)
    add_box(tris, slot_x1, x_right, base_y0, slot_y1 + wall, pedestal_h, back_h)

    # 4. Back cradle wall with center cooling cutout (cutout x = -32 .. +32, z = pedestal_h + 14 .. back_h - 10)
    back_y0 = slot_y1
    back_y1 = slot_y1 + wall
    # Lower back strip
    add_box(tris, slot_x0, slot_x1, back_y0, back_y1, pedestal_h, pedestal_h + 14.0)
    # Left/right back pillars beside cooling window
    add_box(tris, slot_x0, -32.0, back_y0, back_y1, pedestal_h + 14.0, back_h)
    add_box(tris, 32.0, slot_x1, back_y0, back_y1, pedestal_h + 14.0, back_h)
    # Top back bridge above cooling window
    add_box(tris, -32.0, 32.0, back_y0, back_y1, back_h - 10.0, back_h)

    # 5. Front Switch-style lip (left & right wings, low center notch)
    add_box(tris, slot_x0, -24.0, base_y0, slot_y0, pedestal_h, front_h)
    add_box(tris, 24.0, slot_x1, base_y0, slot_y0, pedestal_h, front_h)
    add_box(tris, -24.0, 24.0, base_y0, slot_y0, pedestal_h, pedestal_h + 10.0)

    return tris


if __name__ == "__main__":
    out_path = os.path.join(os.path.dirname(__file__), "LegionY700_Switch_Dock.stl")
    triangles = build_dock()
    write_binary_stl(out_path, triangles)
    print(f"Wrote {len(triangles)} triangles to {out_path}")
