package com.betterenchants.client.genshinui.character;

import com.betterenchants.accessory.Accessories;
import com.betterenchants.accessory.AccessorySlotLoader;
import com.betterenchants.accessory.PlayerAccessoryInventory;
import com.betterenchants.client.genshinui.achievement.BetterAchievementScreen;
import com.betterenchants.client.genshinui.config.GenshinUiClientConfig;
import com.betterenchants.client.genshinui.util.UiAnim;
import com.betterenchants.client.talent.TalentClientData;
import com.betterenchants.client.talent.TalentClientNetworking;
import com.betterenchants.network.CharacterEquipNetworking;
import com.betterenchants.talent.BodyCultivationMechanics;
import com.betterenchants.talent.BodyCultivationStat;
import com.betterenchants.talent.Constitution;
import com.betterenchants.talent.ConstitutionBodySynergy;
import com.betterenchants.talent.Talent;
import com.betterenchants.talent.TalentCatalog;
import com.betterenchants.talent.TalentConstitutionSynergy;
import com.betterenchants.talent.TalentRarity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * 仿原神角色界面（自灵音 NeoForge 版布局/渲染移植，数据接 better）。
 * 左侧竖排 Tab + 中央模型 + 右侧面板；不走原版 Screen 压暗背景。
 */
@Environment(EnvType.CLIENT)
public final class BetterCharacterScreen extends Screen {
	static final int TAB_ATTRIBUTES = 0;
	static final int TAB_WEAPON = 1;
	static final int TAB_EQUIP = 2;
	static final int TAB_ACCESSORY = 3;
	static final int TAB_TALENT = 4; // 体质/天赋/修炼合并入口（占原「天赋」位）
	static final int TAB_THEME = 5;

	private static final String[] TABS = {"属性", "手持", "装备", "饰品", "天赋", "主题"};
	private static final String[] TALENT_SUB = {"修炼", "天赋", "体质"};
	private static final String[] THEME_NAMES = {"默认", "水", "火", "雷", "冰", "风", "草", "岩"};
	private static final BodyCultivationStat[] STATS = BodyCultivationStat.values();

	private static final int COL_TEXT = 0xFFF2F0F2;
	private static final int COL_DIM = 0xFFB9B5B9;
	private static final int COL_ACCENT = 0xFFE8C878;
	private static final int SLOT = 22;

	private final Screen parent;
	private final CharacterModelView modelView = new CharacterModelView(this);

	int selectedTab = TAB_ATTRIBUTES;
	int talentSub; // 0修炼 1天赋 2体质
	float camYaw;
	float camPitch;
	float camZoom = 1.0f;
	private float targetYaw;
	private float targetPitch;
	private float targetZoom = 1.0f;
	boolean rotating;
	private double lastMx;
	private double lastMy;
	long openTimeMs;
	int modelCenterX;
	int modelGroundY;
	float modelScale;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int closeBtnX;
	private int closeBtnY;
	private int closeBtnR = 13;
	/** 平滑滚动：目标 / 当前 */
	private float panelScrollTarget;
	private float panelScroll;
	/** 本帧内容最大可滚距离（不依赖当前 scroll）。 */
	private float panelScrollMax;
	private float panelSlide; // 0→1 右侧面板滑入
	private float tabHighlightY; // 左侧选中指示条 Y
	private long tabSwitchMs;
	private long clickFlashMs;
	private int clickFlashX;
	private int clickFlashY;
	private float[] tabHover = new float[6];
	private float[] talentSubHover = new float[3];
	private float achBtnHover;
	private float closeHoverAnim;
	private int selectedArmorSlot = -1;
	private int selectedAccSlot = -1;
	private boolean offhandMode;
	private int pendingGridY = -1;
	private java.util.function.IntConsumer pendingGridConsumer;
	private final List<HoverLine> hoverLines = new ArrayList<>();
	private long lastFrameMs = System.currentTimeMillis();

	public BetterCharacterScreen(Screen parent) {
		super(Text.translatable("screen.better_enchants.character"));
		this.parent = parent;
	}

	public BetterCharacterScreen(Screen parent, int tab) {
		this(parent);
		this.selectedTab = MathHelper.clamp(tab, 0, TABS.length - 1);
	}

	public void reloadFromData() {
		// 即时从 TalentClientData 重绘即可
	}

	@Override
	protected void init() {
		super.init();
		openTimeMs = System.currentTimeMillis();
		tabSwitchMs = openTimeMs;
		panelSlide = 0f;
		panelScroll = 0f;
		panelScrollTarget = 0f;
		computeLayout();
		tabHighlightY = tabY(selectedTab);
		TalentClientNetworking.requestMaidSync();
	}

	/** 禁止原版压暗/模糊——那就是「字体蒙版」的主因。 */
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void renderInGameBackground(DrawContext context) {
	}

	private void computeLayout() {
		// 右侧面板：更大可读区域，左侧留给模型
		panelW = MathHelper.clamp((int) (width * 0.32f), 180, 280);
		panelX = width - panelW - 16;
		panelY = Math.max(48, (int) (height * 0.11f));
		panelH = Math.max(100, height - panelY - 20);
		modelCenterX = Math.max(90, (panelX - 24) / 2 + 20);
		modelGroundY = (int) (height * 0.82f);
		modelScale = (height * 0.62f) / 1.8f;
		closeBtnR = 12;
		closeBtnX = width - 14 - closeBtnR * 2;
		closeBtnY = 10;
	}

