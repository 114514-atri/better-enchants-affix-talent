package com.betterenchants.compat;

import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Soft bridge for optional Touhou totem companion content (same author pack).
 * <p>
 * When {@code com.betterenchants.touhou.TouhouTotemMechanics} is on the classpath
 * (private/full pack), flight gates and affix pierce apply. Otherwise no-ops.
 * Soft-suggests Touhou Little Maid via {@link MaidCompat}.
 */
public final class TouhouCompat {
	private static final String MECHANICS = "com.betterenchants.touhou.TouhouTotemMechanics";

	private TouhouCompat() {
	}

	public static boolean mechanicsPresent() {
		try {
			Class.forName(MECHANICS, false, TouhouCompat.class.getClassLoader());
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	/** Suppress sword-flight / belt creative flight while suppression totems are active. */
	public static boolean blocksFlight(@Nullable PlayerEntity player) {
		if (player == null || !mechanicsPresent()) {
			return false;
		}
		try {
			Class<?> c = Class.forName(MECHANICS);
			Object r = c.getMethod("blocksFlight", PlayerEntity.class).invoke(null, player);
			return r instanceof Boolean b && b;
		} catch (Throwable t) {
			return false;
		}
	}

	/** Extra affix pierce from active totems; 0 when totem pack absent. */
	public static float affixPierce(@Nullable PlayerEntity attacker) {
		if (attacker == null || !mechanicsPresent()) {
			return 0.0f;
		}
		try {
			Class<?> c = Class.forName(MECHANICS);
			Object r = c.getMethod("affixPierce", PlayerEntity.class).invoke(null, attacker);
			return r instanceof Number n ? n.floatValue() : 0.0f;
		} catch (Throwable t) {
			return 0.0f;
		}
	}
}
