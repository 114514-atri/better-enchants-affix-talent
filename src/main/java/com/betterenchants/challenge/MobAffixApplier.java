package com.betterenchants.challenge;

import java.util.Set;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.util.Identifier;

/** 词条落地时的属性/状态应用（数值按天启 0..100 标定）。 */
public final class MobAffixApplier {
	private static final Identifier FORCE_HP = Identifier.of("better_enchants", "affix_force_hp");
	private static final Identifier FORCE_DMG = Identifier.of("better_enchants", "affix_force_dmg");
	private static final Identifier FORCE_ARMOR = Identifier.of("better_enchants", "affix_force_armor");
	private static final Identifier THICK_HP = Identifier.of("better_enchants", "affix_thick_hp");
	private static final Identifier SWIFT_SPD = Identifier.of("better_enchants", "affix_swift");
	private static final Identifier IRON_ARMOR = Identifier.of("better_enchants", "affix_iron_armor");
	private static final Identifier TITAN_HP = Identifier.of("better_enchants", "affix_titan_hp");
	private static final Identifier APEX_HP = Identifier.of("better_enchants", "affix_apex_hp");
	private static final Identifier APEX_DMG = Identifier.of("better_enchants", "affix_apex_dmg");
	private static final Identifier APEX_ARMOR = Identifier.of("better_enchants", "affix_apex_armor");
	private static final Identifier CROWN_HP = Identifier.of("better_enchants", "affix_crown_hp");
	private static final Identifier CROWN_DMG = Identifier.of("better_enchants", "affix_crown_dmg");
	private static final Identifier CROWN_ARMOR = Identifier.of("better_enchants", "affix_crown_armor");

	private MobAffixApplier() {
	}

	public static void onApplied(LivingEntity entity, Set<MobAffix> affixes, int apocalypseLevel) {
		onApplied(entity, affixes, apocalypseLevel, MobAffixHolder.getPowerLevel(entity));
	}

	public static void onApplied(LivingEntity entity, Set<MobAffix> affixes, int apocalypseLevel, int powerLevel) {
		int tier = MobAffixRoller.tier(apocalypseLevel);
		double p = ApocalypseScale.progress(apocalypseLevel);
		float scale = MobAffixHolder.powerScale(powerLevel);

		if (affixes.contains(MobAffix.THICK_SKIN)) {
			double mul = (0.10 + tier * 0.008) * scale;
			multiplyMaxHealth(entity, THICK_HP, mul);
		}
		if (affixes.contains(MobAffix.SWIFT)) {
			double mul = (0.08 + tier * 0.002) * scale;
			multiplyAttribute(entity, EntityAttributes.GENERIC_MOVEMENT_SPEED, SWIFT_SPD, Math.min(0.35, mul));
		}
		if (affixes.contains(MobAffix.IRON_WILL)) {
			double mul = (0.25 + tier * 0.01) * scale;
			multiplyAttribute(entity, EntityAttributes.GENERIC_ARMOR, IRON_ARMOR, mul);
		}
		if (affixes.contains(MobAffix.REGEN)) {
			int amp = Math.min(3, Math.max(0, (int) ((tier / 25.0) * scale)));
			entity.addStatusEffect(new StatusEffectInstance(
					StatusEffects.REGENERATION, StatusEffectInstance.INFINITE, amp, false, false));
		}
		if (affixes.contains(MobAffix.TITAN)) {
			double mul = (1.5 + p * 6.0) * scale;
			multiplyMaxHealth(entity, TITAN_HP, mul);
		}
		if (affixes.contains(MobAffix.FORCE_OF_NATURE)) {
			double mul = (0.5 + p * 4.5) * scale;
			multiplyMaxHealth(entity, FORCE_HP, mul);
			multiplyAttribute(entity, EntityAttributes.GENERIC_ATTACK_DAMAGE, FORCE_DMG, mul);
			multiplyAttribute(entity, EntityAttributes.GENERIC_ARMOR, FORCE_ARMOR, mul * 0.75);
		}
		if (affixes.contains(MobAffix.APEX)) {
			double mul = (1.0 + p * 8.0) * scale;
			multiplyMaxHealth(entity, APEX_HP, mul);
			multiplyAttribute(entity, EntityAttributes.GENERIC_ATTACK_DAMAGE, APEX_DMG, mul * 0.85);
			multiplyAttribute(entity, EntityAttributes.GENERIC_ARMOR, APEX_ARMOR, mul * 0.7);
			entity.addStatusEffect(new StatusEffectInstance(
					StatusEffects.RESISTANCE, StatusEffectInstance.INFINITE, 1, false, false));
		}
		if (affixes.contains(MobAffix.CALAMITY)) {
			entity.addStatusEffect(new StatusEffectInstance(
					StatusEffects.STRENGTH, StatusEffectInstance.INFINITE, 2, false, false));
			entity.addStatusEffect(new StatusEffectInstance(
					StatusEffects.SPEED, StatusEffectInstance.INFINITE, 1, false, false));
		}
		if (affixes.contains(MobAffix.APOCALYPSE_CROWN)) {
			double mul = (2.0 + p * 12.0) * scale;
			multiplyMaxHealth(entity, CROWN_HP, mul);
			multiplyAttribute(entity, EntityAttributes.GENERIC_ATTACK_DAMAGE, CROWN_DMG, mul);
			multiplyAttribute(entity, EntityAttributes.GENERIC_ARMOR, CROWN_ARMOR, mul);
			// 抗性 I（原 IV 叠满词条后几乎免伤）
			entity.addStatusEffect(new StatusEffectInstance(
					StatusEffects.RESISTANCE, StatusEffectInstance.INFINITE, 0, false, false));
			entity.addStatusEffect(new StatusEffectInstance(
					StatusEffects.STRENGTH, StatusEffectInstance.INFINITE, 3, false, false));
		}
		if (affixes.contains(MobAffix.INVISIBILITY)) {
			entity.setInvisible(true);
			entity.addStatusEffect(new StatusEffectInstance(
					StatusEffects.INVISIBILITY, StatusEffectInstance.INFINITE, 0, false, false));
		}

		applyScaledAffixes(entity, affixes, scale);
	}

