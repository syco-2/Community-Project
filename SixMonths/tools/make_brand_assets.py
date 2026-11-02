# Turns the supplied logo (flat colours on pure white) into real alpha assets:
# the white background is removed, anti-aliased edges are reconstructed against the two
# brand colours, and every launcher/splash/in-app size is generated from that one mark.
#
# Run with: python tools/make_brand_assets.py
from PIL import Image, ImageDraw
import os

BLUE = (47, 128, 237)
ORANGE = (255, 107, 44)
BG_DARK = (11, 15, 20)
BG_RAISED = (18, 24, 32)

SOURCE = "app-icon-package/android/play-store-icon.png"


def load_mark() -> Image.Image:
    """The logo with the white background removed and edges rebuilt without a halo."""
    src = Image.open(SOURCE).convert("RGB")
    w, h = src.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    src_px = src.load()
    out_px = out.load()

    for y in range(h):
        for x in range(w):
            r, g, b = src_px[x, y]
            distance_from_white = max(255 - r, 255 - g, 255 - b)
            if distance_from_white <= 6:
                continue  # background
            # The artwork is two flat colours: pick the one this pixel belongs to and
            # rebuild its alpha from the white-blend, so no grey fringe survives.
            color = BLUE if b >= r else ORANGE
            denom = 255 - min(color)
            alpha = min(1.0, distance_from_white / denom)
            out_px[x, y] = (color[0], color[1], color[2], int(round(alpha * 255)))

    bbox = out.getbbox()
    if bbox:
        out = out.crop(bbox)

    # Square canvas with a little breathing room.
    side = int(max(out.size) * 1.10)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(out, ((side - out.width) // 2, (side - out.height) // 2), out)
    return square


def silhouette(mark: Image.Image) -> Image.Image:
    """White mask of the mark, for the Android 13+ monochrome launcher layer."""
    white = Image.new("RGBA", mark.size, (255, 255, 255, 0))
    white.putalpha(mark.getchannel("A"))
    return white


def write(path, image):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, "PNG", optimize=True)
    print("wrote", path, image.size)


def rounded_mask(size, radius_ratio=0.22):
    s = size * 4
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * radius_ratio), fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def legacy_icon(mark: Image.Image, size: int) -> Image.Image:
    base = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg = Image.new("RGBA", (size, size), BG_RAISED + (255,))
    base.paste(bg, (0, 0), rounded_mask(size))
    inner = int(size * 0.68)
    scaled = mark.resize((inner, inner), Image.LANCZOS)
    base.paste(scaled, ((size - inner) // 2, (size - inner) // 2), scaled)
    return base


def adaptive_foreground(mark: Image.Image, size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner = int(size * 0.60)  # keep the mark inside the adaptive safe zone
    scaled = mark.resize((inner, inner), Image.LANCZOS)
    canvas.paste(scaled, ((size - inner) // 2, (size - inner) // 2), scaled)
    return canvas


def adaptive_background(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), BG_DARK + (255,))
    gradient = Image.new("RGBA", (1, size))
    for y in range(size):
        t = y / max(1, size - 1)
        gradient.putpixel(
            (0, y),
            tuple(int(a + (b - a) * (0.25 + 0.55 * t)) for a, b in zip(BG_DARK, BG_RAISED)) + (255,),
        )
    canvas.paste(gradient.resize((size, size)), (0, 0))
    return canvas


def splash(size: int, mark: Image.Image) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), BG_DARK + (255,))
    inner = int(size * 0.46)
    scaled = mark.resize((inner, inner), Image.LANCZOS)
    canvas.paste(scaled, ((size - inner) // 2, (size - inner) // 2), scaled)
    return canvas


def main():
    mark = load_mark()
    res = "app/src/main/res"

    write(f"{res}/drawable-nodpi/logo.png", mark.resize((512, 512), Image.LANCZOS))
    write(f"{res}/drawable-nodpi/logo_white.png", silhouette(mark).resize((256, 256), Image.LANCZOS))
    write(f"{res}/drawable-nodpi/splash_logo.png", splash(768, mark))

    densities = {
        "mdpi": (48, 108),
        "hdpi": (72, 162),
        "xhdpi": (96, 216),
        "xxhdpi": (144, 324),
        "xxxhdpi": (192, 432),
    }
    for density, (legacy, adaptive) in densities.items():
        write(f"{res}/mipmap-{density}/ic_launcher.png", legacy_icon(mark, legacy))
        write(f"{res}/mipmap-{density}/ic_launcher_round.png", legacy_icon(mark, legacy))
        write(f"{res}/mipmap-{density}/ic_launcher_foreground.png", adaptive_foreground(mark, adaptive))
        write(f"{res}/mipmap-{density}/ic_launcher_background.png", adaptive_background(adaptive))
        write(f"{res}/mipmap-{density}/ic_launcher_monochrome.png", adaptive_foreground(silhouette(mark), adaptive))


if __name__ == "__main__":
    main()
