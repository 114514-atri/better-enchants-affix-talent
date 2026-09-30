package com.betterenchants.client.render;

import com.betterenchants.client.compat.DeferredWorldFx;
import com.betterenchants.client.perf.FxBudget;
import com.betterenchants.network.DamageNumberNetworking;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * 玩家攻击时在世界空间飘出伤害数字（10 格内）。
 * 同一目标短时间内的多段伤害会扇形错开，避免附魔连携时数字叠在一起。
 */
@Environment(EnvType.CLIENT)
public final class DamageNumberHud {
	private static final int MAX_LIFE_TICKS = 36;
	private static final int BURST_WINDOW_TICKS = 5;
	private static final double RISE_PER_TICK = 0.035;
	private static final float LABEL_SCALE = 0.022f;
	private static final int COLOR_SWORD = 0xFFFF5555;
	private static final int COLOR_LIGHTNING = 0xFFFFEE55;
	private static final int COLOR_MISS = 0xFFAAAAAA;
	private static final List<DamageEntry> ENTRIES = new ArrayList<>();
	private static final Map<Integer, BurstTracker> BURSTS = new HashMap<>();
	private static boolean enabled = true;

	private DamageNumberHud() {
	}

	public static void setEnabled(boolean v) {
		enabled = v;
		if (!v) {
			clear();
		}
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void clear() {
		ENTRIES.clear();
		BURSTS.clear();
	}

	public static int entryCount() {
		return ENTRIES.size();
	}

	public static void init() {
		DeferredWorldFx.register(DamageNumberHud::render);
	}

	public static void spawn(
			int entityId,
			double x,
			double y,
			double z,
			float damage,
			boolean lightning,
			boolean miss
	) {
		if (!enabled) {
			return;
		}
		if (!miss && (damage <= 0.0f || !Float.isFinite(damage))) {
			return;
		}
		if (!FxBudget.allowDamageNumber(ENTRIES.size())) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			double maxSq = DamageNumberNetworking.MAX_RANGE * DamageNumberNetworking.MAX_RANGE;
			if (client.player.squaredDistanceTo(x, y, z) > maxSq) {
				return;
			}
		}
		long spawnTick = client.world != null ? client.world.getTime() : 0L;
		String text = miss ? "MISS" : com.betterenchants.util.DamageAmountFormat.format(damage);
		int color = miss ? COLOR_MISS : (lightning ? COLOR_LIGHTNING : COLOR_SWORD);
		Offset offset = allocateOffset(entityId, spawnTick, lightning || miss);
		ENTRIES.add(new DamageEntry(x, y, z, text, color, spawnTick, lightning || miss, offset));
	}

	/** @deprecated 使用带 entityId 的重载 */
	@Deprecated
	public static void spawn(double x, double y, double z, float damage, boolean lightning, boolean miss) {
		spawn(-1, x, y, z, damage, lightning, miss);
	}

	public static void spawnLabel(int entityId, double x, double y, double z, String label, boolean lightning) {
		if (!enabled || label == null || label.isEmpty()) {
			return;
		}
		if (!FxBudget.allowDamageNumber(ENTRIES.size())) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			double maxSq = DamageNumberNetworking.MAX_RANGE * DamageNumberNetworking.MAX_RANGE;
			if (client.player.squaredDistanceTo(x, y, z) > maxSq) {
				return;
			}
		}
		long spawnTick = client.world != null ? client.world.getTime() : 0L;
		int color = lightning ? COLOR_LIGHTNING : COLOR_SWORD;
		Offset offset = allocateOffset(entityId, spawnTick, lightning);
		ENTRIES.add(new DamageEntry(x, y, z, label, color, spawnTick, lightning, offset));
	}

	/** @deprecated 使用带 entityId 的重载 */
	@Deprecated
	public static void spawnLabel(double x, double y, double z, String label, boolean lightning) {
		spawnLabel(-1, x, y, z, label, lightning);
	}

	private static Offset allocateOffset(int entityId, long tick, boolean centered) {
		if (entityId < 0 || centered) {
			return Offset.ZERO;
		}
		BurstTracker tracker = BURSTS.get(entityId);
		if (tracker == null || tick - tracker.lastTick > BURST_WINDOW_TICKS) {
			tracker = new BurstTracker(tick, 0);
		}
		int slot = tracker.slot++;
		tracker.lastTick = tick;
		BURSTS.put(entityId, tracker);
		return offsetForSlot(slot);
	}

	private static Offset offsetForSlot(int slot) {
		int ring = slot / 5;
		int pos = slot % 5;
		float spread = 0.22f + ring * 0.06f;
		float[] xs = {-2f, -1f, 0f, 1f, 2f};
		float x = xs[pos] * spread;
		float z = (pos - 2) * 0.08f;
		float y = ring * 0.14f;
		return new Offset(x, y, z);
	}

	private static void render(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
		if (!enabled) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || ENTRIES.isEmpty()) {
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
		TextRenderer textRenderer = client.textRenderer;
		float tickDelta = context.tickCounter().getTickDelta(false);
		long now = client.world.getTime();
		double camX = camera.getPos().x;
		double camY = camera.getPos().y;
		double camZ = camera.getPos().z;

		Iterator<DamageEntry> iterator = ENTRIES.iterator();
		while (iterator.hasNext()) {
			DamageEntry entry = iterator.next();
			float age = (now - entry.spawnTick()) + tickDelta;
			if (age >= MAX_LIFE_TICKS) {
				iterator.remove();
				continue;
			}

			float alpha = 1.0f - (age / MAX_LIFE_TICKS);
			int color = colorWithAlpha(entry.color(), alpha);
			String text = entry.text();
			int width = textRenderer.getWidth(text);
			double rise = age * RISE_PER_TICK * (entry.raised() ? 1.25 : 1.0);
			Offset off = entry.offset();

			matrices.push();
			matrices.translate(
					entry.x() + off.x() - camX,
					entry.y() + off.y() + rise - camY,
					entry.z() + off.z() - camZ);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
			matrices.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

			Matrix4f matrix = matrices.peek().getPositionMatrix();
			textRenderer.draw(
					text,
					-width / 2.0f,
					0.0f,
					color,
					false,
					matrix,
					consumers,
					TextRenderer.TextLayerType.SEE_THROUGH,
					0,
					LightmapTextureManager.MAX_LIGHT_COORDINATE);
			matrices.pop();
		}
		if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
			immediate.draw();
		}
	}

	private static int colorWithAlpha(int rgb, float alpha) {
		int a = MathHelper.clamp((int) (alpha * 255.0f), 0, 255);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	private static final class BurstTracker {
		long lastTick;
		int slot;

		BurstTracker(long lastTick, int slot) {
			this.lastTick = lastTick;
			this.slot = slot;
		}
	}

	@Environment(EnvType.CLIENT)
	private record Offset(float x, float y, float z) {
		static final Offset ZERO = new Offset(0.0f, 0.0f, 0.0f);
	}

	@Environment(EnvType.CLIENT)
	private record DamageEntry(
			double x,
			double y,
			double z,
			String text,
			int color,
			long spawnTick,
			boolean raised,
			Offset offset
	) {
	}
}
