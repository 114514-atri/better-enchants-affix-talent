package com.betterenchants.talent;

import com.betterenchants.combat.BlockHandler;
import com.betterenchants.combat.SwordShieldHandler;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** 天赋战斗 / 食用 / 挖掘效果。 */
public final class TalentMechanics {
	private static final Identifier BURDEN_TOUGH = Identifier.of("better_enchants", "talent_burden_tough");
	private static final Identifier SHADOW_MOVE = Identifier.of("better_enchants", "talent_shadow_move");
	private static final Identifier HUNT_MOVE = Identifier.of("better_enchants", "talent_hunt_move");
	private static final Identifier VAULT_LUCK = Identifier.of("better_enchants", "talent_vault_luck");
	private static final Identifier GALE_MOVE = Identifier.of("better_enchants", "talent_gale_move");
	private static final Identifier NIGHT_MOVE = Identifier.of("better_enchants", "talent_night_move");

	private TalentMechanics() {
	}

	public static void tick(ServerPlayerEntity player) {
		EntityAttributeInstance tough = player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS);
		if (tough != null) {
			tough.removeModifier(BURDEN_TOUGH);
			if (TalentPlayerData.hasTalent(player, Talent.BURDEN_WALKER)) {
				tough.addTemporaryModifier(new EntityAttributeModifier(BURDEN_TOUGH, 4.0, EntityAttributeModifier.Operation.ADD_VALUE));
			}
		}
		EntityAttributeInstance move = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (move != null) {
			move.removeModifier(SHADOW_MOVE);
			move.removeModifier(HUNT_MOVE);
			move.removeModifier(GALE_MOVE);
			move.removeModifier(NIGHT_MOVE);
			if (TalentPlayerData.hasTalent(player, Talent.SHADOW_STEP) && player.isSneaking()) {
				double bonus = 0.12;
				if (player.getWorld().getLightLevel(player.getBlockPos()) <= 7) {
					bonus += 0.05;
				}
				move.addTemporaryModifier(new EntityAttributeModifier(SHADOW_MOVE, bonus, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
			if (TalentPlayerData.hasTalent(player, Talent.HUNT_INSTINCT)
					&& player.getHealth() / Math.max(1.0f, player.getMaxHealth()) < 0.50f) {
				move.addTemporaryModifier(new EntityAttributeModifier(HUNT_MOVE, 0.15, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
			if (TalentPlayerData.hasTalent(player, Talent.GALE_STEP) && player.isSprinting()) {
				move.addTemporaryModifier(new EntityAttributeModifier(GALE_MOVE, 0.10, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
			if (TalentPlayerData.hasTalent(player, Talent.NIGHT_WALKER)
					&& player.getWorld().getLightLevel(player.getBlockPos()) <= 7) {
				move.addTemporaryModifier(new EntityAttributeModifier(NIGHT_MOVE, 0.05, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
		EntityAttributeInstance luck = player.getAttributeInstance(EntityAttributes.GENERIC_LUCK);
		if (luck != null) {
			luck.removeModifier(VAULT_LUCK);
			if (TalentPlayerData.hasTalent(player, Talent.VAULT_SIGHT)) {
				luck.addTemporaryModifier(new EntityAttributeModifier(VAULT_LUCK, 1.35, EntityAttributeModifier.Operation.ADD_VALUE));
			}
		}
		if (TalentPlayerData.hasTalent(player, Talent.CLEAR_HEART)
				&& player.getHungerManager().getFoodLevel() >= 16
				&& player.age % 200 == 0) {
			player.heal(1.0f);
		}
		if (TalentPlayerData.hasTalent(player, Talent.BEAST_PACT) && player.age % 100 == 0) {
			buffPets(player);
		}
		if (TalentPlayerData.hasTalent(player, Talent.NIGHT_WALKER)
				&& player.getWorld().getLightLevel(player.getBlockPos()) <= 7
				&& player.age % 80 == 0) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false));
		}
		if (TalentPlayerData.hasTalent(player, Talent.ECHO_MINER)
				&& player.getBlockY() <= 48
				&& player.age % 40 == 0
				&& isHoldingTool(player)) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 50, 0, true, false));
		}
		tickWarBurn(player);
		tickBloodSurge(player);
		tickBattleHymn(player);
		tickDimensionWarpDash(player);
		markShadowIdle(player);
	}

	private static boolean isHoldingTool(ServerPlayerEntity player) {
		ItemStack main = player.getMainHandStack();
		return main.isIn(net.minecraft.registry.tag.ItemTags.PICKAXES)
				|| main.isIn(net.minecraft.registry.tag.ItemTags.SHOVELS)
				|| main.isIn(net.minecraft.registry.tag.ItemTags.AXES);
	}

	private static void tickDimensionWarpDash(ServerPlayerEntity player) {
		if (!TalentPlayerData.hasTalent(player, Talent.DIMENSION_WARP)) {
			return;
		}
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		long now = player.getWorld().getTime();
		if (!player.isSneaking() || !player.isSprinting()) {
			return;
		}
		if (now < d.dimensionWarpReadyAt) {
			return;
		}
		d.dimensionWarpReadyAt = now + Math.round(160 * com.betterenchants.talent.ConstitutionMechanics.cooldownMult(player));
		var look = player.getRotationVector();
		double dist = 4.0;
		double tx = player.getX() + look.x * dist;
		double ty = player.getY();
		double tz = player.getZ() + look.z * dist;
		player.requestTeleport(tx, ty, tz);
		player.fallDistance = 0.0f;
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 8, 4, true, false));
	}

	private static void tickWarBurn(ServerPlayerEntity player) {
		if (!TalentPlayerData.hasTalent(player, Talent.WAR_BURN)) {
			TalentPlayerData.data(player).warBurnStacks = 0;
			return;
		}
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		long now = player.getWorld().getTime();
		if (now - d.lastCombatAt > 60L) {
			d.warBurnStacks = 0;
			return;
		}
		if (now - d.warBurnLastHit >= 20L && now - d.lastCombatAt <= 120L) {
			d.warBurnStacks = Math.min(8, d.warBurnStacks + 1);
			d.warBurnLastHit = now;
		}
	}

	private static void tickBloodSurge(ServerPlayerEntity player) {
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		if (!TalentPlayerData.hasTalent(player, Talent.BLOOD_SURGE)) {
			d.bloodSurgeStacks = 0;
			return;
		}
		long now = player.getWorld().getTime();
		if (now > d.bloodSurgeExpire) {
			d.bloodSurgeStacks = 0;
		}
	}

	private static void tickBattleHymn(ServerPlayerEntity player) {
		if (!TalentPlayerData.hasTalent(player, Talent.BATTLE_HYMN)) {
			return;
		}
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		long now = player.getWorld().getTime();
		if (now - d.lastCombatAt > 80L || player.age % 20 != 0) {
			return;
		}
		for (LivingEntity e : player.getWorld().getEntitiesByClass(
				LivingEntity.class, player.getBoundingBox().expand(10.0),
				x -> x != player && x.isAlive()
						&& (x instanceof ServerPlayerEntity
						|| (x instanceof Tameable t && player.equals(t.getOwner()))))) {
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 0, true, false));
		}
	}

	public static boolean tryEternalEmber(ServerPlayerEntity player) {
		if (!TalentPlayerData.hasTalent(player, Talent.ETERNAL_EMBER)) {
			return false;
		}
		long day = player.getWorld().getTimeOfDay() / 24000L;
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		if (d.eternalEmberDay == day) {
			return false;
		}
		d.eternalEmberDay = day;
		player.setHealth(Math.max(1.0f, player.getMaxHealth() * 0.50f));
		player.deathTime = 0;
		player.hurtTime = 0;
		player.extinguish();
		player.clearStatusEffects();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 160, 0));
		player.sendMessage(net.minecraft.text.Text.literal("§6永恒余烬发动！（今日已用）"), false);
		return true;
	}

