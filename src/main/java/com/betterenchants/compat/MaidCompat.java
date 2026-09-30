package com.betterenchants.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

/**
 * 车万女仆（touhou_little_maid）软依赖：类型探测，避免硬引用 EntityMaid。
 */
public final class MaidCompat {
	public static final String MOD_ID = "touhou_little_maid";

	private MaidCompat() {
	}

	public static boolean isLoaded() {
		return FabricLoader.getInstance().isModLoaded(MOD_ID);
	}

	/** 是否为车万女仆实体（namespace + path）。 */
	public static boolean isMaid(@Nullable Entity entity) {
		if (entity == null || !isLoaded()) {
			return false;
		}
		Identifier id = EntityType.getId(entity.getType());
		return id != null && MOD_ID.equals(id.getNamespace()) && "maid".equals(id.getPath());
	}

	/**
	 * 玩家放出的效果不对女仆；女仆放出的附魔效果不对玩家。
	 */
	public static boolean shouldProtectFromOwnerEffects(@Nullable Entity target, @Nullable Entity owner) {
		if (target == null) {
			return false;
		}
		if (isMaid(target) && (owner == null || owner instanceof PlayerEntity)) {
			return true;
		}
		return target instanceof PlayerEntity && isMaid(owner);
	}

	/** 女仆主人（TameableEntity）；非女仆或不存在则 null。 */
	@Nullable
	public static LivingEntity getOwner(@Nullable Entity entity) {
		if (!isMaid(entity)) {
			return null;
		}
		if (entity instanceof TameableEntity tameable) {
			return tameable.getOwner();
		}
		return null;
	}

	@Nullable
	public static UUID getOwnerUuid(@Nullable Entity entity) {
		if (!isMaid(entity)) {
			return null;
		}
		if (entity instanceof TameableEntity tameable) {
			return tameable.getOwnerUuid();
		}
		return null;
	}

	public static boolean isOwnedBy(@Nullable Entity maid, @Nullable PlayerEntity player) {
		if (player == null || !isMaid(maid)) {
			return false;
		}
		if (maid instanceof TameableEntity tameable) {
			return player.equals(tameable.getOwner());
		}
		UUID ownerId = getOwnerUuid(maid);
		return ownerId != null && ownerId.equals(player.getUuid());
	}

	/** 附近属于该玩家的女仆。 */
	public static List<LivingEntity> findOwnedMaids(ServerPlayerEntity player, double range) {
		List<LivingEntity> out = new ArrayList<>();
		if (player == null || !isLoaded() || player.getWorld() == null) {
			return out;
		}
		Box box = player.getBoundingBox().expand(Math.max(8.0, range));
		for (LivingEntity e : player.getWorld().getEntitiesByClass(
				LivingEntity.class, box, ent -> isOwnedBy(ent, player))) {
			out.add(e);
		}
		return out;
	}
}
