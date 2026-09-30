package com.betterenchants.talent;

import com.betterenchants.enchant.MagicProtectionHandler;
import com.betterenchants.network.BodyCultivationNetworking;
import com.betterenchants.compat.OptionalPrivateHooks;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** 体质升级：点数、属性、兜底减伤、洗点。 */
public final class BodyCultivationMechanics {
	private static final Identifier MOD_HP = Identifier.of("better_enchants", "body_cult_hp");
	private static final Identifier MOD_SPEED = Identifier.of("better_enchants", "body_cult_speed");
	private static final Identifier MOD_ARMOR = Identifier.of("better_enchants", "body_cult_armor");

	/** 血量：每有效点 +5% 最大生命，加成上限 +200%。 */
	public static final float HEALTH_PER_POINT = 0.05f;
	public static final float HEALTH_BONUS_CAP = 2.00f;
	/** 力量：每有效点 +10% 攻击伤害，加成上限 +500%。 */
	public static final float POWER_PER_POINT = 0.10f;
	public static final float POWER_BONUS_CAP = 5.00f;

	/** 每升 5 级经验给 1 点。 */
	public static final int LEVELS_PER_POINT = 5;
	/** 洗点冷却：10 分钟。 */
	public static final long RESPEC_COOLDOWN_TICKS = 20L * 60L * 10L;

	private BodyCultivationMechanics() {
	}

	public static void tick(ServerPlayerEntity player) {
		syncXpPoints(player);
		// 加点/洗点已即时刷新；每秒兜底一次即可，避免每 tick 拆装属性修饰符
		if (player.age % 20 == 0) {
			applyAttributes(player);
		}
		MaidBodyCultivation.tickOwner(player);
	}

	public static void syncXpPoints(ServerPlayerEntity player) {
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		int buckets = Math.max(0, player.experienceLevel / LEVELS_PER_POINT);
		if (buckets > d.bodyXpBucketsGranted) {
			int gain = buckets - d.bodyXpBucketsGranted;
			d.bodyXpBucketsGranted = buckets;
			d.bodyTotalPoints += gain;
			player.sendMessage(Text.translatable("message.better_enchants.body_cult.xp_points", gain, d.bodyTotalPoints), true);
			BodyCultivationNetworking.syncTo(player);
			MaidBodyCultivation.refreshFollowingMaids(player);
		}
	}

	public static void onAdvancementComplete(ServerPlayerEntity player, Identifier advancementId) {
		int pts = BodyCultivationRewards.pointsForAdvancement(advancementId);
		if (pts <= 0) {
			return;
		}
		grantAchievementPoints(player, advancementId.toString(), pts);
	}

	public static void onCriterionTriggered(ServerPlayerEntity player, net.minecraft.advancement.criterion.TickCriterion criterion) {
		int pts = BodyCultivationRewards.pointsForCriterion(criterion);
		if (pts <= 0 || criterion == null) {
			return;
		}
		Identifier id = net.minecraft.registry.Registries.CRITERION.getId(criterion);
		grantAchievementPoints(player, id == null ? criterion.toString() : id.toString(), pts);
	}

	private static void grantAchievementPoints(ServerPlayerEntity player, String claimKey, int pts) {
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		if (!d.bodyClaimedRewards.add(claimKey)) {
			return;
		}
		d.bodyTotalPoints += pts;
		player.sendMessage(Text.translatable("message.better_enchants.body_cult.adv_points", pts, d.bodyTotalPoints), false);
		BodyCultivationNetworking.syncTo(player);
		MaidBodyCultivation.refreshFollowingMaids(player);
	}

	public static int unspent(ServerPlayerEntity player) {
		return TalentPlayerData.data(player).bodyUnspent();
	}

	public static int getStat(ServerPlayerEntity player, BodyCultivationStat stat) {
		return TalentPlayerData.data(player).bodyStat(stat);
	}

	/** 首次免费，之后 10 / 15 / 20… 级。 */
	public static int respecLevelCost(int respecCount) {
		if (respecCount <= 0) {
			return 0;
		}
		return 5 + respecCount * 5;
	}

