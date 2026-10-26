#!/usr/bin/env python3
"""Deterministically generates Agris's 128x128 violet/emerald prism icon.png and CONFIG_SCHEMA.json."""
import json
import math
import os
import re
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def write_png(path: str, width: int, height: int, rgba_bytes: bytes) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)  # Filter type 0 (None)
        raw.extend(rgba_bytes[y * stride:(y + 1) * stride])

    def chunk(tag: bytes, data: bytes) -> bytes:
        crc = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    idat = zlib.compress(bytes(raw), 9)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def render_prism_icon(size: int = 128) -> bytes:
    buf = bytearray(size * size * 4)
    cx = (size - 1) * 0.5
    cy = (size - 1) * 0.52
    for y in range(size):
        ny = y / (size - 1.0)
        for x in range(size):
            nx = x / (size - 1.0)
            idx = (y * size + x) * 4

            # Rounded dark obsidian card background
            dx = abs(x - cx) - (size * 0.42)
            dy = abs(y - (size - 1) * 0.5) - (size * 0.42)
            corner = math.hypot(max(dx, 0.0), max(dy, 0.0))
            if corner > size * 0.08:
                buf[idx:idx + 4] = b"\x00\x00\x00\x00"
                continue

            r = int(13 + 12 * ny)
            g = int(17 + 16 * ny)
            b = int(30 + 28 * ny)

            # Outer triangle prism (Aetherium violet -> Agris emerald gradient)
            tx = (nx - 0.5) * 2.0
            ty = (ny - 0.54) * 2.0
            in_outer = ty < 0.55 and (ty - (-0.62)) > abs(tx) * 1.65
            in_inner = ty < 0.40 and (ty - (-0.36)) > abs(tx) * 1.72

            # Dispersion light beam exiting right side of prism
            beam_dist = abs((ny - 0.48) - (nx - 0.52) * 0.28)
            in_beam = nx > 0.50 and nx < 0.90 and beam_dist < 0.095

            if in_outer and not in_inner:
                t = max(0.0, min(1.0, (nx - 0.25) / 0.5))
                r = int(139 * (1.0 - t) + 16 * t)
                g = int(92 * (1.0 - t) + 185 * t)
                b = int(246 * (1.0 - t) + 129 * t)
            elif in_inner:
                r = int(28 + 45 * (1.0 - ny))
                g = int(36 + 75 * nx)
                b = int(68 + 90 * (1.0 - nx))
            elif in_beam:
                glow = max(0.0, 1.0 - beam_dist / 0.095)
                r = min(255, int(r + 80 * glow))
                g = min(255, int(g + 210 * glow))
                b = min(255, int(b + 195 * glow))

            # Subtle border highlight
            if abs(corner - size * 0.075) < 1.3:
                r, g, b = 109, 40, 217

            buf[idx] = max(0, min(255, r))
            buf[idx + 1] = max(0, min(255, g))
            buf[idx + 2] = max(0, min(255, b))
            buf[idx + 3] = 255
    return bytes(buf)


def generate_config_schema() -> None:
    cfg_java = os.path.join(ROOT, "common", "src", "main", "java", "com", "agris", "config", "AgrisConfig.java")
    text = open(cfg_java, encoding="utf-8").read()
    entries = []
    pattern = re.compile(
        r'ConfigValue\.(bool|intRange|string|enumerated)\(\s*"([^"]+)"\s*,\s*([^,\n]+)',
        re.M,
    )
    for m in pattern.finditer(text):
        kind, key, default_expr = m.group(1), m.group(2), m.group(3).strip()
        entries.append({
            "key": key,
            "group": key.split(".", 1)[0],
            "kind": kind,
            "default": default_expr.strip('"'),
        })
    schema = {
        "mod_id": "agris",
        "schema_version": 2,
        "option_count": len(entries),
        "options": entries,
    }
    out_path = os.path.join(ROOT, "CONFIG_SCHEMA.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(schema, f, indent=2)
        f.write("\n")


def main() -> None:
    icon_path = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "agris", "icon.png")
    write_png(icon_path, 128, 128, render_prism_icon(128))
    generate_config_schema()
    print(f"Generated {icon_path} and CONFIG_SCHEMA.json")


if __name__ == "__main__":
    main()