	/** 通用属性词条：按实例等级缩放。 */
	private static void applyScaledAffixes(LivingEntity entity, Set<MobAffix> affixes, float scale) {
		if (affixes.contains(MobAffix.HARDENED)) {
			multiplyMaxHealth(entity, id("hardened_hp"), 0.08 * scale);
		}
		if (affixes.contains(MobAffix.BRUTE)) {
			multiplyAttribute(entity, EntityAttributes.GENERIC_ATTACK_DAMAGE, id("brute_dmg"), 0.08 * scale);
		}
		if (affixes.contains(MobAffix.QUICK_STEP)) {
			multiplyAttribute(entity, EntityAttributes.GENERIC_MOVEMENT_SPEED, id("quick_step"), 0.05 * scale);
		}
		if (affixes.contains(MobAffix.SHELL)) {
			addArmor(entity, id("shell_armor"), 2.0 * scale);
			multiplyAttribute(entity, EntityAttributes.GENERIC_ARMOR, id("shell_armor_mul"), 0.10 * scale);
		}
		if (affixes.contains(MobAffix.STURDY)) {
			multiplyMaxHealth(entity, id("sturdy_hp"), 0.12 * scale);
		}
		if (affixes.contains(MobAffix.SHARP)) {
			multiplyAttribute(entity, EntityAttributes.GENERIC_ATTACK_DAMAGE, id("sharp_dmg"), 0.10 * scale);
		}
		if (affixes.contains(MobAffix.VIGOR)) {
			multiplyMaxHealth(entity, id("vigor_hp"), 0.15 * scale);
		}
		if (affixes.contains(MobAffix.FEROCITY)) {
			multiplyAttribute(entity, EntityAttributes.GENERIC_ATTACK_DAMAGE, id("ferocity_dmg"), 0.12 * scale);
		}
		if (affixes.contains(MobAffix.BONE_PLATE)) {
			addArmor(entity, id("bone_plate"), 3.0 * scale);
			multiplyAttribute(entity, EntityAttributes.GENERIC_ARMOR, id("bone_plate_mul"), 0.15 * scale);
		}
		if (affixes.contains(MobAffix.FLEET)) {
			multiplyAttribute(entity, EntityAttributes.GENERIC_MOVEMENT_SPEED, id("fleet"), 0.08 * scale);
		}
		if (affixes.contains(MobAffix.HEAVY_HAND)) {
			multiplyAttribute(entity, EntityAttributes.GENERIC_ATTACK_DAMAGE, id("heavy_hand"), 0.14 * scale);
		}
		if (affixes.contains(MobAffix.BULWARK)) {
			multiplyMaxHealth(entity, id("bulwark_hp"), 0.20 * scale);
		}
		if (affixes.contains(MobAffix.SAVAGE)) {
			multiplyAttribute(entity, EntityAttributes.GENERIC_ATTACK_DAMAGE, id("savage_dmg"), 0.18 * scale);
		}
		if (affixes.contains(MobAffix.IRON_HIDE)) {
			addArmor(entity, id("iron_hide"), 4.0 * scale);
			multiplyAttribute(entity, EntityAttributes.GENERIC_ARMOR, id("iron_hide_mul"), 0.25 * scale);
		}
		if (affixes.contains(MobAffix.MOMENTUM)) {
			multiplyAttribute(entity, EntityAttributes.GENERIC_MOVEMENT_SPEED, id("momentum_spd"), 0.06 * scale);
			multiplyAttribute(entity, EntityAttributes.GENERIC_ATTACK_DAMAGE, id("momentum_dmg"), 0.08 * scale);
		}
		if (affixes.contains(MobAffix.CRYSTAL_SKIN)) {
			multiplyMaxHealth(entity, id("crystal_hp"), 0.18 * scale);
			addArmor(entity, id("crystal_armor"), 3.0 * scale);
		}
	}

	private static Identifier id(String suffix) {
		return Identifier.of("better_enchants", "affix_" + suffix);
	}

	private static void multiplyMaxHealth(LivingEntity entity, Identifier id, double addMul) {
		EntityAttributeInstance inst = entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (inst == null) {
			return;
		}
		inst.removeModifier(id);
		inst.addPersistentModifier(new EntityAttributeModifier(
				id, addMul, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		if (com.betterenchants.health.VirtualHealth.isEnabled(entity)) {
			com.betterenchants.health.VirtualHealth.refreshMaxFromModifiers(entity);
		} else {
			entity.setHealth(entity.getMaxHealth());
			com.betterenchants.health.VirtualHealth.syncFromAttributes(entity);
		}
	}

	private static void multiplyAttribute(
			LivingEntity entity,
			RegistryEntry<EntityAttribute> attr,
			Identifier id,
			double addMul
	) {
		EntityAttributeInstance inst = entity.getAttributeInstance(attr);
		if (inst == null) {
			return;
		}
		inst.removeModifier(id);
		inst.addPersistentModifier(new EntityAttributeModifier(
				id, addMul, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}

	private static void addArmor(LivingEntity entity, Identifier id, double amount) {
		EntityAttributeInstance inst = entity.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		if (inst == null || amount <= 0.0) {
			return;
		}
		inst.removeModifier(id);
		inst.addPersistentModifier(new EntityAttributeModifier(
				id, amount, EntityAttributeModifier.Operation.ADD_VALUE));
	}
}
