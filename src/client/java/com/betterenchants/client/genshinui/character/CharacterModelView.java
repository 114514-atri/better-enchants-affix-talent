package com.betterenchants.client.genshinui.character;

import com.betterenchants.client.genshinui.config.GenshinUiClientConfig;
import com.betterenchants.client.genshinui.shader.GenshinCharShaders;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 自灵音仿原神 UI（NeoForge）移植的角色模型 / 着色器背景渲染。
 */
@Environment(EnvType.CLIENT)
public final class CharacterModelView {
	private static final float BG_RENDER_SCALE = 0.5f;
	static final int[] THEME_MID = {
			0x373B47, 0x0B3D79, 0x792216, 0x591E8C, 0x59A6C7, 0x29856B, 0x599929, 0x8C6B29
	};
	static final int[] THEME_BOT = {
			0x0B0C0F, 0x020818, 0x180604, 0x0D041A, 0x0F334F, 0x051E1A, 0x0D2405, 0x1F1408
	};

	private static final Vector3f VIEW_LIGHT_0 = new Vector3f(-1.0f, 0.55f, -1.0f).normalize();
	private static final Vector3f VIEW_LIGHT_1 = new Vector3f(1.0f, 0.55f, 1.0f).normalize();
	/** 必须先于 MODEL_LIGHT_* 初始化，toModelSpace 会读到它。 */
	private static final Quaternionf FLIP_QUAT = new Quaternionf().rotateZ((float) Math.PI);
	private static final Vector3f MODEL_LIGHT_0 = toModelSpace(VIEW_LIGHT_0);
	private static final Vector3f MODEL_LIGHT_1 = toModelSpace(VIEW_LIGHT_1);

	private static Framebuffer bgTarget;
	private static int bgW;
	private static int bgH;

	private final BetterCharacterScreen host;
	private final Vector3f tmpLight0 = new Vector3f();
	private final Vector3f tmpLight1 = new Vector3f();
	private final Quaternionf tmpOrbit = new Quaternionf();
	private final Quaternionf tmpWobble = new Quaternionf();
	private final ItemStack[] tmpArmorSave = new ItemStack[4];

	CharacterModelView(BetterCharacterScreen host) {
		this.host = host;
	}