	/** 终焉一念：致死续命（长 CD）。 */
	public static boolean tryFinalWill(ServerPlayerEntity player) {
		if (!TalentPlayerData.hasTalent(player, Talent.FINAL_WILL)) {
			return false;
		}
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		long now = player.getWorld().getTime();
		if (now < d.finalWillReadyAt) {
			return false;
		}
		d.finalWillReadyAt = now + 9600L; // 8 分钟
		d.finalWillBuffUntil = now + 200L; // 10 秒增伤
		player.setHealth(1.0f);
		player.deathTime = 0;
		player.hurtTime = 0;
		player.extinguish();
		player.clearStatusEffects();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 4, true, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 80, 0, true, false));
		player.sendMessage(net.minecraft.text.Text.literal("§c终焉一念发动！"), false);
		return true;
	}

	public static void onEat(ServerPlayerEntity player, ItemStack stack, FoodComponent food) {
		if (food == null) {
			return;
		}
		if (TalentPlayerData.getConstitution(player) == Constitution.HERB) {
			// 翻倍级：额外再给一份同等营养/饱和
			player.getHungerManager().add(Math.max(1, food.nutrition()), food.saturation());
		}
		if (TalentPlayerData.hasTalent(player, Talent.IRON_STOMACH)) {
			player.getHungerManager().add(Math.max(0, Math.round(food.nutrition() * 0.10f)), food.saturation() * 0.10f);
		}
		if (TalentPlayerData.hasTalent(player, Talent.GLUTTON)) {
			player.heal(3.0f);
			player.getHungerManager().add(0, food.saturation() * 0.20f);
		}
		if (!TalentPlayerData.hasTalent(player, Talent.FOOD_SPIRIT)) {
			return;
		}
		player.getHungerManager().add(Math.max(1, Math.round(food.nutrition() * 0.25f)), food.saturation() * 0.25f);
		player.removeStatusEffect(StatusEffects.HUNGER);
		player.removeStatusEffect(StatusEffects.POISON);
		String id = Registries.ITEM.getId(stack.getItem()).toString();
		if (TalentPlayerData.tryMarkFood(player, id)) {
			player.heal(player.getMaxHealth() * 0.02f);
			int n = TalentPlayerData.foodVariety(player);
			player.sendMessage(net.minecraft.text.Text.literal("§a万物食灵：新食物 ×" + n + "（永久生命 +" + (n * 2) + "）"), true);
		}
	}

	public static float modifyOutgoing(ServerPlayerEntity attacker, LivingEntity target, float amount, DamageSource source) {
		if (amount <= 0.0f) {
			return amount;
		}
		float out = amount;
		long now = attacker.getWorld().getTime();
		TalentPlayerData.Data data = TalentPlayerData.data(attacker);

		if (TalentPlayerData.hasTalent(attacker, Talent.SWORD_HEART) && BlockHandler.isSword(attacker.getMainHandStack())) {
			out *= 1.0f + TalentPlayerData.swordKinds(attacker).size() * 0.05f;
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.BLOOD_RITE) && attacker.getHealth() / attacker.getMaxHealth() < 0.30f) {
			out *= 1.15f;
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.SHADOW_STEP)
				&& now >= TalentPlayerData.shadowStrikeReadyAt(attacker)
				&& data.shadowIdleSince > 0L
				&& now - data.shadowIdleSince >= 100L) {
			out *= 1.35f;
			TalentPlayerData.setShadowStrikeReadyAt(attacker, now + 160L);
			data.shadowIdleSince = 0L;
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.ARROW_RAIN) && isRanged(source)) {
			if (attacker.getRandom().nextFloat() < 0.10f) {
				out *= 1.25f;
			}
			UUID tid = target.getUuid();
			int stacks = 0;
			if (tid.equals(TalentPlayerData.shadowTarget(attacker)) && now <= TalentPlayerData.arrowStackExpire(attacker)) {
				stacks = TalentPlayerData.arrowStacks(attacker);
			}
			stacks = Math.min(10, stacks + 1);
			out *= 1.0f + stacks * 0.03f;
			TalentPlayerData.setArrowCombo(attacker, tid, stacks, now + 16L);
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.GODSLAYER)
				&& com.betterenchants.socket.PowerfulGemMechanics.isBossOrElite(target)) {
			out *= 1.25f;
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.GODSLAYER) && now < data.bossKillBuffUntil) {
			out *= 1.15f;
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.HUNT_INSTINCT)
				&& attacker.getHealth() >= attacker.getMaxHealth() - 0.05f) {
			out *= 1.20f;
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.WAR_BURN) && data.warBurnStacks > 0) {
			out *= 1.0f + data.warBurnStacks * 0.03f;
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.NIGHT_WALKER)
				&& attacker.getWorld().getLightLevel(attacker.getBlockPos()) <= 7) {
			out *= 1.05f;
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.VOID_ARROW) && isRanged(source)) {
			out *= 1.18f;
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.BLOOD_SURGE) && data.bloodSurgeStacks > 0) {
			out *= 1.0f + data.bloodSurgeStacks * 0.025f;
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.OMNISCIENCE)) {
			out *= 1.08f;
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.FINAL_WILL) && now < data.finalWillBuffUntil) {
			out *= 1.25f;
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.HEAVEN_JUDGMENT)) {
			UUID tid = target.getUuid();
			if (!tid.equals(data.heavenJudgmentTarget) || now >= data.heavenJudgmentReadyAt) {
				out *= 1.35f;
				data.heavenJudgmentTarget = tid;
				data.heavenJudgmentReadyAt = now + 120L;
			}
		}
		if (TalentPlayerData.hasTalent(attacker, Talent.FROST_HEART)
				&& !isRanged(source)
				&& attacker.getRandom().nextFloat() < 0.18f) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
		}
		TalentPlayerData.setLastCombatAt(attacker, now);
		data.shadowIdleSince = 0L;
		return out;
	}

	public static float modifyIncoming(ServerPlayerEntity player, DamageSource source, float amount) {
		if (amount <= 0.0f) {
			return amount;
		}
		float out = amount;
		long now = player.getWorld().getTime();
		TalentPlayerData.setLastCombatAt(player, now);
		TalentPlayerData.data(player).shadowIdleSince = 0L;
		TalentPlayerData.Data data = TalentPlayerData.data(player);

		if (TalentPlayerData.hasTalent(player, Talent.BLOOD_RITE) && player.getHealth() / player.getMaxHealth() < 0.30f) {
			out *= 1.10f;
			if (TalentPlayerData.hasTalent(player, Talent.GODSLAYER)) {
				out *= 1.05f;
			}
		}
		if (TalentPlayerData.hasTalent(player, Talent.BURDEN_WALKER) && source.isOf(DamageTypes.FALL)) {
			out *= 0.80f;
		}
		if (TalentPlayerData.hasTalent(player, Talent.CLEAR_HEART)
				&& (source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.WITHER) || source.isOf(DamageTypes.INDIRECT_MAGIC))) {
			out *= 0.70f;
		}
		if (TalentPlayerData.hasTalent(player, Talent.ABYSS_RESIST)) {
			World w = player.getWorld();
			if (w.getRegistryKey().equals(World.NETHER)
					&& (source.isOf(DamageTypes.ON_FIRE) || source.isOf(DamageTypes.IN_FIRE)
					|| source.isOf(DamageTypes.LAVA) || source.isOf(DamageTypes.HOT_FLOOR))) {
				out *= source.isOf(DamageTypes.LAVA) || source.isOf(DamageTypes.HOT_FLOOR) ? 0.60f : 0.75f;
			}
			if (w.getRegistryKey().equals(World.END) && source.isOf(DamageTypes.OUT_OF_WORLD) && out > 1.0f) {
				out = 1.0f;
			}
		}
		if (TalentPlayerData.hasTalent(player, Talent.ABSOLUTE_BASTION)) {
			if (BlockHandler.isBlocking(player) || SwordShieldHandler.shouldApply(player, source)) {
				out *= 0.75f;
			} else {
				out *= 1.08f;
			}
		}
		if (TalentPlayerData.hasTalent(player, Talent.SHIELD_GUARD)
				&& (BlockHandler.isBlocking(player) || SwordShieldHandler.shouldApply(player, source))) {
			out *= 0.90f;
		}
		if (TalentPlayerData.hasTalent(player, Talent.HUNT_INSTINCT)
				&& player.getHealth() / Math.max(1.0f, player.getMaxHealth()) < 0.50f) {
			out *= 0.90f;
		}
		if (TalentPlayerData.hasTalent(player, Talent.WAR_BURN) && data.warBurnStacks > 0) {
			out *= 1.0f + data.warBurnStacks * 0.015f;
		}
		if (TalentPlayerData.hasTalent(player, Talent.DIMENSION_WARP) && source.isOf(DamageTypes.FALL)
				&& source.getSource() instanceof net.minecraft.entity.projectile.thrown.EnderPearlEntity) {
			return 0.0f;
		}
		if (TalentPlayerData.hasTalent(player, Talent.UNDYING_WALL)) {
			float hpRatio = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
			if (hpRatio < 0.25f && now >= data.undyingWallReadyAt) {
				data.undyingWallReadyAt = now + 1200L;
				data.undyingWallUntil = now + 100L;
				player.sendMessage(net.minecraft.text.Text.literal("§d不灭壁垒发动"), true);
			}
			if (now < data.undyingWallUntil) {
				out *= 0.75f;
			}
		}
		if (TalentPlayerData.hasTalent(player, Talent.THORN_REVENGE)
				&& source.getAttacker() instanceof LivingEntity attacker
				&& player.getRandom().nextFloat() < 0.20f) {
			attacker.damage(player.getDamageSources().thorns(player), Math.max(1.0f, amount * 0.25f));
		}
		if (TalentPlayerData.hasTalent(player, Talent.TIME_SHARD)
				&& now >= TalentPlayerData.timeShardReadyAt(player)
				&& player.getRandom().nextFloat() < 0.03f) {
			TalentPlayerData.setTimeShardReadyAt(player, now + 1200L);
			triggerTimeSlow(player);
			player.sendMessage(net.minecraft.text.Text.literal("§b时停碎片触发"), true);
		}
		TalentMechanics.notifyPetsOnHurt(player);
		return out;
	}

	public static void triggerTimeSlow(ServerPlayerEntity player) {
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 30, 2));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 30, 1));
		for (LivingEntity e : player.getWorld().getEntitiesByClass(
				LivingEntity.class, player.getBoundingBox().expand(6.0), x -> x != player && x.isAlive())) {
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 2));
		}
	}

	public static void onKill(ServerPlayerEntity killer, LivingEntity killed) {
		float damageDealt = killed.getMaxHealth();
		if (TalentPlayerData.hasTalent(killer, Talent.BLOOD_RITE)) {
			killer.heal(Math.min(6.0f, damageDealt * 0.08f));
		}
		if (TalentPlayerData.hasTalent(killer, Talent.HUNT_INSTINCT)) {
			killer.getHungerManager().add(2, 0.5f);
		}
		if (TalentPlayerData.hasTalent(killer, Talent.GODSLAYER)
				&& com.betterenchants.socket.PowerfulGemMechanics.isBossOrElite(killed)) {
			TalentPlayerData.data(killer).bossKillBuffUntil = killer.getWorld().getTime() + 1200L;
			killer.sendMessage(net.minecraft.text.Text.literal("§c弑神一心：击杀强化 60 秒"), true);
		}
		if (TalentPlayerData.hasTalent(killer, Talent.SOUL_SIPHON)) {
			killer.heal(killer.getMaxHealth() * 0.03f);
		}
		if (TalentPlayerData.hasTalent(killer, Talent.BLOOD_SURGE)) {
			TalentPlayerData.Data d = TalentPlayerData.data(killer);
			d.bloodSurgeStacks = Math.min(12, d.bloodSurgeStacks + 1);
			d.bloodSurgeExpire = killer.getWorld().getTime() + 80L;
		}
		if (TalentPlayerData.hasTalent(killer, Talent.THUNDER_MARK)
				&& killed instanceof net.minecraft.entity.mob.HostileEntity
				&& killer.getRandom().nextFloat() < 0.12f
				&& killer.getWorld() instanceof ServerWorld server) {
			for (LivingEntity e : server.getEntitiesByClass(
					LivingEntity.class, killed.getBoundingBox().expand(5.0),
					x -> x != killer && x != killed && x.isAlive() && x instanceof net.minecraft.entity.mob.HostileEntity)) {
				e.damage(killer.getDamageSources().lightningBolt(), 4.0f);
			}
		}
		Constitution c = TalentPlayerData.getConstitution(killer);
		if (c == Constitution.GODSLAYER_BODY) {
			killer.heal(killer.getMaxHealth() * 0.04f);
		}
	}

	/** 兼容旧签名。 */
	public static void onKill(ServerPlayerEntity killer, float damageDealt) {
		if (TalentPlayerData.hasTalent(killer, Talent.BLOOD_RITE)) {
			killer.heal(Math.min(6.0f, damageDealt * 0.08f));
		}
	}

	public static int fortuneBonus(ServerPlayerEntity player) {
		return TalentPlayerData.hasTalent(player, Talent.VAULT_SIGHT) ? 1 : 0;
	}

	public static float exhaustionMultiplier(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity sp
				&& TalentPlayerData.hasTalent(sp, Talent.LIGHT_FOOT)
				&& !player.isSprinting()) {
			return 0.85f;
		}
		return 1.0f;
	}

	public static float xpMultiplier(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity sp && TalentPlayerData.hasTalent(sp, Talent.OMNISCIENCE)) {
			return 1.12f;
		}
		return 1.0f;
	}

	public static StatusEffectInstance scaleEffect(ServerPlayerEntity player, StatusEffectInstance effect) {
		if (effect == null) {
			return null;
		}
		boolean beneficial = effect.getEffectType().value().isBeneficial();
		float mult = 1.0f;
		int ampReduce = 0;
		if (TalentPlayerData.hasTalent(player, Talent.LAW_UNITY)) {
			if (beneficial) {
				mult *= 1.25f * 1.10f;
			} else {
				mult *= 0.80f;
			}
		}
		if (TalentPlayerData.hasTalent(player, Talent.CLEAR_HEART) && !beneficial) {
			mult *= 0.85f;
		}
		if (TalentPlayerData.hasTalent(player, Talent.POTION_APPRENTICE) && beneficial) {
			mult *= 1.15f;
		}
		if (TalentPlayerData.hasTalent(player, Talent.IRON_STOMACH) && !beneficial) {
			var type = effect.getEffectType();
			if (type == StatusEffects.POISON || type == StatusEffects.HUNGER) {
				mult *= 0.60f;
			}
		}
		if (mult == 1.0f && ampReduce == 0) {
			return effect;
		}
		int dur = Math.max(1, Math.round(effect.getDuration() * mult));
		int amp = Math.max(0, effect.getAmplifier() - ampReduce);
		return new StatusEffectInstance(effect.getEffectType(), dur, amp,
				effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon());
	}

	public static void onBlockBreak(ServerPlayerEntity player, BlockPos pos, BlockState state) {
		if (TalentPlayerData.hasTalent(player, Talent.HARVEST_HAND) && isCrop(state)
				&& player.getRandom().nextFloat() < 0.12f
				&& player.getWorld() instanceof ServerWorld server) {
			for (ItemStack drop : net.minecraft.block.Block.getDroppedStacks(
					state, server, pos, null, player, player.getMainHandStack())) {
				net.minecraft.block.Block.dropStack(server, pos, drop.copy());
			}
		}
		if (!TalentPlayerData.hasTalent(player, Talent.ORE_SENSE) || !isOre(state)) {
			return;
		}
		long now = player.getWorld().getTime();
		if (now >= TalentPlayerData.oreSenseCooldown(player)) {
			player.getHungerManager().add(0, 1.0f);
			TalentPlayerData.setOreSenseCooldown(player, now + 10L);
		}
		if (player.getRandom().nextFloat() < 0.15f && player.getWorld() instanceof ServerWorld server) {
			for (ItemStack drop : net.minecraft.block.Block.getDroppedStacks(
					state, server, pos, null, player, player.getMainHandStack())) {
				net.minecraft.block.Block.dropStack(server, pos, drop.copy());
			}
		}
	}

	private static boolean isCrop(BlockState state) {
		return state.isIn(BlockTags.CROPS)
				|| state.isOf(Blocks.MELON)
				|| state.isOf(Blocks.PUMPKIN)
				|| state.isOf(Blocks.NETHER_WART)
				|| state.isOf(Blocks.COCOA)
				|| state.isOf(Blocks.SWEET_BERRY_BUSH);
	}

	private static boolean isOre(BlockState state) {
		return state.isIn(BlockTags.COAL_ORES) || state.isIn(BlockTags.COPPER_ORES) || state.isIn(BlockTags.IRON_ORES)
				|| state.isIn(BlockTags.GOLD_ORES) || state.isIn(BlockTags.DIAMOND_ORES) || state.isIn(BlockTags.EMERALD_ORES)
				|| state.isIn(BlockTags.LAPIS_ORES) || state.isIn(BlockTags.REDSTONE_ORES)
				|| state.isOf(Blocks.NETHER_QUARTZ_ORE) || state.isOf(Blocks.ANCIENT_DEBRIS);
	}

	private static void markShadowIdle(ServerPlayerEntity player) {
		if (!TalentPlayerData.hasTalent(player, Talent.SHADOW_STEP)) {
			return;
		}
		long now = player.getWorld().getTime();
		TalentPlayerData.Data data = TalentPlayerData.data(player);
		if (now - data.lastCombatAt >= 100L && data.shadowIdleSince == 0L) {
			data.shadowIdleSince = now;
		}
	}

	private static void buffPets(ServerPlayerEntity player) {
		for (LivingEntity e : player.getWorld().getEntitiesByClass(
				LivingEntity.class, player.getBoundingBox().expand(16.0),
				x -> x instanceof Tameable t && player.equals(t.getOwner()))) {
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 120, 0, true, false));
		}
	}

	static void notifyPetsOnHurt(ServerPlayerEntity player) {
		if (!TalentPlayerData.hasTalent(player, Talent.BEAST_PACT)) {
			return;
		}
		for (LivingEntity e : player.getWorld().getEntitiesByClass(
				LivingEntity.class, player.getBoundingBox().expand(16.0),
				x -> x instanceof Tameable t && player.equals(t.getOwner()))) {
			e.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0));
		}
	}

	private static boolean isRanged(DamageSource source) {
		return source.getSource() instanceof PersistentProjectileEntity
				|| source.isOf(DamageTypes.ARROW)
				|| source.isOf(DamageTypes.TRIDENT)
				|| source.isOf(DamageTypes.MOB_PROJECTILE);
	}
}