	private void tickAnim(float dt) {
		camYaw = UiAnim.damp(camYaw, targetYaw, 14f, dt);
		camPitch = UiAnim.damp(camPitch, targetPitch, 14f, dt);
		camZoom = UiAnim.damp(camZoom, targetZoom, 12f, dt);
		panelScroll = UiAnim.damp(panelScroll, panelScrollTarget, 16f, dt);
		panelSlide = UiAnim.damp(panelSlide, 1f, 8f, dt);
		tabHighlightY = UiAnim.damp(tabHighlightY, tabY(selectedTab), 18f, dt);
		closeHoverAnim = UiAnim.damp(closeHoverAnim, 0f, 10f, dt);
		achBtnHover = UiAnim.damp(achBtnHover, 0f, 12f, dt);
		for (int i = 0; i < tabHover.length; i++) {
			tabHover[i] = UiAnim.damp(tabHover[i], 0f, 14f, dt);
		}
		for (int i = 0; i < talentSubHover.length; i++) {
			talentSubHover[i] = UiAnim.damp(talentSubHover[i], 0f, 14f, dt);
		}
	}

	private void pulseClick(int x, int y) {
		clickFlashMs = System.currentTimeMillis();
		clickFlashX = x;
		clickFlashY = y;
	}

	private int scrollPx() {
		return Math.round(panelScroll);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		ClientPlayerEntity player = client != null ? client.player : null;
		if (player == null) {
			close();
			return;
		}
		long now = System.currentTimeMillis();
		float dt = MathHelper.clamp((now - lastFrameMs) / 1000f, 0.001f, 0.05f);
		lastFrameMs = now;
		computeLayout();
		tickAnim(dt);

		modelView.renderShaderBackground(context);

		float uiAlpha = UiAnim.easeOutCubic(CharacterModelView.elapsedSince(openTimeMs) / 280.0f);
		boolean showHeld = selectedTab == TAB_WEAPON || selectedTab == TAB_EQUIP;
		modelView.renderPlayerModel(context, player, showHeld);

		context.getMatrices().push();
		context.getMatrices().translate(0.0f, 0.0f, 100.0f);
		modelView.renderReflectionMist(context);
		context.getMatrices().pop();

		context.getMatrices().push();
		context.getMatrices().translate(0.0f, 0.0f, 200.0f);
		renderTopBar(context, mouseX, mouseY, uiAlpha, player);
		renderTabs(context, mouseX, mouseY, uiAlpha);
		renderRightPanel(context, player, mouseX, mouseY, uiAlpha);
		renderClickFlash(context, uiAlpha);
		context.getMatrices().pop();

		float fade = 1.0f - UiAnim.easeOutCubic(CharacterModelView.elapsedSince(openTimeMs) / 260.0f);
		if (fade > 0.0f) {
			context.getMatrices().push();
			context.getMatrices().translate(0.0f, 0.0f, 600.0f);
			context.fill(0, 0, width, height, ((int) (fade * 255.0f)) << 24);
			context.getMatrices().pop();
		}

		for (HoverLine line : hoverLines) {
			if (mouseX >= line.x && mouseX < line.x + line.w && mouseY >= line.y && mouseY < line.y + line.h
					&& line.tip != null && !line.tip.getString().isEmpty()) {
				context.drawTooltip(textRenderer, line.tip, mouseX, mouseY);
				break;
			}
		}
	}

	private void renderClickFlash(DrawContext context, float alpha) {
		float t = (System.currentTimeMillis() - clickFlashMs) / 280f;
		if (t < 0f || t > 1f) {
			return;
		}
		float a = (1f - t) * 0.55f * alpha;
		int r = (int) (8 + t * 28);
		context.fill(clickFlashX - r, clickFlashY - r, clickFlashX + r, clickFlashY + r,
				UiAnim.mulAlpha(0xFFE8C878, a));
	}

	private void renderTopBar(DrawContext context, int mouseX, int mouseY, float alpha, ClientPlayerEntity player) {
		String title = "冒险者 / " + player.getName().getString();
		drawScaled(context, title, 14, 14, 1.2f, UiAnim.mulAlpha(COL_ACCENT, alpha), true);

		boolean hoverClose = mouseX >= closeBtnX && mouseX < closeBtnX + closeBtnR * 2
				&& mouseY >= closeBtnY && mouseY < closeBtnY + closeBtnR * 2;
		if (hoverClose) {
			closeHoverAnim = 1f;
		}
		float ch = Math.max(closeHoverAnim, hoverClose ? 1f : 0f);
		int closePad = Math.round(ch * 2);
		context.fill(closeBtnX - closePad, closeBtnY - closePad,
				closeBtnX + closeBtnR * 2 + closePad, closeBtnY + closeBtnR * 2 + closePad,
				UiAnim.mulAlpha(hoverClose ? 0x77FFFFFF : 0x33000000, alpha));
		context.drawBorder(closeBtnX, closeBtnY, closeBtnR * 2, closeBtnR * 2,
				UiAnim.mulAlpha(hoverClose ? 0xFFFFFFFF : 0xFFAAAAAA, alpha));
		drawCenteredScaled(context, "✕", closeBtnX + closeBtnR, closeBtnY + closeBtnR - 4, 1.0f,
				UiAnim.mulAlpha(hoverClose ? 0xFFFFFFFF : COL_DIM, alpha));

		int ax = closeBtnX - 78;
		boolean ah = mouseX >= ax && mouseX < ax + 68 && mouseY >= closeBtnY && mouseY < closeBtnY + 22;
		if (ah) {
			achBtnHover = 1f;
		}
		float ahAnim = Math.max(achBtnHover, ah ? 1f : 0f);
		int lift = Math.round(ahAnim * 1.5f);
		context.fill(ax, closeBtnY - lift, ax + 68, closeBtnY + 22 - lift,
				UiAnim.mulAlpha(ah ? 0x66E8C878 : 0x33000000, alpha));
		context.drawBorder(ax, closeBtnY - lift, 68, 22,
				UiAnim.mulAlpha(ah ? 0xFFE8C878 : 0x55AAAAAA, alpha));
		drawCenteredScaled(context, "成就", ax + 34, closeBtnY + 6 - lift, 0.95f, UiAnim.mulAlpha(COL_TEXT, alpha));
	}

