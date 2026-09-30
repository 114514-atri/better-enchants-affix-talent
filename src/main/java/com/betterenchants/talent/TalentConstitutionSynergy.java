package com.betterenchants.talent;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 觉醒体质 ↔ 已选天赋主题联动：匹配时额外增伤 / 减伤。
 * 多项可叠加，总增伤 / 减伤分别封顶。
 */
public final class TalentConstitutionSynergy {
	private static final float OUT_CAP = 0.25f;
	private static final float IN_CAP = 0.20f;

	private record Link(Talent talent, Constitution constitution, float outBonus, float inReduce) {
	}

	private static final Link[] LINKS = {
			new Link(Talent.SWORD_HEART, Constitution.SWORD_BODY, 0.10f, 0.0f),
			new Link(Talent.NIGHT_WALKER, Constitution.LUNAR, 0.08f, 0.05f),
			new Link(Talent.ETERNAL_EMBER, Constitution.PHOENIX, 0.08f, 0.05f),
			new Link(Talent.ETERNAL_EMBER, Constitution.SOLAR, 0.06f, 0.03f),
			new Link(Talent.FROST_HEART, Constitution.FROST, 0.10f, 0.05f),
			new Link(Talent.THUNDER_MARK, Constitution.THUNDER, 0.10f, 0.0f),
			new Link(Talent.GODSLAYER, Constitution.GODSLAYER_BODY, 0.12f, 0.05f),
			new Link(Talent.TIME_SHARD, Constitution.CHRONO_TRAVELER, 0.08f, 0.0f),
			new Link(Talent.DIMENSION_WARP, Constitution.VOID, 0.08f, 0.05f),
			new Link(Talent.DIMENSION_WARP, Constitution.CHRONO_TRAVELER, 0.05f, 0.0f),
			new Link(Talent.ABSOLUTE_BASTION, Constitution.EARTH, 0.05f, 0.08f),
			new Link(Talent.ABSOLUTE_BASTION, Constitution.DRAGON_BLOOD, 0.05f, 0.08f),
			new Link(Talent.SHIELD_GUARD, Constitution.EARTH, 0.0f, 0.06f),
			new Link(Talent.BLOOD_RITE, Constitution.DRAGON_BLOOD, 0.08f, 0.0f),
			new Link(Talent.BLOOD_SURGE, Constitution.DRAGON_BLOOD, 0.08f, 0.0f),
			new Link(Talent.BLOOD_RITE, Constitution.PHOENIX, 0.06f, 0.0f),
			new Link(Talent.CLEAR_HEART, Constitution.ETERNAL_SAINT, 0.0f, 0.08f),
			new Link(Talent.UNDYING_WALL, Constitution.ETERNAL_SAINT, 0.0f, 0.06f),
			new Link(Talent.FOOD_SPIRIT, Constitution.HERB, 0.0f, 0.04f),
			new Link(Talent.GLUTTON, Constitution.HERB, 0.0f, 0.04f),
			new Link(Talent.IRON_STOMACH, Constitution.HERB, 0.0f, 0.03f),
			new Link(Talent.GALE_STEP, Constitution.OCEAN, 0.05f, 0.0f),
			new Link(Talent.LIGHT_FOOT, Constitution.OCEAN, 0.04f, 0.0f),
			new Link(Talent.SHADOW_STEP, Constitution.VOID, 0.06f, 0.04f),
			new Link(Talent.SHADOW_STEP, Constitution.LUNAR, 0.05f, 0.0f),
			new Link(Talent.VOID_ARROW, Constitution.VOID, 0.10f, 0.0f),
			new Link(Talent.LAW_UNITY, Constitution.CHAOS_SOURCE, 0.08f, 0.04f),
			new Link(Talent.OMNISCIENCE, Constitution.CHAOS_SOURCE, 0.08f, 0.04f),
			new Link(Talent.HUNT_INSTINCT, Constitution.SOLAR, 0.06f, 0.0f),
			new Link(Talent.WAR_BURN, Constitution.SOLAR, 0.06f, 0.0f),
			new Link(Talent.WAR_BURN, Constitution.THUNDER, 0.05f, 0.0f),
	};

	private TalentConstitutionSynergy() {
	}

	public static float modifyOutgoing(ServerPlayerEntity attacker, float amount) {
		if (amount <= 0.0f || attacker == null) {
			return amount;
		}
		float bonus = 0.0f;
		Constitution c = TalentPlayerData.getConstitution(attacker);
		if (c == null) {
			return amount;
		}
		for (Link link : LINKS) {
			if (link.constitution == c && link.outBonus > 0.0f
					&& TalentPlayerData.hasTalent(attacker, link.talent)) {
				bonus += link.outBonus;
			}
		}
		if (bonus <= 0.0f) {
			return amount;
		}
		return amount * (1.0f + Math.min(OUT_CAP, bonus));
	}

	public static float modifyIncoming(ServerPlayerEntity victim, float amount) {
		if (amount <= 0.0f || victim == null) {
			return amount;
		}
		float reduce = 0.0f;
		Constitution c = TalentPlayerData.getConstitution(victim);
		if (c == null) {
			return amount;
		}
		for (Link link : LINKS) {
			if (link.constitution == c && link.inReduce > 0.0f
					&& TalentPlayerData.hasTalent(victim, link.talent)) {
				reduce += link.inReduce;
			}
		}
		if (reduce <= 0.0f) {
			return amount;
		}
		return amount * (1.0f - Math.min(IN_CAP, reduce));
	}

	/** 当前玩家已激活的联动条数（UI）。 */
	public static int activeLinkCount(ServerPlayerEntity player) {
		if (player == null) {
			return 0;
		}
		Constitution c = TalentPlayerData.getConstitution(player);
		if (c == null) {
			return 0;
		}
		int n = 0;
		for (Link link : LINKS) {
			if (link.constitution == c && TalentPlayerData.hasTalent(player, link.talent)) {
				n++;
			}
		}
		return n;
	}

	/** 客户端：按本地缓存统计激活联动。 */
	public static int activeLinkCount(Constitution constitution, Iterable<Talent> talents) {
		if (constitution == null || talents == null) {
			return 0;
		}
		java.util.EnumSet<Talent> set = java.util.EnumSet.noneOf(Talent.class);
		for (Talent t : talents) {
			if (t != null) {
				set.add(t);
			}
		}
		int n = 0;
		for (Link link : LINKS) {
			if (link.constitution == constitution && set.contains(link.talent)) {
				n++;
			}
		}
		return n;
	}

	public static java.util.List<Talent> linkedTalents(Constitution constitution) {
		java.util.ArrayList<Talent> out = new java.util.ArrayList<>();
		if (constitution == null) {
			return out;
		}
		for (Link link : LINKS) {
			if (link.constitution == constitution && !out.contains(link.talent)) {
				out.add(link.talent);
			}
		}
		return out;
	}
}
