package com.betterenchants.talent;

import com.betterenchants.network.BodyCultivationNetworking;
import com.betterenchants.network.TalentNetworking;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** 天赋 / 体质：首次进服随机体质 + 随机天赋组选择。通关继承见 copyFrom mixin。 */
public final class TalentBootstrap {
	/** JOIN 当下 S2C 频道常未就绪，延后重试打开选择界面。 */
	private static final Map<UUID, Integer> PENDING_OPEN_TICKS = new ConcurrentHashMap<>();
	private static final int OPEN_RETRY_TICKS = 100;

	private TalentBootstrap() {
	}

	public static void register() {
		// 单人换档不重启 JVM 时，必须清掉按 UUID 缓存的上一档天赋/体质
		ServerLifecycleEvents.SERVER_STARTING.register(server -> clearWorldSession());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearWorldSession());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			ensureAssigned(player);
			if (TalentPlayerData.needsTalentSelection(player)) {
				PENDING_OPEN_TICKS.put(player.getUuid(), OPEN_RETRY_TICKS);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				PENDING_OPEN_TICKS.remove(handler.getPlayer().getUuid()));
		ServerTickEvents.END_SERVER_TICK.register(TalentBootstrap::tickPendingOpen);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity killer
					&& entity instanceof net.minecraft.entity.LivingEntity living) {
				TalentMechanics.onKill(killer, living);
			}
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayerEntity sp && !world.isClient()) {
				TalentMechanics.onBlockBreak(sp, pos, state);
			}
		});
	}

	private static void clearWorldSession() {
		PENDING_OPEN_TICKS.clear();
		TalentPlayerData.clearSessionCache();
	}

	public static void ensureAssigned(ServerPlayerEntity player) {
		if (!TalentPlayerData.hasConstitution(player)) {
			Constitution rolled = Constitution.roll(player.getRandom());
			TalentPlayerData.setConstitution(player, rolled);
			player.sendMessage(
					Text.translatable("message.better_enchants.talent.constitution_awakened",
							TalentCatalog.constitutionName(rolled)),
					false);
		}
		TalentPlayerData.ensureInitialTalentRoll(player);
		BodyCultivationMechanics.applyAttributes(player);
		TalentNetworking.syncTo(player);
		BodyCultivationNetworking.syncTo(player);
		if (TalentPlayerData.needsTalentSelection(player)) {
			TalentNetworking.requestOpenSelect(player);
		}
	}

	/** 命令 / 重置天赋后再次排队打开。 */
	public static void queueOpenSelect(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		PENDING_OPEN_TICKS.put(player.getUuid(), OPEN_RETRY_TICKS);
		TalentNetworking.syncTo(player);
		TalentNetworking.requestOpenSelect(player);
	}

	/** 测试指令：打开体质+天赋自选界面（不依赖随机组 / 作弊）。 */
	public static void openFreeSelect(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		PENDING_OPEN_TICKS.remove(player.getUuid());
		// 仅发打开包；避免 Sync(needsSelection=false) 与自选界面竞态关屏
		TalentNetworking.requestOpenFreeSelect(player);
	}

	private static void tickPendingOpen(MinecraftServer server) {
		if (PENDING_OPEN_TICKS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Integer>> it = PENDING_OPEN_TICKS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> e = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			if (!TalentPlayerData.needsTalentSelection(player)) {
				it.remove();
				continue;
			}
			boolean canOpen = ServerPlayNetworking.canSend(player, TalentNetworking.OpenSelectPayload.ID);
			if (canOpen) {
				// 频道就绪后再 sync + open，避免 JOIN 瞬间丢包；客户端 Sync 也会自开界面
				TalentNetworking.syncTo(player);
				TalentNetworking.requestOpenSelect(player);
				it.remove();
				continue;
			}
			int left = e.getValue() - 1;
			if (left <= 0) {
				TalentNetworking.syncTo(player);
				it.remove();
			} else {
				e.setValue(left);
			}
		}
	}
}
