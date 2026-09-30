package com.betterenchants.client.render;

import com.betterenchants.compat.OptionalPrivateHooks;

import com.betterenchants.client.challenge.MobAffixClient;
import com.betterenchants.client.compat.DeferredWorldFx;
import com.betterenchants.client.perf.FxBudget;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * 世界空间实体血条（显示范围 10 格）。
 * 无专属 bar：条下显示「名字 血量【护盾】」+ 彩色词条。
 * 有专属 bar：仅在头顶显示词条。
 */
@Environment(EnvType.CLIENT)
public final class EntityHealthBarHud {
	private static final Identifier WHITE = Identifier.ofVanilla("textures/misc/white.png");
	private static final double MAX_RANGE = 8.0;
	private static final float BAR_WIDTH = 40.0f;
	private static final float BAR_HEIGHT = 4.0f;
	private static final float LABEL_SCALE = 0.025f;
	private static boolean enabled = true;

	private EntityHealthBarHud() {
	}

	public static void setEnabled(boolean v) {
		enabled = v;
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void init() {
		DeferredWorldFx.register(EntityHealthBarHud::render);
	}

	private static void render(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
		if (!enabled) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || client.options.hudHidden) {
			return;
		}
		MatrixStack matrices = context.matrixStack();
		if (matrices == null) {
			return;
		}
		VertexConsumerProvider consumers = context.consumers();
		if (consumers == null) {
			consumers = client.getBufferBuilders().getEntityVertexConsumers();
		}

		Camera camera = context.camera();
		Vec3d cameraPos = camera.getPos();
		Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
		TextRenderer textRenderer = client.textRenderer;
		float tickDelta = context.tickCounter().getTickDelta(false);
		PlayerEntity player = client.player;
		Box box = player.getBoundingBox().expand(MAX_RANGE);
		double rangeSq = MAX_RANGE * MAX_RANGE;
		int drawn = 0;
		long worldTime = client.world.getTime();

		for (Entity entity : client.world.getOtherEntities(player, box)) {
			if (drawn >= FxBudget.maxHealthBars()) {
				break;
			}
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}
			if (living instanceof PlayerEntity) {
				continue;
			}
			if (!living.isAlive()) {
				continue;
			}
			// isInvisibleTo 已由破隐 mixin 穿透；此处再兜底一次
			if (living.isInvisibleTo(player)
					&& !com.betterenchants.enchant.BreakInvisHandler.wearsBreakInvis(player)) {
				continue;
			}
			if (living.squaredDistanceTo(player) > rangeSq) {
				continue;
			}

			double x = MathHelper.lerp(tickDelta, living.lastRenderX, living.getX()) - cameraPos.x;
			double y = MathHelper.lerp(tickDelta, living.lastRenderY, living.getY()) + living.getHeight() + 0.55 - cameraPos.y;
			double z = MathHelper.lerp(tickDelta, living.lastRenderZ, living.getZ()) - cameraPos.z;
			// 背后实体不画血条
			if (x * look.x + y * look.y + z * look.z < -0.15) {
				continue;
			}

			boolean exclusive = com.betterenchants.client.boss.BossExclusiveBarHud.hidesWorldHealthBar(living);
			List<MobAffixClient.AffixView> affixes = MobAffixClient.get(living.getId());
			if (exclusive) {
				if (!affixes.isEmpty()) {
					renderAffixesOnly(matrices, consumers, textRenderer, camera, affixes, x, y, z, worldTime);
					drawn++;
				}
				continue;
			}

			renderEntityBar(matrices, consumers, textRenderer, camera, living, affixes, x, y, z, worldTime);
			drawn++;
		}

		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw(RenderLayer.getEntityTranslucent(WHITE));
			immediate.draw();
		}
	}

	private static void renderAffixesOnly(
			MatrixStack matrices,
			VertexConsumerProvider consumers,
			TextRenderer textRenderer,
			Camera camera,
			List<MobAffixClient.AffixView> affixes,
			double x,
			double y,
			double z,
			long worldTime
	) {
		matrices.push();
		matrices.translate(x, y, z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
		matrices.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		// 专属 bar 头顶：词条叠在实体上方（第一层最下，往上加行）
		MobAffixClient.drawAffixViews(textRenderer, matrix, consumers, affixes, 0.0f, -8.0f, worldTime);
		matrices.pop();
	}

	private static void renderEntityBar(
			MatrixStack matrices,
			VertexConsumerProvider consumers,
			TextRenderer textRenderer,
			Camera camera,
			LivingEntity living,
			List<MobAffixClient.AffixView> affixes,
			double x,
			double y,
			double z,
			long worldTime
	) {
		float maxHealth;
		float health;
		float healthRatio;
		String hpText;
		if (living instanceof com.betterenchants.entity.TargetDummyEntity dummy) {
			if (dummy.isInfiniteHealth()) {
				maxHealth = 1.0f;
				health = 1.0f;
				healthRatio = 1.0f;
				hpText = "∞/∞";
			} else {
				java.math.BigDecimal vmax = dummy.getVirtualMaxHealth();
				java.math.BigDecimal vhp = dummy.getVirtualHealth();
				if (vmax.signum() <= 0) {
					vmax = java.math.BigDecimal.ONE;
				}
				healthRatio = vhp.divide(vmax, java.math.MathContext.DECIMAL32).floatValue();
				healthRatio = MathHelper.clamp(healthRatio, 0.0f, 1.0f);
				maxHealth = 1.0f;
				health = healthRatio;
				hpText = com.betterenchants.dummy.TargetDummyNumbers.formatInteger(vhp)
						+ "/"
						+ com.betterenchants.dummy.TargetDummyNumbers.formatInteger(vmax);
			}
		} else {
			maxHealth = Math.max(living.getMaxHealth(), 1.0f);
			health = MathHelper.clamp(living.getHealth(), 0.0f, maxHealth);
			healthRatio = health / maxHealth;
			hpText = formatHp(health, maxHealth);
		}
		int fillColor = healthColor(healthRatio);

		float shield = 0.0f;
		float maxShield = 0.0f;
		if (OptionalPrivateHooks.isPrivateBossHost(living)) {
			shield = com.betterenchants.boss.BossCombat.currentShield(living);
			maxShield = com.betterenchants.boss.BossCombat.maxShield(living);
		}

		matrices.push();
		matrices.translate(x, y, z);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
		matrices.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

		MatrixStack.Entry entry = matrices.peek();
		Matrix4f matrix = entry.getPositionMatrix();

		float barX = -BAR_WIDTH / 2.0f;
		float barY = -8.0f;

		if (living instanceof com.betterenchants.entity.TargetDummyEntity) {
			DummyCombatHud.Stats combat = DummyCombatHud.get(living.getId());
			if (combat != null) {
				drawCenteredLabel(textRenderer, matrix, consumers,
						"DPS " + combat.dps(), barX, barY - 22.0f, 0xFFFFEE88);
				drawCenteredLabel(textRenderer, matrix, consumers,
						"总伤 " + combat.total(), barX, barY - 12.0f, 0xFFFFEE88);
			}
		}

		// 血条在上
		VertexConsumer barVc = consumers.getBuffer(RenderLayer.getEntityTranslucent(WHITE));
		drawRect(barVc, entry, matrix, barX - 1.0f, barY - 1.0f, BAR_WIDTH + 2.0f, BAR_HEIGHT + 2.0f, 0xCC000000);
		drawRect(barVc, entry, matrix, barX, barY, BAR_WIDTH, BAR_HEIGHT, 0xFF2A2A2A);
		float filledWidth = Math.max(1.0f, BAR_WIDTH * healthRatio);
		drawRect(barVc, entry, matrix, barX, barY, filledWidth, BAR_HEIGHT, fillColor | 0xFF000000);
		if (maxShield > 0.0f) {
			float shRatio = MathHelper.clamp(shield / maxShield, 0.0f, 1.0f);
			float shWidth = Math.max(0.0f, BAR_WIDTH * shRatio);
			if (shWidth > 0.0f) {
				drawRect(barVc, entry, matrix, barX, barY, shWidth, BAR_HEIGHT, 0xB8E8EEF5);
			}
		}

		// 条下：名字 血量【护盾】
		String name = living.getName().getString();
		String quick = com.betterenchants.client.creaturestaff.CreatureStaffClient.quickLabelFor(living.getUuid());
		String full = com.betterenchants.client.creaturestaff.CreatureStaffClient.fullLabelFor(living.getUuid());
		StringBuilder line = new StringBuilder();
		if (quick != null) {
			line.append('[').append(quick).append("] ");
		}
		if (full != null) {
			line.append('[').append(full).append("] ");
		}
		line.append(name).append(' ').append(hpText);
		if (maxShield > 0.0f) {
			line.append('【').append(formatHp(shield, maxShield)).append('】');
		}
		String info = line.toString();
		float infoY = barY + BAR_HEIGHT + 3.0f;
		int infoW = textRenderer.getWidth(info);
		textRenderer.draw(
				info, -infoW / 2.0f, infoY, 0xFFE8E8E8, false, matrix, consumers,
				TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000,
				LightmapTextureManager.MAX_LIGHT_COORDINATE);

		com.betterenchants.blessing.BlessingMarkData marks = living.getAttached(
				com.betterenchants.registry.ModAttachments.BLESSING_MARKS);
		if (marks != null && marks.visible()) {
			float xCursor = infoW / 2.0f + 4.0f;
			if (marks.flameHits() > 0 || marks.flameUntil() > worldTime) {
				String flame = marks.flameUntil() > worldTime ? "炎!" : (marks.flameHits() + "/15");
				textRenderer.draw(flame, xCursor, infoY, 0xFFFF3333, false, matrix, consumers,
						TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000,
						LightmapTextureManager.MAX_LIGHT_COORDINATE);
				xCursor += textRenderer.getWidth(flame) + 3.0f;
			}
			if (marks.frostHits() > 0 || marks.frostUntil() > worldTime) {
				String frost = marks.frostUntil() > worldTime ? "霜!" : (marks.frostHits() + "/15");
				textRenderer.draw(frost, xCursor, infoY, 0xFF4499FF, false, matrix, consumers,
						TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000,
						LightmapTextureManager.MAX_LIGHT_COORDINATE);
				xCursor += textRenderer.getWidth(frost) + 3.0f;
			}
			if (marks.voidMarks() > 0 || marks.voidHits() > 0) {
				String vd = "虚" + marks.voidMarks() + "(" + marks.voidHits() + "/5)";
				textRenderer.draw(vd, xCursor, infoY, 0xFFAA66FF, false, matrix, consumers,
						TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000,
						LightmapTextureManager.MAX_LIGHT_COORDINATE);
			}
		}

		// 词条在血条上方（第一层紧贴条上沿，多出行继续往上）
		if (!affixes.isEmpty()) {
			MobAffixClient.drawAffixViews(
					textRenderer, matrix, consumers, affixes, 0.0f, barY - 4.0f, worldTime);
		}

		matrices.pop();
	}

	private static void drawCenteredLabel(
			TextRenderer textRenderer,
			Matrix4f matrix,
			VertexConsumerProvider consumers,
			String text,
			float barX,
			float y,
			int color
	) {
		int width = textRenderer.getWidth(text);
		textRenderer.draw(
				text,
				barX + (BAR_WIDTH - width) / 2.0f,
				y,
				color,
				false,
				matrix,
				consumers,
				TextRenderer.TextLayerType.SEE_THROUGH,
				0x66000000,
				LightmapTextureManager.MAX_LIGHT_COORDINATE);
	}

	private static String formatHp(float health, float maxHealth) {
		return com.betterenchants.util.DamageAmountFormat.format(health)
				+ "/"
				+ com.betterenchants.util.DamageAmountFormat.format(maxHealth);
	}

	private static int healthColor(float ratio) {
		int red = (int) (255.0f * (1.0f - ratio) + 64.0f * ratio);
		int green = (int) (64.0f * (1.0f - ratio) + 220.0f * ratio);
		return (red << 16) | (green << 8);
	}

	private static void drawRect(
			VertexConsumer vc,
			MatrixStack.Entry entry,
			Matrix4f matrix,
			float x,
			float y,
			float width,
			float height,
			int color) {
		float r = ((color >> 16) & 0xFF) / 255.0f;
		float g = ((color >> 8) & 0xFF) / 255.0f;
		float b = (color & 0xFF) / 255.0f;
		float a = ((color >> 24) & 0xFF) / 255.0f;
		vert(vc, entry, matrix, x, y + height, r, g, b, a);
		vert(vc, entry, matrix, x + width, y + height, r, g, b, a);
		vert(vc, entry, matrix, x + width, y, r, g, b, a);
		vert(vc, entry, matrix, x, y, r, g, b, a);
	}

	private static void vert(
			VertexConsumer vc,
			MatrixStack.Entry entry,
			Matrix4f matrix,
			float x,
			float y,
			float r,
			float g,
			float b,
			float a) {
		vc.vertex(matrix, x, y, 0.0f)
				.color(r, g, b, a)
				.texture(0.5f, 0.5f)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
				.normal(entry, 0.0f, 0.0f, 1.0f);
	}
}
