package com.betterenchants.challenge;

/**
 * Apocalypse mob affixes. Players never hold these.
 * {@link #grade()} is 1..10 for every 10 apocalypse levels; once unlocked, higher
 * difficulties may still roll lower grades at reduced weight.
 */
public enum MobAffix {
	// Grade 1 (difficulty 0-9)
	THICK_SKIN("thick_skin", "厚皮", MobAffixRarity.WHITE, 1),
	NIGHT_WALKER("night_walker", "夜行", MobAffixRarity.WHITE, 1),
	VENOM("venom", "淬毒", MobAffixRarity.GREEN, 1),
	WITHER_TOUCH("wither_touch", "凋亡损伤", MobAffixRarity.GREEN, 1),
	BURN("burn", "炽热灼燃", MobAffixRarity.GREEN, 1),
	FROST("frost", "冰霜", MobAffixRarity.GREEN, 1),
	HARDENED("hardened", "硬化", MobAffixRarity.WHITE, 1),
	BRUTE("brute", "蛮力", MobAffixRarity.WHITE, 1),
	QUICK_STEP("quick_step", "快步", MobAffixRarity.WHITE, 1),
	SHELL("shell", "甲壳", MobAffixRarity.WHITE, 1),
	STURDY("sturdy", "壮实", MobAffixRarity.WHITE, 1),
	TOUGH_HIDE("tough_hide", "韧皮", MobAffixRarity.WHITE, 1),
	SHARP("sharp", "锐利", MobAffixRarity.GREEN, 1),
	LEECH_TOUCH("leech_touch", "吸血触", MobAffixRarity.GREEN, 1),
	SLOW_STRIKE("slow_strike", "缓击", MobAffixRarity.GREEN, 1),
	HUNGER_BITE("hunger_bite", "饥咬", MobAffixRarity.GREEN, 1),
	SPARK("spark", "火星", MobAffixRarity.GREEN, 1),
	PACK_HUNTER("pack_hunter", "群猎", MobAffixRarity.GREEN, 1),

	// Grade 2 (difficulty 10-19)
	SWIFT("swift", "疾行", MobAffixRarity.WHITE, 2),
	DAMAGE_REDUCTION("damage_reduction", "减伤", MobAffixRarity.BLUE, 2),
	DODGE("dodge", "我闪", MobAffixRarity.BLUE, 2),
	DARKNESS_AURA("darkness_aura", "黑暗", MobAffixRarity.BLUE, 2),
	EXTRA_HIT("extra_hit", "追击", MobAffixRarity.BLUE, 2),
	VIGOR("vigor", "活力", MobAffixRarity.GREEN, 2),
	FEROCITY("ferocity", "凶猛", MobAffixRarity.GREEN, 2),
	BONE_PLATE("bone_plate", "骨甲", MobAffixRarity.GREEN, 2),
	FLEET("fleet", "轻捷", MobAffixRarity.GREEN, 2),
	SPIKED("spiked", "尖刺", MobAffixRarity.GREEN, 2),
	GUARD("guard", "守卫", MobAffixRarity.GREEN, 2),
	FROSTBITE("frostbite", "冻伤", MobAffixRarity.GREEN, 2),
	BLIND_STRIKE("blind_strike", "致盲", MobAffixRarity.BLUE, 2),
	WEAKEN("weaken", "削弱", MobAffixRarity.BLUE, 2),
	LIFE_TAP("life_tap", "命抽", MobAffixRarity.BLUE, 2),
	ENRAGE("enrage", "激怒", MobAffixRarity.BLUE, 2),
	HEAVY_HAND("heavy_hand", "重手", MobAffixRarity.BLUE, 2),

	// Grade 3 (difficulty 20-29)
	REGEN("regen", "再生", MobAffixRarity.GREEN, 3),
	INVISIBILITY("invisibility", "隐身", MobAffixRarity.PURPLE, 3),
	RANGED_IMMUNE("ranged_immune", "不吃这套", MobAffixRarity.PURPLE, 3),
	CRIT("crit", "强击", MobAffixRarity.PURPLE, 3),
	PHASE("phase", "虚化", MobAffixRarity.PURPLE, 3),
	PLAYER_HUNTER("player_hunter", "强袭", MobAffixRarity.PURPLE, 3),
	CLEAVE("cleave", "横扫", MobAffixRarity.BLUE, 3),
	TOXIC_CLOUD("toxic_cloud", "毒雾", MobAffixRarity.BLUE, 3),
	BULWARK("bulwark", "壁垒", MobAffixRarity.BLUE, 3),
	SAVAGE("savage", "野蛮", MobAffixRarity.BLUE, 3),
	ADAPTIVE("adaptive", "适应", MobAffixRarity.BLUE, 3),
	TRUE_DAMAGE("true_damage", "真伤", MobAffixRarity.RED, 3),
	BLOOD_RAGE("blood_rage", "浴血奋战", MobAffixRarity.RED, 3),

