package com.betterenchants.challenge;

import com.betterenchants.compat.OptionalPrivateHooks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.util.Identifier;

/**
 * 天启难度下敌对 / 中立实体的基础强度（与是否 roll 到词条无关）。
 * 生命/护甲：每 1.5 级 ×1.25（满级约 66 次）；攻击：每 3 级 ×1.10；减伤在战斗管线按同公式结算。
 */
public final class ApocalypseMobPower {
	private static final Identifier BASE_HP = Identifier.of("better_enchants", "apocalypse_base_hp");
	private static final Identifier BASE_DMG = Identifier.of("better_enchants", "apocalypse_base_dmg");
	private static final Identifier BASE_ARMOR = Identifier.of("better_enchants", "apocalypse_base_armor");

	private ApocalypseMobPower() {
	}

	/**
	 * 可吃天启成长 / 词条的实体。
	 * {@link HostileEntity} / {@link Angerable} 不够：恶魂、史莱姆、岩浆怪、疣猪兽、末影龙等是 {@link Monster}；
	 * 终焉恶魔等 Boss 仅实现 private boss hosts (soft-detected)。
	 */
	public static boolean isScalable(LivingEntity entity) {
		return entity instanceof HostileEntity
				|| entity instanceof Angerable
				|| entity instanceof Monster
				|| OptionalPrivateHooks.isPrivateBossHost(entity);
	}

	public static void apply(LivingEntity entity, int apocalypseLevel) {
		if (entity == null || MobAffixHolder.isPlayer(entity) || !isScalable(entity)) {
			return;
		}
		int level = ApocalypseScale.clampLevel(apocalypseLevel);
		if (level <= 0) {
			clear(entity);
			return;
		}
		multiply(entity, EntityAttributes.GENERIC_MAX_HEALTH, BASE_HP, ApocalypseScale.baseHealthMul(level));
		if (com.betterenchants.health.VirtualHealth.isEnabled(entity)) {
			com.betterenchants.health.VirtualHealth.refreshMaxFromModifiers(entity);
		} else if (com.betterenchants.health.VirtualHealth.isVirtualHealthCandidate(entity)) {
			entity.setHealth(entity.getMaxHealth());
			com.betterenchants.health.VirtualHealth.syncFromAttributes(entity);
		} else {
			// 小领袖/普通怪：不进虚拟壳，保留叠乘后的真实 float 血量
			com.betterenchants.health.VirtualHealth.demoteIfShellOnly(entity);
			entity.setHealth(entity.getMaxHealth());
		}

		multiply(entity, EntityAttributes.GENERIC_ATTACK_DAMAGE, BASE_DMG, ApocalypseScale.baseDamageMul(level));
		multiply(entity, EntityAttributes.GENERIC_ARMOR, BASE_ARMOR, ApocalypseScale.baseArmorMul(level));
	}

	/** 去掉天启基础强度修饰符（关天启 / 等级归 0 时用）。 */
	public static void clear(LivingEntity entity) {
		if (entity == null) {
			return;
		}
		remove(entity, EntityAttributes.GENERIC_MAX_HEALTH, BASE_HP);
		remove(entity, EntityAttributes.GENERIC_ATTACK_DAMAGE, BASE_DMG);
		remove(entity, EntityAttributes.GENERIC_ARMOR, BASE_ARMOR);
		if (com.betterenchants.health.VirtualHealth.isEnabled(entity)) {
			com.betterenchants.health.VirtualHealth.refreshMaxFromModifiers(entity);
		}
	}

	private static void multiply(
			LivingEntity entity,
			net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
			Identifier id,
			double addMul
	) {
		if (addMul <= 0.0) {
			return;
		}
		EntityAttributeInstance inst = entity.getAttributeInstance(attr);
		if (inst == null) {
			return;
		}
		inst.removeModifier(id);
		inst.addPersistentModifier(new EntityAttributeModifier(
				id, addMul, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}

	private static void remove(
			LivingEntity entity,
			net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
			Identifier id
	) {
		EntityAttributeInstance inst = entity.getAttributeInstance(attr);
		if (inst != null) {
			inst.removeModifier(id);
		}
	}
}