	private int tabStartY() {
		return (int) (height * 0.16f);
	}

	private int tabSpacing() {
		int available = ((int) (height * 0.88f)) - tabStartY();
		return Math.max(34, Math.min(50, available / Math.max(1, TABS.length - 1)));
	}

	private int tabY(int i) {
		return tabStartY() + i * tabSpacing();
	}

	private void renderTabs(DrawContext context, int mouseX, int mouseY, float alpha) {
		// 选中指示条（平滑跟随）
		int indY = Math.round(tabHighlightY);
		context.fill(6, indY - 6, 10, indY + 18, UiAnim.mulAlpha(COL_ACCENT, alpha * 0.9f));

		for (int i = 0; i < TABS.length; i++) {
			float stagger = UiAnim.easeOutCubic(
					(CharacterModelView.elapsedSince(openTimeMs) - i * 50.0f) / 300.0f);
			float a = alpha * stagger;
			if (a <= 0f) {
				continue;
			}
			int x = 28 - (int) ((1.0f - stagger) * 28.0f);
			int y = tabY(i);
			int hitH = tabSpacing() - 6;
			boolean selected = i == selectedTab;
			boolean hover = mouseX >= x - 8 && mouseX < x + 120
					&& mouseY >= y - 6 && mouseY < y - 6 + hitH + 12;
			if (hover) {
				tabHover[i] = 1f;
			}
			float hAnim = Math.max(tabHover[i], hover ? 1f : 0f);
			float slideX = selected ? 6f : hAnim * 4f;
			int drawX = x + Math.round(slideX);

			if (selected) {
				drawHGradient(context, drawX - 12, y - 8, drawX + 132, y + 20,
						UiAnim.mulAlpha(0x44FFFFFF, a), 0x00FFFFFF);
			} else if (hAnim > 0.05f) {
				drawHGradient(context, drawX - 12, y - 8, drawX + 120, y + 20,
						UiAnim.mulAlpha(0x22FFFFFF, a * hAnim), 0x00FFFFFF);
			}
			int diamondCol = selected ? COL_ACCENT : hover ? COL_TEXT : COL_DIM;
			drawScaled(context, "♦", drawX, y + 2, 0.85f, UiAnim.mulAlpha(diamondCol, a), false);
			float textScale = selected ? 1.38f : (hover ? 1.28f : 1.18f);
			int textCol = selected ? COL_TEXT : hover ? 0xFFDEDEDE : COL_DIM;
			drawScaled(context, TABS[i], drawX + 14, y, textScale, UiAnim.mulAlpha(textCol, a), selected);
		}
	}

	private void renderRightPanel(DrawContext context, ClientPlayerEntity player, int mouseX, int mouseY, float alpha) {
		hoverLines.clear();
		pendingGridY = -1;
		pendingGridConsumer = null;
		panelScrollMax = 0f;

		float slideT = UiAnim.easeOutCubic(panelSlide);
		float tabFade = UiAnim.easeOutCubic(MathHelper.clamp(
				(System.currentTimeMillis() - tabSwitchMs) / 220f, 0f, 1f));
		int offsetX = Math.round((1f - slideT) * 36f);
		float panelAlpha = alpha * slideT * (0.55f + 0.45f * tabFade);

		context.getMatrices().push();
		context.getMatrices().translate(offsetX, 0, 0);

		// 面板背景：左侧柔边
		context.fill(panelX - 4, panelY - 8, panelX + panelW + 8, panelY + panelH + 8,
				UiAnim.mulAlpha(0xCC12101A, panelAlpha));
		context.fill(panelX - 4, panelY - 8, panelX, panelY + panelH + 8,
				UiAnim.mulAlpha(0x55E8C878, panelAlpha * 0.7f));
		context.drawBorder(panelX - 4, panelY - 8, panelW + 12, panelH + 16,
				UiAnim.mulAlpha(0x44E8C878, panelAlpha));

		context.enableScissor(panelX - 2, panelY - 2, panelX + panelW + 4, panelY + panelH + 4);
		switch (selectedTab) {
			case TAB_ATTRIBUTES -> renderAttributes(context, player);
			case TAB_WEAPON -> renderWeapon(context, player, mouseX, mouseY);
			case TAB_EQUIP -> renderEquip(context, player, mouseX, mouseY);
			case TAB_ACCESSORY -> renderAccessory(context, player, mouseX, mouseY);
			case TAB_TALENT -> renderTalentHub(context, mouseX, mouseY);
			case TAB_THEME -> renderTheme(context, mouseX, mouseY);
			default -> {
			}
		}
		context.disableScissor();
		panelScrollTarget = MathHelper.clamp(panelScrollTarget, 0f, panelScrollMax);
		if (panelScroll > panelScrollMax) {
			panelScroll = panelScrollMax;
		}
		drawScrollBar(context, panelAlpha);
		context.getMatrices().pop();
	}

