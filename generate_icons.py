#!/usr/bin/env python3
"""Generate FoxControl launcher icons from the fox character image."""

import os
import numpy as np
from PIL import Image, ImageDraw

PROJECT = "/home/andrew/Documents/foxControl/app/src/main/res"
SOURCE_IMAGE = "/home/andrew/.hermes/profiles/coder/cache/images/img_a96523c227b2.jpg"

# Density buckets and their icon sizes
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

FOREGROUND_SIZE = 108


def remove_white_background(img, threshold=230):
    """Remove white/light background using numpy for speed."""
    if img.mode != "RGBA":
        img = img.convert("RGBA")

    arr = np.array(img)  # shape: (H, W, 4)

    r, g, b, a = arr[:, :, 0], arr[:, :, 1], arr[:, :, 2], arr[:, :, 3]

    # White/light pixels become transparent
    mask = (r > threshold) & (g > threshold) & (b > threshold)
    arr[mask, 3] = 0  # Set alpha to 0 for white pixels

    return Image.fromarray(arr)


def create_foreground():
    """Create foreground icon for adaptive icon (108x108 canvas)."""
    src = Image.open(SOURCE_IMAGE)
    src = src.convert("RGBA")

    # Remove white background
    src = remove_white_background(src, threshold=230)

    # Create transparent canvas
    canvas = Image.new("RGBA", (FOREGROUND_SIZE, FOREGROUND_SIZE), (0, 0, 0, 0))

    # Scale to fit with padding
    margin = 16
    available = FOREGROUND_SIZE - 2 * margin  # 76x76
    scale = min(available / src.width, available / src.height)

    new_size = (int(src.width * scale), int(src.height * scale))
    resampled = src.resize(new_size, Image.Resampling.LANCZOS)

    # Center on canvas
    x = (FOREGROUND_SIZE - new_size[0]) // 2
    y = (FOREGROUND_SIZE - new_size[1]) // 2
    canvas.paste(resampled, (x, y), resampled)

    return canvas


def create_square_launcher(size):
    """Create square launcher icon for a density bucket."""
    src = Image.open(SOURCE_IMAGE)
    src = src.convert("RGBA")

    # Remove white background
    src = remove_white_background(src, threshold=230)

    # Resize to target size
    resized = src.resize((size, size), Image.Resampling.LANCZOS)

    # Create transparent canvas
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    # If source is smaller than target, center it
    if resized.width < size or resized.height < size:
        x = (size - resized.width) // 2
        y = (size - resized.height) // 2
        canvas.paste(resized, (x, y), resized)
    else:
        canvas = resized

    return canvas


def create_round_launcher(size):
    """Create circular (rounded) version of the launcher icon."""
    # Create the square icon first
    square = create_square_launcher(size)

    # Create circular mask
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse([0, 0, size - 1, size - 1], fill=255)

    # Apply mask to the square icon
    result = square.copy()
    result.putalpha(mask)

    return result


def main():
    print(f"Source image: {SOURCE_IMAGE}")
    src = Image.open(SOURCE_IMAGE)
    print(f"Source size: {src.size}")

    for density, size in DENSITIES.items():
        mipmap_dir = os.path.join(PROJECT, f"mipmap-{density}")
        os.makedirs(mipmap_dir, exist_ok=True)

        # Square launcher icon
        launcher = create_square_launcher(size)
        launcher_path = os.path.join(mipmap_dir, "ic_launcher.png")
        launcher.save(launcher_path, "PNG")
        print(f"  {density}/ic_launcher.png: {launcher.size}")

        # Round launcher icon
        round_icon = create_round_launcher(size)
        round_path = os.path.join(mipmap_dir, "ic_launcher_round.png")
        round_icon.save(round_path, "PNG")
        print(f"  {density}/ic_launcher_round.png: {round_icon.size}")

        # Adaptive icon foreground
        foreground = create_foreground()
        fg_path = os.path.join(mipmap_dir, "ic_launcher_foreground.png")
        foreground.save(fg_path, "PNG")
        print(f"  {density}/ic_launcher_foreground.png: {foreground.size}")

    print("\nAll icons generated successfully!")


if __name__ == "__main__":
    main()
