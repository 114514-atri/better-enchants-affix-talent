package com.betterenchants.health;

import com.betterenchants.entity.TargetDummyEntity;
import java.math.BigDecimal;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

/**
 * 虚拟血池门面：max ≥ {@link #THRESHOLD} 时启用；假人始终启用。
 * 启用后真实血量在 {@link VirtualHealthPool}，原版 float 仅作比例壳。
 */
public final class VirtualHealth {
	/** 超过此最大生命才启用虚拟池。 */
	public static final BigDecimal THRESHOLD = BigDecimal.valueOf(1_000_000L);
	/** 比例壳的 max_health 属性目标（血条用 ratio * SHELL_MAX）。 */
	public static final float SHELL_MAX = 10_000.0f;

	private static final Map<LivingEntity, VirtualHealthPool> POOLS = new WeakHashMap<>();
	/** 防止 syncShell ↔ setHealth mixin 递归。 */
	private static final ThreadLocal<Boolean> SUPPRESS = ThreadLocal.withInitial(() -> false);

	private VirtualHealth() {
	}

	public static boolean isSuppressed() {
		return Boolean.TRUE.equals(SUPPRESS.get());
	}

	public static void runSuppressed(Runnable action) {
		boolean prev = isSuppressed();
		SUPPRESS.set(true);
		try {
			action.run();
		} finally {
			SUPPRESS.set(prev);
		}
	}

	public static VirtualHealthPool pool(LivingEntity entity) {
		return POOLS.computeIfAbsent(entity, e -> new VirtualHealthPool());
	}

	@Nullable
	public static VirtualHealthPool poolIfPresent(LivingEntity entity) {
		return POOLS.get(entity);
	}

	/**
	 * 仅王级及以上 Boss / 假人走虚拟血池。
	 * 小领袖与普通怪即使用天启叠到百万血，也保留 float 生命，避免壳化成 10000。
	 */
	public static boolean isVirtualHealthCandidate(LivingEntity entity) {
		if (entity instanceof TargetDummyEntity) {
			return true;
		}
		return entity instanceof com.betterenchants.boss.KingBoss
				|| entity instanceof com.betterenchants.boss.RoyalBoss
				|| entity instanceof com.betterenchants.boss.ImperialBoss
				|| entity instanceof com.betterenchants.boss.BossEntities.FinalDemonLordEntity;
	}

	public static boolean isEnabled(LivingEntity entity) {
		if (entity instanceof TargetDummyEntity) {
			return true;
		}
		VirtualHealthPool p = POOLS.get(entity);
		return p != null && p.isEnabled();
	}

	/** 属性或已有池 max 是否应启用。 */
	public static boolean shouldEnable(LivingEntity entity) {
		if (entity instanceof TargetDummyEntity) {
			return true;
		}
		if (!isVirtualHealthCandidate(entity)) {
			return false;
		}
		VirtualHealthPool p = POOLS.get(entity);
		if (p != null && p.isEnabled()) {
			return true;
		}
		if (p != null && p.getMax().compareTo(THRESHOLD) >= 0) {
			return true;
		}
		return attributeMax(entity).compareTo(THRESHOLD) >= 0;
	}

	public static BigDecimal attributeMax(LivingEntity entity) {
		EntityAttributeInstance inst = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (inst == null) {
			return VirtualNumbers.fromFloat(entity.getMaxHealth());
		}
		return VirtualNumbers.fromFloat((float) inst.getValue());
	}

