package com.betterenchants.talent;

import com.betterenchants.compat.MaidCompat;
import com.betterenchants.enchant.MagicProtectionHandler;
import com.betterenchants.network.BodyCultivationNetworking;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 女仆体质升级：始终跟随绑定主人的体质加点与点数预算。
 */
public final class MaidBodyCultivation {
	private static final Identifier MOD_HP = Identifier.of("better_enchants", "maid_body_cult_hp");
	private static final Identifier MOD_SPEED = Identifier.of("better_enchants", "maid_body_cult_speed");
	private static final Identifier MOD_ARMOR = Identifier.of("better_enchants", "maid_body_cult_armor");
	private static final double FIND_RANGE = 96.0;

	private MaidBodyCultivation() {
	}

	public static MaidBodyData data(ServerPlayerEntity owner, UUID maidId) {
		return TalentPlayerData.data(owner).maidBodies.computeIfAbsent(maidId, u -> new MaidBodyData());
	}

	/** 有效加点：始终等同主人。 */
	public static int effectiveStat(ServerPlayerEntity owner, UUID maidId, BodyCultivationStat stat) {
		return TalentPlayerData.data(owner).bodyStat(stat);
	}

	/** 主人加点/洗点后，刷新附近已加载女仆属性。 */
	public static void refreshFollowingMaids(ServerPlayerEntity owner) {
		if (!MaidCompat.isLoaded()) {
			return;
		}
		for (LivingEntity maid : MaidCompat.findOwnedMaids(owner, FIND_RANGE)) {
			applyAttributes(owner, maid);
		}
	}

	public static void tickOwner(ServerPlayerEntity owner) {
		if (!MaidCompat.isLoaded() || owner.age % 40 != 0) {
			return;
		}
		ensureKnownMaids(owner);
		for (LivingEntity maid : MaidCompat.findOwnedMaids(owner, FIND_RANGE)) {
			data(owner, maid.getUuid()).displayName = maid.getName().getString();
			applyAttributes(owner, maid);
		}
	}

	public static void ensureKnownMaids(ServerPlayerEntity owner) {
		if (!MaidCompat.isLoaded()) {
			return;
		}
		for (LivingEntity maid : MaidCompat.findOwnedMaids(owner, FIND_RANGE)) {
			data(owner, maid.getUuid()).displayName = maid.getName().getString();
		}
	}

	@Nullable
	public static LivingEntity findLoaded(ServerPlayerEntity owner, UUID maidId) {
		if (maidId == null || !(owner.getWorld() instanceof ServerWorld world)) {
			return null;
		}
		for (LivingEntity maid : MaidCompat.findOwnedMaids(owner, FIND_RANGE)) {
			if (maidId.equals(maid.getUuid())) {
				return maid;
			}
		}
		var entity = world.getEntity(maidId);
		if (entity instanceof LivingEntity living
				&& MaidCompat.isMaid(living)
				&& MaidCompat.isOwnedBy(living, owner)) {
			return living;
		}
		return null;
	}

	public static void applyToLoadedMaid(ServerPlayerEntity owner, UUID maidId) {
		LivingEntity maid = findLoaded(owner, maidId);
		if (maid != null) {
			applyAttributes(owner, maid);
		}
	}

	public static void applyAttributes(ServerPlayerEntity owner, LivingEntity maid) {
		if (owner == null || maid == null || !MaidCompat.isOwnedBy(maid, owner)) {
			return;
		}
		UUID maidId = maid.getUuid();
		Constitution c = TalentPlayerData.data(owner).constitution;
		float hp = ConstitutionBodySynergy.effectivePoints(
				c, BodyCultivationStat.HEALTH, effectiveStat(owner, maidId, BodyCultivationStat.HEALTH));
		float spd = ConstitutionBodySynergy.effectivePoints(
				c, BodyCultivationStat.SPEED, effectiveStat(owner, maidId, BodyCultivationStat.SPEED));
		float body = ConstitutionBodySynergy.effectivePoints(
				c, BodyCultivationStat.BODY, effectiveStat(owner, maidId, BodyCultivationStat.BODY));
		setMul(maid, EntityAttributes.GENERIC_MAX_HEALTH, MOD_HP, BodyCultivationMechanics.healthBonusPct(hp));
		setMul(maid, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_SPEED, spd * 0.01);
		setMul(maid, EntityAttributes.GENERIC_ARMOR, MOD_ARMOR, BodyCultivationMechanics.armorBonusPct(body));
		if (maid.getHealth() > maid.getMaxHealth()) {
			maid.setHealth(maid.getMaxHealth());
		}
	}

