package com.betterenchants.command;

import com.betterenchants.challenge.ApocalypseScale;
import com.betterenchants.challenge.WorldChallengeMode;
import com.betterenchants.challenge.WorldChallengeState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** `/challenge` 查询或设置噩梦/天启。 */
public final class ChallengeCommands {
	private ChallengeCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("challenge")
				.executes(ctx -> status(ctx.getSource()))
				.then(CommandManager.literal("status")
						.executes(ctx -> status(ctx.getSource())))
				.then(CommandManager.literal("mode")
						.requires(src -> src.hasPermissionLevel(2))
						.then(CommandManager.argument("mode", StringArgumentType.word())
								.executes(ctx -> {
									WorldChallengeMode mode = WorldChallengeMode.byKey(StringArgumentType.getString(ctx, "mode"));
									WorldChallengeState state = WorldChallengeState.get(ctx.getSource().getServer());
									state.setMode(mode);
									if (mode.forcesHardDifficulty()) {
										ctx.getSource().getServer().setDifficulty(net.minecraft.world.Difficulty.HARD, true);
									}
									ctx.getSource().sendFeedback(() -> Text.literal("挑战模式 → " + mode.labelZh()), true);
									return 1;
								})))
				.then(CommandManager.literal("level")
						.requires(src -> src.hasPermissionLevel(2))
						.then(CommandManager.argument("level", IntegerArgumentType.integer(0, WorldChallengeState.MAX_LEVEL))
								.executes(ctx -> {
									int refreshed = com.betterenchants.challenge.ApocalypseLevelAdmin.applyLevel(
											ctx.getSource().getServer(),
											IntegerArgumentType.getInteger(ctx, "level"),
											true);
									WorldChallengeState state = WorldChallengeState.get(ctx.getSource().getServer());
									ctx.getSource().sendFeedback(() -> Text.literal(
											"天启等级 → " + state.getApocalypseLevel()
													+ " | 已刷新实体 " + refreshed), true);
									return 1;
								})))
				.then(CommandManager.literal("points")
						.requires(src -> src.hasPermissionLevel(2))
						.then(CommandManager.argument("points", LongArgumentType.longArg(0))
								.executes(ctx -> {
									WorldChallengeState state = WorldChallengeState.get(ctx.getSource().getServer());
									state.setPoints(LongArgumentType.getLong(ctx, "points"));
									ctx.getSource().sendFeedback(
											() -> Text.literal("天启点数 → " + state.getApocalypsePoints()), true);
									return 1;
								})))
				.then(CommandManager.literal("addpoints")
						.requires(src -> src.hasPermissionLevel(2))
						.then(CommandManager.argument("amount", LongArgumentType.longArg(1))
								.executes(ctx -> {
									WorldChallengeState state = WorldChallengeState.get(ctx.getSource().getServer());
									long amount = LongArgumentType.getLong(ctx, "amount");
									int gained = state.addPoints(amount);
									ctx.getSource().sendFeedback(() -> Text.literal(
											"+" + amount + " 点，升 " + gained + " 级 → L" + state.getApocalypseLevel()
													+ " (" + state.getApocalypsePoints() + "/" + state.needForNextLevel() + ")"), true);
									return 1;
								}))));
	}

	private static int status(ServerCommandSource source) {
		WorldChallengeState state = WorldChallengeState.get(source.getServer());
		WorldChallengeMode mode = state.getMode();
		if (!mode.isApocalypse()) {
			source.sendFeedback(() -> Text.literal("挑战模式：" + mode.labelZh()), false);
			return mode.id();
		}
		long need = state.needForNextLevel();
		source.sendFeedback(() -> Text.literal(
				"挑战模式：天启 | 等级 " + state.getApocalypseLevel() + "/" + WorldChallengeState.MAX_LEVEL
						+ " | 进度 " + state.getApocalypsePoints() + "/" + need
						+ " | 阶位 T" + ApocalypseScale.tier(state.getApocalypseLevel())), false);
		return state.getApocalypseLevel();
	}
}
