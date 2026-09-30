package com.betterenchants.command;

import com.betterenchants.network.BodyCultivationNetworking;
import com.betterenchants.network.TalentNetworking;
import com.betterenchants.talent.BodyCultivationMechanics;
import com.betterenchants.talent.BodyCultivationStat;
import com.betterenchants.talent.Constitution;
import com.betterenchants.talent.MaidBodyCultivation;
import com.betterenchants.talent.Talent;
import com.betterenchants.talent.TalentBootstrap;
import com.betterenchants.talent.TalentPlayerData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.EnumSet;
import java.util.Set;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class TalentCommands {
	private TalentCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("constitution")
				.executes(ctx -> {
					ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
					Constitution c = TalentPlayerData.getConstitution(p);
					ctx.getSource().sendFeedback(() -> Text.literal("体质：" + (c == null ? "无" : c.labelZh())), false);
					return 1;
				})
				.then(CommandManager.literal("set")
						.requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.argument("id", StringArgumentType.word())
								.executes(ctx -> {
									ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
									Constitution c = Constitution.byId(StringArgumentType.getString(ctx, "id"));
									if (c == null) {
										ctx.getSource().sendError(Text.literal("未知体质"));
										return 0;
									}
									TalentPlayerData.setConstitution(p, c);
									BodyCultivationMechanics.applyAttributes(p);
									TalentNetworking.syncTo(p);
									BodyCultivationNetworking.syncTo(p);
									ctx.getSource().sendFeedback(() -> Text.literal("体质 → " + c.labelZh()), true);
									return 1;
								}))));

		dispatcher.register(CommandManager.literal("talent")
				.executes(ctx -> {
					ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
					for (String line : TalentPlayerData.describe(p)) {
						ctx.getSource().sendFeedback(() -> Text.literal(line), false);
					}
					if (!TalentPlayerData.talentsLocked(p)) {
						ctx.getSource().sendFeedback(() -> Text.literal("§e未锁定：打开选择界面或 /talent refresh|confirm <0-2>"), false);
						TalentBootstrap.queueOpenSelect(p);
					}
					return 1;
				})
				.then(CommandManager.literal("refresh")
						.executes(ctx -> {
							ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
							if (TalentPlayerData.talentsLocked(p)) {
								ctx.getSource().sendError(Text.literal("天赋已锁定"));
								return 0;
							}
							if (!TalentPlayerData.tryRefreshTalentRoll(p)) {
								ctx.getSource().sendError(Text.literal("无法再刷新"));
								return 0;
							}
							TalentNetworking.syncTo(p);
							ctx.getSource().sendFeedback(() -> Text.literal(
									"已刷新，剩余次数：" + TalentPlayerData.refreshesRemaining(p)), false);
							return 1;
						}))
				.then(CommandManager.literal("confirm")
						.then(CommandManager.argument("index", IntegerArgumentType.integer(0, 2))
								.executes(ctx -> {
									ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
									int idx = IntegerArgumentType.getInteger(ctx, "index");
									if (!TalentPlayerData.confirmTalentRoll(p, idx)) {
										ctx.getSource().sendError(Text.literal("确认失败"));
										return 0;
									}
									TalentNetworking.syncTo(p);
									ctx.getSource().sendFeedback(() -> Text.literal("天赋已锁定"), false);
									return 1;
								})))
				.then(CommandManager.literal("clear")
						.requires(s -> s.hasPermissionLevel(2))
						.executes(ctx -> {
							ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
							TalentPlayerData.clearTalents(p);
							TalentPlayerData.ensureInitialTalentRoll(p);
							TalentBootstrap.queueOpenSelect(p);
							ctx.getSource().sendFeedback(() -> Text.literal("已清空并重新随机天赋"), true);
							return 1;
						}))
				.then(CommandManager.literal("set")
						.requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.argument("ids", StringArgumentType.greedyString())
								.executes(ctx -> {
									ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
									String[] parts = StringArgumentType.getString(ctx, "ids").split("[\\s,]+");
									Set<Talent> set = EnumSet.noneOf(Talent.class);
									for (String part : parts) {
										Talent t = Talent.byId(part.trim());
										if (t != null) {
											set.add(t);
										}
									}
									TalentPlayerData.setTalents(p, set, true);
									TalentNetworking.syncTo(p);
									ctx.getSource().sendFeedback(() -> Text.literal("已设置 " + set.size() + " 个天赋"), true);
									return 1;
								}))));

		dispatcher.register(CommandManager.literal("bodycult")
				.executes(ctx -> {
					ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
					for (String line : TalentPlayerData.describe(p)) {
						if (line.startsWith("体质升级")) {
							ctx.getSource().sendFeedback(() -> Text.literal(line), false);
						}
					}
					return 1;
				})
				.then(CommandManager.literal("points")
						.requires(s -> s.hasPermissionLevel(2))
						.then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 9999))
								.executes(ctx -> {
									ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
									int amt = IntegerArgumentType.getInteger(ctx, "amount");
									TalentPlayerData.data(p).bodyTotalPoints += amt;
									MaidBodyCultivation.refreshFollowingMaids(p);
									BodyCultivationNetworking.syncTo(p);
									ctx.getSource().sendFeedback(() -> Text.literal("已给予 " + amt + " 体质点"), true);
									return 1;
								})))
				.then(CommandManager.literal("add")
						.then(CommandManager.argument("stat", StringArgumentType.word())
								.then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 100))
										.executes(ctx -> {
											ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
											BodyCultivationStat stat = BodyCultivationStat.byId(
													StringArgumentType.getString(ctx, "stat"));
											if (stat == null) {
												ctx.getSource().sendError(Text.literal("未知属性"));
												return 0;
											}
											int amt = IntegerArgumentType.getInteger(ctx, "amount");
											if (!BodyCultivationMechanics.tryAllocate(p, stat, amt)) {
												ctx.getSource().sendError(Text.literal("点数不足或已达上限"));
												return 0;
											}
											ctx.getSource().sendFeedback(() -> Text.literal("已加点 " + stat.id() + " +" + amt), false);
											return 1;
										}))))
				.then(CommandManager.literal("respec")
						.executes(ctx -> {
							ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
							if (!BodyCultivationMechanics.tryRespec(p, false)) {
								ctx.getSource().sendError(Text.literal("无法洗点（无加点 / 冷却中 / 等级不足）"));
								return 0;
							}
							ctx.getSource().sendFeedback(() -> Text.literal("已洗点"), false);
							return 1;
						})
						.then(CommandManager.literal("free")
								.requires(s -> s.hasPermissionLevel(2))
								.executes(ctx -> {
									ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
									if (!BodyCultivationMechanics.tryRespec(p, true)) {
										ctx.getSource().sendError(Text.literal("没有已分配点数"));
										return 0;
									}
									ctx.getSource().sendFeedback(() -> Text.literal("已免费洗点"), true);
									return 1;
								}))));
	}
}