	public static int respecLevelCost(ServerPlayerEntity player) {
		return respecLevelCost(TalentPlayerData.data(player).bodyRespecCount);
	}

	public static boolean canRespec(ServerPlayerEntity player) {
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		if (d.bodySpent() <= 0) {
			return false;
		}
		long now = player.getWorld().getTime();
		if (now < d.bodyRespecReadyAt) {
			return false;
		}
		return player.experienceLevel >= respecLevelCost(d.bodyRespecCount);
	}

	/**
	 * 重置全部加点，点数退回未分配；消耗经验等级并进入冷却。
	 * @param free 管理端免费洗点
	 */
	public static boolean tryRespec(ServerPlayerEntity player, boolean free) {
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		if (d.bodySpent() <= 0) {
			return false;
		}
		long now = player.getWorld().getTime();
		if (!free) {
			if (now < d.bodyRespecReadyAt) {
				return false;
			}
			int cost = respecLevelCost(d.bodyRespecCount);
			if (player.experienceLevel < cost) {
				return false;
			}
			if (cost > 0) {
				player.addExperienceLevels(-cost);
			}
			d.bodyRespecCount++;
			d.bodyRespecReadyAt = now + RESPEC_COOLDOWN_TICKS;
		}
		d.bodyPower = 0;
		d.bodyHealth = 0;
		d.bodySpeed = 0;
		d.bodyBody = 0;
		d.bodySpell = 0;
		applyAttributes(player);
		OptionalPrivateHooks.refreshSpellMana(player);
		MaidBodyCultivation.refreshFollowingMaids(player);
		BodyCultivationNetworking.syncTo(player);
		if (!free) {
			player.sendMessage(Text.translatable(
					"message.better_enchants.body_cult.respec_ok",
					respecLevelCost(d.bodyRespecCount)), false);
		}
		return true;
	}

	public static boolean tryAllocate(ServerPlayerEntity player, BodyCultivationStat stat, int amount) {
		if (stat == null || amount <= 0) {
			return false;
		}
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		if (d.bodyUnspent() < amount) {
			return false;
		}
		int current = d.bodyStat(stat);
		if (stat.hasCap() && current + amount > stat.maxPoints()) {
			amount = stat.maxPoints() - current;
			if (amount <= 0) {
				return false;
			}
		}
		d.setBodyStat(stat, current + amount);
		applyAttributes(player);
		OptionalPrivateHooks.refreshSpellMana(player);
		MaidBodyCultivation.refreshFollowingMaids(player);
		BodyCultivationNetworking.syncTo(player);
		return true;
	}