	void renderShaderBackground(DrawContext context) {
		int theme = GenshinUiClientConfig.themeIndex;
		if (!GenshinCharShaders.isReady()) {
			int mid = 0xFF000000 | THEME_MID[theme];
			int bot = 0xFF000000 | THEME_BOT[theme];
			context.fillGradient(0, 0, host.width, host.height, mid, bot);
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		Framebuffer main = mc.getFramebuffer();
		int tw = Math.max(2, (int) (main.textureWidth * BG_RENDER_SCALE));
		int th = Math.max(2, (int) (main.textureHeight * BG_RENDER_SCALE));
		ensureBg(tw, th);
		bgTarget.beginWrite(true);
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableBlend();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		float time = (System.currentTimeMillis() % 100000L) / 1000.0f;
		float aspect = host.height <= 0 ? 1.0f : (float) host.width / (float) host.height;
		GenshinCharShaders.setUniforms(
				time,
				aspect,
				(float) Math.toRadians(host.camYaw),
				(float) Math.toRadians(host.camPitch),
				host.camZoom,
				theme
		);
		RenderSystem.setShader(GenshinCharShaders::program);
		Tessellator tess = Tessellator.getInstance();
		BufferBuilder bb = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
		bb.vertex(-1f, -1f, 0f).texture(0f, 0f);
		bb.vertex(1f, -1f, 0f).texture(1f, 0f);
		bb.vertex(1f, 1f, 0f).texture(1f, 1f);
		bb.vertex(-1f, 1f, 0f).texture(0f, 1f);
		BufferRenderer.drawWithGlobalProgram(bb.end());
		mc.getFramebuffer().beginWrite(true);
		RenderSystem.setShaderTexture(0, bgTarget.getColorAttachment());
		RenderSystem.setShader(GameRenderer::getPositionTexProgram);
		BufferBuilder up = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
		Matrix4f mat = context.getMatrices().peek().getPositionMatrix();
		up.vertex(mat, 0f, 0f, 0f).texture(0f, 1f);
		up.vertex(mat, 0f, host.height, 0f).texture(0f, 0f);
		up.vertex(mat, host.width, host.height, 0f).texture(1f, 0f);
		up.vertex(mat, host.width, 0f, 0f).texture(1f, 1f);
		BufferRenderer.drawWithGlobalProgram(up.end());
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		RenderSystem.depthMask(true);
		// 关键：GUI 文字前必须关 depth，否则字会被实体 depth 蒙住
		RenderSystem.disableDepthTest();
	}

	void renderPlayerModel(DrawContext context, PlayerEntity player, boolean showHeldItem) {
		float intro = easeOutCubic(elapsedSince(host.openTimeMs) / 420.0f);
		float zoomK = MathHelper.clamp(1.0f / host.camZoom, 0.45f, 1.25f);
		float scale = host.modelScale * zoomK * (0.94f + 0.06f * intro);
		PlayerInventory inv = player.getInventory();
		ItemStack savedMain = inv.main.get(inv.selectedSlot);
		ItemStack savedOff = inv.offHand.get(0);
		boolean showArmor = host.selectedTab == BetterCharacterScreen.TAB_EQUIP;
		ItemStack[] savedArmor = null;
		if (!showArmor) {
			savedArmor = tmpArmorSave;
			for (int i = 0; i < 4; i++) {
				savedArmor[i] = inv.armor.get(i);
				inv.armor.set(i, ItemStack.EMPTY);
			}
		}
		if (!showHeldItem) {
			inv.main.set(inv.selectedSlot, ItemStack.EMPTY);
			inv.offHand.set(0, ItemStack.EMPTY);
		}
		try {
			Quaternionf orbit = tmpOrbit.identity()
					.rotateX((float) Math.toRadians(host.camPitch))
					.rotateY((float) Math.toRadians(host.camYaw));
			applyModelLight(orbit, false, 0.0f);
			drawEntity(context, player, host.modelCenterX, host.modelGroundY, scale, orbit, false, 0.0f, 0xF000F0);
			context.draw();
			float tSec = (System.currentTimeMillis() % 100000L) / 1000.0f;
			float wobble = (float) (Math.sin(tSec * 1.6f) * 0.02f + Math.sin(tSec * 3.7f) * 0.008f);
			RenderSystem.disableCull();
			applyModelLight(orbit, true, wobble);
			drawEntity(context, player, host.modelCenterX, host.modelGroundY, scale, orbit, true, wobble, 0xA000A0);
			context.draw();
			RenderSystem.enableCull();
			DiffuseLighting.enableGuiDepthLighting();
		} finally {
			inv.main.set(inv.selectedSlot, savedMain);
			inv.offHand.set(0, savedOff);
			if (savedArmor != null) {
				for (int i = 0; i < 4; i++) {
					inv.armor.set(i, savedArmor[i]);
				}
			}
			RenderSystem.disableDepthTest();
		}
	}

	void renderReflectionMist(DrawContext context) {
		int fogRgb = THEME_MID[GenshinUiClientConfig.themeIndex] & 0xFFFFFF;
		int halfW = (int) (host.modelScale * 1.6f);
		int y1 = host.modelGroundY;
		int y2 = Math.min(host.height, host.modelGroundY + (int) (host.modelScale * 1.8f) + 8);
		for (int i = 0; i < 20; i++) {
			int sx1 = (host.modelCenterX - halfW) + ((2 * halfW) * i) / 20;
			int sx2 = (host.modelCenterX - halfW) + ((2 * halfW) * (i + 1)) / 20;
			float t = Math.abs(((i + 0.5f) / 20f) - 0.5f) * 2.0f;
			float edge = 1.0f - (t * t * (3.0f - 2.0f * t));
			int aBot = Math.round(138.0f * edge);
			if (aBot > 0) {
				context.fillGradient(sx1, y1, sx2, y2, fogRgb, (aBot << 24) | fogRgb);
			}
		}
	}

	private void drawEntity(
			DrawContext context,
			PlayerEntity player,
			float x,
			float groundY,
			float scale,
			Quaternionf orbit,
			boolean mirror,
			float wobble,
			int packedLight
	) {
		float savedBodyRot = player.bodyYaw;
		float savedBodyRotO = player.prevBodyYaw;
		float savedYRot = player.getYaw();
		float savedXRot = player.getPitch();
		float savedHeadRot = player.headYaw;
		float savedHeadRotO = player.prevHeadYaw;
		try {
			player.bodyYaw = 180.0f;
			player.prevBodyYaw = 180.0f;
			player.setYaw(180.0f);
			player.setPitch(0.0f);
			player.headYaw = 180.0f;
			player.prevHeadYaw = 180.0f;
			context.getMatrices().push();
			context.getMatrices().translate(x, groundY, 50.0);
			context.getMatrices().scale(scale, scale, -scale);
			context.getMatrices().multiply(orbit);
			if (mirror) {
				context.getMatrices().scale(1.0f, -1.0f, 1.0f);
				context.getMatrices().multiply(tmpWobble.identity().rotateZ(wobble));
			}
			context.getMatrices().multiply(FLIP_QUAT);
			EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
			dispatcher.setRenderShadows(false);
			dispatcher.render(player, 0.0, 0.0, 0.0, 0.0f, 1.0f, context.getMatrices(), context.getVertexConsumers(), packedLight);
			dispatcher.setRenderShadows(true);
			context.getMatrices().pop();
		} finally {
			player.bodyYaw = savedBodyRot;
			player.prevBodyYaw = savedBodyRotO;
			player.setYaw(savedYRot);
			player.setPitch(savedXRot);
			player.headYaw = savedHeadRot;
			player.prevHeadYaw = savedHeadRotO;
		}
	}

	private static Vector3f toModelSpace(Vector3f viewLight) {
		Vector3f l = new Vector3f(viewLight);
		l.z = -l.z;
		Quaternionf inv = new Quaternionf(FLIP_QUAT);
		inv.invert();
		l.rotate(inv);
		return l.normalize();
	}

	private void trackLight(Vector3f out, Vector3f modelLight, Quaternionf orbit, boolean mirror, float wobble) {
		out.set(modelLight);
		out.rotateZ(wobble);
		if (mirror) {
			out.y = -out.y;
		}
		out.rotate(orbit);
		out.z = -out.z;
	}

	private void applyModelLight(Quaternionf orbit, boolean mirror, float wobble) {
		trackLight(tmpLight0, MODEL_LIGHT_0, orbit, mirror, wobble);
		trackLight(tmpLight1, MODEL_LIGHT_1, orbit, mirror, wobble);
		RenderSystem.setShaderLights(tmpLight0, tmpLight1);
	}

	private static void ensureBg(int w, int h) {
		if (bgTarget != null && bgW == w && bgH == h) {
			return;
		}
		if (bgTarget != null) {
			bgTarget.delete();
		}
		bgTarget = new SimpleFramebuffer(w, h, false, false);
		bgW = w;
		bgH = h;
	}

	static float easeOutCubic(float t) {
		t = MathHelper.clamp(t, 0f, 1f);
		float u = 1f - t;
		return 1f - u * u * u;
	}

	static float elapsedSince(long ms) {
		return Math.max(0f, System.currentTimeMillis() - ms);
	}
}
