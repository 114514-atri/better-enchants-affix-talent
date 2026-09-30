package com.betterenchants.challenge;

/**
 * 天启强度标尺：等级 0..{@link WorldChallengeState#MAX_LEVEL}（100）。
 * <ul>
 *   <li>生命/护甲：每 1.5 级提升一次，×1.25（+25%）；满级约 66 次</li>
 *   <li>攻击：每 3 级提升一次，×1.10</li>
 *   <li>减伤能力：随攻击档（每 3 级）×1.02</li>
 * </ul>
 * 公式：{@code p = n × (1 + m)^k}。
 */
public final class ApocalypseScale {
	public static final int TIER_MAX = 100;

	/** 生命/护甲每隔多少级提升一次（1.5 → 满级 floor(100/1.5)=66 次）。 */
	public static final double HEALTH_STEP_LEVELS = 1.5;
	/** @deprecated 用 {@link #HEALTH_STEP_LEVELS}；保留常量以免旧引用断裂。 */
	@Deprecated
	public static final int STEPS_PER_HEALTH = 2;
	/** 攻击（及减伤能力）每多少级提升一次。 */
	public static final int STEPS_PER_DAMAGE = 3;

	/** @deprecated 用 {@link #STEPS_PER_DAMAGE} */
	@Deprecated
	public static final int STEPS_PER_BOOST = STEPS_PER_DAMAGE;

	public static final double HEALTH_RATE = 0.25;
	public static final double DAMAGE_RATE = 0.10;
	public static final double ARMOR_RATE = 0.25;
	public static final double DAMAGE_REDUCTION_RATE = 0.02;

	private ApocalypseScale() {
	}

	public static int clampLevel(int level) {
		return Math.max(0, Math.min(WorldChallengeState.MAX_LEVEL, level));
	}

	/** 0.0 .. 1.0 相对满级进度。 */
	public static double progress(int level) {
		return clampLevel(level) / (double) WorldChallengeState.MAX_LEVEL;
	}

	/**
	 * 阶位 0..100，与天启等级对齐（满级阶位 100）。
	 */
	public static int tier(int level) {
		return Math.max(0, Math.min(TIER_MAX, clampLevel(level)));
	}

	/** 旧代码常用 0..10 档。 */
	public static int legacyTier(int level) {
		return Math.max(0, Math.min(10, tier(level) / 10));
	}

	public static int healthBoostSteps(int level) {
		// floor(L / 1.5) ≡ (L * 2) / 3；满级 100 → 66
		return (clampLevel(level) * 2) / 3;
	}

	public static int damageBoostSteps(int level) {
		return clampLevel(level) / STEPS_PER_DAMAGE;
	}

	/** @deprecated 等同 {@link #damageBoostSteps(int)} */
	@Deprecated
	public static int boostSteps(int level) {
		return damageBoostSteps(level);
	}

	/** {@code p = n × (1 + m)^k} */
	public static double scale(double base, double rate, int steps) {
		if (steps <= 0 || base == 0.0) {
			return base;
		}
		return base * Math.pow(1.0 + rate, steps);
	}

	/**
	 * ADD_MULTIPLIED_BASE 用：使最终属性 = 基础 × (1+m)^k，
	 * 即 modifier = (1+m)^k − 1。
	 */
	public static double multipliedBaseBonus(double rate, int steps) {
		if (steps <= 0) {
			return 0.0;
		}
		return Math.pow(1.0 + rate, steps) - 1.0;
	}

	public static double baseHealthMul(int level) {
		return multipliedBaseBonus(HEALTH_RATE, healthBoostSteps(level));
	}

	public static double baseDamageMul(int level) {
		return multipliedBaseBonus(DAMAGE_RATE, damageBoostSteps(level));
	}

	public static double baseArmorMul(int level) {
		return multipliedBaseBonus(ARMOR_RATE, healthBoostSteps(level));
	}

	/**
	 * 承伤系数：减伤能力按攻击档 (1.02)^k 成长，承伤 = 1 / (1.02)^k。
	 * 永不归零，不会免伤。
	 */
	public static float baseDamageTakenFactor(int level) {
		int k = damageBoostSteps(level);
		if (k <= 0) {
			return 1.0f;
		}
		return (float) (1.0 / Math.pow(1.0 + DAMAGE_REDUCTION_RATE, k));
	}

	/** 升级所需点数：round(36 × 1.115^L)。 */
	public static long needForNextLevel(int level) {
		level = Math.max(0, Math.min(WorldChallengeState.MAX_LEVEL - 1, level));
		return Math.round(36.0 * Math.pow(1.115, level));
	}

	/** 击杀点数随等级略增。 */
	public static double killPointScale(int level) {
		return 1.0 + Math.min(4.0, clampLevel(level) * 0.04);
	}
}