	private void drawScrollBar(DrawContext context, float alpha) {
		if (panelScrollMax <= 2f && panelScroll <= 2f) {
			return;
		}
		int barX = panelX + panelW + 2;
		int barH = panelH;
		context.fill(barX, panelY, barX + 3, panelY + barH, UiAnim.mulAlpha(0x33000000, alpha));
		float maxScroll = Math.max(1f, panelScrollMax);
		float thumbH = MathHelper.clamp(barH * (barH / (barH + maxScroll)), 24f, barH * 0.6f);
		float thumbY = panelY + (barH - thumbH) * (panelScroll / Math.max(1f, maxScroll));
		context.fill(barX, Math.round(thumbY), barX + 3, Math.round(thumbY + thumbH),
				UiAnim.mulAlpha(0xAAE8C878, alpha));
	}

	private void renderAttributes(DrawContext context, ClientPlayerEntity player) {
		int y = panelY + 10 - scrollPx();
		drawScaled(context, "角色属性", panelX + 4, y, 1.12f, COL_ACCENT, true);
		y += 22;
		y = attrLine(context, panelX + 4, y, "生命",
				String.format("%.1f / %.1f", player.getHealth(), player.getMaxHealth()));
		y = attrLine(context, panelX + 4, y, "护甲", String.valueOf(player.getArmor()));
		y = attrLine(context, panelX + 4, y, "攻击", fmt(player, EntityAttributes.GENERIC_ATTACK_DAMAGE));
		y = attrLine(context, panelX + 4, y, "攻速", fmt(player, EntityAttributes.GENERIC_ATTACK_SPEED));
		y = attrLine(context, panelX + 4, y, "移速", fmt(player, EntityAttributes.GENERIC_MOVEMENT_SPEED));
		y = attrLine(context, panelX + 4, y, "幸运", fmt(player, EntityAttributes.GENERIC_LUCK));
		y += 12;
		drawScaled(context, "体修摘要", panelX + 4, y, 1.05f, COL_ACCENT, true);
		y += 20;
		for (BodyCultivationStat stat : STATS) {
			int pts = TalentClientData.bodyStat(stat);
			float eff = ConstitutionBodySynergy.effectivePoints(TalentClientData.constitution(), stat, pts);
			y = attrLine(context, panelX + 4, y,
					Text.translatable("gui.better_enchants.body_cult.stat." + stat.id()).getString(),
					pts + " → " + Math.round(eff));
		}
		y += 10;
		attrLine(context, panelX + 4, y, "未分配", String.valueOf(TalentClientData.bodyUnspent()));
		bumpScrollMax(y + 24);
	}

	private void bumpScrollMax(int contentBottom) {
		// contentBottom 已含 -scrollPx()；换算成未滚动坐标系再算最大可滚距离
		int max = Math.max(0, contentBottom + scrollPx() - (panelY + panelH));
		if (max > panelScrollMax) {
			panelScrollMax = max;
		}
	}

	private String fmt(ClientPlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr) {
		var inst = player.getAttributeInstance(attr);
		return inst == null ? "-" : String.format("%.2f", inst.getValue());
	}

	private int attrLine(DrawContext context, int x, int y, String name, String value) {
		context.fill(x - 2, y - 2, x + panelW - 8, y + 13, 0x22000000);
		context.drawTextWithShadow(textRenderer, name, x, y, COL_DIM);
		int vw = textRenderer.getWidth(value);
		context.drawTextWithShadow(textRenderer, value, x + panelW - 16 - vw, y, COL_TEXT);
		return y + 16;
	}

	private void renderTalentHub(DrawContext context, int mouseX, int mouseY) {
		int y = panelY + 6;
		int chipW = Math.max(52, (panelW - 16) / TALENT_SUB.length);
		for (int i = 0; i < TALENT_SUB.length; i++) {
			int bx = panelX + 4 + i * (chipW + 4);
			boolean sel = talentSub == i;
			boolean hover = mouseX >= bx && mouseX < bx + chipW && mouseY >= y && mouseY < y + 18;
			if (hover) {
				talentSubHover[i] = 1f;
			}
			float h = Math.max(talentSubHover[i], hover ? 1f : 0f);
			int fill = sel ? 0x99E8C878 : (h > 0.2f ? 0x66505060 : 0x44404050);
			context.fill(bx, y, bx + chipW, y + 18, fill);
			if (sel) {
				context.fill(bx, y + 16, bx + chipW, y + 18, COL_ACCENT);
			}
			drawCenteredScaled(context, TALENT_SUB[i], bx + chipW / 2f, y + 5, 0.9f, COL_TEXT);
		}
		y = panelY + 32 - scrollPx();
		switch (talentSub) {
			case 0 -> renderCultivate(context, y, mouseX, mouseY);
			case 1 -> renderTalents(context, y);
			case 2 -> renderConstitution(context, y);
			default -> {
			}
		}
		if (talentSub == 0) {
			final int footerH = 28;
			int footerTop = panelY + panelH - footerH;
			int py = panelY + 56 - scrollPx();
			for (int i = 0; i < STATS.length; i++) {
				int by = py + i * 28;
				if (by + 20 > footerTop) {
					continue;
				}
				boolean hover = mouseX >= panelX + panelW - 30 && mouseX < panelX + panelW - 6
						&& mouseY >= by && mouseY < by + 20;
				context.fill(panelX + panelW - 30, by, panelX + panelW - 6, by + 20,
						hover ? 0x88E8C878 : 0x66404050);
				drawCenteredScaled(context, "+", panelX + panelW - 18, by + 6, 1.05f,
						hover ? 0xFFFFFFFF : COL_ACCENT);
			}
			boolean rh = mouseX >= panelX + 4 && mouseX < panelX + 108
					&& mouseY >= footerTop + 2 && mouseY < panelY + panelH - 4;
			context.fill(panelX + 4, footerTop + 2, panelX + 108, panelY + panelH - 4,
					rh ? 0x88E8C878 : 0x66404050);
			int cost = BodyCultivationMechanics.respecLevelCost(TalentClientData.bodyRespecCount());
			String label = cost <= 0 ? "免费洗点" : ("洗点 " + cost + "级");
			drawCenteredScaled(context, label, panelX + 56, footerTop + 8, 0.85f, COL_TEXT);
		}
	}

