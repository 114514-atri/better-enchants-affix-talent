package com.betterenchants.client.genshinui.achievement;

import com.betterenchants.client.genshinui.util.UiAnim;
import com.betterenchants.talent.BodyCultivationRewards;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientAdvancementManager;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * 成就浏览器：左右分栏 + 平滑滚动 + 筛选/选中动画。
 */
@Environment(EnvType.CLIENT)
public final class BetterAchievementScreen extends Screen {
	private static final int PAD = 14;
	private static final int TOP = 54;
	private static final int BOTTOM = 38;
	private static final int GAP = 12;
	private static final int ROW_H = 30;
	private static final int COL_ACCENT = 0xFFE8C878;
	private static final int COL_TEXT = 0xFFF2F0F2;
	private static final int COL_DIM = 0xFFB9B5B9;

	private static final String[] FILTER_IDS = {"all", "done", "todo", "be"};
	private static final String[] FILTER_LABELS = {"全部", "已完成", "未完成", "本模组"};

	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private float scroll;
	private float scrollTarget;
	private int selected = -1;
	private float selectedAnimY;
	private String filter = "all";
	private long openMs;
	private long filterSwitchMs;
	private long selectMs;
	private long clickFlashMs;
	private int clickFlashX;
	private int clickFlashY;
	private float detailSlide;
	private long lastFrameMs = System.currentTimeMillis();
	private final float[] filterHover = new float[FILTER_IDS.length];
	private float closeHover;

