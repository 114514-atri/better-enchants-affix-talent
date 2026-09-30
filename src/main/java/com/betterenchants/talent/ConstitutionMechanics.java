package com.betterenchants.talent;

import com.betterenchants.combat.BlockHandler;
import com.betterenchants.enchant.BloodMoonHandler;
import com.betterenchants.inventory.SacredChestPlayerData;
import com.betterenchants.quality.ItemQuality;
import com.betterenchants.quality.ItemQualityData;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/** 体质战斗 / 每 tick 效果（对齐混沌雷体强度）。 */
public final class ConstitutionMechanics {
	private static final Identifier MOD_HP = Identifier.of("better_enchants", "const_hp");
	private static final Identifier MOD_HP_FLAT = Identifier.of("better_enchants", "const_hp_flat");
	private static final Identifier MOD_DMG = Identifier.of("better_enchants", "const_dmg");
	private static final Identifier MOD_SPEED = Identifier.of("better_enchants", "const_atk_spd");
	private static final Identifier MOD_ARMOR = Identifier.of("better_enchants", "const_armor");
	private static final Identifier MOD_TOUGH = Identifier.of("better_enchants", "const_tough");
	private static final Identifier MOD_KB = Identifier.of("better_enchants", "const_kb");
	private static final Identifier MOD_MOVE = Identifier.of("better_enchants", "const_move");
	private static final Identifier FOOD_HP = Identifier.of("better_enchants", "talent_food_hp");

	/** 防止体质附加伤递归触发。 */
	private static final ThreadLocal<Integer> PROC_DEPTH = ThreadLocal.withInitial(() -> 0);

	private ConstitutionMechanics() {
	}

