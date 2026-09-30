package com.betterenchants.client.talent;

import com.betterenchants.talent.Constitution;
import com.betterenchants.talent.Talent;
import com.betterenchants.talent.TalentCatalog;
import com.betterenchants.talent.TalentPlayerData;
import com.betterenchants.talent.TalentRarity;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 测试自选：1 体质 + 6 天赋（{@code /yandere restart}）。
 */
@Environment(EnvType.CLIENT)
public final class TalentFreeSelectScreen extends Screen {
	private static final int BTN_W = 108;
	private static final int BTN_H = 16;
	private static final int GAP = 4;
	private static final int COLS = 5;

	private final Screen parent;
	private Constitution selectedConstitution;
	private final Set<Talent> selectedTalents = EnumSet.noneOf(Talent.class);
	private int talentScroll;
	/** 已点确认、等待服务端 Sync 后再关界面。 */
	private boolean waitingForApply;

	public TalentFreeSelectScreen(Screen parent) {
		super(Text.translatable("screen.better_enchants.talent_free_select"));
		this.parent = parent;
		this.selectedConstitution = TalentClientData.constitution();
		this.selectedTalents.addAll(TalentClientData.talents());
		while (this.selectedTalents.size() > TalentPlayerData.MAX_TALENTS) {
			Talent drop = this.selectedTalents.iterator().next();
			this.selectedTalents.remove(drop);
		}
	}

	public boolean shouldCloseAfterSync(boolean needsSelection) {
		return waitingForApply && !needsSelection;
	}

	@Override
	protected void init() {
		rebuild();
	}

	private void rebuild() {
		this.clearChildren();
		int cx = this.width / 2;
		int left = cx - (COLS * (BTN_W + GAP) - GAP) / 2;
		int y = 46;

		Constitution[] constitutions = Constitution.values();
		for (int i = 0; i < constitutions.length; i++) {
			Constitution c = constitutions[i];
			int col = i % COLS;
			int row = i / COLS;
			int x = left + col * (BTN_W + GAP);
			int by = y + row * (BTN_H + GAP);
			boolean on = c == selectedConstitution;
			this.addDrawableChild(ButtonWidget.builder(
							TalentCatalog.constitutionName(c).copy()
									.styled(s -> s.withColor(on ? 0xFFE8C878 : 0xFFC8C0B0)),
							b -> {
								selectedConstitution = c;
								rebuild();
							})
					.dimensions(x, by, BTN_W, BTN_H)
					.tooltip(Tooltip.of(TalentCatalog.constitutionDesc(c)))
					.build());
		}

		int constitutionRows = (constitutions.length + COLS - 1) / COLS;
		int talentTop = y + constitutionRows * (BTN_H + GAP) + 22;
		int bottomBar = this.height - 32;
		int visibleRows = Math.max(1, (bottomBar - talentTop - 8) / (BTN_H + GAP));
		Talent[] all = Talent.values();
		int totalRows = (all.length + COLS - 1) / COLS;
		int maxScroll = Math.max(0, totalRows - visibleRows);
		talentScroll = Math.min(Math.max(0, talentScroll), maxScroll);

		for (int row = 0; row < visibleRows; row++) {
			int dataRow = row + talentScroll;
			if (dataRow >= totalRows) {
				break;
			}
			for (int col = 0; col < COLS; col++) {
				int idx = dataRow * COLS + col;
				if (idx >= all.length) {
					break;
				}
				Talent t = all[idx];
				int x = left + col * (BTN_W + GAP);
				int by = talentTop + row * (BTN_H + GAP);
				boolean on = selectedTalents.contains(t);
				this.addDrawableChild(ButtonWidget.builder(
								TalentCatalog.talentNameColored(t),
								b -> toggleTalent(t))
						.dimensions(x, by, BTN_W, BTN_H)
						.tooltip(Tooltip.of(Text.empty()
								.append(TalentCatalog.rarityLabel(t.rarity()))
								.append(Text.literal("\n"))
								.append(TalentCatalog.talentDescStyled(t))))
						.build()).active = on || selectedTalents.size() < TalentPlayerData.MAX_TALENTS
						|| selectedTalents.contains(t);
				if (on) {
					// 已选：按钮保持可点以取消；颜色靠文本稀有度
				}
			}
		}

		boolean canConfirm = selectedConstitution != null
				&& selectedTalents.size() == TalentPlayerData.MAX_TALENTS;
		int btnY = this.height - 28;
		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("gui.better_enchants.talent_free_select.confirm"),
						b -> {
							if (!canConfirm) {
								return;
							}
							List<String> ids = new ArrayList<>(selectedTalents.size());
							for (Talent t : selectedTalents) {
								ids.add(t.id());
							}
							waitingForApply = true;
							TalentClientNetworking.submitFreeSelect(selectedConstitution.id(), ids);
						})
				.dimensions(cx - 160, btnY, 100, 20)
				.build()).active = canConfirm;

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("gui.better_enchants.talent_select.clear"),
						b -> {
							selectedTalents.clear();
							rebuild();
						})
				.dimensions(cx - 50, btnY, 100, 20)
				.build());

		this.addDrawableChild(ButtonWidget.builder(
						Text.translatable("gui.cancel"),
						b -> close())
				.dimensions(cx + 60, btnY, 100, 20)
				.build());
	}

	private void toggleTalent(Talent talent) {
		if (selectedTalents.contains(talent)) {
			selectedTalents.remove(talent);
			rebuild();
			return;
		}
		if (selectedTalents.size() >= TalentPlayerData.MAX_TALENTS) {
			return;
		}
		for (Talent existing : List.copyOf(selectedTalents)) {
			if (talent.conflictsWith(existing)) {
				selectedTalents.remove(existing);
			}
		}
		if (selectedTalents.size() >= TalentPlayerData.MAX_TALENTS) {
			return;
		}
		selectedTalents.add(talent);
		rebuild();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount != 0) {
			talentScroll -= (int) Math.signum(verticalAmount);
			rebuild();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.translatable("gui.better_enchants.talent_free_select.title"),
				this.width / 2,
				12,
				0xFFFFFF);
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.translatable("gui.better_enchants.talent_free_select.hint"),
				this.width / 2,
				26,
				0xFFB5A890);

		int constitutionRows = (Constitution.values().length + COLS - 1) / COLS;
		int talentLabelY = 46 + constitutionRows * (BTN_H + GAP) + 8;
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.translatable("gui.better_enchants.talent_free_select.constitution_section"),
				this.width / 2,
				36,
				0xFFE8C878);
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.translatable(
						"gui.better_enchants.talent_select.count",
						selectedTalents.size(),
						TalentPlayerData.MAX_TALENTS),
				this.width / 2,
				talentLabelY,
				0xFFE8DCC8);

		int lx = this.width / 2 - 150;
		int legendY = this.height - 48;
		for (TalentRarity r : TalentRarity.values()) {
			context.drawTextWithShadow(
					this.textRenderer,
					TalentCatalog.rarityLabel(r),
					lx,
					legendY,
					r.nameColor());
			lx += 52;
		}
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}
}
