package com.betterenchants.talent;

import com.betterenchants.advancement.ModCriteria;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.advancement.criterion.TickCriterion;
import net.minecraft.util.Identifier;

/**
 * 有难度成就 → 体质点数。
 * 自定义成就走 {@link ModCriteria#trigger}；原版成就走进度完成 mixin。
 */
public final class BodyCultivationRewards {
	private static final Map<Identifier, Integer> BY_ADVANCEMENT = new HashMap<>();
	private static final Map<TickCriterion, Integer> BY_CRITERION = new HashMap<>();

	static {
		// —— 原版硬核 ——
		adv("minecraft:end/kill_dragon", 5);
		adv("minecraft:nether/summon_wither", 3);
		adv("minecraft:nether/create_full_beacon", 3);
		adv("minecraft:end/elytra", 2);
		adv("minecraft:adventure/kill_all_mobs", 5);
		adv("minecraft:adventure/adventuring_time", 4);
		adv("minecraft:husbandry/balanced_diet", 2);
		adv("minecraft:nether/all_effects", 4);
		adv("minecraft:adventure/hero_of_the_village", 2);
		adv("minecraft:end/respawn_dragon", 3);

		// —— 模组自定义（偏难） ——
		crit(ModCriteria.CURSE_MODE_PRO, 8);
		crit(ModCriteria.CURSE_MODE_HELL, 5);
		crit(ModCriteria.CURSE_MODE_HARD, 3);
		crit(ModCriteria.GOD_SLAYER, 4);
		crit(ModCriteria.DEATH_IMMUNE_SAVIOR, 3);
		crit(ModCriteria.SWORD_BLOCK_100, 3);
		crit(ModCriteria.ELECTROTHERAPY_MASTER, 2);
		crit(ModCriteria.BLACK_HOLE_KILL, 3);
		crit(ModCriteria.DIMENSION_SLASH_CAST, 2);
		crit(ModCriteria.SCARLET_ENTER, 2);
		crit(ModCriteria.CURSE_ALL_HELMET, 4);
		crit(ModCriteria.BLOOD_MOON_SURVIVE, 2);
		crit(ModCriteria.ONE_TRICK_MASTER, 3);
		crit(ModCriteria.ZENITH_ENCHANT, 3);
		crit(ModCriteria.VEIN_MINER_LEGEND, 2);
		crit(ModCriteria.GIANT_SWORD_KILL, 2);
		crit(ModCriteria.DIMENSIONAL_FOIL_XYZ, 3);
		crit(ModCriteria.SWORD_BEAM_360, 2);
		crit(ModCriteria.ETERNAL_SPEAR, 2);
		crit(ModCriteria.GOD_PUNISH, 2);
		crit(ModCriteria.CURSE_MODE_EASY, 1);
		crit(ModCriteria.SCARLET_VOID_RESCUE, 2);
		crit(ModCriteria.PARRY_PERFECT, 0); // 量产成就不给点；百次格挡另算
	}

	private BodyCultivationRewards() {
	}

	private static void adv(String id, int points) {
		if (points > 0) {
			BY_ADVANCEMENT.put(Identifier.of(id), points);
		}
	}

	private static void crit(TickCriterion criterion, int points) {
		if (criterion != null && points > 0) {
			BY_CRITERION.put(criterion, points);
		}
	}

	public static int pointsForAdvancement(Identifier id) {
		if (id == null) {
			return 0;
		}
		return BY_ADVANCEMENT.getOrDefault(id, 0);
	}

	public static int pointsForCriterion(TickCriterion criterion) {
		if (criterion == null) {
			return 0;
		}
		return BY_CRITERION.getOrDefault(criterion, 0);
	}
}
