package com.betterenchants.challenge;

/** 词条品级。 */
public enum MobAffixRarity {
	WHITE(0, "白"),
	GREEN(1, "绿"),
	BLUE(2, "蓝"),
	PURPLE(3, "紫"),
	GOLD(4, "金"),
	RED(5, "红"),
	RAINBOW(6, "彩虹");

	private final int id;
	private final String labelZh;

	MobAffixRarity(int id, String labelZh) {
		this.id = id;
		this.labelZh = labelZh;
	}

	public int id() {
		return id;
	}

	public String labelZh() {
		return labelZh;
	}

	public static MobAffixRarity byId(int id) {
		for (MobAffixRarity r : values()) {
			if (r.id == id) {
				return r;
			}
		}
		return WHITE;
	}
}