	// Grade 4 (difficulty 30-39)
	VAMPIRIC("vampiric", "汲血", MobAffixRarity.PURPLE, 4),
	IRON_HIDE("iron_hide", "铁皮", MobAffixRarity.BLUE, 4),
	RENDING("rending", "撕裂", MobAffixRarity.BLUE, 4),
	MOMENTUM("momentum", "动量", MobAffixRarity.BLUE, 4),
	CRYSTAL_SKIN("crystal_skin", "晶肤", MobAffixRarity.BLUE, 4),
	SHOCKWAVE("shockwave", "震荡", MobAffixRarity.BLUE, 4),
	BLOODLUST("bloodlust", "嗜血", MobAffixRarity.PURPLE, 4),
	SHADOW_STEP("shadow_step", "影步", MobAffixRarity.PURPLE, 4),
	CURSE_TOUCH("curse_touch", "咒触", MobAffixRarity.PURPLE, 4),
	HUNTER_MARK("hunter_mark", "猎印", MobAffixRarity.PURPLE, 4),
	FORCE_OF_NATURE("force_of_nature", "力拔山兮", MobAffixRarity.RAINBOW, 4),

	// Grade 5 (difficulty 40-49)
	EXPLOSIVE("explosive", "爆裂", MobAffixRarity.RED, 5),
	LAST_STAND("last_stand", "背水", MobAffixRarity.PURPLE, 5),
	WAR_DRUM("war_drum", "战鼓", MobAffixRarity.PURPLE, 5),
	IRON_WILL("iron_will", "铁壁", MobAffixRarity.BLUE, 5),
	GODSLAYER_CREATIVE("godslayer_creative", "弑神", MobAffixRarity.RAINBOW, 5),

	// Grade 6 (difficulty 50-59)
	TITAN("titan", "泰坦", MobAffixRarity.RED, 6),

	// Grade 7 (difficulty 60-69)
	NIRVANA("nirvana", "涅槃", MobAffixRarity.RAINBOW, 7),

	// Grade 8 (difficulty 70-79)
	APEX("apex", "顶点", MobAffixRarity.RAINBOW, 8),

	// Grade 9 (difficulty 80-89)
	CALAMITY("calamity", "天灾", MobAffixRarity.RAINBOW, 9),

	// Grade 10 (difficulty 90-100)
	APOCALYPSE_CROWN("apocalypse_crown", "天启冠冕", MobAffixRarity.RAINBOW, 10);

	public static final int GRADE_MIN = 1;
	public static final int GRADE_MAX = 10;

	private final String id;
	private final String labelZh;
	private final MobAffixRarity rarity;
	private final int grade;

	MobAffix(String id, String labelZh, MobAffixRarity rarity, int grade) {
		this.id = id;
		this.labelZh = labelZh;
		this.rarity = rarity;
		this.grade = Math.max(GRADE_MIN, Math.min(GRADE_MAX, grade));
	}

	public String id() {
		return id;
	}

	public String labelZh() {
		return labelZh;
	}

	public MobAffixRarity rarity() {
		return rarity;
	}

	/** Affix grade 1..10. */
	public int grade() {
		return grade;
	}

	/** First difficulty this grade can appear: G1->0, G2->10, ..., G10->90. */
	public int unlockDifficulty() {
		return (grade - 1) * 10;
	}

	/** Unlocked grades remain available at all higher difficulties. */
	public boolean unlockedAt(int apocalypseLevel) {
		return apocalypseLevel >= unlockDifficulty();
	}

	public static MobAffix byId(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		int colon = id.indexOf(':');
		String key = colon >= 0 ? id.substring(0, colon) : id;
		for (MobAffix a : values()) {
			if (a.id.equals(key)) {
				return a;
			}
		}
		return null;
	}
}
