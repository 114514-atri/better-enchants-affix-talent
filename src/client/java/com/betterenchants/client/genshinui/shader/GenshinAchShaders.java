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
public final class GenshinAchShaders {
	public static final Identifier ID = BetterEnchantsMod.id("genshin_ach_bg");

	@Nullable
	private static ShaderProgram program;

	private GenshinAchShaders() {
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

	public static void setUniforms(float time, float aspect) {
		if (program == null) {
			return;
		}
		GlUniform t = program.getUniform("uTime");
		if (t != null) {
			t.set(time);
		}
		GlUniform a = program.getUniform("uAspect");
		if (a != null) {
			a.set(aspect);
		}
	}
}
