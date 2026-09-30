package com.betterenchants.talent;

import java.util.Locale;

/** 体质升级五项属性。 */
public enum BodyCultivationStat {
	POWER("power", -1),
	/** 可无限加点；生命加成在 {@link BodyCultivationMechanics#healthBonusPct} 软封顶 +200%。 */
	HEALTH("health", -1),
	SPEED("speed", 50),
	BODY("body", -1),
	SPELL("spell", -1);

	private final String id;
	/** 可投入点数上限；-1 表示无上限。 */
	private final int maxPoints;

	BodyCultivationStat(String id, int maxPoints) {
		this.id = id;
		this.maxPoints = maxPoints;
	}

	public String id() {
		return id;
	}

	public int maxPoints() {
		return maxPoints;
	}

	public boolean hasCap() {
		return maxPoints >= 0;
	}

	public static BodyCultivationStat byId(String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		String key = id.toLowerCase(Locale.ROOT);
		for (BodyCultivationStat s : values()) {
			if (s.id.equals(key) || s.name().equalsIgnoreCase(key)) {
				return s;
			}
		}
		return null;
	}
}
