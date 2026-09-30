package com.betterenchants.talent;

/**
 * 觉醒体质 ↔ 体质加点联动：契合属性的加点效果按倍率结算。
 * 混沌源体为全体小幅加成。
 */
public final class ConstitutionBodySynergy {
	/** 契合属性效果倍率。 */
	public static final float FAVORED_MULT = 1.50f;
	/** 混沌源体全体倍率。 */
	public static final float CHAOS_ALL_MULT = 1.20f;

	private ConstitutionBodySynergy() {
	}

	public static float effectiveness(Constitution constitution, BodyCultivationStat stat) {
		if (constitution == null || stat == null) {
			return 1.0f;
		}
		if (constitution == Constitution.CHAOS_SOURCE) {
			return CHAOS_ALL_MULT;
		}
		for (BodyCultivationStat f : favoredStats(constitution)) {
			if (f == stat) {
				return FAVORED_MULT;
			}
		}
		return 1.0f;
	}

	public static BodyCultivationStat[] favoredStats(Constitution constitution) {
		if (constitution == null) {
			return new BodyCultivationStat[0];
		}
		return switch (constitution) {
			case LUNAR -> new BodyCultivationStat[]{BodyCultivationStat.SPELL, BodyCultivationStat.HEALTH};
			case SOLAR -> new BodyCultivationStat[]{BodyCultivationStat.POWER, BodyCultivationStat.HEALTH};
			case FROST -> new BodyCultivationStat[]{BodyCultivationStat.SPELL, BodyCultivationStat.BODY};
			case PHOENIX -> new BodyCultivationStat[]{BodyCultivationStat.HEALTH, BodyCultivationStat.POWER};
			case SWORD_BODY -> new BodyCultivationStat[]{BodyCultivationStat.POWER, BodyCultivationStat.SPEED};
			case EARTH -> new BodyCultivationStat[]{BodyCultivationStat.BODY, BodyCultivationStat.HEALTH};
			case OCEAN -> new BodyCultivationStat[]{BodyCultivationStat.SPEED, BodyCultivationStat.SPELL};
			case VOID -> new BodyCultivationStat[]{BodyCultivationStat.SPELL, BodyCultivationStat.SPEED};
			case THUNDER -> new BodyCultivationStat[]{BodyCultivationStat.POWER, BodyCultivationStat.SPEED};
			case HERB -> new BodyCultivationStat[]{BodyCultivationStat.HEALTH, BodyCultivationStat.SPELL};
			case CHAOS_SOURCE -> BodyCultivationStat.values();
			case GODSLAYER_BODY -> new BodyCultivationStat[]{BodyCultivationStat.POWER, BodyCultivationStat.BODY};
			case ETERNAL_SAINT -> new BodyCultivationStat[]{BodyCultivationStat.HEALTH, BodyCultivationStat.BODY};
			case CHRONO_TRAVELER -> new BodyCultivationStat[]{BodyCultivationStat.SPEED, BodyCultivationStat.SPELL};
			case DRAGON_BLOOD -> new BodyCultivationStat[]{BodyCultivationStat.POWER, BodyCultivationStat.BODY};
		};
	}

	/** 有效加点（显示/结算用）。 */
	public static float effectivePoints(Constitution constitution, BodyCultivationStat stat, int points) {
		if (points <= 0) {
			return 0.0f;
		}
		return points * effectiveness(constitution, stat);
	}

	public static boolean isFavored(Constitution constitution, BodyCultivationStat stat) {
		return effectiveness(constitution, stat) > 1.001f;
	}
}
