package com.betterenchants.challenge;

/**
 * 创建世界时客户端选择的挑战档；仅在真正创建世界时写入 {@link WorldChallengeState}。
 */
public final class WorldChallengePending {
	private static WorldChallengeMode pending = WorldChallengeMode.OFF;
	private static boolean commitOnCreate;

	private WorldChallengePending() {
	}

	public static void set(WorldChallengeMode mode) {
		pending = mode == null ? WorldChallengeMode.OFF : mode;
	}

	public static WorldChallengeMode get() {
		return pending;
	}

	public static void markCreateCommitted() {
		commitOnCreate = true;
	}

	public static WorldChallengeMode consumeIfCommitted() {
		if (!commitOnCreate) {
			return WorldChallengeMode.OFF;
		}
		commitOnCreate = false;
		WorldChallengeMode m = pending;
		pending = WorldChallengeMode.OFF;
		return m;
	}
}
