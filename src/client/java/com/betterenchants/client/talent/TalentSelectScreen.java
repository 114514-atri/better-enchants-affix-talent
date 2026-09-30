package com.betterenchants.client.talent;

import com.betterenchants.talent.Talent;
import com.betterenchants.talent.TalentCatalog;
import com.betterenchants.talent.TalentRarity;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 随机天赋确认：最多 3 组（初始 + 2 次刷新），确认后锁定其中一组 6 天赋。
 * 名称按稀有度着色，描述粉白。
 */
@Environment(EnvType.CLIENT)
public final class TalentSelectScreen extends Screen {
	private static final int COL_W = 128;
	private static final int COL_GAP = 8;
	private static final int ROW_H = 18;
	private static final int LIST_TOP = 72;

	private final Screen parent;
	private final boolean mandatory;
	private int selectedRoll;

	public TalentSelectScreen(Screen parent, boolean mandatory) {
		super(Text.translatable("screen.better_enchants.talent_select"));
		this.parent = parent;
		this.mandatory = mandatory;
		this.selectedRoll = 0;
	}

	public void reloadFromData() {
		List<List<Talent>> rolls = TalentClientData.rolls();
		if (selectedRoll >= rolls.size()) {
			selectedRoll = Math.max(0, rolls.size() - 1);
		}
		rebuild();
	}

	@Override
	protected void init() {
		rebuild();
	}

	private void rebuild() {
		this.clearChildren();
		List<List<Talent>> rolls = TalentClientData.rolls();
		if (rolls.isEmpty()) {
			return;
		}
		if (selectedRoll >= rolls.size()) {
			selectedRoll = 0;
		}
		int cx = this.width / 2;
		int cols = rolls.size();
		int totalW = cols * COL_W + (cols - 1) * COL_GAP;
		int left = cx - totalW / 2;

		for (int i = 0; i < cols; i++) {
			int x = left + i * (COL_W + COL_GAP);
			int idx = i;
			boolean on = idx == selectedRoll;
			this.addDrawableChild(ButtonWidget.builder(
							Text.translatable("gui.better_enchants.talent_select.roll_tab", idx + 1)
									.copy().styled(s -> s.withColor(on ? 0xE8C878 : 0xC8C0B0)),
							b -> {
								selectedRoll = idx;
								rebuild();
							})
					.dimensions(x, 48, COL_W, 18)
					.build());
		}

		if (selectedRoll >= 0 && selectedRoll < rolls.size()) {
			List<Talent> roll = rolls.get(selectedRoll);
			int x = left + selectedRoll * (COL_W + COL_GAP);
			for (int i = 0; i < roll.size(); i++) {
				Talent t = roll.get(i);
				int y = LIST_TOP + i * ROW_H;
				this.addDrawableChild(ButtonWidget.builder(
								TalentCatalog.talentNameColored(t),
								b -> {
								})
						.dimensions(x, y, COL_W, ROW_H - 2)
						.tooltip(Tooltip.of(Text.empty()
								.append(TalentCatalog.rarityLabel(t.rarity()))
								.append(Text.literal("\n"))
								.append(TalentCatalog.talentDescStyled(t))))
						.build());
			}
		}

		int btnY = this.height - 28;
		boolean canRefresh = TalentClientData.refreshesRemaining() > 0 && rolls.size() < 3;
		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("gui.better_enchants.talent_select.refresh",
								TalentClientData.refreshesRemaining()),
						b -> TalentClientNetworking.refreshRoll())
				.dimensions(cx - 160, btnY, 100, 20)
				.build()).active = canRefresh;

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("gui.better_enchants.talent_select.confirm"),
						b -> {
							TalentClientNetworking.confirmRoll(selectedRoll);
							if (!this.mandatory && this.client != null) {
								this.client.setScreen(this.parent);
							}
						})
				.dimensions(cx - 50, btnY, 100, 20)
				.build()).active = !rolls.isEmpty();

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("gui.cancel"),
						b -> {
							if (!this.mandatory) {
								close();
							}
						})
				.dimensions(cx + 60, btnY, 100, 20)
				.build()).active = !this.mandatory;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.translatable("gui.better_enchants.talent_select.title"),
				this.width / 2,
				14,
				0xFFFFFF);
		if (TalentClientData.constitution() != null) {
			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.translatable(
							"gui.better_enchants.talent_select.constitution",
							TalentCatalog.constitutionName(TalentClientData.constitution())),
					this.width / 2,
					28,
					0xFFE8C878);
		}
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.translatable("gui.better_enchants.talent_select.roll_hint"),
				this.width / 2,
				38,
				0xFFB5A890);

		// 稀有度图例
		int legendY = this.mandatory ? this.height - 70 : this.height - 46;
		int lx = this.width / 2 - 150;
		for (TalentRarity r : TalentRarity.values()) {
			context.drawTextWithShadow(
					this.textRenderer,
					TalentCatalog.rarityLabel(r),
					lx,
					legendY,
					r.nameColor());
			lx += 52;
		}

		if (this.mandatory) {
			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.translatable("gui.better_enchants.talent_select.mandatory_hint"),
					this.width / 2,
					this.height - 52,
					0xFFB5A890);
		}
		if (TalentClientData.rolls().isEmpty()) {
			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.translatable("gui.better_enchants.talent_select.waiting"),
					this.width / 2,
					this.height / 2,
					0xFFB5A890);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !this.mandatory;
	}

	@Override
	public void close() {
		if (this.mandatory) {
			return;
		}
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}
}