	public BetterAchievementScreen(Screen parent) {
		super(Text.translatable("screen.better_enchants.achievements"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		openMs = System.currentTimeMillis();
		filterSwitchMs = openMs;
		detailSlide = 0f;
		rebuildRows();
	}

	private Layout layout() {
		int contentH = Math.max(48, height - TOP - BOTTOM);
		int usable = Math.max(100, width - PAD * 2 - GAP);
		int listW = MathHelper.clamp(usable * 46 / 100, 160, 300);
		int detailW = usable - listW;
		if (detailW < 140) {
			listW = Math.max(140, usable - 140);
			detailW = usable - listW;
		}
		int listX = PAD;
		int detailX = listX + listW + GAP;
		return new Layout(listX, TOP, listW, contentH, detailX, TOP, detailW, contentH);
	}

	private void rebuildRows() {
		rows.clear();
		selected = -1;
		scroll = 0f;
		scrollTarget = 0f;
		selectedAnimY = TOP;
		MinecraftClient mc = client;
		if (mc == null || mc.getNetworkHandler() == null) {
			return;
		}
		ClientAdvancementManager mgr = mc.getNetworkHandler().getAdvancementHandler();
		java.util.Map<?, AdvancementProgress> progressMap = readProgressMap(mgr);
		for (PlacedAdvancement placed : mgr.getManager().getAdvancements()) {
			AdvancementEntry entry = placed.getAdvancementEntry();
			if (entry == null) {
				continue;
			}
			AdvancementDisplay display = entry.value().display().orElse(null);
			if (display == null) {
				continue;
			}
			Identifier id = entry.id();
			AdvancementProgress progress = progressMap.get(entry);
			if (progress == null) {
				progress = progressMap.get(id);
			}
			boolean done = progress != null && progress.isDone();
			boolean be = "better_enchants".equals(id.getNamespace());
			if ("done".equals(filter) && !done) {
				continue;
			}
			if ("todo".equals(filter) && done) {
				continue;
			}
			if ("be".equals(filter) && !be) {
				continue;
			}
			int points = BodyCultivationRewards.pointsForAdvancement(id);
			rows.add(new Row(entry, display, done, points));
		}
		rows.sort(Comparator
				.comparing((Row r) -> !r.done)
				.thenComparing(r -> r.display.getTitle().getString()));
		filterSwitchMs = System.currentTimeMillis();
	}

	@SuppressWarnings("unchecked")
	private static java.util.Map<?, AdvancementProgress> readProgressMap(ClientAdvancementManager mgr) {
		try {
			java.lang.reflect.Field field = ClientAdvancementManager.class.getDeclaredField("advancementProgresses");
			field.setAccessible(true);
			Object raw = field.get(mgr);
			if (raw instanceof java.util.Map<?, ?> map) {
				return (java.util.Map<?, AdvancementProgress>) map;
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return java.util.Map.of();
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void renderInGameBackground(DrawContext context) {
	}

	private void tickAnim(float dt) {
		scroll = UiAnim.damp(scroll, scrollTarget, 16f, dt);
		detailSlide = UiAnim.damp(detailSlide, selected >= 0 ? 1f : 0f, 10f, dt);
		closeHover = UiAnim.damp(closeHover, 0f, 12f, dt);
		for (int i = 0; i < filterHover.length; i++) {
			filterHover[i] = UiAnim.damp(filterHover[i], 0f, 14f, dt);
		}
		if (selected >= 0) {
			float targetY = TOP + selected * ROW_H - scroll;
			selectedAnimY = UiAnim.damp(selectedAnimY, targetY, 18f, dt);
		}
		int maxScroll = Math.max(0, rows.size() * ROW_H - layout().listH);
		if (scrollTarget > maxScroll) {
			scrollTarget = maxScroll;
		}
	}

	private void pulse(int x, int y) {
		clickFlashMs = System.currentTimeMillis();
		clickFlashX = x;
		clickFlashY = y;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		long now = System.currentTimeMillis();
		float dt = MathHelper.clamp((now - lastFrameMs) / 1000f, 0.001f, 0.05f);
		lastFrameMs = now;
		tickAnim(dt);

		float openA = UiAnim.easeOutCubic(MathHelper.clamp((now - openMs) / 260f, 0f, 1f));

		context.fillGradient(0, 0, width, height, 0xFF1A1830, 0xFF08060E);
		RenderSystem.disableDepthTest();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

		context.getMatrices().push();
		context.getMatrices().translate(0, (1f - openA) * -8f, 0);

		drawCenteredScaled(context, title.getString(), width / 2f, 10, 1.15f,
				UiAnim.mulAlpha(COL_ACCENT, openA));

		renderFilters(context, mouseX, mouseY, openA);
		renderClose(context, mouseX, mouseY, openA);

		Layout L = layout();
		float listSlide = UiAnim.easeOutCubic(MathHelper.clamp((now - openMs) / 320f, 0f, 1f));
		int listOx = Math.round((1f - listSlide) * -20f);
		context.fill(L.listX - 4 + listOx, L.listY - 4, L.listX + L.listW + 4 + listOx, L.listY + L.listH + 4,
				UiAnim.mulAlpha(0xCC14121A, openA));
		context.drawBorder(L.listX - 4 + listOx, L.listY - 4, L.listW + 8, L.listH + 8,
				UiAnim.mulAlpha(0x55E8C878, openA));

		float filterFade = UiAnim.easeOutCubic(MathHelper.clamp((now - filterSwitchMs) / 180f, 0f, 1f));
		context.enableScissor(L.listX + listOx, L.listY, L.listX + L.listW + listOx, L.listY + L.listH);
		int y = L.listY - Math.round(scroll);
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			int rowY = y;
			if (rowY + ROW_H >= L.listY && rowY <= L.listY + L.listH) {
				boolean sel = i == selected;
				boolean hover = mouseX >= L.listX + listOx && mouseX < L.listX + L.listW + listOx
						&& mouseY >= rowY && mouseY < rowY + ROW_H - 2;
				int bg = sel ? 0x77E8C878 : (hover ? 0x55383028 : 0x4428221C);
				context.fill(L.listX + listOx, rowY, L.listX + L.listW + listOx, rowY + ROW_H - 2,
						UiAnim.mulAlpha(bg, openA * (0.55f + 0.45f * filterFade)));
				if (sel) {
					context.fill(L.listX + listOx, rowY, L.listX + 3 + listOx, rowY + ROW_H - 2, COL_ACCENT);
				}
				ItemStack icon = row.display.getIcon();
				context.drawItem(icon, L.listX + 6 + listOx, rowY + 5);
				context.drawItemInSlot(textRenderer, icon, L.listX + 6 + listOx, rowY + 5);
				String titleStr = trimToWidth(row.display.getTitle().getString(), L.listW - 40);
				context.drawTextWithShadow(textRenderer, titleStr, L.listX + 30 + listOx, rowY + 5,
						UiAnim.mulAlpha(row.done ? 0xFFC8E8A0 : COL_TEXT, openA * filterFade));
				String sub = row.done ? "已完成" : "未完成";
				if (row.points > 0) {
					sub += " · 体修 +" + row.points;
				}
				context.drawTextWithShadow(textRenderer, trimToWidth(sub, L.listW - 40),
						L.listX + 30 + listOx, rowY + 16, UiAnim.mulAlpha(COL_DIM, openA * filterFade));
			}
			y += ROW_H;
		}
		context.disableScissor();

		// 滚动条
		drawListScrollBar(context, L, listOx, openA);

		// 详情面板滑入
		int detailOx = Math.round((1f - UiAnim.easeOutCubic(Math.max(detailSlide, openA * 0.6f))) * 28f);
		float detA = openA * (0.4f + 0.6f * Math.max(detailSlide, 0.35f));
		context.fill(L.detailX - 4 + detailOx, L.detailY - 4,
				L.detailX + L.detailW + 4 + detailOx, L.detailY + L.detailH + 4,
				UiAnim.mulAlpha(0xCC14121A, detA));
		context.drawBorder(L.detailX - 4 + detailOx, L.detailY - 4, L.detailW + 8, L.detailH + 8,
				UiAnim.mulAlpha(0x55E8C878, detA));

		context.enableScissor(L.detailX + detailOx, L.detailY, L.detailX + L.detailW + detailOx, L.detailY + L.detailH);
		if (selected >= 0 && selected < rows.size()) {
			float selFade = UiAnim.easeOutCubic(MathHelper.clamp((now - selectMs) / 200f, 0f, 1f));
			Row row = rows.get(selected);
			int dx = L.detailX + 8 + detailOx;
			int dy = L.detailY + 10 + Math.round((1f - selFade) * 10f);
			int dw = L.detailW - 16;
			context.drawItem(row.display.getIcon(), dx, dy);
			context.drawItemInSlot(textRenderer, row.display.getIcon(), dx, dy);
			context.drawTextWithShadow(textRenderer,
					trimToWidth(row.display.getTitle().getString(), dw - 26),
					dx + 24, dy + 4, UiAnim.mulAlpha(COL_ACCENT, selFade));
			context.drawTextWithShadow(textRenderer,
					trimToWidth(row.entry.id().toString(), dw),
					dx, dy + 26, UiAnim.mulAlpha(0xFF807868, selFade));
			int ty = dy + 44;
			for (String line : wrap(row.display.getDescription().getString(), dw)) {
				if (ty > L.detailY + L.detailH - 48) {
					break;
				}
				context.drawTextWithShadow(textRenderer, line, dx, ty, UiAnim.mulAlpha(0xFFD8D0C8, selFade));
				ty += 13;
			}
			ty += 10;
			if (row.points > 0) {
				context.drawTextWithShadow(textRenderer,
						Text.translatable("gui.better_enchants.achievements.body_points", row.points),
						dx, ty, UiAnim.mulAlpha(COL_ACCENT, selFade));
			} else {
				context.drawTextWithShadow(textRenderer,
						Text.translatable("gui.better_enchants.achievements.no_body_points"),
						dx, ty, UiAnim.mulAlpha(0xFF807868, selFade));
			}
			ty += 16;
			context.drawTextWithShadow(textRenderer,
					row.done
							? Text.translatable("gui.better_enchants.achievements.status.done")
							: Text.translatable("gui.better_enchants.achievements.status.todo"),
					dx, ty, UiAnim.mulAlpha(row.done ? 0xFFC8E8A0 : COL_DIM, selFade));
		} else {
			context.drawTextWithShadow(textRenderer,
					Text.translatable("gui.better_enchants.achievements.pick"),
					L.detailX + 10 + detailOx, L.detailY + 14, UiAnim.mulAlpha(COL_DIM, detA));
		}
		context.disableScissor();

		context.drawTextWithShadow(textRenderer,
				Text.translatable("gui.better_enchants.achievements.count", rows.size()),
				PAD, height - 26, UiAnim.mulAlpha(COL_DIM, openA));

		renderClickFlash(context, openA);
		context.getMatrices().pop();

		float fade = 1f - openA;
		if (fade > 0.01f) {
			context.fill(0, 0, width, height, ((int) (fade * 200)) << 24);
		}
	}

	private void renderFilters(DrawContext context, int mouseX, int mouseY, float alpha) {
		int bx = PAD;
		int by = 28;
		for (int i = 0; i < FILTER_IDS.length; i++) {
			boolean sel = FILTER_IDS[i].equals(filter);
			int w = 52 + (i == 3 ? 12 : 0);
			boolean hover = mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + 18;
			if (hover) {
				filterHover[i] = 1f;
			}
			float h = Math.max(filterHover[i], hover ? 1f : 0f);
			int lift = Math.round(h * 1.5f);
			context.fill(bx, by - lift, bx + w, by + 18 - lift,
					UiAnim.mulAlpha(sel ? 0x99E8C878 : (h > 0.2f ? 0x66505060 : 0x44302828), alpha));
			if (sel) {
				context.fill(bx, by + 16 - lift, bx + w, by + 18 - lift, COL_ACCENT);
			}
			drawCenteredScaled(context, FILTER_LABELS[i], bx + w / 2f, by + 5 - lift, 0.9f,
					UiAnim.mulAlpha(COL_TEXT, alpha));
			bx += w + 6;
		}
	}

	private void renderClose(DrawContext context, int mouseX, int mouseY, float alpha) {
		int cx = width - PAD - 72;
		int cy = height - 30;
		boolean hover = mouseX >= cx && mouseX < cx + 72 && mouseY >= cy && mouseY < cy + 20;
		if (hover) {
			closeHover = 1f;
		}
		float h = Math.max(closeHover, hover ? 1f : 0f);
		int lift = Math.round(h * 1.5f);
		context.fill(cx, cy - lift, cx + 72, cy + 20 - lift,
				UiAnim.mulAlpha(hover ? 0x88E8C878 : 0x44302828, alpha));
		context.drawBorder(cx, cy - lift, 72, 20, UiAnim.mulAlpha(hover ? COL_ACCENT : 0x55AAAAAA, alpha));
		drawCenteredScaled(context, "完成", cx + 36, cy + 6 - lift, 0.95f, UiAnim.mulAlpha(COL_TEXT, alpha));
	}

	private void drawListScrollBar(DrawContext context, Layout L, int ox, float alpha) {
		int maxScroll = Math.max(0, rows.size() * ROW_H - L.listH);
		if (maxScroll <= 2) {
			return;
		}
		int barX = L.listX + L.listW + ox - 3;
		context.fill(barX, L.listY, barX + 3, L.listY + L.listH, UiAnim.mulAlpha(0x33000000, alpha));
		float thumbH = MathHelper.clamp(L.listH * (L.listH / (float) (L.listH + maxScroll)), 28f, L.listH * 0.55f);
		float thumbY = L.listY + (L.listH - thumbH) * (scroll / Math.max(1f, maxScroll));
		context.fill(barX, Math.round(thumbY), barX + 3, Math.round(thumbY + thumbH),
				UiAnim.mulAlpha(0xAAE8C878, alpha));
	}

	private void renderClickFlash(DrawContext context, float alpha) {
		float t = (System.currentTimeMillis() - clickFlashMs) / 260f;
		if (t < 0f || t > 1f) {
			return;
		}
		float a = (1f - t) * 0.5f * alpha;
		int r = (int) (6 + t * 22);
		context.fill(clickFlashX - r, clickFlashY - r, clickFlashX + r, clickFlashY + r,
				UiAnim.mulAlpha(0xFFE8C878, a));
	}

	private void drawCenteredScaled(DrawContext context, String text, float cx, float y, float scale, int color) {
		context.getMatrices().push();
		float w = textRenderer.getWidth(text) * scale;
		context.getMatrices().translate(cx - w * 0.5f, y, 0);
		context.getMatrices().scale(scale, scale, 1f);
		context.drawTextWithShadow(textRenderer, text, 0, 0, color);
		context.getMatrices().pop();
	}

	private String trimToWidth(String text, int maxWidth) {
		if (textRenderer.getWidth(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		int budget = maxWidth - textRenderer.getWidth(ellipsis);
		if (budget <= 0) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			sb.append(text.charAt(i));
			if (textRenderer.getWidth(sb.toString()) > budget) {
				sb.setLength(Math.max(0, sb.length() - 1));
				break;
			}
		}
		return sb.append(ellipsis).toString();
	}

	private List<String> wrap(String text, int maxWidth) {
		List<String> out = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return out;
		}
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			line.append(text.charAt(i));
			if (textRenderer.getWidth(line.toString()) > maxWidth) {
				if (line.length() > 1) {
					out.add(line.substring(0, line.length() - 1));
					line = new StringBuilder().append(text.charAt(i));
				}
			}
		}
		if (!line.isEmpty()) {
			out.add(line.toString());
		}
		return out;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int bx = PAD;
		int by = 28;
		for (int i = 0; i < FILTER_IDS.length; i++) {
			int w = 52 + (i == 3 ? 12 : 0);
			if (mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + 18) {
				filter = FILTER_IDS[i];
				rebuildRows();
				pulse(bx + w / 2, by + 9);
				return true;
			}
			bx += w + 6;
		}
		int cx = width - PAD - 72;
		int cy = height - 30;
		if (mouseX >= cx && mouseX < cx + 72 && mouseY >= cy && mouseY < cy + 20) {
			pulse(cx + 36, cy + 10);
			close();
			return true;
		}
		Layout L = layout();
		if (mouseX >= L.listX && mouseX < L.listX + L.listW
				&& mouseY >= L.listY && mouseY < L.listY + L.listH) {
			int idx = (int) ((mouseY - L.listY + scroll) / ROW_H);
			if (idx >= 0 && idx < rows.size()) {
				selected = idx;
				selectMs = System.currentTimeMillis();
				detailSlide = 0.2f;
				pulse(L.listX + L.listW / 2, (int) mouseY);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int maxScroll = Math.max(0, rows.size() * ROW_H - layout().listH);
		scrollTarget = MathHelper.clamp(scrollTarget - (float) verticalAmount * 28f, 0f, maxScroll);
		return true;
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private record Layout(int listX, int listY, int listW, int listH, int detailX, int detailY, int detailW, int detailH) {
	}

	private record Row(AdvancementEntry entry, AdvancementDisplay display, boolean done, int points) {
	}
}
