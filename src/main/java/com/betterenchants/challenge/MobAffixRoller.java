package com.betterenchants.challenge;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.random.Random;

/**
 * 按天启等级 roll 词条。
 * <ul>
 *   <li>词条分 10 档，对应每十级难度；解锁后高难度仍可出低档（权重更低）</li>
 *   <li>难度越高：单实体词条数越多（相对已解锁池），满级 = 全部词条</li>
 *   <li>难度越高：实例等级越高</li>
 * </ul>
 */
public final class MobAffixRoller {
	private MobAffixRoller() {
	}

	public static void tryRoll(LivingEntity entity, int apocalypseLevel, Random random) {
		if (entity == null || MobAffixHolder.isPlayer(entity) || MobAffixHolder.hasRolled(entity)) {
			return;
		}
		if (!(entity instanceof MobEntity)) {
			MobAffixHolder.markRolled(entity);
			return;
		}
		int level = ApocalypseScale.clampLevel(apocalypseLevel);
		ApocalypseMobPower.apply(entity, level);

		if (!ApocalypseMobPower.isScalable(entity)) {
			MobAffixHolder.markRolled(entity);
			return;
		}

		int band = difficultyBand(level);
		List<MobAffix> unlocked = unlockedPool(level);
		int count = rollCount(level, unlocked.size(), random);
		if (count <= 0 || unlocked.isEmpty()) {
			MobAffixHolder.markRolled(entity);
			com.betterenchants.boss.BossCombat.resyncShieldWithHealth(entity);
			return;
		}

		Set<MobAffix> picked = EnumSet.noneOf(MobAffix.class);
		if (count >= unlocked.size()) {
			picked.addAll(unlocked);
		} else {
			for (int i = 0; i < count; i++) {
				MobAffix a = pickOne(level, band, random, picked);
				if (a == null) {
					break;
				}
				picked.add(a);
			}
		}

		int powerLevel = rollPowerLevel(band, random);
		MobAffixHolder.set(entity, picked, powerLevel);
		MobAffixApplier.onApplied(entity, picked, level, powerLevel);
		com.betterenchants.boss.BossCombat.resyncShieldWithHealth(entity);
	}

	/** 当前难度档位 1..10：L0–9→1，L10–19→2，…，L90–100→10。 */
	public static int difficultyBand(int apocalypseLevel) {
		int level = ApocalypseScale.clampLevel(apocalypseLevel);
		return Math.max(MobAffix.GRADE_MIN, Math.min(MobAffix.GRADE_MAX, level / 10 + 1));
	}

	/** 该难度下已解锁的全部词条。 */
	public static List<MobAffix> unlockedPool(int apocalypseLevel) {
		int level = ApocalypseScale.clampLevel(apocalypseLevel);
		List<MobAffix> out = new ArrayList<>();
		for (MobAffix a : MobAffix.values()) {
			if (a.unlockedAt(level)) {
				out.add(a);
			}
		}
		return out;
	}

	/**
	 * 单实体词条数：相对「当前已解锁池」随难度上升，满级强制全部。
	 * <ul>
	 *   <li>低难：0～少量</li>
	 *   <li>中难：约为已解锁池的对应进度占比</li>
	 *   <li>满级 100：全部词条</li>
	 * </ul>
	 */
	private static int rollCount(int level, int unlockedSize, Random random) {
		if (unlockedSize <= 0) {
			return 0;
		}
		if (level >= WorldChallengeState.MAX_LEVEL) {
			return unlockedSize;
		}

		double t = ApocalypseScale.progress(level);
		// 略偏高难：中段不要一下堆满，接近满级时快速逼近全池
		double fraction = Math.pow(t, 0.85);
		double expected = unlockedSize * fraction;

		if (expected < 1.0) {
			// 极低难度：多数无词条，少数 1 条，极少数 2 条
			if (random.nextDouble() < expected * 0.85) {
				return 1;
			}
			return random.nextFloat() < 0.08f ? 1 : 0;
		}

		int base = (int) Math.floor(expected);
		int spread = Math.max(1, (int) Math.ceil(unlockedSize * 0.06 * (0.35 + t)));
		int count = base + random.nextInt(spread + 1);
		// 高难保底：至少拿到进度对应的大部分
		int floor = Math.max(1, (int) Math.floor(expected * 0.75));
		count = Math.max(floor, count);
		return Math.max(0, Math.min(unlockedSize, count));
	}

