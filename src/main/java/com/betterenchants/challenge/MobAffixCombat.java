package com.betterenchants.challenge;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** 词条战斗结算（持有者必须是非玩家；数值按天启 0..100）。 */
public final class MobAffixCombat {
	private MobAffixCombat() {
	}

	public static boolean cancelIncoming(LivingEntity victim, DamageSource source) {
		if (MobAffixHolder.isPlayer(victim)) {
			return false;
		}
		if (com.betterenchants.combat.DamageKinds.isForceOrEnding(source)
				|| com.betterenchants.accessory.AccessoryMechanics.isRingForceContext()) {
			return false;
		}
		Random random = victim.getRandom();
		int tier = currentTier(victim);

		if (com.betterenchants.combat.DamageKinds.canImmune(source)
				&& MobAffixHolder.has(victim, MobAffix.RANGED_IMMUNE) && isRanged(source)) {
			return true;
		}
		if (com.betterenchants.combat.DamageKinds.canDodge(source) && MobAffixHolder.has(victim, MobAffix.DODGE)) {
			float chance = Math.min(0.45f, 0.08f + tier * 0.0035f);
			if (random.nextFloat() < chance) {
				return true;
			}
		}
		if (com.betterenchants.combat.DamageKinds.canDodge(source) && MobAffixHolder.has(victim, MobAffix.PHASE)) {
			float chance = Math.min(0.35f, 0.06f + tier * 0.0025f);
			if (random.nextFloat() < chance) {
				return true;
			}
		}
		if (com.betterenchants.combat.DamageKinds.canDodge(source)
				&& MobAffixHolder.has(victim, MobAffix.APOCALYPSE_CROWN)) {
			float chance = Math.min(0.20f, 0.05f + tier * 0.0015f);
			if (random.nextFloat() < chance) {
				return true;
			}
		}
		return false;
	}

	public static float modifyOutgoing(LivingEntity attacker, LivingEntity target, float amount, DamageSource source) {
		if (attacker == null || MobAffixHolder.isPlayer(attacker) || amount <= 0.0f) {
			return amount;
		}
		int tier = currentTier(attacker);
		float out = amount;

		if (MobAffixHolder.has(attacker, MobAffix.PLAYER_HUNTER) && target instanceof PlayerEntity) {
			out *= 1.0f + Math.min(2.5f, 0.10f * (1 + tier * 0.15f));
		}
		if (MobAffixHolder.has(attacker, MobAffix.BLOOD_RAGE)) {
			float missing = 1.0f - attacker.getHealth() / Math.max(1.0f, attacker.getMaxHealth());
			int stacks = Math.min(12, (int) (missing * 12.0f));
			out *= 1.0f + stacks * 0.10f;
		}
		if (MobAffixHolder.has(attacker, MobAffix.CRIT)) {
			float chance = Math.min(0.55f, 0.15f + tier * 0.003f);
			if (attacker.getRandom().nextFloat() < chance) {
				out *= 1.0f + Math.min(3.0f, 0.25f * (1 + tier * 0.08f));
			}
		}
		if (MobAffixHolder.has(attacker, MobAffix.NIGHT_WALKER) && attacker.getWorld().getLightLevel(attacker.getBlockPos()) <= 7) {
			out *= 1.10f + Math.min(0.40f, tier * 0.002f);
		}
		if (MobAffixHolder.has(attacker, MobAffix.CALAMITY) && target instanceof PlayerEntity) {
			out *= 1.35f + Math.min(1.0f, tier * 0.005f);
		}
		if (MobAffixHolder.has(attacker, MobAffix.APOCALYPSE_CROWN)) {
			out *= 1.25f + Math.min(1.5f, tier * 0.008f);
		}

		// 低段：群猎 / 激怒 / 猎印 / 重手（按实例等级缩放）
		float scale = MobAffixHolder.powerScale(attacker);
		if (MobAffixHolder.has(attacker, MobAffix.PACK_HUNTER)) {
			int allies = attacker.getWorld().getEntitiesByClass(
					LivingEntity.class,
					attacker.getBoundingBox().expand(6.0),
					e -> e != attacker && e.isAlive() && (e instanceof HostileEntity || e instanceof MobEntity)
			).size();
			if (allies > 0) {
				out *= 1.0f + Math.min(0.30f, (0.05f + allies * 0.04f) * scale);
			}
		}
		if (MobAffixHolder.has(attacker, MobAffix.ENRAGE)
				&& attacker.getHealth() / Math.max(1.0f, attacker.getMaxHealth()) <= 0.50f) {
			out *= 1.0f + 0.20f * scale;
		}
		if (MobAffixHolder.has(attacker, MobAffix.HUNTER_MARK) && target instanceof PlayerEntity) {
			out *= 1.0f + 0.25f * scale;
		}
		if (MobAffixHolder.has(attacker, MobAffix.HEAVY_HAND) && attacker.getRandom().nextFloat() < 0.20f) {
			out *= 1.0f + 0.35f * scale;
		}
		return out;
	}

