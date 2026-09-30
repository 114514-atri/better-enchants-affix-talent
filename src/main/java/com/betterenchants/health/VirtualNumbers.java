package com.betterenchants.health;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** 任意精度数值工具（虚拟血池 / 假人共用）。 */
public final class VirtualNumbers {
	public static final MathContext MATH = new MathContext(64, RoundingMode.HALF_UP);
	public static final BigDecimal ZERO = BigDecimal.ZERO;
	public static final BigDecimal ONE = BigDecimal.ONE;
	public static final int MAX_DIGITS = 256;

	private VirtualNumbers() {
	}

	public static BigDecimal parse(String raw, BigDecimal fallback) {
		if (raw == null) {
			return fallback;
		}
		String t = raw.trim().replace(",", "").replace("_", "");
		if (t.isEmpty() || isInfiniteToken(t)) {
			return fallback;
		}
		try {
			BigDecimal v = new BigDecimal(t, MATH);
			if (v.signum() < 0) {
				return fallback;
			}
			if (v.precision() > MAX_DIGITS) {
				return fallback;
			}
			return v.stripTrailingZeros();
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	public static boolean isInfiniteToken(String t) {
		if (t == null) {
			return false;
		}
		String s = t.trim();
		return s.equals("∞") || s.equalsIgnoreCase("inf") || s.equalsIgnoreCase("infinite")
				|| s.equals("-1");
	}

	public static String format(BigDecimal v) {
		if (v == null) {
			return "0";
		}
		return v.stripTrailingZeros().toPlainString();
	}

	public static String formatInteger(BigDecimal v) {
		if (v == null) {
			return "0";
		}
		return v.setScale(0, RoundingMode.HALF_UP).toPlainString();
	}

	public static String formatCompact(BigDecimal v, int maxLen) {
		String s = formatInteger(v);
		if (s.length() <= maxLen) {
			return s;
		}
		return s.substring(0, maxLen - 1) + "…";
	}

	/** 由原版 float 伤害转入虚拟池：大数避免 Math.round(float)→int 钳死在 2147483647。 */
	public static BigDecimal fromDamageFloat(float amount) {
		if (!Float.isFinite(amount) || amount <= 0.0f) {
			return ZERO;
		}
		if (amount >= 1.0e9f) {
			return BigDecimal.valueOf(amount).setScale(0, RoundingMode.HALF_UP).max(ONE);
		}
		long rounded = Math.round((double) amount);
		if (rounded <= 0L) {
			rounded = 1L;
		}
		return BigDecimal.valueOf(rounded);
	}

	public static BigDecimal fromFloat(float amount) {
		if (!Float.isFinite(amount) || amount < 0.0f) {
			return ZERO;
		}
		return BigDecimal.valueOf(amount).stripTrailingZeros();
	}

	/** 护甲软减伤：damage * 100 / (100 + armor)。 */
	public static BigDecimal applyArmor(BigDecimal damage, BigDecimal armor) {
		if (damage.signum() <= 0) {
			return ZERO;
		}
		if (armor == null || armor.signum() <= 0) {
			return damage;
		}
		BigDecimal denom = BigDecimal.valueOf(100).add(armor, MATH);
		return damage.multiply(BigDecimal.valueOf(100), MATH).divide(denom, MATH);
	}

	public static BigDecimal min(BigDecimal a, BigDecimal b) {
		return a.compareTo(b) <= 0 ? a : b;
	}

	public static BigDecimal max(BigDecimal a, BigDecimal b) {
		return a.compareTo(b) >= 0 ? a : b;
	}
}