	private void renderCultivate(DrawContext context, int y0, int mouseX, int mouseY) {
		int y = y0;
		context.drawTextWithShadow(textRenderer, Text.translatable(
				"gui.better_enchants.body_cult.points",
				TalentClientData.bodyUnspent(),
				TalentClientData.bodyTotalPoints()), panelX + 4, y, COL_ACCENT);
		y += 16;
		Constitution c = TalentClientData.constitution();
		if (c != null) {
			context.drawTextWithShadow(textRenderer, Text.translatable(
					"gui.better_enchants.talent_hub.synergy_line",
					TalentCatalog.constitutionName(c)), panelX + 4, y, COL_DIM);
		}
		y = panelY + 56 - scrollPx();
		final int footerH = 28;
		int footerTop = panelY + panelH - footerH;
		for (BodyCultivationStat stat : STATS) {
			int points = TalentClientData.bodyStat(stat);
			float eff = ConstitutionBodySynergy.effectivePoints(c, stat, points);
			boolean favored = ConstitutionBodySynergy.isFavored(c, stat);
			String cap = stat.hasCap() ? "/" + stat.maxPoints() : "";
			if (y + 22 <= footerTop) {
				context.fill(panelX + 2, y - 2, panelX + panelW - 34, y + 22, 0x33181018);
				context.drawTextWithShadow(textRenderer,
						Text.translatable("gui.better_enchants.body_cult.stat." + stat.id())
								.append(favored ? Text.literal(" ★") : Text.empty()),
						panelX + 6, y + 2, favored ? COL_ACCENT : COL_TEXT);
				context.drawTextWithShadow(textRenderer, points + cap, panelX + 6, y + 12, 0xFFC8E8A0);
				context.drawTextWithShadow(textRenderer, effectSummary(stat, eff), panelX + 56, y + 12, COL_DIM);
			}
			y += 28;
		}
		// 预留下方洗点按钮区域，避免滚动内容叠上去
		bumpScrollMax(y + footerH + 8);
	}

	private void renderTalents(DrawContext context, int y0) {
		int y = y0;
		if (TalentClientData.talents().isEmpty()) {
			context.drawTextWithShadow(textRenderer,
					Text.translatable("gui.better_enchants.talent_detail.no_talents"), panelX + 4, y, COL_TEXT);
			return;
		}
		Constitution c = TalentClientData.constitution();
		for (Talent t : TalentClientData.talents()) {
			boolean linked = c != null && TalentConstitutionSynergy.linkedTalents(c).contains(t);
			MutableText label = Text.empty().append(TalentCatalog.talentNameColored(t))
					.append(Text.literal(" · ")).append(TalentCatalog.rarityLabel(t.rarity()));
			if (linked) {
				label.append(Text.translatable("gui.better_enchants.talent_hub.linked_mark"));
			}
			context.fill(panelX + 2, y - 2, panelX + panelW - 4, y + 16, 0x5528221C);
			context.drawTextWithShadow(textRenderer, label, panelX + 6, y + 2, t.rarity().nameColor());
			hoverLines.add(new HoverLine(panelX, y, panelW, 16, TalentCatalog.talentDescStyled(t)));
			y += 20;
		}
		bumpScrollMax(y + 16);
	}

	private void renderConstitution(DrawContext context, int y0) {
		int y = y0;
		Constitution c = TalentClientData.constitution();
		if (c == null) {
			context.drawTextWithShadow(textRenderer,
					Text.translatable("gui.better_enchants.talent_detail.no_constitution"), panelX + 4, y, COL_TEXT);
			return;
		}
		Text title = Text.translatable("gui.better_enchants.talent_detail.constitution_row",
				TalentCatalog.constitutionName(c));
		context.drawTextWithShadow(textRenderer, title, panelX + 4, y, COL_ACCENT);
		hoverLines.add(new HoverLine(panelX, y, panelW, 14, TalentCatalog.constitutionDesc(c)));
		y += 18;
		for (String line : wrap(TalentCatalog.constitutionDesc(c).getString(), panelW - 16)) {
			context.drawTextWithShadow(textRenderer, line, panelX + 4, y, TalentRarity.DESC_COLOR);
			y += 13;
		}
		bumpScrollMax(y + 16);
	}

	private static Text effectSummary(BodyCultivationStat stat, float effectivePoints) {
		int rounded = Math.round(effectivePoints);
		return switch (stat) {
			case POWER -> {
				int dmgPct = Math.round(BodyCultivationMechanics.powerBonusPct(effectivePoints) * 100.0f);
				yield Text.translatable("gui.better_enchants.body_cult.effect.power", dmgPct);
			}
			case HEALTH -> {
				int hpPct = (int) Math.round(BodyCultivationMechanics.healthBonusPct(effectivePoints) * 100.0);
				yield Text.translatable("gui.better_enchants.body_cult.effect.health", hpPct);
			}
			case SPEED -> Text.translatable("gui.better_enchants.body_cult.effect.speed", rounded);
			case BODY -> {
				int dr = Math.min(75, rounded);
				int armorPct = (int) Math.round(BodyCultivationMechanics.armorBonusPct(effectivePoints) * 100.0);
				yield Text.translatable("gui.better_enchants.body_cult.effect.body", dr, armorPct);
			}
			case SPELL -> {
				int dr = Math.min(75, rounded);
				int manaPct = (int) Math.round(BodyCultivationMechanics.manaBonusPct(effectivePoints) * 100.0);
				yield Text.translatable("gui.better_enchants.body_cult.effect.spell", dr, manaPct);
			}
		};
	}

