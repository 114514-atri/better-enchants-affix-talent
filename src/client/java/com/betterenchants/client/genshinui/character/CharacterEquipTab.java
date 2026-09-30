package com.betterenchants.client.genshinui.character;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

@Environment(EnvType.CLIENT)
final class CharacterEquipTab {
	private final BetterCharacterScreen host;

	CharacterEquipTab(BetterCharacterScreen host) {
		this.host = host;
	}

	ItemStack armorStackAt(PlayerEntity player, int index) {
		if (player == null || index < 0 || index >= player.getInventory().main.size()) {
			return ItemStack.EMPTY;
		}
		return player.getInventory().main.get(index);
	}
}
