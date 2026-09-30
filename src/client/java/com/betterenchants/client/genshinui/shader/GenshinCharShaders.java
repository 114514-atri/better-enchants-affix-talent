package com.betterenchants.client.genshinui.shader;

import com.betterenchants.BetterEnchantsMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class GenshinCharShaders {
	public static final Identifier ID = BetterEnchantsMod.id("genshin_char_bg");

	@Nullable
	private static ShaderProgram program;

	private GenshinCharShaders() {
	}

	public static void init() {
		CoreShaderRegistrationCallback.EVENT.register(ctx ->
				ctx.register(ID, VertexFormats.POSITION_TEXTURE, loaded -> program = loaded));
	}

	@Nullable
	public static ShaderProgram program() {
		return program;
	}

	public static boolean isReady() {
		return program != null;
	}

	public static void setUniforms(float time, float aspect, float yaw, float pitch, float zoom, float theme) {
		if (program == null) {
			return;
		}
		set1("uTime", time);
		set1("uAspect", aspect);
		set1("uYaw", yaw);
		set1("uPitch", pitch);
		set1("uZoom", zoom);
		set1("uTheme", theme);
	}

	private static void set1(String name, float v) {
		GlUniform u = program.getUniform(name);
		if (u != null) {
			u.set(v);
		}
	}
}
