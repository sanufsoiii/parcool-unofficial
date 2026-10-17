package com.alrex.parcool.utilities;

import net.minecraft.util.FastColor;
import net.minecraft.world.item.DyeColor;

public class ColorUtil {
	public static int getColorCodeFromARGB(int a, int r, int g, int b) {
		return a * 0x1000000 + r * 0x10000 + g * 0x100 + b;
	}

	/**
	 * The packed RGB of a dye's texture colour.
	 * <p>
	 * 1.20.6 exposes it as an RGB float triplet ({@link DyeColor#getTextureDiffuseColors()}); the
	 * packed int ({@code DyeColor#getTextureDiffuseColor()}) only arrives in 1.21. Vanilla builds the
	 * triplet by dividing each channel of the packed colour by 255, so rounding recovers the exact
	 * value the 1.21.1 tree used.
	 */
	public static int getTextureColor(DyeColor color) {
		float[] rgb = color.getTextureDiffuseColors();
		return FastColor.ARGB32.color(
				Math.round(rgb[0] * 255f),
				Math.round(rgb[1] * 255f),
				Math.round(rgb[2] * 255f)
		);
	}

	public static int multiple(int value, double scale) {
		int a = ((value & 0xFF000000) >> 24) & 0xFF,
				r = (value & 0x00FF0000) >> 16,
				g = (value & 0x0000FF00) >> 8,
				b = value & 0x000000FF;
		r = (int) Math.min(0xFF, r * scale);
		g = (int) Math.min(0xFF, g * scale);
		b = (int) Math.min(0xFF, b * scale);
		return getColorCodeFromARGB(a, r, g, b);
	}
}