	public static void applyAttributes(ServerPlayerEntity player) {
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		Constitution c = d.constitution;
		float hp = ConstitutionBodySynergy.effectivePoints(c, BodyCultivationStat.HEALTH, d.bodyHealth);
		float spd = ConstitutionBodySynergy.effectivePoints(c, BodyCultivationStat.SPEED, d.bodySpeed);
		float body = ConstitutionBodySynergy.effectivePoints(c, BodyCultivationStat.BODY, d.bodyBody);
		setMul(player, EntityAttributes.GENERIC_MAX_HEALTH, MOD_HP, healthBonusPct(hp));
		setMul(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_SPEED, spd * 0.01);
		setMul(player, EntityAttributes.GENERIC_ARMOR, MOD_ARMOR, armorBonusPct(body));
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/**
	 * 血量加成比例（ADD_MULTIPLIED_BASE）：每有效点 +5%，封顶 +200%。
	 * 超出封顶仍可继续加点，但不再增加生命上限。
	 */
	public static double healthBonusPct(float effectiveHealthPoints) {
		if (effectiveHealthPoints <= 0.0f) {
			return 0.0;
		}
		return Math.min(HEALTH_BONUS_CAP, effectiveHealthPoints * HEALTH_PER_POINT);
	}

	/**
	 * 力量伤害加成比例：每有效点 +10%，封顶 +400%。
	 */
	public static float powerBonusPct(float effectivePowerPoints) {
		if (effectivePowerPoints <= 0.0f) {
			return 0.0f;
		}
		return Math.min(POWER_BONUS_CAP, effectivePowerPoints * POWER_PER_POINT);
	}

	/** 肉体强度护甲加成（比例，ADD_MULTIPLIED_BASE）；入参可为有效点数。 */
	public static double armorBonusPct(int bodyPoints) {
		return armorBonusPct((float) bodyPoints);
	}

	public static double armorBonusPct(float bodyPoints) {
		if (bodyPoints <= 0.0f) {
			return 0.0;
		}
		if (bodyPoints <= 75.0f) {
			return bodyPoints * 0.01;
		}
		return 0.75 + (bodyPoints - 75.0f) * 0.02;
	}

	public static float physicalDr(int bodyPoints) {
		return physicalDr((float) bodyPoints);
	}

	public static float physicalDr(float bodyPoints) {
		return Math.min(0.75f, Math.max(0.0f, bodyPoints) * 0.01f);
	}

	public static float magicDr(int spellPoints) {
		return magicDr((float) spellPoints);
	}

	public static float magicDr(float spellPoints) {
		return Math.min(0.75f, Math.max(0.0f, spellPoints) * 0.01f);
	}

	/** 法力上限加成比例（相对基础计算后的 max）。 */
	public static float manaBonusPct(int spellPoints) {
		return manaBonusPct((float) spellPoints);
	}

	public static float manaBonusPct(float spellPoints) {
		if (spellPoints <= 0.0f) {
			return 0.0f;
		}
		if (spellPoints <= 75.0f) {
			return spellPoints * 0.01f;
		}
		return 0.75f + (spellPoints - 75.0f) * 0.02f;
	}

	public static float manaBonusPct(ServerPlayerEntity player) {
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		float eff = ConstitutionBodySynergy.effectivePoints(
				d.constitution, BodyCultivationStat.SPELL, d.bodySpell);
		return manaBonusPct(eff);
	}

	public static float modifyOutgoing(ServerPlayerEntity attacker, float amount) {
		TalentPlayerData.Data d = TalentPlayerData.data(attacker);
		float power = ConstitutionBodySynergy.effectivePoints(
				d.constitution, BodyCultivationStat.POWER, d.bodyPower);
		if (power <= 0.0f || amount <= 0.0f) {
			return amount;
		}
		return amount * (1.0f + powerBonusPct(power));
	}

	/**
	 * 体修力量对天启词条叠乘减伤的穿透（已并入 {@link com.betterenchants.combat.AffixPierceCombat}）。
	 * @deprecated 战斗管线请用 AffixPierceCombat.apply
	 */
	@Deprecated
	public static float pierceAffixDr(ServerPlayerEntity attacker, float beforeAffixDr, float afterAffixDr) {
		return com.betterenchants.combat.AffixPierceCombat.apply(attacker, beforeAffixDr, afterAffixDr);
	}

	/**
	 * 兜底减伤：在剑盾/套装等最优减伤之后再乘算。
	 * 物理减伤不进最优池；魔法减伤同理。
	 */
	public static float modifyIncomingFallback(ServerPlayerEntity victim, DamageSource source, float amount) {
		if (amount <= 0.0f) {
			return amount;
		}
		TalentPlayerData.Data d = TalentPlayerData.data(victim);
		if (MagicProtectionHandler.isMagicDamage(source)) {
			float spell = ConstitutionBodySynergy.effectivePoints(
					d.constitution, BodyCultivationStat.SPELL, d.bodySpell);
			float dr = magicDr(spell);
			if (dr > 0.0f) {
				amount *= 1.0f - dr;
			}
		} else {
			float body = ConstitutionBodySynergy.effectivePoints(
					d.constitution, BodyCultivationStat.BODY, d.bodyBody);
			float dr = physicalDr(body);
			if (dr > 0.0f) {
				amount *= 1.0f - dr;
			}
		}
		return amount;
	}

	private static void setMul(
			ServerPlayerEntity player,
			net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
			Identifier id,
			double amount
	) {
		EntityAttributeInstance inst = player.getAttributeInstance(attr);
		if (inst == null) {
			return;
		}
		inst.removeModifier(id);
		if (Math.abs(amount) > 1.0E-6) {
			inst.addTemporaryModifier(new EntityAttributeModifier(
					id, amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}
}
