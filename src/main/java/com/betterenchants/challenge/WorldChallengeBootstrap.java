package com.betterenchants.challenge;

import com.betterenchants.compat.OptionalPrivateHooks;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

/** 噩梦 / 天启启动、进度与刷怪词条。 */
public final class WorldChallengeBootstrap {
	private static long lastDay = Long.MIN_VALUE;

	private WorldChallengeBootstrap() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(WorldChallengeBootstrap::onServerStarted);
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof LivingEntity living) || living instanceof PlayerEntity) {
				return;
			}
			WorldChallengeState state = WorldChallengeState.get(world);
			if (!state.getMode().isApocalypse()) {
				return;
			}
			if (!MobAffixHolder.hasRolled(living)) {
				MobAffixRoller.tryRoll(living, state.getApocalypseLevel(), living.getRandom());
			} else if (!MobAffixHolder.get(living).isEmpty()) {
				com.betterenchants.network.MobAffixNetworking.sync(living);
			}
			if (OptionalPrivateHooks.isPrivateBossHost(living)) {
				com.betterenchants.boss.BossCombat.resyncShieldWithHealth(living);
			}
		});
		net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.START_TRACKING.register((tracked, player) -> {
			if (tracked instanceof LivingEntity living && !MobAffixHolder.get(living).isEmpty()) {
				com.betterenchants.network.MobAffixNetworking.syncTo(player, living);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity.getWorld() instanceof ServerWorld server) || entity instanceof PlayerEntity) {
				return;
			}
			MobAffixCombat.onDeath(entity, source);
			WorldChallengeState state = WorldChallengeState.get(server);
			if (!state.getMode().isApocalypse()) {
				return;
			}
			PlayerEntity playerKiller = resolvePlayerKiller(source.getAttacker(), source.getSource());
			if (playerKiller == null) {
				return;
			}
			long pts = killPoints(entity, server);
			if (pts > 0) {
				pts = Math.max(1L, Math.round(pts * ApocalypseScale.killPointScale(state.getApocalypseLevel())));
				int gained = state.addPoints(pts);
				if (gained > 0 && playerKiller instanceof ServerPlayerEntity sp) {
					sp.sendMessage(Text.literal("§6天启等级提升 → §e" + state.getApocalypseLevel()), false);
				}
			}
			if (entity instanceof EnderDragonEntity && !state.hasMilestone("dragon")) {
				state.addMilestone("dragon");
				state.addPoints(120);
			}
			if (entity instanceof WitherEntity && !state.hasMilestone("wither")) {
				state.addMilestone("wither");
				state.addPoints(100);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
			if (alive) {
				return;
			}
			WorldChallengeState state = WorldChallengeState.get(newP.getServerWorld());
			state.applyDeathProgressPenalty();
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
				(player, origin, destination) -> {
					WorldChallengeState state = WorldChallengeState.get(destination);
					if (!state.getMode().isApocalypse()) {
						return;
					}
					if (destination.getRegistryKey().equals(World.NETHER) && !state.hasMilestone("nether")) {
						state.addMilestone("nether");
						state.addPoints(40);
						player.sendMessage(Text.literal("§6天启里程碑：首次进入下界 +40"), false);
					} else if (destination.getRegistryKey().equals(World.END) && !state.hasMilestone("end")) {
						state.addMilestone("end");
						state.addPoints(40);
						player.sendMessage(Text.literal("§6天启里程碑：首次进入末地 +40"), false);
					}
				});
		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (!world.getRegistryKey().equals(World.OVERWORLD)) {
				return;
			}
			WorldChallengeState state = WorldChallengeState.get(world);
			if (!state.getMode().isApocalypse()) {
				return;
			}
			long day = world.getTimeOfDay() / 24000L;
			if (lastDay == Long.MIN_VALUE) {
				lastDay = day;
				return;
			}
			if (day != lastDay) {
				lastDay = day;
				long bonus = 6L + state.getApocalypseLevel() / 50L;
				bonus = Math.max(6L, Math.min(200L, bonus));
				int gained = state.addPoints(bonus);
				if (gained > 0) {
					for (ServerPlayerEntity p : world.getServer().getPlayerManager().getPlayerList()) {
						p.sendMessage(Text.literal("§6天启等级提升 → §e" + state.getApocalypseLevel()), false);
					}
				}
			}
			if (world.getTime() % 20L == 0L) {
				for (Entity e : world.iterateEntities()) {
					if (e instanceof LivingEntity living && !MobAffixHolder.isPlayer(living)) {
						MobAffixCombat.tickAura(living);
						if (MobAffixHolder.has(living, MobAffix.NIGHT_WALKER)
								&& world.getLightLevel(living.getBlockPos()) <= 7) {
							living.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, false, false));
						}
					}
				}
			}
		});
	}

	private static PlayerEntity resolvePlayerKiller(Entity attacker, Entity source) {
		if (attacker instanceof PlayerEntity p) {
			return p;
		}
		if (source instanceof PlayerEntity p) {
			return p;
		}
		if (attacker instanceof Tameable tameable && tameable.getOwner() instanceof PlayerEntity p) {
			return p;
		}
		return null;
	}

	private static void onServerStarted(MinecraftServer server) {
		WorldChallengeState state = WorldChallengeState.get(server);
		WorldChallengeMode pending = WorldChallengePending.consumeIfCommitted();
		if (pending.isEnabled() && !state.getMode().isEnabled()) {
			state.setMode(pending);
		}
		if (state.getMode().forcesHardDifficulty()) {
			server.setDifficulty(Difficulty.HARD, true);
		}
	}

	private static long killPoints(LivingEntity entity, ServerWorld world) {
		double dim = 1.0;
		if (world.getRegistryKey().equals(World.NETHER)) {
			dim = 1.15;
		} else if (world.getRegistryKey().equals(World.END)) {
			dim = 1.3;
		}
		long base = 1L;
		if (entity instanceof EnderDragonEntity || entity instanceof WitherEntity) {
			base = 120L;
		} else if (entity.hasCustomName() || entity.isGlowing()) {
			base = 5L;
		} else if (!(entity instanceof HostileEntity) && !(entity instanceof MobEntity)) {
			return 0L;
		}
		return Math.max(1L, Math.round(base * dim));
	}

	public static boolean allowsCreativeHurt(LivingEntity attacker) {
		return attacker != null && MobAffixHolder.has(attacker, MobAffix.GODSLAYER_CREATIVE);
	}
}
