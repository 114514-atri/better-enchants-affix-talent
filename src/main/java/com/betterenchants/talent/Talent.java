package com.betterenchants.talent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.math.random.Random;

/** 玩家随机天赋（每组 6 个）；带稀有度颜色。 */
public enum Talent {
	/* —— 原有 —— */
	FOOD_SPIRIT("food_spirit", "万物食灵", TalentRarity.WHITE),
	SWORD_HEART("sword_heart", "剑心", TalentRarity.BLUE),
	ARROW_RAIN("arrow_rain", "箭雨心法", TalentRarity.GREEN),
	ORE_SENSE("ore_sense", "矿脉感应", TalentRarity.WHITE),
	BURDEN_WALKER("burden_walker", "负重行者", TalentRarity.WHITE),
	SHADOW_STEP("shadow_step", "影步", TalentRarity.BLUE),
	BLOOD_RITE("blood_rite", "血祭", TalentRarity.PURPLE),
	CLEAR_HEART("clear_heart", "清心", TalentRarity.GREEN),
	CRAFTSMAN("craftsman", "匠造", TalentRarity.WHITE),
	ABYSS_RESIST("abyss_resist", "深渊抗性", TalentRarity.GREEN),
	BEAST_PACT("beast_pact", "万灵契约", TalentRarity.GREEN),
	TIME_SHARD("time_shard", "时停碎片", TalentRarity.PURPLE),
	GODSLAYER("godslayer", "弑神一心", TalentRarity.GOLD),
	ETERNAL_EMBER("eternal_ember", "永恒余烬", TalentRarity.GOLD),
	ABSOLUTE_BASTION("absolute_bastion", "绝对壁垒", TalentRarity.PURPLE),
	HUNT_INSTINCT("hunt_instinct", "猎杀本能", TalentRarity.BLUE),
	VAULT_SIGHT("vault_sight", "宝库窥视", TalentRarity.GREEN),
	DIMENSION_WARP("dimension_warp", "次元折跃", TalentRarity.BLUE),
	WAR_BURN("war_burn", "战意燃烧", TalentRarity.PURPLE),
	LAW_UNITY("law_unity", "万法归一", TalentRarity.BLUE),

	/* —— 新增 20 —— */
	NIGHT_WALKER("night_walker", "夜行者", TalentRarity.WHITE),
	HARVEST_HAND("harvest_hand", "丰收之手", TalentRarity.WHITE),
	LIGHT_FOOT("light_foot", "轻足", TalentRarity.WHITE),
	POTION_APPRENTICE("potion_apprentice", "药剂学徒", TalentRarity.WHITE),
	IRON_STOMACH("iron_stomach", "铁胃", TalentRarity.GREEN),
	SHIELD_GUARD("shield_guard", "盾卫", TalentRarity.GREEN),
	GALE_STEP("gale_step", "疾风步", TalentRarity.GREEN),
	ECHO_MINER("echo_miner", "回响矿工", TalentRarity.GREEN),
	THORN_REVENGE("thorn_revenge", "荆棘反噬", TalentRarity.BLUE),
	GLUTTON("glutton", "暴食者", TalentRarity.BLUE),
	FROST_HEART("frost_heart", "冰心", TalentRarity.BLUE),
	THUNDER_MARK("thunder_mark", "雷痕", TalentRarity.BLUE),
	BLOOD_SURGE("blood_surge", "嗜血狂潮", TalentRarity.PURPLE),
	UNDYING_WALL("undying_wall", "不灭壁垒", TalentRarity.PURPLE),
	VOID_ARROW("void_arrow", "裂空箭", TalentRarity.PURPLE),
	SOUL_SIPHON("soul_siphon", "噬魂汲取", TalentRarity.PURPLE),
	HEAVEN_JUDGMENT("heaven_judgment", "天罚裁决", TalentRarity.GOLD),
	BATTLE_HYMN("battle_hymn", "战歌", TalentRarity.GOLD),
	OMNISCIENCE("omniscience", "万象洞悉", TalentRarity.GOLD),
	FINAL_WILL("final_will", "终焉一念", TalentRarity.RED);

	private final String id;
	private final String labelZh;
	private final TalentRarity rarity;

	Talent(String id, String labelZh, TalentRarity rarity) {
		this.id = id;
		this.labelZh = labelZh;
		this.rarity = rarity;
	}

	public String id() {
		return id;
	}

	public String labelZh() {
		return labelZh;
	}

	public TalentRarity rarity() {
		return rarity;
	}

	public boolean conflictsWith(Talent other) {
		if (other == null) {
			return false;
		}
		return (this == BLOOD_RITE && other == CLEAR_HEART)
				|| (this == CLEAR_HEART && other == BLOOD_RITE);
	}

	public static Talent byId(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		String key = id.toLowerCase(Locale.ROOT);
		for (Talent t : values()) {
			if (t.id.equals(key) || t.name().equalsIgnoreCase(key)) {
				return t;
			}
		}
		return null;
	}

	/** 按稀有度权重抽取，不放回；自动避开互斥。 */
	public static List<Talent> rollWeighted(Random random, int count) {
		List<Talent> pool = new ArrayList<>(List.of(values()));
		List<Talent> picked = new ArrayList<>(count);
		while (picked.size() < count && !pool.isEmpty()) {
			int total = 0;
			for (Talent t : pool) {
				boolean conflict = false;
				for (Talent existing : picked) {
					if (t.conflictsWith(existing)) {
						conflict = true;
						break;
					}
				}
				if (!conflict) {
					total += t.rarity.rollWeight();
				}
			}
			if (total <= 0) {
				break;
			}
			int r = random.nextInt(total);
			int acc = 0;
			Talent chosen = null;
			for (Talent t : pool) {
				boolean conflict = false;
				for (Talent existing : picked) {
					if (t.conflictsWith(existing)) {
						conflict = true;
						break;
					}
				}
				if (conflict) {
					continue;
				}
				acc += t.rarity.rollWeight();
				if (r < acc) {
					chosen = t;
					break;
				}
			}
			if (chosen == null) {
				break;
			}
			picked.add(chosen);
			pool.remove(chosen);
		}
		return picked;
	}
}