	public static void tick(ServerPlayerEntity player) {
		if (player.age % 20 == 0) {
			applyFoodSpiritMaxHp(player);
		}
		Constitution c = TalentPlayerData.getConstitution(player);
		if (c == null) {
			clearCombatMods(player);
			return;
		}
		ServerWorld world = player.getServerWorld();
		long time = world.getTimeOfDay() % 24000L;
		boolean day = time < 12000L;
		boolean night = !day;
		float mult = 0.0f;
		float reduce = 0.0f;
		boolean repair = false;
		boolean nightVision = false;
		boolean holdingSword = BlockHandler.isSword(player.getMainHandStack());
		boolean earthStand = c == Constitution.EARTH && isEarthStanding(player);
		boolean inWater = player.isTouchingWaterOrRain();
		float hpRatio = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
		boolean lowHp = hpRatio <= 0.30f;
		boolean dragonRage = c == Constitution.DRAGON_BLOOD && hpRatio <= 0.40f;
		boolean phoenixLow = c == Constitution.PHOENIX && hpRatio <= 0.25f;
		int cond = (day ? 1 : 0)
				| (night ? 2 : 0)
				| (holdingSword ? 4 : 0)
				| (earthStand ? 8 : 0)
				| (inWater ? 16 : 0)
				| (player.isInLava() ? 32 : 0)
				| (world.isThundering() ? 64 : 0)
				| (lowHp ? 128 : 0)
				| (dragonRage ? 256 : 0)
				| (phoenixLow ? 512 : 0)
				| (c.ordinal() << 10);
		TalentPlayerData.Data data = TalentPlayerData.data(player);
		boolean attrsDirty = cond != data.constCondKey || (player.age & 7) == 0;

		switch (c) {
			case LUNAR -> {
				if (night) {
					float moon = world.getMoonSize();
					mult = 0.25f + moon * 0.45f;
					reduce = 0.30f;
					nightVision = true;
					if (player.age % 80 == 0) {
						player.heal(player.getMaxHealth() * 0.03f);
					}
					if (BloodMoonHandler.isActive(world)) {
						mult *= 2.0f;
						reduce = Math.min(0.75f, reduce * 2.0f);
						repair = true;
						cond |= 1024;
					}
				}
			}
			case SOLAR -> {
				if (day) {
					mult = 0.35f;
					reduce = 0.30f;
					if (player.age % 80 == 0) {
						player.heal(player.getMaxHealth() * 0.06f);
					}
					repair = true;
					if (player.isOnFire() || player.isInLava()) {
						player.heal(player.getMaxHealth() * 0.02f / 20.0f);
					}
				}
			}
			case FROST -> {
				if (isFrostActive(player)) {
					mult = 0.30f;
					reduce = 0.28f;
					cond |= 2048;
					if (player.age % 60 == 0) {
						player.heal(player.getMaxHealth() * 0.035f);
					}
				} else if (isWarmPenalty(player)) {
					cond |= 4096;
				}
			}
			case PHOENIX -> {
				if (player.isInLava()) {
					player.heal(player.getMaxHealth() * 0.01f / 20.0f);
					player.addVelocity(0.0, 0.025, 0.0);
				}
				if (phoenixLow) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, true, false));
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 40, 0, true, false));
				}
			}
			case SWORD_BODY -> {
			}
			case EARTH -> {
				if (earthStand && player.getVelocity().horizontalLengthSquared() < 1.0E-4 && player.isOnGround()) {
					reduce = 0.25f;
					if (player.age % 60 == 0) {
						player.heal(player.getMaxHealth() * 0.02f);
					}
				}
			}
			case OCEAN -> {
				if (inWater) {
					mult = 0.25f;
					if (player.age % 80 == 0) {
						player.heal(player.getMaxHealth() * 0.03f);
					}
				} else if (isDryBiome(player)) {
					cond |= 8192;
				}
			}
			case VOID -> {
				if (world.getRegistryKey().equals(World.END)) {
					mult = 0.35f;
				}
			}
			case THUNDER -> {
				if (world.isThundering()) {
					mult = 0.35f;
				}
			}
			case HERB -> {
				if (player.age % 80 == 0 && isHerbNearby(world, player.getBlockPos())) {
					player.heal(player.getMaxHealth() * 0.02f);
				}
			}
			case CHAOS_SOURCE -> {
				mult = 0.15f;
				reduce = 0.08f;
			}
			case GODSLAYER_BODY -> mult = 0.10f;
			case ETERNAL_SAINT -> {
				if (player.age % 20 == 0) {
					downgradeNegatives(player);
				}
			}
			case CHRONO_TRAVELER -> mult = 0.08f;
			case DRAGON_BLOOD -> {
				if (dragonRage) {
					mult = 0.40f;
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 1, true, false));
				}
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, true, false));
			}
		}

		data.tmpDamageReduce = reduce;
		if (nightVision) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 300, 0, true, false));
		}
		if (repair && player.age % 20 == 0) {
			repairGear(player, 0.015f);
		}
		if (player.age % 40 == 0) {
			syncSwordKinds(player);
		}

		attrsDirty = attrsDirty || cond != data.constCondKey
				|| Float.floatToIntBits(mult) != Float.floatToIntBits(data.constCachedMult);
		if (!attrsDirty) {
			return;
		}
		data.constCondKey = cond;
		data.constCachedMult = mult;
		clearCombatMods(player);
		data.tmpDamageReduce = reduce;

		switch (c) {
			case FROST -> {
				if ((cond & 4096) != 0) {
					setMove(player, -0.03);
				}
			}
			case SWORD_BODY -> {
				if (holdingSword) {
					setAttr(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, MOD_DMG, 0.18);
					setAttr(player, EntityAttributes.GENERIC_ATTACK_SPEED, MOD_SPEED, 0.12);
				}
			}
			case EARTH -> {
				if (earthStand) {
					setAttr(player, EntityAttributes.GENERIC_ARMOR, MOD_ARMOR, 8.0);
					setAttr(player, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, MOD_TOUGH, 4.0);
					setAttr(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, MOD_KB, 0.25);
				}
			}
			case OCEAN -> {
				if (inWater) {
					setMove(player, 0.50);
				} else if ((cond & 8192) != 0) {
					setMove(player, -0.05);
				}
			}
			case THUNDER -> setAttr(player, EntityAttributes.GENERIC_ATTACK_SPEED, MOD_SPEED, 0.08);
			case ETERNAL_SAINT -> setFlatHp(player, 12.0);
			case DRAGON_BLOOD -> {
				setAttr(player, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, MOD_TOUGH, 10.0);
				setAttr(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, MOD_KB, 0.50);
			}
			default -> {
			}
		}

		if (mult > 0.0f) {
			setAttr(player, EntityAttributes.GENERIC_MAX_HEALTH, MOD_HP, mult);
			setAttr(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, MOD_DMG, mult);
			setAttr(player, EntityAttributes.GENERIC_ATTACK_SPEED, MOD_SPEED, mult);
			if (c != Constitution.EARTH && c != Constitution.DRAGON_BLOOD && c != Constitution.ETERNAL_SAINT) {
				setAttr(player, EntityAttributes.GENERIC_ARMOR, MOD_ARMOR, mult);
				setAttr(player, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, MOD_TOUGH, mult);
			}
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
		}
	}

	public static float modifyIncoming(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof ServerPlayerEntity player) || amount <= 0.0f) {
			return amount;
		}
		Constitution c = TalentPlayerData.getConstitution(player);
		if (c == null) {
			return amount;
		}
		float out = amount;
		float reduce = TalentPlayerData.data(player).tmpDamageReduce;
		if (reduce > 0.0f) {
			out *= 1.0f - reduce;
		}

		if (c == Constitution.SOLAR || c == Constitution.PHOENIX) {
			if (isFireDamage(source)) {
				if (c == Constitution.SOLAR) {
					return 0.0f;
				}
				out *= 0.25f;
			}
		}
		if (c == Constitution.FROST && (source.isOf(DamageTypes.FREEZE) || source.isOf(DamageTypes.IN_WALL))) {
			return 0.0f;
		}
		if (c == Constitution.EARTH && source.isOf(DamageTypes.EXPLOSION)) {
			out *= 0.50f;
		}
		if (c == Constitution.OCEAN) {
			if (source.isOf(DamageTypes.DROWN) || source.isOf(DamageTypes.IN_WALL)) {
				return 0.0f;
			}
			if (source.isOf(DamageTypes.LIGHTNING_BOLT)) {
				out *= 0.25f;
			}
		}
		if (c == Constitution.THUNDER && source.isOf(DamageTypes.LIGHTNING_BOLT)) {
			player.heal(player.getMaxHealth() * 0.10f);
			spawnSpark(player.getServerWorld(), player.getPos());
			return 0.0f;
		}
		if (c == Constitution.VOID) {
			if (source.isOf(DamageTypes.FALL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
				out *= 0.50f;
			}
			if (player.getWorld().getRegistryKey().equals(World.NETHER) && isFireDamage(source)) {
				out *= 1.05f;
			}
		}
		if (c == Constitution.HERB && (source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.WITHER)
				|| source.isOf(DamageTypes.INDIRECT_MAGIC))) {
			if (player.hasStatusEffect(StatusEffects.POISON) || player.hasStatusEffect(StatusEffects.WITHER)
					|| source.isOf(DamageTypes.WITHER)) {
				player.heal(Math.min(out, player.getMaxHealth() * 0.02f));
				return 0.0f;
			}
		}
		if (c == Constitution.GODSLAYER_BODY && source.getAttacker() instanceof LivingEntity atk
				&& com.betterenchants.socket.PowerfulGemMechanics.isBossOrElite(atk)) {
			out *= 0.70f;
		}
		if (c == Constitution.CHAOS_SOURCE && TalentPlayerData.data(player).chaosShieldAbs > 0.01f) {
			float abs = TalentPlayerData.data(player).chaosShieldAbs;
			float used = Math.min(abs, out);
			TalentPlayerData.data(player).chaosShieldAbs = abs - used;
			out -= used;
		} else if (c == Constitution.CHAOS_SOURCE && player.getRandom().nextFloat() < 0.12f) {
			TalentPlayerData.data(player).chaosShieldAbs = player.getMaxHealth() * 0.15f;
		}

		// 受击反击（跳过递归附加伤）
		if (PROC_DEPTH.get() == 0 && source.getAttacker() instanceof LivingEntity attacker
				&& attacker.isAlive() && attacker != player) {
			applyIncomingProcs(player, c, attacker, out, source);
		}

		if (c == Constitution.CHRONO_TRAVELER) {
			long now = player.getWorld().getTime();
			TalentPlayerData.Data d = TalentPlayerData.data(player);
			if (now >= d.chronoSlowReadyAt && player.getRandom().nextFloat() < 0.08f) {
				d.chronoSlowReadyAt = now + 1200L;
				TalentMechanics.triggerTimeSlow(player);
			}
		}
		return Math.max(0.0f, out);
	}

	private static void applyIncomingProcs(
			ServerPlayerEntity player, Constitution c, LivingEntity attacker, float dealt, DamageSource source
	) {
		ServerWorld world = player.getServerWorld();
		switch (c) {
			case THUNDER -> {
				if (player.getRandom().nextFloat() < 0.25f) {
					ElementHelper.lightning(player, attacker, dealt * 0.50f, true);
				}
			}
			case LUNAR -> {
				if (player.getWorld().getTimeOfDay() % 24000L >= 12000L
						&& player.getRandom().nextFloat() < 0.20f) {
					attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1));
					attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 0));
				}
			}
			case SOLAR -> {
				if (player.getRandom().nextFloat() < 0.20f) {
					attacker.setOnFireFor(5);
					ElementHelper.direct(player, attacker, dealt * 0.40f);
				}
			}
			case FROST -> {
				if (player.getRandom().nextFloat() < 0.25f) {
					attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 3));
					attacker.setFrozenTicks(Math.max(attacker.getFrozenTicks(), 60));
					ElementHelper.direct(player, attacker, dealt * 0.35f);
				}
			}
			case SWORD_BODY -> {
				if (BlockHandler.isSword(player.getMainHandStack()) && player.getRandom().nextFloat() < 0.15f) {
					ElementHelper.direct(player, attacker, dealt * 0.40f);
				}
			}
			case EARTH -> {
				if (isEarthStanding(player) && player.getRandom().nextFloat() < 0.25f) {
					quake(world, player, attacker, dealt * 0.35f);
				}
			}
			case HERB -> {
				if (player.getRandom().nextFloat() < 0.20f) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 80, 1));
				}
			}
			case CHAOS_SOURCE -> {
				if (player.getRandom().nextFloat() < 0.10f) {
					blinkAway(world, attacker, player.getPos());
				}
			}
			case ETERNAL_SAINT -> {
				if (player.getRandom().nextFloat() < 0.15f) {
					player.heal(player.getMaxHealth() * 0.04f);
					ElementHelper.direct(player, attacker, dealt * 0.35f);
				}
			}
			default -> {
			}
		}
	}

	public static float modifyOutgoing(ServerPlayerEntity attacker, LivingEntity target, float amount, DamageSource source) {
		if (amount <= 0.0f || target == null) {
			return amount;
		}
		Constitution c = TalentPlayerData.getConstitution(attacker);
		if (c == null) {
			return amount;
		}
		float out = amount;

		if (c == Constitution.THUNDER && source.isOf(DamageTypes.LIGHTNING_BOLT)) {
			out *= 2.0f;
		}
		if (c == Constitution.SWORD_BODY && BlockHandler.isSword(attacker.getMainHandStack())) {
			int kinds = TalentPlayerData.swordKinds(attacker).size();
			out *= 1.0f + kinds * 0.03f;
			int combo = TalentPlayerData.swordCombo(attacker) + 1;
			long now = attacker.getWorld().getTime();
			if (combo >= 5 && now >= TalentPlayerData.swordCritReadyAt(attacker)) {
				out *= 2.0f;
				TalentPlayerData.setSwordCombo(attacker, 0);
				TalentPlayerData.setSwordCritReadyAt(attacker, now + 100L);
			} else {
				TalentPlayerData.setSwordCombo(attacker, Math.min(combo, 5));
			}
		}
		if (c == Constitution.FROST && isFrostActive(attacker)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1));
		}
		if (c == Constitution.OCEAN && attacker.getMainHandStack().getItem() instanceof net.minecraft.item.TridentItem) {
			out *= 1.35f;
		}
		if (c == Constitution.PHOENIX) {
			if (nowWithinPhoenixBoost(attacker)) {
				target.setOnFireFor(6);
				out *= 1.40f;
			}
		}
		if (c == Constitution.GODSLAYER_BODY
				&& com.betterenchants.socket.PowerfulGemMechanics.isBossOrElite(target)) {
			out *= 1.55f;
		}
		if (c == Constitution.DRAGON_BLOOD
				&& attacker.getHealth() / Math.max(1.0f, attacker.getMaxHealth()) <= 0.40f) {
			out *= 1.45f;
		}
		if (c == Constitution.CHAOS_SOURCE
				&& ItemQualityData.get(attacker.getMainHandStack()) == ItemQuality.CHAOS) {
			out *= 1.20f;
		}
		if (c == Constitution.LUNAR && BloodMoonHandler.isActive(attacker.getServerWorld())) {
			attacker.heal(out * 0.08f);
		}
		if (c == Constitution.VOID && attacker.getRandom().nextFloat() < 0.20f) {
			out *= 1.30f;
		}

		if (PROC_DEPTH.get() == 0) {
			applyOutgoingProcs(attacker, c, target, out, source);
		}
		return out;
	}

	private static void applyOutgoingProcs(
			ServerPlayerEntity attacker, Constitution c, LivingEntity target, float dealt, DamageSource source
	) {
		if (source.isOf(DamageTypes.LIGHTNING_BOLT) && c != Constitution.THUNDER) {
			return;
		}
		ServerWorld world = attacker.getServerWorld();
		switch (c) {
			case THUNDER -> {
				boolean storm = world.isThundering();
				float chance = storm ? 1.0f : 0.25f;
				if (attacker.getRandom().nextFloat() < chance) {
					ElementHelper.lightning(attacker, target, dealt * 0.75f, true);
				}
			}
			case SOLAR -> {
				if (attacker.getRandom().nextFloat() < 0.25f) {
					target.setOnFireFor(5);
					ElementHelper.direct(attacker, target, dealt * 0.60f);
				}
			}
			case FROST -> {
				if (isFrostActive(attacker) && attacker.getRandom().nextFloat() < 0.30f) {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
					ElementHelper.direct(attacker, target, dealt * 0.50f);
				}
			}
			case OCEAN -> {
				if (attacker.isTouchingWaterOrRain() && attacker.getRandom().nextFloat() < 0.25f) {
					ElementHelper.direct(attacker, target, dealt * 0.60f);
				}
			}
			case HERB -> {
				if (attacker.getRandom().nextFloat() < 0.15f) {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 1));
				}
			}
			case CHRONO_TRAVELER -> {
				if (attacker.getRandom().nextFloat() < 0.15f) {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2));
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 60, 1));
				}
			}
			default -> {
			}
		}
	}

	public static float cooldownMult(ServerPlayerEntity player) {
		return TalentPlayerData.getConstitution(player) == Constitution.CHRONO_TRAVELER ? 0.70f : 1.0f;
	}

	public static float healMult(ServerPlayerEntity player) {
		return TalentPlayerData.getConstitution(player) == Constitution.ETERNAL_SAINT ? 1.70f : 1.0f;
	}

	public static float naturalRegenMult(ServerPlayerEntity player) {
		if (TalentPlayerData.getConstitution(player) != Constitution.GODSLAYER_BODY) {
			return 1.0f;
		}
		long now = player.getWorld().getTime();
		if (now - TalentPlayerData.lastCombatAt(player) > 100L) {
			return 0.75f;
		}
		return 1.0f;
	}

	public static boolean tryPhoenix(ServerPlayerEntity player) {
		if (TalentPlayerData.getConstitution(player) != Constitution.PHOENIX) {
			return false;
		}
		long now = player.getWorld().getTime();
		if (now < TalentPlayerData.phoenixReadyAt(player)) {
			return false;
		}
		TalentPlayerData.setPhoenixReadyAt(player, now + 20L * 60L * 5L);
		player.setHealth(Math.max(1.0f, player.getMaxHealth() * 0.55f));
		player.deathTime = 0;
		player.hurtTime = 0;
		player.extinguish();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 1));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 300, 0));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 900, 1));
		TalentPlayerData.data(player).phoenixBoostUntil = now + 900L;
		player.sendMessage(net.minecraft.text.Text.literal("§6涅槃凤凰体发动！"), false);
		return true;
	}

	private static boolean nowWithinPhoenixBoost(ServerPlayerEntity player) {
		return player.getWorld().getTime() < TalentPlayerData.data(player).phoenixBoostUntil;
	}

	private static boolean isFireDamage(DamageSource source) {
		return source.isOf(DamageTypes.ON_FIRE) || source.isOf(DamageTypes.IN_FIRE)
				|| source.isOf(DamageTypes.LAVA) || source.isOf(DamageTypes.HOT_FLOOR);
	}

	private static void quake(ServerWorld world, ServerPlayerEntity player, LivingEntity primary, float damage) {
		Box box = primary.getBoundingBox().expand(3.5);
		for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box,
				x -> x != player && x.isAlive())) {
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
			ElementHelper.direct(player, e, damage * (e == primary ? 1.0f : 0.55f));
		}
		world.spawnParticles(ParticleTypes.CLOUD, primary.getX(), primary.getY(), primary.getZ(),
				18, 1.2, 0.2, 1.2, 0.02);
	}

	private static void blinkAway(ServerWorld world, LivingEntity target, Vec3d from) {
		Vec3d away = target.getPos().subtract(from);
		if (away.lengthSquared() < 1.0E-4) {
			away = new Vec3d(target.getRandom().nextGaussian(), 0.0, target.getRandom().nextGaussian());
		}
		away = away.normalize().multiply(4.0 + target.getRandom().nextDouble() * 2.0);
		Vec3d dest = target.getPos().add(away.x, 0.2, away.z);
		target.requestTeleport(dest.x, dest.y, dest.z);
		world.spawnParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + 0.5, target.getZ(),
				24, 0.4, 0.6, 0.4, 0.05);
	}

	private static void spawnSpark(ServerWorld world, Vec3d at) {
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 16, 0.35, 0.5, 0.35, 0.02);
	}

	private static void setFlatHp(ServerPlayerEntity player, double amount) {
		EntityAttributeInstance inst = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (inst == null) {
			return;
		}
		inst.removeModifier(MOD_HP_FLAT);
		inst.addTemporaryModifier(new EntityAttributeModifier(MOD_HP_FLAT, amount, EntityAttributeModifier.Operation.ADD_VALUE));
	}

	private static void downgradeNegatives(ServerPlayerEntity player) {
		for (StatusEffectInstance inst : List.copyOf(player.getStatusEffects())) {
			if (inst.getEffectType().value().isBeneficial()) {
				continue;
			}
			if (inst.getAmplifier() <= 0) {
				player.removeStatusEffect(inst.getEffectType());
			} else {
				player.removeStatusEffect(inst.getEffectType());
				player.addStatusEffect(new StatusEffectInstance(
						inst.getEffectType(), inst.getDuration(), inst.getAmplifier() - 1,
						inst.isAmbient(), inst.shouldShowParticles(), inst.shouldShowIcon()));
			}
		}
	}

	private static void applyFoodSpiritMaxHp(ServerPlayerEntity player) {
		EntityAttributeInstance inst = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (inst == null) {
			return;
		}
		inst.removeModifier(FOOD_HP);
		if (!TalentPlayerData.hasTalent(player, Talent.FOOD_SPIRIT)) {
			return;
		}
		double bonus = TalentPlayerData.foodVariety(player) * 2.0;
		if (bonus > 0.0) {
			inst.addTemporaryModifier(new EntityAttributeModifier(FOOD_HP, bonus, EntityAttributeModifier.Operation.ADD_VALUE));
		}
	}

	private static void clearCombatMods(ServerPlayerEntity player) {
		remove(player, EntityAttributes.GENERIC_MAX_HEALTH, MOD_HP);
		remove(player, EntityAttributes.GENERIC_MAX_HEALTH, MOD_HP_FLAT);
		remove(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, MOD_DMG);
		remove(player, EntityAttributes.GENERIC_ATTACK_SPEED, MOD_SPEED);
		remove(player, EntityAttributes.GENERIC_ARMOR, MOD_ARMOR);
		remove(player, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, MOD_TOUGH);
		remove(player, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, MOD_KB);
		remove(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_MOVE);
		TalentPlayerData.data(player).tmpDamageReduce = 0.0f;
	}

	private static void setAttr(ServerPlayerEntity player,
			net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
			Identifier id, double amount) {
		EntityAttributeInstance inst = player.getAttributeInstance(attr);
		if (inst == null) {
			return;
		}
		inst.removeModifier(id);
		EntityAttributeModifier.Operation op = (id.equals(MOD_ARMOR) || id.equals(MOD_TOUGH) || id.equals(MOD_KB))
				? EntityAttributeModifier.Operation.ADD_VALUE
				: EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE;
		inst.addTemporaryModifier(new EntityAttributeModifier(id, amount, op));
	}

	private static void setMove(ServerPlayerEntity player, double amount) {
		setAttr(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_MOVE, amount);
	}

	private static void remove(ServerPlayerEntity player,
			net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
			Identifier id) {
		EntityAttributeInstance inst = player.getAttributeInstance(attr);
		if (inst != null) {
			inst.removeModifier(id);
		}
	}

	private static boolean isFrostActive(ServerPlayerEntity player) {
		BlockPos pos = player.getBlockPos();
		Block block = player.getWorld().getBlockState(pos.down()).getBlock();
		if (block == Blocks.SNOW || block == Blocks.SNOW_BLOCK || block == Blocks.POWDER_SNOW
				|| block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE || block == Blocks.FROSTED_ICE) {
			return true;
		}
		Biome biome = player.getWorld().getBiome(pos).value();
		return biome.getTemperature() <= 0.15f;
	}

	private static boolean isWarmPenalty(ServerPlayerEntity player) {
		if (player.getWorld().getRegistryKey().equals(World.NETHER)) {
			return true;
		}
		Biome biome = player.getWorld().getBiome(player.getBlockPos()).value();
		return biome.getTemperature() >= 1.0f;
	}

	private static boolean isEarthStanding(ServerPlayerEntity player) {
		Block b = player.getWorld().getBlockState(player.getBlockPos().down()).getBlock();
		return b == Blocks.STONE || b == Blocks.DEEPSLATE || b == Blocks.DIRT || b == Blocks.GRASS_BLOCK
				|| b == Blocks.COARSE_DIRT || b == Blocks.ROOTED_DIRT || b.getDefaultState().isIn(BlockTags.BASE_STONE_OVERWORLD);
	}

	private static boolean isDryBiome(ServerPlayerEntity player) {
		Biome biome = player.getWorld().getBiome(player.getBlockPos()).value();
		return biome.getTemperature() >= 1.5f;
	}

	private static boolean isHerbNearby(ServerWorld world, BlockPos origin) {
		for (BlockPos p : BlockPos.iterate(origin.add(-2, -1, -2), origin.add(2, 1, 2))) {
			var state = world.getBlockState(p);
			if (state.isIn(BlockTags.FLOWERS) || state.isIn(BlockTags.CROPS) || state.isOf(Blocks.SHORT_GRASS)
					|| state.isOf(Blocks.TALL_GRASS) || state.isOf(Blocks.FERN)) {
				return true;
			}
		}
		return false;
	}

	private static void repairGear(ServerPlayerEntity player, float fraction) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getEquippedStack(slot);
			com.betterenchants.util.ItemDurabilityRepair.repairFraction(player, stack, fraction);
		}
		for (int i = 0; i < player.getInventory().size(); i++) {
			com.betterenchants.util.ItemDurabilityRepair.repairFraction(
					player, player.getInventory().getStack(i), fraction);
		}
	}

	private static void syncSwordKinds(ServerPlayerEntity player) {
		scanSwords(player, player.getInventory());
		scanSwords(player, player.getEnderChestInventory());
		scanSwords(player, SacredChestPlayerData.get(player));
	}

	private static void scanSwords(ServerPlayerEntity player, net.minecraft.inventory.Inventory inv) {
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (BlockHandler.isSword(stack)) {
				TalentPlayerData.addSwordKind(player, net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString());
			}
		}
	}

	/** 安全元素伤：不刷原版闪电实体，不毁方块/掉落。 */
	static final class ElementHelper {
		private ElementHelper() {
		}

		static void lightning(ServerPlayerEntity owner, LivingEntity target, float damage, boolean ignite) {
			if (!deal(owner, target, damage, true)) {
				return;
			}
			if (ignite) {
				target.setOnFireFor(5);
			}
			if (owner.getWorld() instanceof ServerWorld world) {
				spawnSpark(world, target.getPos());
			}
		}

		static void direct(ServerPlayerEntity owner, LivingEntity target, float damage) {
			deal(owner, target, damage, false);
		}

		private static boolean deal(ServerPlayerEntity owner, LivingEntity target, float damage, boolean asLightning) {
			if (owner == null || target == null || !target.isAlive() || damage <= 0.01f) {
				return false;
			}
			int depth = PROC_DEPTH.get();
			if (depth >= 2) {
				return false;
			}
			PROC_DEPTH.set(depth + 1);
			try {
				DamageSource source = asLightning
						? owner.getDamageSources().lightningBolt()
						: owner.getDamageSources().playerAttack(owner);
				return target.damage(source, MathHelper.clamp(damage, 0.5f, 200.0f));
			} finally {
				PROC_DEPTH.set(depth);
			}
		}
	}

	/** @deprecated 兼容旧调用名，请用 {@link ElementHelper}。 */
	@Deprecated
	static final class LightningHelper {
		static void strike(ServerWorld world, LivingEntity target) {
			if (world.getClosestPlayer(target, 32.0) instanceof ServerPlayerEntity player) {
				ElementHelper.lightning(player, target, 6.0f, true);
			} else {
				target.damage(world.getDamageSources().lightningBolt(), 6.0f);
				target.setOnFireFor(5);
			}
		}
	}
}
