package com.betterenchants.talent;

import java.util.Locale;
import net.minecraft.util.math.random.Random;

/** 随机体质（每人仅 1，不可自选）。 */
public enum Constitution {
	LUNAR("lunar", "太阴玄体", 14),
	SOLAR("solar", "赤炎神体", 14),
	FROST("frost", "寒霜圣体", 12),
	PHOENIX("phoenix", "涅槃凤凰体", 8),
	SWORD_BODY("sword_body", "不灭剑心体", 10),
	EARTH("earth", "厚土磐体", 12),
	OCEAN("ocean", "沧澜水体", 12),
	VOID("void", "虚空劫体", 6),
	THUNDER("thunder", "混沌雷体", 6),
	HERB("herb", "百草灵体", 10),
	CHAOS_SOURCE("chaos_source", "混沌源体", 4),
	GODSLAYER_BODY("godslayer_body", "弑神魔体", 3),
	ETERNAL_SAINT("eternal_saint", "永恒圣体", 4),
	CHRONO_TRAVELER("chrono_traveler", "时空旅人体", 3),
	DRAGON_BLOOD("dragon_blood", "龙血霸体", 3);

	private final String id;
	private final String labelZh;
	private final int weight;

	Constitution(String id, String labelZh, int weight) {
		this.id = id;
		this.labelZh = labelZh;
		this.weight = weight;
	}

	public String id() {
		return id;
	}

	public String labelZh() {
		return labelZh;
	}

	public int weight() {
		return weight;
	}

	public static Constitution byId(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		String key = id.toLowerCase(Locale.ROOT);
		for (Constitution c : values()) {
			if (c.id.equals(key) || c.name().equalsIgnoreCase(key)) {
				return c;
			}
		}
		return null;
	}

	public static Constitution roll(Random random) {
		int total = 0;
		for (Constitution c : values()) {
			total += c.weight;
		}
		int r = random.nextInt(Math.max(1, total));
		int acc = 0;
		for (Constitution c : values()) {
			acc += c.weight;
			if (r < acc) {
				return c;
			}
		}
		return LUNAR;
	}
}