	public static float modifyIncoming(LivingEntity victim, float amount) {
		return modifyIncoming(victim, null, amount);
	}

	public static float modifyIncoming(LivingEntity victim, DamageSource source, float amount) {
		if (MobAffixHolder.isPlayer(victim) || amount <= 0.0f) {
			return amount;
		}
		if (source != null && !com.betterenchants.combat.DamageKinds.canGeneralReduce(source)) {
			return amount;
		}
		int tier = currentTier(victim);
		float out = amount;

		if (ApocalypseMobPower.isScalable(victim) && victim.getWorld() instanceof ServerWorld server) {
			WorldChallengeState state = WorldChallengeState.get(server);
			if (state.getMode().isApocalypse()) {
				out *= ApocalypseScale.baseDamageTakenFactor(state.getApocalypseLevel());
			}
		}

		if (MobAffixHolder.has(victim, MobAffix.DAMAGE_REDUCTION)) {
			float reduce = Math.min(0.50f, 0.05f + tier * 0.0045f);
			out *= 1.0f - reduce;
		}
		if (MobAffixHolder.has(victim, MobAffix.IRON_WILL)) {
			float reduce = Math.min(0.30f, 0.08f + tier * 0.0022f);
			out *= 1.0f - reduce;
		}
		if (MobAffixHolder.has(victim, MobAffix.TITAN)) {
			out *= 0.88f;
		}
		if (MobAffixHolder.has(victim, MobAffix.APEX)) {
			out *= 0.80f;
		}
		if (MobAffixHolder.has(victim, MobAffix.APOCALYPSE_CROWN)) {
			out *= 0.75f;
		}

		// 低段减伤（按实例等级缩放）
		float scale = MobAffixHolder.powerScale(victim);
		if (MobAffixHolder.has(victim, MobAffix.TOUGH_HIDE)) {
			out *= 1.0f - 0.05f * scale;
		}
		if (MobAffixHolder.has(victim, MobAffix.GUARD)) {
			out *= 1.0f - 0.08f * scale;
		}
		if (MobAffixHolder.has(victim, MobAffix.ADAPTIVE)) {
			out *= 1.0f - 0.12f * scale;
		}
		if (MobAffixHolder.has(victim, MobAffix.BULWARK)) {
			out *= 1.0f - 0.10f * scale;
		}
		if (MobAffixHolder.has(victim, MobAffix.LAST_STAND)
				&& victim.getHealth() / Math.max(1.0f, victim.getMaxHealth()) <= 0.35f) {
			out *= 1.0f - 0.25f * scale;
		}
		// 叠乘软顶：满词条时至少保留原伤的 12%，避免无秒杀手段时只能打出个位数
		if (amount > 0.0f && out < amount * 0.12f) {
			out = amount * 0.12f;
		}
		return out;
	}

	/** 受伤后尖刺反伤：由伤害 mixin 在确认命中后调用。 */
	public static void onDamagedBy(LivingEntity victim, LivingEntity attacker, float dealt) {
		if (victim == null || attacker == null || MobAffixHolder.isPlayer(victim) || dealt <= 0.0f) {
			return;
		}
		if (com.betterenchants.accessory.AccessoryMechanics.blocksAffixReflect(attacker)) {
			return;
		}
		if (!MobAffixHolder.has(victim, MobAffix.SPIKED) || !attacker.isAlive()) {
			return;
		}
		float reflect = Math.max(0.5f, dealt * 0.08f * MobAffixHolder.powerScale(victim));
		attacker.damage(victim.getDamageSources().thorns(victim), reflect);
	}

	public static boolean isTrueDamage(LivingEntity attacker) {
		return attacker != null && !MobAffixHolder.isPlayer(attacker)
				&& (MobAffixHolder.has(attacker, MobAffix.TRUE_DAMAGE)
				|| MobAffixHolder.has(attacker, MobAffix.CALAMITY)
				|| MobAffixHolder.has(attacker, MobAffix.APOCALYPSE_CROWN));
	}

