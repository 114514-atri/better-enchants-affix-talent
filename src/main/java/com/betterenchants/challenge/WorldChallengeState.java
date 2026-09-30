package com.betterenchants.challenge;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

/** 噩梦 / 天启世界存档：模式、天启等级与点数、里程碑。 */
public final class WorldChallengeState extends PersistentState {
	public static final String ID = "better_enchants_world_challenge";
	public static final int MAX_LEVEL = 100;

	private WorldChallengeMode mode = WorldChallengeMode.OFF;
	private int apocalypseLevel;
	private long apocalypsePoints;
	private boolean deathProgressPenalty = true;
	private final Set<String> milestones = new HashSet<>();

	public WorldChallengeMode getMode() {
		return mode;
	}

	public void setMode(WorldChallengeMode mode) {
		this.mode = mode == null ? WorldChallengeMode.OFF : mode;
		markDirty();
	}

	public int getApocalypseLevel() {
		return apocalypseLevel;
	}

	public long getApocalypsePoints() {
		return apocalypsePoints;
	}

	public boolean deathProgressPenalty() {
		return deathProgressPenalty;
	}

	public void setDeathProgressPenalty(boolean value) {
		this.deathProgressPenalty = value;
		markDirty();
	}

	public boolean hasMilestone(String key) {
		return milestones.contains(key);
	}

	public void addMilestone(String key) {
		if (milestones.add(key)) {
			markDirty();
		}
	}

	/** 升到下一档所需点数（曲线见 {@link ApocalypseScale#needForNextLevel(int)}）。 */
	public static long needForNextLevel(int level) {
		return ApocalypseScale.needForNextLevel(level);
	}

	public long needForNextLevel() {
		if (apocalypseLevel >= MAX_LEVEL) {
			return 0L;
		}
		return needForNextLevel(apocalypseLevel);
	}

	/**
	 * @return 实际升了几级
	 */
	public int addPoints(long amount) {
		if (!mode.isApocalypse() || amount <= 0L || apocalypseLevel >= MAX_LEVEL) {
			return 0;
		}
		apocalypsePoints += amount;
		int gained = 0;
		while (apocalypseLevel < MAX_LEVEL) {
			long need = needForNextLevel(apocalypseLevel);
			if (apocalypsePoints < need) {
				break;
			}
			apocalypsePoints -= need;
			apocalypseLevel++;
			gained++;
		}
		markDirty();
		return gained;
	}

	/** 死亡：回退当前级进度约 15%，不降级。 */
	public void applyDeathProgressPenalty() {
		if (!mode.isApocalypse() || !deathProgressPenalty || apocalypseLevel >= MAX_LEVEL) {
			return;
		}
		long need = needForNextLevel(apocalypseLevel);
		long cut = Math.max(1L, Math.round(need * 0.15));
		apocalypsePoints = Math.max(0L, apocalypsePoints - cut);
		markDirty();
	}

	public void setLevel(int level) {
		apocalypseLevel = Math.max(0, Math.min(MAX_LEVEL, level));
		markDirty();
	}

	public void setPoints(long points) {
		apocalypsePoints = Math.max(0L, points);
		markDirty();
	}

	public static WorldChallengeState get(MinecraftServer server) {
		ServerWorld overworld = server.getWorld(World.OVERWORLD);
		if (overworld == null) {
			overworld = server.getOverworld();
		}
		PersistentStateManager manager = overworld.getPersistentStateManager();
		return manager.getOrCreate(new PersistentState.Type<>(WorldChallengeState::new, WorldChallengeState::fromNbt, null), ID);
	}

	public static WorldChallengeState get(ServerWorld world) {
		return get(world.getServer());
	}

	public static WorldChallengeMode mode(ServerWorld world) {
		return get(world).getMode();
	}

	public static WorldChallengeMode mode(MinecraftServer server) {
		return get(server).getMode();
	}

	private static WorldChallengeState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		WorldChallengeState state = new WorldChallengeState();
		state.mode = WorldChallengeMode.byId(nbt.getInt("mode"));
		state.apocalypseLevel = Math.max(0, Math.min(MAX_LEVEL, nbt.getInt("level")));
		state.apocalypsePoints = Math.max(0L, nbt.getLong("points"));
		if (nbt.contains("deathPenalty")) {
			state.deathProgressPenalty = nbt.getBoolean("deathPenalty");
		}
		NbtList list = nbt.getList("milestones", NbtElement.STRING_TYPE);
		for (int i = 0; i < list.size(); i++) {
			state.milestones.add(list.getString(i));
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putInt("mode", mode.id());
		nbt.putInt("level", apocalypseLevel);
		nbt.putLong("points", apocalypsePoints);
		nbt.putBoolean("deathPenalty", deathProgressPenalty);
		NbtList list = new NbtList();
		for (String key : milestones) {
			list.add(NbtString.of(key));
		}
		nbt.put("milestones", list);
		return nbt;
	}
}