	private void renderWeapon(DrawContext context, ClientPlayerEntity player, int mx, int my) {
		int y = panelY + 8 - scrollPx();
		drawScaled(context, offhandMode ? "副手" : "主手", panelX + 4, y, 1.05f, COL_ACCENT, true);
		int toggleX = panelX + panelW - 72;
		boolean th = mx >= toggleX && mx < toggleX + 64 && my >= y - 2 && my < y + 16;
		context.fill(toggleX, y - 2, toggleX + 64, y + 16, th ? 0x88E8C878 : 0x55404050);
		drawCenteredScaled(context, "主/副手", toggleX + 32, y + 3, 0.85f, COL_TEXT);
		y += 22;
		ItemStack held = offhandMode ? player.getOffHandStack() : player.getMainHandStack();
		context.fill(panelX + 2, y - 2, panelX + panelW - 4, y + 22, 0x33181018);
		context.drawItem(held, panelX + 6, y);
		context.drawItemInSlot(textRenderer, held, panelX + 6, y);
		context.drawTextWithShadow(textRenderer,
				held.isEmpty() ? Text.literal("空") : held.getName(), panelX + 28, y + 6, COL_TEXT);
		y += 28;
		context.drawTextWithShadow(textRenderer, "点击背包物品切换", panelX + 4, y, COL_DIM);
		y += 16;
		drawInventoryGrid(context, player, panelX + 4, y, mx, my, slot -> {
			if (ClientPlayNetworking.canSend(CharacterEquipNetworking.ReplaceHandPayload.ID)) {
				ClientPlayNetworking.send(new CharacterEquipNetworking.ReplaceHandPayload(slot, offhandMode));
			}
		});
		bumpScrollMax(y + 4 * (SLOT + 2) + 40);
	}

	private void renderEquip(DrawContext context, ClientPlayerEntity player, int mx, int my) {
		int y = panelY + 8 - scrollPx();
		context.drawTextWithShadow(textRenderer, "选择部位后点背包；右键卸下", panelX + 4, y, COL_DIM);
		y += 18;
		String[] labels = {"头", "胸", "腿", "脚"};
		int gap = Math.max(8, (panelW - 16 - 4 * SLOT) / 3);
		for (int i = 0; i < 4; i++) {
			ItemStack stack = player.getInventory().armor.get(3 - i);
			int sx = panelX + 6 + i * (SLOT + gap);
			boolean sel = selectedArmorSlot == i;
			boolean hover = mx >= sx && mx < sx + SLOT && my >= y && my < y + SLOT;
			context.fill(sx - 2, y - 2, sx + SLOT + 2, y + SLOT + 2,
					sel ? 0xAAE8C878 : (hover ? 0x88505060 : 0x66404050));
			context.drawItem(stack, sx + 3, y + 3);
			context.drawItemInSlot(textRenderer, stack, sx + 3, y + 3);
			context.drawTextWithShadow(textRenderer, labels[i], sx + 4, y + SLOT + 3, COL_DIM);
		}
		y += SLOT + 22;
		if (selectedArmorSlot >= 0) {
			final int armorIdx = selectedArmorSlot;
			drawInventoryGrid(context, player, panelX + 4, y, mx, my, slot -> {
				ItemStack stack = player.getInventory().main.get(slot);
				if (!(stack.getItem() instanceof ArmorItem armor) || armorTypeIndex(armor) != armorIdx) {
					return;
				}
				if (ClientPlayNetworking.canSend(CharacterEquipNetworking.ReplaceArmorPayload.ID)) {
					ClientPlayNetworking.send(new CharacterEquipNetworking.ReplaceArmorPayload(armorIdx, slot));
				}
			});
			bumpScrollMax(y + 4 * (SLOT + 2) + 40);
		}
	}

	private static int armorTypeIndex(ArmorItem armor) {
		return switch (armor.getType()) {
			case HELMET -> 0;
			case CHESTPLATE -> 1;
			case LEGGINGS -> 2;
			case BOOTS -> 3;
			default -> -1;
		};
	}

	private void renderAccessory(DrawContext context, ClientPlayerEntity player, int mx, int my) {
		PlayerAccessoryInventory acc = Accessories.get(player);
		int y = panelY + 8 - scrollPx();
		context.drawTextWithShadow(textRenderer, "饰品槽；右键卸下", panelX + 4, y, COL_DIM);
		y += 18;
		int cols = Math.max(1, (panelW - 8) / (SLOT + 6));
		int count = Math.min(acc.size(), Math.max(1, AccessorySlotLoader.slotCount()));
		for (int i = 0; i < count; i++) {
			int cx = panelX + 6 + (i % cols) * (SLOT + 6);
			int cy = y + (i / cols) * (SLOT + 16);
			boolean sel = selectedAccSlot == i;
			boolean hover = mx >= cx && mx < cx + SLOT && my >= cy && my < cy + SLOT;
			context.fill(cx - 2, cy - 2, cx + SLOT + 2, cy + SLOT + 2,
					sel ? 0xAAE8C878 : (hover ? 0x88505060 : 0x66404050));
			ItemStack stack = acc.getStack(i);
			context.drawItem(stack, cx + 3, cy + 3);
			context.drawItemInSlot(textRenderer, stack, cx + 3, cy + 3);
		}
		int gridY = y + ((count + cols - 1) / cols) * (SLOT + 16) + 10;
		if (selectedAccSlot >= 0) {
			final int accIdx = selectedAccSlot;
			drawInventoryGrid(context, player, panelX + 4, gridY, mx, my, slot -> {
				ItemStack stack = player.getInventory().main.get(slot);
				if (!PlayerAccessoryInventory.canAccept(accIdx, stack)) {
					return;
				}
				if (ClientPlayNetworking.canSend(CharacterEquipNetworking.ReplaceAccessoryPayload.ID)) {
					ClientPlayNetworking.send(new CharacterEquipNetworking.ReplaceAccessoryPayload(accIdx, slot));
				}
			});
			bumpScrollMax(gridY + 4 * (SLOT + 2) + 40);
		} else {
			bumpScrollMax(gridY + 20);
		}
	}

