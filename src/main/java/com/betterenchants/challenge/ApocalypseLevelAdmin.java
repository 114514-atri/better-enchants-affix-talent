package com.betterenchants.challenge;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Difficulty;

/**
 * 管理端改天启等级后：确保模式开启，并刷新已加载实体的基础强度。
 */
public final class ApocalypseLevelAdmin {
	private ApocalypseLevelAdmin() {
	}

	/**
	 * @param enableApocalypseIfNeeded 等级 &gt; 0 时自动切到天启
	 * @return 刷新到的实体数
	 */
	public static int applyLevel(MinecraftServer server, int level, boolean enableApocalypseIfNeeded) {
		WorldChallengeState state = WorldChallengeState.get(server);
		int clamped = ApocalypseScale.clampLevel(level);
		state.setLevel(clamped);
		if (enableApocalypseIfNeeded && clamped > 0 && !state.getMode().isApocalypse()) {
			state.setMode(WorldChallengeMode.APOCALYPSE);
			server.setDifficulty(Difficulty.HARD, true);
		}
		if (!state.getMode().isApocalypse()) {
			return clearLoaded(server);
		}
		return refreshLoaded(server, state.getApocalypseLevel());
	}

	public static int refreshLoaded(MinecraftServer server, int level) {
		int n = 0;
		for (ServerWorld world : server.getWorlds()) {
			for (Entity entity : world.iterateEntities()) {
				if (!(entity instanceof LivingEntity living) || MobAffixHolder.isPlayer(living)) {
					continue;
				}
				ApocalypseMobPower.apply(living, level);
				if (MobAffixHolder.hasRolled(living) && !MobAffixHolder.get(living).isEmpty()) {
					MobAffixApplier.onApplied(living, MobAffixHolder.get(living), level,
							MobAffixHolder.getPowerLevel(living));
				} else if (!MobAffixHolder.hasRolled(living)) {
					MobAffixRoller.tryRoll(living, level, living.getRandom());
				}
				n++;
			}
		}
		return n;
	}

	public static int clearLoaded(MinecraftServer server) {
		int n = 0;
		for (ServerWorld world : server.getWorlds()) {
			for (Entity entity : world.iterateEntities()) {
				if (!(entity instanceof LivingEntity living) || MobAffixHolder.isPlayer(living)) {
					continue;
				}
				ApocalypseMobPower.clear(living);
				n++;
			}
		}
		return n;
	}
}
