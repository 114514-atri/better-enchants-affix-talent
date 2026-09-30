package com.betterenchants.challenge;

import net.minecraft.text.Text;

/** 建世界难度扩展：原版四档之外的噩梦 / 天启（天启 = 噩梦 + 词条）。 */
public enum WorldChallengeMode {
	OFF(0, "关闭", "off"),
	NIGHTMARE(1, "噩梦", "nightmare"),
	APOCALYPSE(2, "天启", "apocalypse");

	private final int id;
	private final String labelZh;
	private final String key;

	WorldChallengeMode(int id, String labelZh, String key) {
		this.id = id;
		this.labelZh = labelZh;
		this.key = key;
	}

	public int id() {
		return id;
	}

	public String key() {
		return key;
	}

	public String labelZh() {
		return labelZh;
	}

	public Text label() {
		return Text.literal(labelZh);
	}

	public boolean isEnabled() {
		return this != OFF;
	}

	public boolean isNightmare() {
		return this == NIGHTMARE;
	}

	public boolean isApocalypse() {
		return this == APOCALYPSE;
	}

	/** 噩梦 / 天启均强制困难为底层难度。 */
	public boolean forcesHardDifficulty() {
		return isEnabled();
	}

	/** 是否使用噩梦数值倍率（天启继承）。 */
	public boolean usesNightmareBase() {
		return isNightmare() || isApocalypse();
	}

	public static WorldChallengeMode byId(int id) {
		for (WorldChallengeMode m : values()) {
			if (m.id == id) {
				return m;
			}
		}
		return OFF;
	}

	public static WorldChallengeMode byKey(String key) {
		if (key == null || key.isEmpty()) {
			return OFF;
		}
		for (WorldChallengeMode m : values()) {
			if (m.key.equalsIgnoreCase(key) || m.name().equalsIgnoreCase(key)) {
				return m;
			}
		}
		return OFF;
	}
}