	private void renderTheme(DrawContext context, int mx, int my) {
		int y = panelY + 10 - scrollPx();
		context.drawTextWithShadow(textRenderer, "背景元素主题", panelX + 4, y, COL_DIM);
		y += 20;
		for (int i = 0; i < THEME_NAMES.length; i++) {
			boolean sel = GenshinUiClientConfig.themeIndex == i;
			boolean hover = mx >= panelX + 4 && mx < panelX + panelW - 4 && my >= y && my < y + 20;
			context.fill(panelX + 4, y, panelX + panelW - 4, y + 20,
					sel ? 0x99E8C878 : (hover ? 0x66505060 : 0x44404050));
			if (sel) {
				context.fill(panelX + 4, y, panelX + 8, y + 20, COL_ACCENT);
			}
			context.drawTextWithShadow(textRenderer, THEME_NAMES[i], panelX + 14, y + 6, COL_TEXT);
			y += 24;
		}
		bumpScrollMax(y + 16);
	}

	private void drawInventoryGrid(
			DrawContext context,
			ClientPlayerEntity player,
			int px,
			int y,
			int mx,
			int my,
			java.util.function.IntConsumer onClick
	) {
		PlayerInventory inv = player.getInventory();
		int cols = 9;
		for (int i = 0; i < inv.main.size(); i++) {
			int cx = px + (i % cols) * (SLOT + 2);
			int cy = y + (i / cols) * (SLOT + 2);
			// grid y already accounts for scroll via caller; don't double-subtract unless y wasn't scrolled
			ItemStack stack = inv.main.get(i);
			boolean hover = mx >= cx && mx < cx + SLOT && my >= cy && my < cy + SLOT;
			context.fill(cx, cy, cx + SLOT, cy + SLOT, hover ? 0x88505060 : 0x66404050);
			if (!stack.isEmpty()) {
				context.drawItem(stack, cx + 3, cy + 3);
				context.drawItemInSlot(textRenderer, stack, cx + 3, cy + 3);
			}
			if (hover) {
				context.fill(cx, cy, cx + SLOT, cy + SLOT, 0x33FFFFFF);
			}
		}
		pendingGridY = y;
		pendingGridConsumer = onClick;
	}

