package com.betterenchants.command;

import com.betterenchants.network.BodyCultivationNetworking;
import com.betterenchants.talent.BodyCultivationMechanics;
import com.betterenchants.talent.BodyCultivationStat;
import com.betterenchants.talent.MaidBodyCultivation;
import com.betterenchants.talent.TalentPlayerData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * {@code /get points <n>} / {@code /delete points <n>}：给予或扣除体质点。
 * 需开启作弊（权限 2），仅对执行者玩家生效；女仆点数预算与主人一致。
 */
public final class BodyPointsCommands {
	private BodyPointsCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("get")
				.requires(s -> s.hasPermissionLevel(2))
				.then(CommandManager.literal("points")
						.then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 99999))
								.executes(ctx -> grant(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount"))))));

		dispatcher.register(CommandManager.literal("delete")
				.requires(s -> s.hasPermissionLevel(2))
				.then(CommandManager.literal("points")
						.then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 99999))
								.executes(ctx -> revoke(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount"))))));
	}

	private static int grant(ServerCommandSource source, int amount) {
		ServerPlayerEntity player;
		try {
			player = source.getPlayerOrThrow();
		} catch (Exception e) {
			source.sendError(Text.translatable("message.better_enchants.body_cult.cmd_player_only"));
			return 0;
		}
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		d.bodyTotalPoints += amount;
		BodyCultivationMechanics.applyAttributes(player);
		MaidBodyCultivation.refreshFollowingMaids(player);
		BodyCultivationNetworking.syncTo(player);
		int total = d.bodyTotalPoints;
		source.sendFeedback(() -> Text.translatable(
				"message.better_enchants.body_cult.get_points", amount, total), true);
		return 1;
	}

	private static int revoke(ServerCommandSource source, int amount) {
		ServerPlayerEntity player;
		try {
			player = source.getPlayerOrThrow();
		} catch (Exception e) {
			source.sendError(Text.translatable("message.better_enchants.body_cult.cmd_player_only"));
			return 0;
		}
		TalentPlayerData.Data d = TalentPlayerData.data(player);
		int before = d.bodyTotalPoints;
		int remove = Math.min(amount, Math.max(0, before));
		d.bodyTotalPoints = Math.max(0, before - remove);
		clampSpentToTotal(d);
		BodyCultivationMechanics.applyAttributes(player);
		MaidBodyCultivation.refreshFollowingMaids(player);
		BodyCultivationNetworking.syncTo(player);
		int total = d.bodyTotalPoints;
		int finalRemove = remove;
		source.sendFeedback(() -> Text.translatable(
				"message.better_enchants.body_cult.delete_points", finalRemove, total), true);
		return 1;
	}

	private static void clampSpentToTotal(TalentPlayerData.Data d) {
		while (d.bodySpent() > d.bodyTotalPoints) {
			if (trimOne(d, BodyCultivationStat.SPELL)) {
				continue;
			}
			if (trimOne(d, BodyCultivationStat.BODY)) {
				continue;
			}
			if (trimOne(d, BodyCultivationStat.SPEED)) {
				continue;
			}
			if (trimOne(d, BodyCultivationStat.HEALTH)) {
				continue;
			}
			if (trimOne(d, BodyCultivationStat.POWER)) {
				continue;
			}
			break;
		}
	}

	private static boolean trimOne(TalentPlayerData.Data d, BodyCultivationStat stat) {
		int v = d.bodyStat(stat);
		if (v <= 0) {
			return false;
		}
		d.setBodyStat(stat, v - 1);
		return true;
	}
}
