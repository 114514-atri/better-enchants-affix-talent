package com.betterenchants.challenge;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * 噩梦档统一倍率（≈困难 ×2）。
 * 天启 = 噩梦倍率 + 词条，因此同样吃此倍率。
 */
public final class NightmareRules {
	public static final float DAMAGE_MULT = 2.0f;
	public static final float EXHAUSTION_MULT = 2.0f;

	private NightmareRules() {
	}

	/** 噩梦或天启（天启继承噩梦数值）。 */
	public static boolean usesNightmareBase(World world) {
		if (!(world instanceof ServerWorld server)) {
			return false;
		}
		WorldChallengeMode mode = WorldChallengeState.mode(server);
		return mode.isNightmare() || mode.isApocalypse();
	}

	public static float playerIncomingMult(LivingEntity victim) {
		if (!(victim instanceof PlayerEntity) || !usesNightmareBase(victim.getWorld())) {
			return 1.0f;
		}
		return DAMAGE_MULT;
	}

	public static float exhaustionMult(PlayerEntity player) {
		return usesNightmareBase(player.getWorld()) ? EXHAUSTION_MULT : 1.0f;
	}
}
