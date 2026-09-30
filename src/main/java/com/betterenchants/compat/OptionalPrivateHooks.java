package com.betterenchants.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Soft hooks for optional private companion content (Scarlet realm, sacred stations, etc.).
 * <p>
 * If the private extension mod is absent, every method is a no-op / null-safe fallback.
 * Forks may delete this class and all call sites.
 * <p>
 * Allowed references: same-author projects such as {@code yandere}, and this author's
 * original private content (Scarlet / sacred). No third-party mod hard dependency.
 */
public final class OptionalPrivateHooks {
	private static final String PRIVATE_MOD_ID = "better_enchants_private";

	private OptionalPrivateHooks() {
	}

	public static boolean privateContentPresent() {
		return FabricLoader.getInstance().isModLoaded(PRIVATE_MOD_ID)
				|| classPresent("com.betterenchants.boss.BossMinion")
				|| classPresent("com.betterenchants.dimension.ModDimensions");
	}

	public static boolean classPresent(String fqn) {
		try {
			Class.forName(fqn, false, OptionalPrivateHooks.class.getClassLoader());
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	/** Scarlet realm dimension key when private dimension pack is installed. */
	@Nullable
	public static RegistryKey<World> scarletDimensionKey() {
		if (!privateContentPresent()) {
			return null;
		}
		try {
			return RegistryKey.of(RegistryKeys.WORLD, Identifier.of("better_enchants", "scarlet_realm"));
		} catch (Throwable t) {
			return null;
		}
	}

	/** True when {@code entity} is a private boss minion / boss host (reflective). */
	public static boolean isPrivateBossHost(Object entity) {
		if (entity == null || !privateContentPresent()) {
			return false;
		}
		try {
			Class<?> c = Class.forName("com.betterenchants.boss.BossMinion");
			return c.isInstance(entity);
		} catch (Throwable t) {
			return false;
		}
	}

	/** Optional spell-player bridge; returns null when spell pack is absent. */
	@Nullable
	public static Object spellPlayerBridge() {
		return null;
	}

	/** True when the player is in Scarlet realm and private dimension content is loaded. */
	public static boolean isInScarletRealm(Object world) {
		if (world == null || !privateContentPresent()) {
			return false;
		}
		try {
			var key = scarletDimensionKey();
			if (key == null) {
				return false;
			}
			Object regKey = world.getClass().getMethod("getRegistryKey").invoke(world);
			return key.equals(regKey);
		} catch (Throwable t) {
			return false;
		}
	}

	/** Optional mana refresh when private spell pack is present; otherwise no-op. */
	public static void refreshSpellMana(Object player) {
		if (player == null || !classPresent("com.betterenchants.spell.SpellPlayers")) {
			return;
		}
		try {
			Class<?> c = Class.forName("com.betterenchants.spell.SpellPlayers");
			c.getMethod("refreshMaxMana", Class.forName("net.minecraft.server.network.ServerPlayerEntity"))
					.invoke(null, player);
		} catch (Throwable ignored) {
			try {
				Class<?> c = Class.forName("com.betterenchants.spell.SpellPlayers");
				for (var m : c.getMethods()) {
					if (m.getName().equals("refreshMaxMana") && m.getParameterCount() == 1) {
						m.invoke(null, player);
						return;
					}
				}
			} catch (Throwable ignored2) {
			}
		}
	}

	/** Optional spell data bridge; returns null when absent. */
	public static Object getSpellData(Object player) {
		if (player == null || !classPresent("com.betterenchants.spell.SpellPlayers")) {
			return null;
		}
		try {
			Class<?> c = Class.forName("com.betterenchants.spell.SpellPlayers");
			for (var m : c.getMethods()) {
				if (m.getName().equals("get") && m.getParameterCount() == 1) {
					return m.invoke(null, player);
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	public static void setSpellData(Object player, Object data) {
		if (player == null || data == null || !classPresent("com.betterenchants.spell.SpellPlayers")) {
			return;
		}
		try {
			Class<?> c = Class.forName("com.betterenchants.spell.SpellPlayers");
			for (var m : c.getMethods()) {
				if (m.getName().equals("set") && m.getParameterCount() == 2) {
					m.invoke(null, player, data);
					return;
				}
			}
		} catch (Throwable ignored) {
		}
	}

}
