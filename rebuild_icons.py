#!/usr/bin/env python3
"""
Rebuild FoxControl icon structure to match ChatAI pattern.

Structure:
- mipmap/{density}/ic_launcher.png, ic_launcher_round.png (legacy, 64x64)
- drawable/ic_launcher_custom.png (108x108 foreground)
- drawable/ic_launcher_foreground.xml (bitmap wrapper)
- values/ic_launcher_background.xml (background color)
- mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml (adaptive)
- mipmap-{density}/ic_launcher.xml, ic_launcher_round.xml (adaptive per-density)
"""

import os
import numpy as np
from PIL import Image, ImageDraw

BASE = "/home/andrew/Documents/foxControl/app/src/main/res"
SOURCE = "/home/andrew/.hermes/profiles/coder/cache/images/img_15603598f622.jpg"

DENSITIES = ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]
LEGACY_SIZE = 64  # 64x64 for base mipmap/ (legacy)
FOREGROUND_SIZE = 108


def remove_white_background(img, threshold=230):
    if img.mode != "RGBA":
        img = img.convert("RGBA")
    arr = np.array(img)
    r, g, b, a = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2], arr[:, :, 3]
    mask = (r > threshold) & (g > threshold) & (b > threshold)
    arr[mask, 3] = 0
    return Image.fromarray(arr)


def create_fox_icon(size):
    """Create a fox icon with transparent background."""
    src = Image.open(SOURCE)
    src = src.convert("RGBA")
    src = remove_white_background(src, threshold=230)
    resized = src.resize((size, size), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    if resized.width < size or resized.height < size:
        x = (size - resized.width) // 2
        y = (size - resized.height) // 2
        canvas.paste(resized, (x, y), resized)
    else:
        canvas = resized
    return canvas


def create_round_icon(size):
    """Create circular version."""
    square = create_fox_icon(size)
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse([0, 0, size - 1, size - 1], fill=255)
    result = square.copy()
    result.putalpha(mask)
    return result


def create_foreground():
    """Create 108x108 foreground for adaptive icon."""
    src = Image.open(SOURCE)
    src = src.convert("RGBA")
    src = remove_white_background(src, threshold=230)

    canvas = Image.new("RGBA", (FOREGROUND_SIZE, FOREGROUND_SIZE), (0, 0, 0, 0))

    margin = 16
    available = FOREGROUND_SIZE - 2 * margin  # 76x76
    scale = min(available / src.width, available / src.height)
    new_size = (int(src.width * scale), int(src.height * scale))
    resampled = src.resize(new_size, Image.Resampling.LANCZOS)

    x = (FOREGROUND_SIZE - new_size[0]) // 2
    y = (FOREGROUND_SIZE - new_size[1]) // 2
    canvas.paste(resampled, (x, y), resampled)
    return canvas


def adaptive_xml(background_ref, foreground_ref):
    return f"""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="{background_ref}"/>
    <foreground android:drawable="{foreground_ref}"/>
</adaptive-icon>
"""


def main():
    print("=== Generating legacy PNG icons (base mipmap/) ===")
    for density in DENSITIES:
        mipmap_dir = os.path.join(BASE, "mipmap", density)
        os.makedirs(mipmap_dir, exist_ok=True)

        launcher = create_fox_icon(LEGACY_SIZE)
        launcher.save(os.path.join(mipmap_dir, "ic_launcher.png"), "PNG")
        print(f"  mipmap/{density}/ic_launcher.png: {LEGACY_SIZE}")

        round_icon = create_round_icon(LEGACY_SIZE)
        round_icon.save(os.path.join(mipmap_dir, "ic_launcher_round.png"), "PNG")
        print(f"  mipmap/{density}/ic_launcher_round.png: {LEGACY_SIZE}")

    print("\n=== Creating drawable foreground ===")
    foreground = create_foreground()
    fg_path = os.path.join(BASE, "drawable", "ic_launcher_custom.png")
    foreground.save(fg_path, "PNG")
    print(f"  drawable/ic_launcher_custom.png: {foreground.size}")

    fg_xml = """<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/ic_launcher_custom"
    android:gravity="center" />
"""
    with open(os.path.join(BASE, "drawable", "ic_launcher_foreground.xml"), "w") as f:
        f.write(fg_xml)
    print("  drawable/ic_launcher_foreground.xml created")

    print("\n=== Moving background color to values/ ===")
    bg_xml = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#E91E8C</color>
</resources>
"""
    with open(os.path.join(BASE, "values", "ic_launcher_background.xml"), "w") as f:
        f.write(bg_xml)
    print("  values/ic_launcher_background.xml created")

    print("\n=== Creating adaptive icon XML files ===")

    # mipmap-anydpi-v26
    anydpi_dir = os.path.join(BASE, "mipmap-anydpi-v26")
    os.makedirs(anydpi_dir, exist_ok=True)

    launcher_xml = adaptive_xml("@color/ic_launcher_background", "@drawable/ic_launcher_foreground")
    with open(os.path.join(anydpi_dir, "ic_launcher.xml"), "w") as f:
        f.write(launcher_xml)
    print("  mipmap-anydpi-v26/ic_launcher.xml")

    with open(os.path.join(anydpi_dir, "ic_launcher_round.xml"), "w") as f:
        f.write(launcher_xml)
    print("  mipmap-anydpi-v26/ic_launcher_round.xml")

    # mipmap-{density}/ic_launcher.xml and ic_launcher_round.xml
    for density in DENSITIES:
        density_dir = os.path.join(BASE, f"mipmap-{density}")
        os.makedirs(density_dir, exist_ok=True)

        with open(os.path.join(density_dir, "ic_launcher.xml"), "w") as f:
            f.write(launcher_xml)
        print(f"  mipmap-{density}/ic_launcher.xml")

        with open(os.path.join(density_dir, "ic_launcher_round.xml"), "w") as f:
            f.write(launcher_xml)
        print(f"  mipmap-{density}/ic_launcher_round.xml")

    print("\n=== Removing old wrong files ===")
    for density in DENSITIES:
        density_dir = os.path.join(BASE, f"mipmap-{density}")
        old_files = ["ic_launcher_foreground.png", "ic_launcher.png", "ic_launcher_round.png"]
        for old_file in old_files:
            old_path = os.path.join(density_dir, old_file)
            if os.path.exists(old_path):
                os.remove(old_path)
                print(f"  Removed: {old_path}")

    # Remove old color/ directory
    old_color = os.path.join(BASE, "color")
    if os.path.exists(old_color):
        os.rmdir(old_color)
        print(f"  Removed: {old_color}/")

    print("\n✅ All done! Structure rebuilt.")


if __name__ == "__main__":
    main()