	private void drawScaled(DrawContext context, String text, float x, float y, float scale, int color, boolean shadow) {
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0);
		context.getMatrices().scale(scale, scale, 1f);
		if (shadow) {
			context.drawTextWithShadow(textRenderer, text, 0, 0, color);
		} else {
			context.drawText(textRenderer, text, 0, 0, color, false);
		}
		context.getMatrices().pop();
	}

	private void drawCenteredScaled(DrawContext context, String text, float cx, float y, float scale, int color) {
		float w = textRenderer.getWidth(text) * scale;
		drawScaled(context, text, cx - w * 0.5f, y, scale, color, true);
	}

	private void drawHGradient(DrawContext context, int x1, int y1, int x2, int y2, int left, int right) {
		context.fillGradient(x1, y1, x2, y2, left, right);
	}

	private static int mulAlpha(int argb, float alpha) {
		return UiAnim.mulAlpha(argb, alpha);
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

	private void selectTab(int tab) {
		if (selectedTab == tab) {
			return;
		}
		selectedTab = tab;
		panelScroll = 0f;
		panelScrollTarget = 0f;
		panelSlide = 0.35f;
		tabSwitchMs = System.currentTimeMillis();
		selectedArmorSlot = -1;
		selectedAccSlot = -1;
		pulseClick(40, tabY(tab) + 6);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX >= closeBtnX && mouseX < closeBtnX + closeBtnR * 2
				&& mouseY >= closeBtnY && mouseY < closeBtnY + closeBtnR * 2) {
			pulseClick(closeBtnX + closeBtnR, closeBtnY + closeBtnR);
			close();
			return true;
		}
		int ax = closeBtnX - 78;
		if (mouseX >= ax && mouseX < ax + 68 && mouseY >= closeBtnY && mouseY < closeBtnY + 22) {
			pulseClick(ax + 34, closeBtnY + 11);
			client.setScreen(new BetterAchievementScreen(this));
			return true;
		}
		for (int i = 0; i < TABS.length; i++) {
			int x = 28;
			int y = tabY(i);
			int hitH = tabSpacing() - 6;
			if (mouseX >= x - 8 && mouseX < x + 120 && mouseY >= y - 6 && mouseY < y - 6 + hitH + 12) {
				selectTab(i);
				return true;
			}
		}
		if (selectedTab == TAB_TALENT) {
			int chipW = Math.max(52, (panelW - 16) / TALENT_SUB.length);
			for (int i = 0; i < TALENT_SUB.length; i++) {
				int bx = panelX + 4 + i * (chipW + 4);
				int by = panelY + 6;
				if (mouseX >= bx && mouseX < bx + chipW && mouseY >= by && mouseY < by + 18) {
					talentSub = i;
					panelScrollTarget = 0f;
					panelScroll = 0f;
					tabSwitchMs = System.currentTimeMillis();
					if (i == 0) {
						TalentClientNetworking.requestMaidSync();
					}
					pulseClick(bx + chipW / 2, by + 9);
					return true;
				}
			}
			if (talentSub == 0) {
				final int footerH = 28;
				int footerTop = panelY + panelH - footerH;
				int py = panelY + 56 - scrollPx();
				for (int i = 0; i < STATS.length; i++) {
					int by = py + i * 28;
					if (by + 20 > footerTop) {
						continue;
					}
					if (mouseX >= panelX + panelW - 30 && mouseX < panelX + panelW - 6
							&& mouseY >= by && mouseY < by + 20) {
						TalentClientNetworking.allocateBody(STATS[i], 1);
						pulseClick(panelX + panelW - 18, by + 10);
						return true;
					}
				}
				if (mouseX >= panelX + 4 && mouseX < panelX + 108
						&& mouseY >= footerTop + 2 && mouseY < panelY + panelH - 4) {
					TalentClientNetworking.respecBody();
					pulseClick(panelX + 56, footerTop + 12);
					return true;
				}
			}
		}
		if (selectedTab == TAB_WEAPON) {
			int toggleX = panelX + panelW - 72;
			int y = panelY + 8 - scrollPx();
			if (mouseX >= toggleX && mouseX < toggleX + 64 && mouseY >= y - 2 && mouseY < y + 16) {
				offhandMode = !offhandMode;
				pulseClick(toggleX + 32, y + 7);
				return true;
			}
		}
		if (selectedTab == TAB_EQUIP) {
			int y = panelY + 8 - scrollPx() + 18;
			int gap = Math.max(8, (panelW - 16 - 4 * SLOT) / 3);
			for (int i = 0; i < 4; i++) {
				int sx = panelX + 6 + i * (SLOT + gap);
				if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= y && mouseY < y + SLOT) {
					if (button == 1) {
						if (ClientPlayNetworking.canSend(CharacterEquipNetworking.ReplaceArmorPayload.ID)) {
							ClientPlayNetworking.send(new CharacterEquipNetworking.ReplaceArmorPayload(
									i, CharacterEquipNetworking.UNEQUIP));
						}
					} else {
						selectedArmorSlot = i;
					}
					pulseClick(sx + SLOT / 2, y + SLOT / 2);
					return true;
				}
			}
		}
		if (selectedTab == TAB_ACCESSORY && client != null && client.player != null) {
			PlayerAccessoryInventory acc = Accessories.get(client.player);
			int y = panelY + 8 - scrollPx() + 18;
			int cols = Math.max(1, (panelW - 8) / (SLOT + 6));
			int count = Math.min(acc.size(), Math.max(1, AccessorySlotLoader.slotCount()));
			for (int i = 0; i < count; i++) {
				int cx = panelX + 6 + (i % cols) * (SLOT + 6);
				int cy = y + (i / cols) * (SLOT + 16);
				if (mouseX >= cx && mouseX < cx + SLOT && mouseY >= cy && mouseY < cy + SLOT) {
					if (button == 1) {
						if (ClientPlayNetworking.canSend(CharacterEquipNetworking.ReplaceAccessoryPayload.ID)) {
							ClientPlayNetworking.send(new CharacterEquipNetworking.ReplaceAccessoryPayload(
									i, CharacterEquipNetworking.UNEQUIP));
						}
					} else {
						selectedAccSlot = i;
					}
					pulseClick(cx + SLOT / 2, cy + SLOT / 2);
					return true;
				}
			}
		}
		if (selectedTab == TAB_THEME) {
			int y = panelY + 10 - scrollPx() + 20;
			for (int i = 0; i < THEME_NAMES.length; i++) {
				if (mouseX >= panelX + 4 && mouseX < panelX + panelW - 4 && mouseY >= y && mouseY < y + 20) {
					GenshinUiClientConfig.setTheme(i);
					pulseClick(panelX + panelW / 2, y + 10);
					return true;
				}
				y += 24;
			}
		}
		if (pendingGridConsumer != null && pendingGridY >= 0 && client != null && client.player != null) {
			int cols = 9;
			int size = client.player.getInventory().main.size();
			int px = selectedTab == TAB_WEAPON || selectedTab == TAB_EQUIP || selectedTab == TAB_ACCESSORY
					? panelX + 4 : panelX;
			for (int i = 0; i < size; i++) {
				int cx = px + (i % cols) * (SLOT + 2);
				int cy = pendingGridY + (i / cols) * (SLOT + 2);
				if (mouseX >= cx && mouseX < cx + SLOT && mouseY >= cy && mouseY < cy + SLOT) {
					pendingGridConsumer.accept(i);
					pulseClick(cx + SLOT / 2, cy + SLOT / 2);
					return true;
				}
			}
		}
		if (button == 0 && mouseX < panelX - 8) {
			rotating = true;
			lastMx = mouseX;
			lastMy = mouseY;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		rotating = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (rotating) {
			targetYaw += (float) (mouseX - lastMx) * 0.45f;
			targetPitch = MathHelper.clamp(targetPitch + (float) (mouseY - lastMy) * 0.35f, -35f, 55f);
			lastMx = mouseX;
			lastMy = mouseY;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (mouseX < panelX - 8) {
			targetZoom = MathHelper.clamp(targetZoom - (float) verticalAmount * 0.12f, 0.55f, 1.85f);
			return true;
		}
		// 惯性目标滚动：滚轮一次约 28px，平滑插值跟随
		panelScrollTarget = MathHelper.clamp(
				panelScrollTarget - (float) verticalAmount * 28f, 0f, Math.max(panelScrollMax, 0f));
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_K) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
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

	private record HoverLine(int x, int y, int w, int h, Text tip) {
	}
}
