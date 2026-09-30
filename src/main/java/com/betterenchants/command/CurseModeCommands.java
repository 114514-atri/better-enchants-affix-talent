package com.betterenchants.command;

import com.betterenchants.cursemode.CurseModeDifficulty;
import com.betterenchants.cursemode.CurseModeState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** `/cursemode` 查询或设置（仅控制台/未启用诅咒时；启用后仍会被作弊拦截？留作控制台）。 */
public final class CurseModeCommands {
	private CurseModeCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("cursemode")
				.requires(src -> src.hasPermissionLevel(2))
				.executes(ctx -> {
					CurseModeDifficulty d = CurseModeState.difficulty(ctx.getSource().getServer());
					ctx.getSource().sendFeedback(() -> Text.literal("诅咒模式：" + d.labelZh()), false);
					return d.id();
				})
				.then(CommandManager.argument("level", StringArgumentType.word())
						.executes(ctx -> {
							// 仅允许非玩家（控制台）修改，避免诅咒模式自破
							if (ctx.getSource().isExecutedByPlayer()) {
								ctx.getSource().sendError(Text.literal("指令被诅咒抹去了痕迹"));
								return 0;
							}
							CurseModeDifficulty d = CurseModeDifficulty.byKey(StringArgumentType.getString(ctx, "level"));
							CurseModeState.get(ctx.getSource().getServer()).setDifficulty(d);
							ctx.getSource().sendFeedback(() -> Text.literal("已设置诅咒模式：" + d.labelZh()), true);
							return 1;
						})));
	}
}