	/**
	 * 在属性变更后调用：若跨过阈值则启用并把当前 float 生命拷进池；
	 * 已启用后不再用 {@code getValue()} 抬 max（壳 base=SHELL_MAX 时 getValue 会被乘区污染成假天文数字）。
	 */
	public static void syncFromAttributes(LivingEntity entity) {
		if (entity.getWorld().isClient() || entity instanceof TargetDummyEntity) {
			return;
		}
		// 误进虚拟池的小领袖等：拆壳恢复真实 float 血量
		if (!isVirtualHealthCandidate(entity)) {
			demoteIfShellOnly(entity);
			return;
		}
		VirtualHealthPool p = pool(entity);
		if (p.isEnabled()) {
			// 仅维持壳属性与壳血比例，绝不按污染后的 attribute value 抬池
			setShellAttributeMax(entity);
			syncShell(entity);
			return;
		}
		BigDecimal attrMax = attributeMaxBeforeShell(entity);
		if (attrMax.compareTo(THRESHOLD) < 0) {
			return;
		}
		EntityAttributeInstance inst = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (inst != null) {
			p.setDesignBase(inst.getBaseValue());
		}
		float ratio = entity.getMaxHealth() <= 0.0f ? 1.0f : entity.getHealth() / entity.getMaxHealth();
		BigDecimal cur = attrMax.multiply(BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, ratio))), VirtualNumbers.MATH);
		p.enableWith(attrMax, cur);
		setShellAttributeMax(entity);
		syncShell(entity);
	}

	/**
	 * 非候选实体若已被壳化（base=SHELL_MAX 或池已启用）：按池/乘区还原为 float 生命。
	 */
	public static void demoteIfShellOnly(LivingEntity entity) {
		if (entity.getWorld().isClient() || isVirtualHealthCandidate(entity)) {
			return;
		}
		VirtualHealthPool p = POOLS.get(entity);
		EntityAttributeInstance inst = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		boolean shelled = inst != null && Math.abs(inst.getBaseValue() - SHELL_MAX) < 0.5;
		if (!shelled && (p == null || !p.isEnabled())) {
			return;
		}
		double design = p != null ? p.getDesignBase() : 0.0;
		float ratio = 1.0f;
		if (p != null && p.isEnabled()) {
			ratio = p.ratio();
		} else if (entity.getMaxHealth() > 0.0f) {
			ratio = Math.max(0.0f, Math.min(1.0f, entity.getHealth() / entity.getMaxHealth()));
		}
		runSuppressed(() -> {
			if (inst != null) {
				if (design > 1.0 && Math.abs(design - SHELL_MAX) > 0.5) {
					inst.setBaseValue(design);
				} else {
					// 无设计基础时：把当前「去壳」估值写回 base（去掉乘区反推）
					double add = 0.0;
					double mulBase = 0.0;
					double mulTotal = 1.0;
					for (var mod : inst.getModifiers()) {
						switch (mod.operation()) {
							case ADD_VALUE -> add += mod.value();
							case ADD_MULTIPLIED_BASE -> mulBase += mod.value();
							case ADD_MULTIPLIED_TOTAL -> mulTotal *= (1.0 + mod.value());
						}
					}
					double targetMax = p != null && p.isEnabled()
							? Math.min(p.getMax().doubleValue(), Float.MAX_VALUE / 8.0)
							: Math.min(attributeMax(entity).doubleValue(), Float.MAX_VALUE / 8.0);
					double denom = Math.max(1e-6, (1.0 + mulBase) * mulTotal);
					double restoredBase = Math.max(1.0, (targetMax / denom) - add);
					if (!Double.isFinite(restoredBase) || restoredBase > Float.MAX_VALUE / 8.0) {
						restoredBase = Math.min(1_000_000.0, Float.MAX_VALUE / 16.0);
					}
					inst.setBaseValue(restoredBase);
				}
			}
		});
		POOLS.remove(entity);
		float max = entity.getMaxHealth();
		entity.setHealth(Math.max(1.0f, max * Math.max(0.01f, ratio)));
	}

	/**
	 * 天启/词条改乘区后：按壳化前 designBase + 当前修饰符重算真实 max。
	 */
	public static void refreshMaxFromModifiers(LivingEntity entity) {
		if (entity.getWorld().isClient() || !isEnabled(entity)) {
			syncFromAttributes(entity);
			return;
		}
		VirtualHealthPool p = pool(entity);
		EntityAttributeInstance inst = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (inst == null) {
			return;
		}
		double design = p.getDesignBase();
		if (design <= 1.0 || Math.abs(design - SHELL_MAX) < 0.5) {
			// 尚未记录：尝试在不改壳的情况下读不到，保底不抬爆
			return;
		}
		double add = 0.0;
		double mulBase = 0.0;
		double mulTotal = 1.0;
		for (var mod : inst.getModifiers()) {
			switch (mod.operation()) {
				case ADD_VALUE -> add += mod.value();
				case ADD_MULTIPLIED_BASE -> mulBase += mod.value();
				case ADD_MULTIPLIED_TOTAL -> mulTotal *= (1.0 + mod.value());
			}
		}
		double value = (design + add) * (1.0 + mulBase) * mulTotal;
		if (!Double.isFinite(value) || value <= 0.0) {
			return;
		}
		BigDecimal next = value >= Float.MAX_VALUE
				? BigDecimal.valueOf(Float.MAX_VALUE)
				: VirtualNumbers.fromFloat((float) value);
		if (next.compareTo(p.getMax()) != 0) {
			p.setMax(next, true);
		}
		setShellAttributeMax(entity);
		syncShell(entity);
	}

	/**
	 * 启用前读取真实最大生命。若 base 已被改成壳，则用乘区反推不可靠，回退池 max / THRESHOLD。
	 */
	public static BigDecimal attributeMaxBeforeShell(LivingEntity entity) {
		EntityAttributeInstance inst = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (inst == null) {
			return VirtualNumbers.fromFloat(entity.getMaxHealth());
		}
		double base = inst.getBaseValue();
		if (Math.abs(base - SHELL_MAX) < 0.5) {
			VirtualHealthPool p = POOLS.get(entity);
			if (p != null && p.isEnabled() && p.getMax().compareTo(THRESHOLD) >= 0) {
				return p.getMax();
			}
			// 壳已就位但池未启用：用「去壳」近似 — base 当作设计基础不可用，取 getValue/SHELL 不合理
			// 回退：把当前 getValue 当作已含乘区的结果不可用；用 modifiers 重建
			return reconstructMaxFromModifiers(inst);
		}
		double value = inst.getValue();
		if (!Double.isFinite(value) || value <= 0.0) {
			return VirtualNumbers.ONE;
		}
		if (value >= Float.MAX_VALUE) {
			return BigDecimal.valueOf(Float.MAX_VALUE);
		}
		return VirtualNumbers.fromFloat((float) value);
	}

	/** base=SHELL 时：max ≈ SHELL * (1+ΣmulBase) * Π(1+mulTotal) + add（忽略错误 base）。 */
	private static BigDecimal reconstructMaxFromModifiers(EntityAttributeInstance inst) {
		double add = 0.0;
		double mulBase = 0.0;
		double mulTotal = 1.0;
		for (var mod : inst.getModifiers()) {
			switch (mod.operation()) {
				case ADD_VALUE -> add += mod.value();
				case ADD_MULTIPLIED_BASE -> mulBase += mod.value();
				case ADD_MULTIPLIED_TOTAL -> mulTotal *= (1.0 + mod.value());
			}
		}
		// 没有设计 base 时，用「乘区本身」估：若只有天启 mul，则无法还原；
		// 实践上启用应在 setShell 之前发生。此处保底返回 THRESHOLD。
		double approx = (SHELL_MAX + add) * (1.0 + mulBase) * mulTotal;
		if (!Double.isFinite(approx) || approx < THRESHOLD.doubleValue()) {
			return THRESHOLD;
		}
		if (approx >= Float.MAX_VALUE) {
			return BigDecimal.valueOf(Float.MAX_VALUE);
		}
		return VirtualNumbers.fromFloat((float) approx);
	}

	public static void setShellAttributeMax(LivingEntity entity) {
		runSuppressed(() -> {
			EntityAttributeInstance inst = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
			if (inst != null && Math.abs(inst.getBaseValue() - SHELL_MAX) > 0.5) {
				inst.setBaseValue(SHELL_MAX);
			}
		});
	}

	/** 按池比例写壳血。 */
	public static void syncShell(LivingEntity entity) {
		if (!isEnabled(entity) || entity.getWorld().isClient()) {
			return;
		}
		VirtualHealthPool p = pool(entity);
		if (p.isDead()) {
			// 不 suppress：让 Fantasy Zero / 死亡 mixin 能看到 setHealth(0)
			entity.setHealth(0.0f);
			return;
		}
		runSuppressed(() -> {
			float shell = Math.max(0.001f, SHELL_MAX * p.ratio());
			if (p.isInfinite()) {
				shell = SHELL_MAX;
			}
			entity.setHealth(shell);
		});
	}

	/**
	 * 虚拟扣血。
	 * @return {@code true} 表示血池已死（调用方应走原版致死）
	 */
	public static boolean handleDamage(LivingEntity entity, float amount) {
		return handleDamage(entity, VirtualNumbers.fromDamageFloat(amount));
	}

	/** BigDecimal 直扣，避免天文血量百分比经 float 溢出后失效。 */
	public static boolean handleDamage(LivingEntity entity, BigDecimal amount) {
		if (!isEnabled(entity) || amount == null || amount.signum() <= 0) {
			return false;
		}
		VirtualHealthPool p = pool(entity);
		if (p.isInfinite()) {
			return false;
		}
		p.applyDamage(amount);
		syncShell(entity);
		return p.isDead();
	}

	/** 虚拟血路径不走 applyDamage，需单独报飘字，否则看起来像「没伤害」。 */
	public static void reportDamageNumber(LivingEntity entity, DamageSource source, float amount) {
		if (amount <= 0.01f || !(source.getAttacker() instanceof net.minecraft.server.network.ServerPlayerEntity attacker)) {
			return;
		}
		com.betterenchants.network.DamageNumberNetworking.reportPlayerAttack(attacker, entity, amount, null);
	}

	public static float applyHeal(LivingEntity entity, float amount) {
		if (!isEnabled(entity) || amount <= 0.0f) {
			return amount;
		}
		if (com.betterenchants.combat.BanHealingRules.shouldBlockHeal(entity)) {
			return 0.0f;
		}
		VirtualHealthPool p = pool(entity);
		p.heal(VirtualNumbers.fromDamageFloat(amount));
		syncShell(entity);
		return 0.0f;
	}

	public static BigDecimal getHealthBd(LivingEntity entity) {
		if (isEnabled(entity)) {
			return pool(entity).getCurrent();
		}
		return VirtualNumbers.fromFloat(entity.getHealth());
	}

	public static BigDecimal getMaxHealthBd(LivingEntity entity) {
		if (isEnabled(entity)) {
			return pool(entity).getMax();
		}
		return attributeMax(entity);
	}

	public static float getHealthFloat(LivingEntity entity, float vanilla) {
		if (isSuppressed() || !isEnabled(entity)) {
			return vanilla;
		}
		VirtualHealthPool p = pool(entity);
		if (p.isDead()) {
			return 0.0f;
		}
		return Math.max(0.001f, SHELL_MAX * p.ratio());
	}

	public static float getMaxHealthFloat(LivingEntity entity, float vanilla) {
		if (isSuppressed() || !isEnabled(entity)) {
			return vanilla;
		}
		return SHELL_MAX;
	}

	/** setHealth 拦截：映射到池。 */
	public static float remapSetHealth(LivingEntity entity, float health) {
		if (isSuppressed() || !isEnabled(entity)) {
			return health;
		}
		VirtualHealthPool p = pool(entity);
		// Fantasy Zero 哨兵血量：保持极小正值，不按壳比例抹掉池
		if (entity instanceof com.betterenchants.boss.BossEntities.FinalDemonLordEntity lord
				&& lord.isFinalSkillComing()
				&& health > 0.0f && health < 1.0f) {
			if (p.getCurrent().signum() <= 0) {
				p.setCurrent(VirtualNumbers.fromFloat(0.3999f));
			}
			return 0.3999f;
		}
		if (health <= 0.0f) {
			p.setCurrent(VirtualNumbers.ZERO);
			return 0.0f;
		}
		if (health <= SHELL_MAX + 1.0f) {
			float ratio = Math.min(1.0f, health / SHELL_MAX);
			p.setCurrent(p.getMax().multiply(BigDecimal.valueOf(ratio), VirtualNumbers.MATH));
			return Math.max(0.001f, SHELL_MAX * p.ratio());
		}
		p.setCurrent(VirtualNumbers.fromFloat(health));
		return Math.max(0.001f, SHELL_MAX * p.ratio());
	}

	public static void writeNbt(LivingEntity entity, NbtCompound nbt) {
		VirtualHealthPool p = POOLS.get(entity);
		if (p != null && (p.isEnabled() || entity instanceof TargetDummyEntity)) {
			p.writeNbt(nbt);
		}
	}

	public static void readNbt(LivingEntity entity, NbtCompound nbt) {
		if (!nbt.contains("be_virtual_on") && !nbt.contains("be_virtual_max")) {
			return;
		}
		VirtualHealthPool p = pool(entity);
		p.readNbt(nbt);
		if (p.isEnabled() || entity instanceof TargetDummyEntity) {
			p.setEnabled(true);
			if (!(entity instanceof TargetDummyEntity)) {
				setShellAttributeMax(entity);
			}
			syncShell(entity);
		}
	}

	/** 直接设置虚拟 max（击杀加成等），可超过属性。 */
	public static void setVirtualMax(LivingEntity entity, BigDecimal max, boolean fill) {
		VirtualHealthPool p = pool(entity);
		boolean was = p.isEnabled();
		p.enableWith(max, fill ? max : (was ? p.getCurrent() : max));
		if (!(entity instanceof TargetDummyEntity)) {
			setShellAttributeMax(entity);
		}
		syncShell(entity);
	}

	public static void addVirtualMax(LivingEntity entity, BigDecimal bonus) {
		if (bonus == null || bonus.signum() <= 0) {
			return;
		}
		VirtualHealthPool p = pool(entity);
		if (!p.isEnabled()) {
			BigDecimal base = attributeMax(entity);
			BigDecimal next = base.add(bonus);
			if (next.compareTo(THRESHOLD) < 0) {
				// 仍低于阈值：走原版 modifier 即可，由调用方处理
				return;
			}
			float ratio = entity.getMaxHealth() <= 0 ? 1f : entity.getHealth() / entity.getMaxHealth();
			p.enableWith(next, next.multiply(BigDecimal.valueOf(ratio), VirtualNumbers.MATH));
		} else {
			p.setMax(p.getMax().add(bonus), false);
			p.setCurrent(p.getCurrent().add(bonus));
		}
		if (!(entity instanceof TargetDummyEntity)) {
			setShellAttributeMax(entity);
		}
		syncShell(entity);
	}
}
