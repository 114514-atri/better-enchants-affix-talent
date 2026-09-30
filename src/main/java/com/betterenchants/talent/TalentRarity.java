package com.betterenchants.talent;

/** 天赋稀有度：白 < 绿 < 蓝 < 紫 < 金 < 红。 */
public enum TalentRarity {
	WHITE("white", 0xE8E8E8, 40),
	GREEN("green", 0x55C96B, 28),
	BLUE("blue", 0x5B9BFF, 18),
	PURPLE("purple", 0xC77DFF, 10),
	GOLD("gold", 0xFFC94A, 5),
	RED("red", 0xFF4A4A, 2);

	/** 描述统一粉白。 */
	public static final int DESC_COLOR = 0xFFE8F0;

	private final String id;
	private final int nameColor;
	private final int rollWeight;

	TalentRarity(String id, int nameColor, int rollWeight) {
		this.id = id;
		this.nameColor = nameColor;
		this.rollWeight = rollWeight;
	}

	public String id() {
		return id;
	}

	public int nameColor() {
		return nameColor;
	}

	public int rollWeight() {
		return rollWeight;
	}
}
