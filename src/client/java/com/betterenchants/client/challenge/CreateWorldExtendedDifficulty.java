package com.betterenchants.client.challenge;

import com.betterenchants.challenge.WorldChallengeMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.world.Difficulty;

/**
 * 创建世界难度循环：和平→简单→普通→困难→噩梦→天启→和平。
 * 极限模式仅循环：困难→噩梦→天启（底层难度仍为 HARD）。
 */
@Environment(EnvType.CLIENT)
public enum CreateWorldExtendedDifficulty {
	PEACEFUL(Difficulty.PEACEFUL, WorldChallengeMode.OFF, "和平"),
	EASY(Difficulty.EASY, WorldChallengeMode.OFF, "简单"),
	NORMAL(Difficulty.NORMAL, WorldChallengeMode.OFF, "普通"),
	HARD(Difficulty.HARD, WorldChallengeMode.OFF, "困难"),
	NIGHTMARE(Difficulty.HARD, WorldChallengeMode.NIGHTMARE, "噩梦"),
	APOCALYPSE(Difficulty.HARD, WorldChallengeMode.APOCALYPSE, "天启");

	private final Difficulty vanilla;
	private final WorldChallengeMode challenge;
	private final String labelZh;

	CreateWorldExtendedDifficulty(Difficulty vanilla, WorldChallengeMode challenge, String labelZh) {
		this.vanilla = vanilla;
		this.challenge = challenge;
		this.labelZh = labelZh;
	}

	public Difficulty vanilla() {
		return vanilla;
	}

	public WorldChallengeMode challenge() {
		return challenge;
	}

	public String labelZh() {
		return labelZh;
	}

	public Text optionMessage() {
		return Text.translatable("options.difficulty").append(": ").append(Text.literal(labelZh));
	}

	public Text tooltip() {
		return switch (this) {
			case NIGHTMARE -> Text.literal("困难基准再 ×2（伤害 / 饥饿），无词条");
			case APOCALYPSE -> Text.literal("噩梦基准 + 怪物词条/属性成长（天启等级上限 100；词条分 10 档）");
			case HARD -> Text.literal("极限模式下可在 困难 / 噩梦 / 天启 间切换");
			default -> vanilla.getInfo();
		};
	}

	public static CreateWorldExtendedDifficulty current(Difficulty vanilla, WorldChallengeMode mode) {
		if (mode == WorldChallengeMode.APOCALYPSE) {
			return APOCALYPSE;
		}
		if (mode == WorldChallengeMode.NIGHTMARE) {
			return NIGHTMARE;
		}
		return switch (vanilla) {
			case PEACEFUL -> PEACEFUL;
			case EASY -> EASY;
			case NORMAL -> NORMAL;
			case HARD -> HARD;
		};
	}

	/** 极限：原版 getDifficulty 恒为 HARD，只看挑战档。 */
	public static CreateWorldExtendedDifficulty forHardcore(WorldChallengeMode mode) {
		if (mode == WorldChallengeMode.APOCALYPSE) {
			return APOCALYPSE;
		}
		if (mode == WorldChallengeMode.NIGHTMARE) {
			return NIGHTMARE;
		}
		return HARD;
	}

	public CreateWorldExtendedDifficulty next() {
		return switch (this) {
			case PEACEFUL -> EASY;
			case EASY -> NORMAL;
			case NORMAL -> HARD;
			case HARD -> NIGHTMARE;
			case NIGHTMARE -> APOCALYPSE;
			case APOCALYPSE -> PEACEFUL;
		};
	}

	/** 极限模式：困难 → 噩梦 → 天启 → 困难。 */
	public CreateWorldExtendedDifficulty nextHardcore() {
		return switch (this) {
			case NIGHTMARE -> APOCALYPSE;
			case APOCALYPSE -> HARD;
			default -> NIGHTMARE;
		};
	}
}
