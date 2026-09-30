package com.betterenchants.client.talent;

import com.betterenchants.network.BodyCultivationNetworking;
import com.betterenchants.network.TalentNetworking;
import com.betterenchants.talent.BodyCultivationStat;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public final class TalentClientNetworking {
	private TalentClientNetworking() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TalentClientData.clear());

		ClientPlayNetworking.registerGlobalReceiver(TalentNetworking.SyncPayload.ID, (payload, context) ->
				context.client().execute(() -> {
					TalentClientData.apply(
							payload.constitutionId(),
							payload.talentIds(),
							payload.talentsLocked(),
							payload.needsSelection(),
							payload.rolls(),
							payload.refreshesRemaining()
					);
					MinecraftClient client = context.client();
					if (client.player == null) {
						return;
					}
					if (client.currentScreen instanceof com.betterenchants.client.genshinui.character.BetterCharacterScreen hub) {
						hub.reloadFromData();
					}
					if (!payload.needsSelection()) {
						if (client.currentScreen instanceof TalentSelectScreen) {
							client.setScreen(null);
						} else if (client.currentScreen instanceof TalentFreeSelectScreen free
								&& free.shouldCloseAfterSync(false)) {
							client.setScreen(null);
						}
						return;
					}
					// 测试自选界面优先，勿被强制随机组界面顶掉
					if (client.currentScreen instanceof TalentFreeSelectScreen) {
						return;
					}
					// JOIN 时 OpenSelect 常因频道未就绪丢失；Sync 到达后主动打开
					if (client.currentScreen instanceof TalentSelectScreen select) {
						select.reloadFromData();
					} else {
						client.setScreen(new TalentSelectScreen(client.currentScreen, true));
					}
				}));

		ClientPlayNetworking.registerGlobalReceiver(TalentNetworking.OpenSelectPayload.ID, (payload, context) ->
				context.client().execute(() -> {
					MinecraftClient client = context.client();
					if (client.player == null) {
						return;
					}
					if (client.currentScreen instanceof TalentFreeSelectScreen) {
						return;
					}
					if (client.currentScreen instanceof TalentSelectScreen select) {
						select.reloadFromData();
						return;
					}
					client.setScreen(new TalentSelectScreen(null, true));
				}));

		ClientPlayNetworking.registerGlobalReceiver(TalentNetworking.OpenFreeSelectPayload.ID, (payload, context) ->
				context.client().execute(() -> {
					MinecraftClient client = context.client();
					if (client.player == null) {
						return;
					}
					if (client.currentScreen instanceof TalentFreeSelectScreen) {
						return;
					}
					client.setScreen(new TalentFreeSelectScreen(client.currentScreen));
				}));

		ClientPlayNetworking.registerGlobalReceiver(BodyCultivationNetworking.SyncPayload.ID, (payload, context) ->
				context.client().execute(() -> {
					TalentClientData.applyBody(
							payload.totalPoints(),
							payload.power(),
							payload.health(),
							payload.speed(),
							payload.body(),
							payload.spell(),
							payload.unspent(),
							payload.respecCount(),
							payload.respecReadyAt()
					);
					MinecraftClient client = context.client();
					if (client.currentScreen instanceof com.betterenchants.client.genshinui.character.BetterCharacterScreen screen) {
						screen.reloadFromData();
					}
				}));

		ClientPlayNetworking.registerGlobalReceiver(BodyCultivationNetworking.MaidSyncPayload.ID, (payload, context) ->
				context.client().execute(() -> {
					TalentClientData.applyMaids(payload.entries());
					MinecraftClient client = context.client();
					if (client.currentScreen instanceof com.betterenchants.client.genshinui.character.BetterCharacterScreen screen) {
						screen.reloadFromData();
					}
				}));
	}

	public static void confirmRoll(int rollIndex) {
		if (!ClientPlayNetworking.canSend(TalentNetworking.ConfirmRollPayload.ID)) {
			return;
		}
		ClientPlayNetworking.send(new TalentNetworking.ConfirmRollPayload(rollIndex));
	}

	public static void refreshRoll() {
		if (!ClientPlayNetworking.canSend(TalentNetworking.RefreshPayload.ID)) {
			return;
		}
		ClientPlayNetworking.send(new TalentNetworking.RefreshPayload());
	}

	public static void submitFreeSelect(String constitutionId, List<String> talentIds) {
		if (constitutionId == null || talentIds == null
				|| !ClientPlayNetworking.canSend(TalentNetworking.FreeSelectSubmitPayload.ID)) {
			return;
		}
		ClientPlayNetworking.send(new TalentNetworking.FreeSelectSubmitPayload(constitutionId, talentIds));
	}

	public static void allocateBody(BodyCultivationStat stat, int amount) {
		if (stat == null || !ClientPlayNetworking.canSend(BodyCultivationNetworking.AllocatePayload.ID)) {
			return;
		}
		ClientPlayNetworking.send(new BodyCultivationNetworking.AllocatePayload(stat.id(), amount, ""));
	}

	public static void respecBody() {
		if (!ClientPlayNetworking.canSend(BodyCultivationNetworking.RespecPayload.ID)) {
			return;
		}
		ClientPlayNetworking.send(new BodyCultivationNetworking.RespecPayload(""));
	}

	public static void requestMaidSync() {
		if (!ClientPlayNetworking.canSend(BodyCultivationNetworking.RequestMaidSyncPayload.ID)) {
			return;
		}
		ClientPlayNetworking.send(new BodyCultivationNetworking.RequestMaidSyncPayload());
	}
}
