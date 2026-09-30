package com.betterenchants.client.genshinui.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;

/** 面板动画 / 平滑滚动公共工具。 */
@Environment(EnvType.CLIENT)
public final class UiAnim {
	private UiAnim() {
	}

	public static float easeOutCubic(float t) {
		t = MathHelper.clamp(t, 0f, 1f);
		float u = 1f - t;
		return 1f - u * u * u;
	}

	public static float easeOutBack(float t) {
		t = MathHelper.clamp(t, 0f, 1f);
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * MathHelper.clamp(t, 0f, 1f);
	}

	public static float damp(float current, float target, float speed, float dt) {
		float k = 1f - (float) Math.exp(-speed * Math.max(0.001f, dt));
		return current + (target - current) * k;
	}

	public static int mulAlpha(int argb, float alpha) {
		int a = (argb >>> 24) & 0xFF;
		if (a == 0) {
			a = 255;
		}
		int na = MathHelper.clamp(Math.round(a * MathHelper.clamp(alpha, 0f, 1f)), 0, 255);
		return (na << 24) | (argb & 0x00FFFFFF);
	}

	public static long now() {
		return System.currentTimeMillis();
	}
}