	public static float modifyOutgoing(LivingEntity attacker, float amount) {
		if (amount <= 0.0f || !MaidCompat.isMaid(attacker)) {
			return amount;
		}
		ServerPlayerEntity owner = ownerAsServer(attacker);
		if (owner == null) {
			return amount;
		}
		Constitution c = TalentPlayerData.data(owner).constitution;
		float power = ConstitutionBodySynergy.effectivePoints(
				c, BodyCultivationStat.POWER, effectiveStat(owner, attacker.getUuid(), BodyCultivationStat.POWER));
		if (power <= 0.0f) {
			return amount;
		}
		return amount * (1.0f + BodyCultivationMechanics.powerBonusPct(power));
	}

	public static float modifyIncomingFallback(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0.0f || !MaidCompat.isMaid(victim)) {
			return amount;
		}
		ServerPlayerEntity owner = ownerAsServer(victim);
		if (owner == null) {
			return amount;
		}
		Constitution c = TalentPlayerData.data(owner).constitution;
		UUID id = victim.getUuid();
		if (MagicProtectionHandler.isMagicDamage(source)) {
			float spell = ConstitutionBodySynergy.effectivePoints(
					c, BodyCultivationStat.SPELL, effectiveStat(owner, id, BodyCultivationStat.SPELL));
			float dr = BodyCultivationMechanics.magicDr(spell);
			if (dr > 0.0f) {
				amount *= 1.0f - dr;
			}
		} else {
			float body = ConstitutionBodySynergy.effectivePoints(
					c, BodyCultivationStat.BODY, effectiveStat(owner, id, BodyCultivationStat.BODY));
			float dr = BodyCultivationMechanics.physicalDr(body);
			if (dr > 0.0f) {
				amount *= 1.0f - dr;
			}
		}
		return amount;
	}

	@Nullable
	private static ServerPlayerEntity ownerAsServer(LivingEntity maid) {
		LivingEntity owner = MaidCompat.getOwner(maid);
		return owner instanceof ServerPlayerEntity sp ? sp : null;
	}

	private static void setMul(
			LivingEntity entity,
			RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
			Identifier id,
			double amount
	) {
		EntityAttributeInstance inst = entity.getAttributeInstance(attr);
		if (inst == null) {
			return;
		}
		inst.removeModifier(id);
		if (Math.abs(amount) > 1.0E-6) {
			inst.addTemporaryModifier(new EntityAttributeModifier(
					id, amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	public static List<BodyCultivationNetworking.MaidBodyEntry> syncEntries(ServerPlayerEntity owner) {
		ensureKnownMaids(owner);
		List<BodyCultivationNetworking.MaidBodyEntry> out = new ArrayList<>();
		TalentPlayerData.Data od = TalentPlayerData.data(owner);
		for (Map.Entry<UUID, MaidBodyData> e : od.maidBodies.entrySet()) {
			UUID id = e.getKey();
			MaidBodyData d = e.getValue();
			LivingEntity loaded = findLoaded(owner, id);
			String name = d.displayName;
			if (loaded != null) {
				name = loaded.getName().getString();
				d.displayName = name;
			}
			if (name == null || name.isEmpty()) {
				name = id.toString().substring(0, 8);
			}
			out.add(new BodyCultivationNetworking.MaidBodyEntry(
					id, name, true, loaded != null,
					od.bodyTotalPoints, od.bodyPower, od.bodyHealth, od.bodySpeed, od.bodyBody, od.bodySpell,
					od.bodyUnspent()));
		}
		return out;
	}
}