	/** 实例词条等级 1..band，偏向高档。 */
	private static int rollPowerLevel(int band, Random random) {
		band = Math.max(1, Math.min(MobAffix.GRADE_MAX, band));
		double sum = 0.0;
		double[] w = new double[band];
		for (int lv = 1; lv <= band; lv++) {
			w[lv - 1] = Math.pow(lv, 1.35);
			sum += w[lv - 1];
		}
		double roll = random.nextDouble() * sum;
		double acc = 0.0;
		for (int i = 0; i < band; i++) {
			acc += w[i];
			if (roll <= acc) {
				return i + 1;
			}
		}
		return band;
	}

	private static MobAffix pickOne(int level, int band, Random random, Set<MobAffix> exclude) {
		List<MobAffix> pool = new ArrayList<>();
		List<Double> weights = new ArrayList<>();
		double sum = 0.0;
		for (MobAffix a : MobAffix.values()) {
			if (exclude.contains(a) || !a.unlockedAt(level)) {
				continue;
			}
			double w = rarityWeight(a.rarity(), level) * gradeWeight(a.grade(), band);
			if (w <= 0.0) {
				continue;
			}
			pool.add(a);
			weights.add(w);
			sum += w;
		}
		if (pool.isEmpty() || sum <= 0.0) {
			return null;
		}
		double roll = random.nextDouble() * sum;
		double acc = 0.0;
		for (int i = 0; i < pool.size(); i++) {
			acc += weights.get(i);
			if (roll <= acc) {
				return pool.get(i);
			}
		}
		return pool.get(pool.size() - 1);
	}

	/**
	 * 档位权重：当前档最高；已解锁的低档仍可出但权重递减；未解锁为 0。
	 */
	public static double gradeWeight(int affixGrade, int difficultyBand) {
		if (affixGrade > difficultyBand) {
			return 0.0;
		}
		int lag = difficultyBand - affixGrade;
		if (lag == 0) {
			return 1.35;
		}
		if (lag == 1) {
			return 0.85;
		}
		// 更旧的低档：保底仍能刷到，但权重明显更低
		return Math.max(0.12, 0.55 / lag);
	}

	/** 稀有度权重：低难度偏白绿，高难度抬高蓝紫红彩。 */
	public static double rarityWeight(MobAffixRarity rarity, int level) {
		double t = ApocalypseScale.progress(level);
		return switch (rarity) {
			case WHITE -> Math.max(0.08, 1.15 - t);
			case GREEN -> 0.90 * (1.0 - 0.45 * t);
			case BLUE -> 0.12 + 0.55 * Math.pow(Math.max(0.02, t), 0.55);
			case PURPLE -> 0.06 + 0.40 * Math.pow(Math.max(0.02, t), 0.75);
			case GOLD -> 0.03 + 0.28 * Math.pow(Math.max(0.02, t), 0.95);
			case RED -> 0.04 + 0.30 * Math.pow(Math.max(0.02, t), 1.05);
			case RAINBOW -> 0.02 + 0.22 * Math.pow(Math.max(0.02, t), 1.25);
		};
	}

	public static int tier(int apocalypseLevel) {
		return ApocalypseScale.tier(apocalypseLevel);
	}

	public static boolean isHostileStyle(LivingEntity entity) {
		return entity instanceof HostileEntity || entity instanceof MobEntity mob && mob.isAttacking();
	}
}
