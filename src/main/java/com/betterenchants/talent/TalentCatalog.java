package com.betterenchants.talent;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** 天赋 / 体质展示文案（lang 键 + 稀有度颜色）。 */
public final class TalentCatalog {
	private TalentCatalog() {
	}

	public static Text talentName(Talent talent) {
		return Text.translatable("talent.better_enchants." + talent.id() + ".name");
	}

	/** 名称按稀有度着色。 */
	public static MutableText talentNameColored(Talent talent) {
		return talentName(talent).copy().styled(s -> s.withColor(talent.rarity().nameColor()));
	}

	public static Text talentDesc(Talent talent) {
		return Text.translatable("talent.better_enchants." + talent.id() + ".desc");
	}

	/** 描述统一粉白。 */
	public static MutableText talentDescStyled(Talent talent) {
		return talentDesc(talent).copy().styled(s -> s.withColor(TalentRarity.DESC_COLOR));
	}

	public static MutableText talentRowLabel(Talent talent) {
		return Text.translatable("gui.better_enchants.talent_detail.talent_row", talentNameColored(talent))
				.styled(s -> s.withColor(0xE8DCC8));
	}

	public static Text constitutionName(Constitution constitution) {
		return Text.translatable("constitution.better_enchants." + constitution.id() + ".name");
	}

	public static Text constitutionDesc(Constitution constitution) {
		return Text.translatable("constitution.better_enchants." + constitution.id() + ".desc");
	}

	public static Text rarityLabel(TalentRarity rarity) {
		return Text.translatable("talent.better_enchants.rarity." + rarity.id())
				.styled(s -> s.withColor(rarity.nameColor()).withFormatting(Formatting.BOLD));
	}
}
