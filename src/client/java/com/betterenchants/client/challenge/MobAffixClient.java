package com.betterenchants.client.challenge;

import com.betterenchants.challenge.MobAffix;
import com.betterenchants.challenge.MobAffixRarity;
import com.betterenchants.network.MobAffixNetworking;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/** 客户端词条缓存与头顶绘制（含实例等级）。 */
@Environment(EnvType.CLIENT)
public final class MobAffixClient {
	public record AffixView(MobAffix affix, int level) {
	}

	private static final Map<Integer, List<AffixView>> CACHE = new ConcurrentHashMap<>();

	private MobAffixClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(MobAffixNetworking.SyncPayload.ID, (payload, context) ->
				context.client().execute(() -> put(payload.entityId(), payload.affixIds())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CACHE.clear());
	}

	private static void put(int entityId, List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			CACHE.remove(entityId);
			return;
		}
		List<AffixView> list = new ArrayList<>();
		for (String raw : ids) {
			int colon = raw.indexOf(':');
			String id = colon >= 0 ? raw.substring(0, colon) : raw;
			int lv = 1;
			if (colon >= 0 && colon + 1 < raw.length()) {
				try {
					lv = Integer.parseInt(raw.substring(colon + 1));
				} catch (NumberFormatException ignored) {
					lv = 1;
				}
			}
			MobAffix a = MobAffix.byId(id);
			if (a != null) {
				list.add(new AffixView(a, Math.max(1, Math.min(10, lv))));
			}
		}
		list.sort((a, b) -> {
			int c = Integer.compare(b.affix().rarity().id(), a.affix().rarity().id());
			if (c != 0) {
				return c;
			}
			return Integer.compare(b.level(), a.level());
		});
		CACHE.put(entityId, List.copyOf(list));
	}

	public static List<AffixView> get(int entityId) {
		return CACHE.getOrDefault(entityId, List.of());
	}

	public static boolean hasAny(int entityId) {
		return !get(entityId).isEmpty();
	}

	/** 兼容旧调用：仅返回词条枚举。 */
	public static List<MobAffix> getAffixes(int entityId) {
		List<AffixView> views = get(entityId);
		if (views.isEmpty()) {
			return List.of();
		}
		List<MobAffix> out = new ArrayList<>(views.size());
		for (AffixView v : views) {
			out.add(v.affix());
		}
		return out;
	}

	public static float drawAffixRow(
			TextRenderer textRenderer,
			Matrix4f matrix,
			VertexConsumerProvider consumers,
			List<MobAffix> affixes,
			float centerX,
			float y,
			long worldTime
	) {
		List<AffixView> views = new ArrayList<>();
		for (MobAffix a : affixes) {
			views.add(new AffixView(a, 1));
		}
		return drawAffixViews(textRenderer, matrix, consumers, views, centerX, y, worldTime);
	}

	public static float drawAffixViews(
			TextRenderer textRenderer,
			Matrix4f matrix,
			VertexConsumerProvider consumers,
			List<AffixView> affixes,
			float centerX,
			float y,
			long worldTime
	) {
		if (affixes == null || affixes.isEmpty()) {
			return 0.0f;
		}
		final int perLine = 5;
		int lines = (affixes.size() + perLine - 1) / perLine;
		// 第一层在最下方（y），多出的行依次往上叠
		float lineHeight = 10.0f;
		for (int line = 0; line < lines; line++) {
			int from = line * perLine;
			int to = Math.min(affixes.size(), from + perLine);
			float rowY = y - line * lineHeight;
			drawAffixLine(textRenderer, matrix, consumers, affixes, from, to, centerX, rowY, worldTime);
		}
		return lines * lineHeight;
	}

	private static void drawAffixLine(
			TextRenderer textRenderer,
			Matrix4f matrix,
			VertexConsumerProvider consumers,
			List<AffixView> affixes,
			int from,
			int to,
			float centerX,
			float y,
			long worldTime
	) {
		float total = 0.0f;
		for (int i = from; i < to; i++) {
			if (i > from) {
				total += textRenderer.getWidth(" ");
			}
			total += textRenderer.getWidth(labelOf(affixes.get(i)));
		}
		float x = centerX - total / 2.0f;
		for (int i = from; i < to; i++) {
			AffixView v = affixes.get(i);
			String label = labelOf(v);
			int color = rgb(v.affix().rarity(), worldTime, i) | 0xFF000000;
			textRenderer.draw(
					label, x, y, color, false, matrix, consumers,
					TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000,
					LightmapTextureManager.MAX_LIGHT_COORDINATE);
			x += textRenderer.getWidth(label);
			if (i + 1 < to) {
				textRenderer.draw(
						" ", x, y, 0xFFFFFFFF, false, matrix, consumers,
						TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000,
						LightmapTextureManager.MAX_LIGHT_COORDINATE);
				x += textRenderer.getWidth(" ");
			}
		}
	}

	private static String labelOf(AffixView v) {
		return "[" + v.affix().labelZh() + "·" + v.level() + "]";
	}

	public static int rgb(MobAffixRarity rarity, long worldTime, int index) {
		return switch (rarity) {
			case WHITE -> 0xF0F0F0;
			case GREEN -> 0x55FF55;
			case BLUE -> 0x5555FF;
			case PURPLE -> 0xFF55FF;
			case GOLD -> 0xFFAA00;
			case RED -> 0xFF5555;
			case RAINBOW -> {
				float hue = ((worldTime + index * 12L) % 40L) / 40.0f;
				yield hsvToRgb(hue, 0.85f, 1.0f);
			}
		};
	}

	private static int hsvToRgb(float h, float s, float v) {
		int i = Math.min(5, (int) (h * 6.0f));
		float f = h * 6.0f - i;
		float p = v * (1.0f - s);
		float q = v * (1.0f - f * s);
		float t = v * (1.0f - (1.0f - f) * s);
		float r;
		float g;
		float b;
		switch (i) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}
}
