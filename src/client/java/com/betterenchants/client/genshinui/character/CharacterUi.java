package com.betterenchants.client.genshinui.character;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
final class CharacterUi {
	float easeOutCubic(float t) {
		float clamped = MathHelper.clamp(t, 0.0f, 1.0f);
		float inv = 1.0f - clamped;
		return 1.0f - (inv * inv * inv);
	}

	float elapsedSince(long timeMs) {
		return System.currentTimeMillis() - timeMs;
	}

	int armorTypeIndex(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor)) {
			return -1;
		}
		return switch (armor.getType()) {
			case HELMET -> 0;
			case CHESTPLATE -> 1;
			case LEGGINGS -> 2;
			case BOOTS -> 3;
			default -> -1;
		};
	}
}