	public static void onMeleeHit(LivingEntity attacker, LivingEntity target) {
		if (attacker == null || target == null || MobAffixHolder.isPlayer(attacker)) {
			return;
		}
		boolean blockDebuff = com.betterenchants.accessory.AccessoryMechanics.blocksAffixDebuff(target);
		int tier = currentTier(attacker);
		if (!blockDebuff && MobAffixHolder.has(attacker, MobAffix.VENOM)) {
			int amp = Math.min(2, tier / 40);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100 + tier, amp));
		}
		if (!blockDebuff && MobAffixHolder.has(attacker, MobAffix.WITHER_TOUCH)) {
			int amp = Math.min(2, tier / 40);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100 + tier, amp));
		}
		if (!blockDebuff && MobAffixHolder.has(attacker, MobAffix.BURN)) {
			target.setOnFireFor(5 + Math.min(10, tier / 10));
		}
		if (!blockDebuff && MobAffixHolder.has(attacker, MobAffix.FROST)) {
			target.setFrozenTicks(Math.max(target.getFrozenTicks(), 100 + tier * 2));
		}
		if (MobAffixHolder.has(attacker, MobAffix.EXTRA_HIT) && attacker.getRandom().nextFloat() < Math.min(0.35f, 0.18f + tier * 0.0015f)) {
			float base = attackDamageOr(attacker, 4.0f);
			MobEntity mob = attacker instanceof MobEntity m ? m : null;
			target.damage(attacker.getDamageSources().mobAttack(mob), Math.max(1.0f, base * (0.6f + tier * 0.004f)));
		}
		if (MobAffixHolder.has(attacker, MobAffix.VAMPIRIC)) {
			float heal = Math.min(attacker.getMaxHealth() * 0.08f, 2.0f + tier * 0.15f);
			attacker.heal(heal);
		}
		if (!blockDebuff && MobAffixHolder.has(attacker, MobAffix.CALAMITY) && target instanceof PlayerEntity) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 0));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
		}

		// —— 低段近战特效 ——
		if (MobAffixHolder.has(attacker, MobAffix.LEECH_TOUCH)) {
			attacker.heal(1.0f);
		}
		if (!blockDebuff && MobAffixHolder.has(attacker, MobAffix.SLOW_STRIKE)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0));
		}
		if (!blockDebuff && MobAffixHolder.has(attacker, MobAffix.HUNGER_BITE) && target instanceof PlayerEntity) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 80, 0));
		}
		if (!blockDebuff && MobAffixHolder.has(attacker, MobAffix.SPARK)) {
			target.setOnFireFor(2);
		}
		if (!blockDebuff && MobAffixHolder.has(attacker, MobAffix.FROSTBITE)) {
			target.setFrozenTicks(Math.max(target.getFrozenTicks(), 60));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0));
		}
		if (!blockDebuff && MobAffixHolder.has(attacker, MobAffix.BLIND_STRIKE) && target instanceof PlayerEntity) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0));
		}
		if (!blockDebuff && MobAffixHolder.has(attacker, MobAffix.WEAKEN)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0));
		}
		if (MobAffixHolder.has(attacker, MobAffix.LIFE_TAP)) {
			attacker.heal(Math.max(1.0f, attacker.getMaxHealth() * 0.02f));
		}
		if (MobAffixHolder.has(attacker, MobAffix.BLOODLUST)) {
			attacker.heal(Math.max(1.5f, attacker.getMaxHealth() * 0.04f));
		}
		if (MobAffixHolder.has(attacker, MobAffix.SHADOW_STEP)) {
			attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, false, false));
		}
		if (!blockDebuff && MobAffixHolder.has(attacker, MobAffix.CURSE_TOUCH)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 60, 0));
			if (attacker.getRandom().nextFloat() < 0.35f) {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 40, 0));
			}
		}
		if (!blockDebuff && MobAffixHolder.has(attacker, MobAffix.RENDING)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0));
		}
		if (MobAffixHolder.has(attacker, MobAffix.CLEAVE) && attacker.getWorld() instanceof ServerWorld) {
			float base = attackDamageOr(attacker, 4.0f);
			float splash = Math.max(0.5f, base * 0.25f);
			MobEntity mob = attacker instanceof MobEntity m ? m : null;
			for (LivingEntity other : attacker.getWorld().getEntitiesByClass(
					LivingEntity.class,
					target.getBoundingBox().expand(1.75),
					e -> e != attacker && e != target && e.isAlive() && e instanceof PlayerEntity
			)) {
				other.damage(attacker.getDamageSources().mobAttack(mob), splash);
			}
		}
		if (MobAffixHolder.has(attacker, MobAffix.SHOCKWAVE) && target instanceof PlayerEntity) {
			double dx = target.getX() - attacker.getX();
			double dz = target.getZ() - attacker.getZ();
			double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
			target.addVelocity(dx / len * 0.55, 0.18, dz / len * 0.55);
			target.velocityModified = true;
		}
	}

	public static void onDeath(LivingEntity dead, DamageSource source) {
		if (dead == null || MobAffixHolder.isPlayer(dead) || !MobAffixHolder.has(dead, MobAffix.EXPLOSIVE)) {
			return;
		}
		if (!(dead.getWorld() instanceof ServerWorld server)) {
			return;
		}
		int tier = MobAffixRoller.tier(WorldChallengeState.get(server).getApocalypseLevel());
		float power = 1.5f + Math.min(4.0f, tier * 0.04f);
		server.createExplosion(dead, dead.getX(), dead.getY(), dead.getZ(), power, World.ExplosionSourceType.MOB);
	}

	public static void tickAura(LivingEntity holder) {
		if (holder.getWorld().isClient() || MobAffixHolder.isPlayer(holder)) {
			return;
		}
		if (MobAffixHolder.has(holder, MobAffix.INVISIBILITY)) {
			holder.setInvisible(true);
		}
		if (MobAffixHolder.has(holder, MobAffix.DARKNESS_AURA) && holder.age % 20 == 0) {
			for (LivingEntity other : holder.getWorld().getEntitiesByClass(
					LivingEntity.class, holder.getBoundingBox().expand(3.0 + currentTier(holder) * 0.02),
					e -> e != holder && e.isAlive() && !com.betterenchants.accessory.AccessoryMechanics.blocksAffixDebuff(e))) {
				other.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 120, 0, false, true));
			}
		}
		if (MobAffixHolder.has(holder, MobAffix.CALAMITY) && holder.age % 40 == 0) {
			for (PlayerEntity player : holder.getWorld().getEntitiesByClass(
					PlayerEntity.class, holder.getBoundingBox().expand(6.0),
					e -> e.isAlive() && !com.betterenchants.accessory.AccessoryMechanics.blocksAffixDebuff(e))) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0, false, true));
			}
		}
		if (MobAffixHolder.has(holder, MobAffix.TOXIC_CLOUD) && holder.age % 40 == 0) {
			for (PlayerEntity player : holder.getWorld().getEntitiesByClass(
					PlayerEntity.class, holder.getBoundingBox().expand(3.5),
					e -> e.isAlive() && !com.betterenchants.accessory.AccessoryMechanics.blocksAffixDebuff(e))) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 50, 0, false, true));
			}
		}
		if (MobAffixHolder.has(holder, MobAffix.WAR_DRUM) && holder.age % 40 == 0) {
			for (LivingEntity ally : holder.getWorld().getEntitiesByClass(
					LivingEntity.class,
					holder.getBoundingBox().expand(5.0),
					e -> e != holder && e.isAlive() && (e instanceof HostileEntity || e instanceof MobEntity)
							&& !(e instanceof PlayerEntity)
			)) {
				ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 50, 0, false, false));
				ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 50, 0, false, false));
			}
		}
		if (MobAffixHolder.has(holder, MobAffix.LAST_STAND)
				&& holder.getHealth() / Math.max(1.0f, holder.getMaxHealth()) <= 0.35f
				&& holder.age % 40 == 0) {
			holder.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 50, 0, false, false));
		}
	}

	public static boolean tryNirvana(LivingEntity entity) {
		if (MobAffixHolder.isPlayer(entity) || !MobAffixHolder.has(entity, MobAffix.NIRVANA)) {
			return false;
		}
		if (entity.getCommandTags().contains("be_affix_nirvana_used")) {
			return false;
		}
		entity.addCommandTag("be_affix_nirvana_used");
		entity.setHealth(Math.max(1.0f, entity.getMaxHealth()));
		entity.deathTime = 0;
		entity.hurtTime = 0;
		entity.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 4, false, true));
		entity.extinguish();
		return true;
	}

	/** 恶魂等实体没有 attack_damage 属性，直接 getAttributeValue 会炸。 */
	private static float attackDamageOr(LivingEntity entity, float fallback) {
		var inst = entity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		return inst != null ? (float) inst.getValue() : fallback;
	}

	private static int currentTier(LivingEntity entity) {
		if (!(entity.getWorld() instanceof ServerWorld server)) {
			return 0;
		}
		return MobAffixRoller.tier(WorldChallengeState.get(server).getApocalypseLevel());
	}

	private static boolean isRanged(DamageSource source) {
		if (source.isOf(DamageTypes.ARROW) || source.isOf(DamageTypes.TRIDENT) || source.isOf(DamageTypes.MOB_PROJECTILE)) {
			return true;
		}
		Entity src = source.getSource();
		return src instanceof ProjectileEntity;
	}
}
