package com.betterenchants.challenge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * 词条挂在实体 Command Tag 上（随实体 NBT 持久化）。
 * 标签：{@code be_affix_rolled}、{@code be_affix_<id>}、{@code be_affix_lv_<1-10>}
 */
public final class MobAffixHolder {
	public static final String ROLLED_TAG = "be_affix_rolled";
	public static final String PREFIX = "be_affix_";
	public static final String LEVEL_PREFIX = "be_affix_lv_";

	private MobAffixHolder() {
	}

	public static boolean isPlayer(LivingEntity entity) {
		return entity instanceof PlayerEntity;
	}

	public static boolean hasRolled(LivingEntity entity) {
		return entity != null && entity.getCommandTags().contains(ROLLED_TAG);
	}

	public static void markRolled(LivingEntity entity) {
		if (entity != null) {
			entity.addCommandTag(ROLLED_TAG);
		}
	}

	public static Set<MobAffix> get(LivingEntity entity) {
		if (entity == null || isPlayer(entity)) {
			return EnumSet.noneOf(MobAffix.class);
		}
		EnumSet<MobAffix> set = EnumSet.noneOf(MobAffix.class);
		for (String tag : entity.getCommandTags()) {
			if (!tag.startsWith(PREFIX) || tag.equals(ROLLED_TAG) || tag.startsWith(LEVEL_PREFIX)) {
				continue;
			}
			MobAffix a = MobAffix.byId(tag.substring(PREFIX.length()));
			if (a != null) {
				set.add(a);
			}
		}
		return set;
	}

	public static boolean has(LivingEntity entity, MobAffix affix) {
		return entity != null && affix != null && entity.getCommandTags().contains(PREFIX + affix.id());
	}

	/** 该实体词条实例等级 1..10（未标记时按 1）。 */
	public static int getPowerLevel(LivingEntity entity) {
		if (entity == null) {
			return 1;
		}
		for (String tag : entity.getCommandTags()) {
			if (!tag.startsWith(LEVEL_PREFIX)) {
				continue;
			}
			try {
				int lv = Integer.parseInt(tag.substring(LEVEL_PREFIX.length()));
				return Math.max(MobAffix.GRADE_MIN, Math.min(MobAffix.GRADE_MAX, lv));
			} catch (NumberFormatException ignored) {
				return 1;
			}
		}
		return 1;
	}

	/** 词条效果强度倍率：Lv1≈0.55 … Lv10≈1.0。 */
	public static float powerScale(LivingEntity entity) {
		return powerScale(getPowerLevel(entity));
	}

	public static float powerScale(int powerLevel) {
		int lv = Math.max(MobAffix.GRADE_MIN, Math.min(MobAffix.GRADE_MAX, powerLevel));
		return 0.50f + lv * 0.05f;
	}

	public static void set(LivingEntity entity, Set<MobAffix> affixes) {
		set(entity, affixes, 1);
	}

	public static void set(LivingEntity entity, Set<MobAffix> affixes, int powerLevel) {
		if (entity == null || isPlayer(entity)) {
			return;
		}
		for (MobAffix existing : MobAffix.values()) {
			entity.removeCommandTag(PREFIX + existing.id());
		}
		for (int i = MobAffix.GRADE_MIN; i <= MobAffix.GRADE_MAX; i++) {
			entity.removeCommandTag(LEVEL_PREFIX + i);
		}
		entity.addCommandTag(ROLLED_TAG);
		int lv = Math.max(MobAffix.GRADE_MIN, Math.min(MobAffix.GRADE_MAX, powerLevel));
		entity.addCommandTag(LEVEL_PREFIX + lv);
		if (affixes != null) {
			for (MobAffix a : affixes) {
				entity.addCommandTag(PREFIX + a.id());
			}
		}
		com.betterenchants.network.MobAffixNetworking.sync(entity);
	}

	public static List<String> labels(LivingEntity entity) {
		List<String> out = new ArrayList<>();
		int lv = getPowerLevel(entity);
		for (MobAffix a : get(entity)) {
			out.add("§" + color(a.rarity()) + "[" + a.labelZh() + "·" + lv + "]§r");
		}
		Collections.sort(out);
		return out;
	}

	private static char color(MobAffixRarity r) {
		return switch (r) {
			case WHITE -> 'f';
			case GREEN -> 'a';
			case BLUE -> '9';
			case PURPLE -> 'd';
			case GOLD -> '6';
			case RED -> 'c';
			case RAINBOW -> 'b';
		};
	}
}
