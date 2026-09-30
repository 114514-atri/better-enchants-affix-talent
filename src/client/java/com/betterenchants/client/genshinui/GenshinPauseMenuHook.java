package com.betterenchants.client.genshinui;

import com.betterenchants.client.genshinui.character.BetterCharacterScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** 暂停菜单入口：角色界面（成就从界面内打开）。 */
@Environment(EnvType.CLIENT)
public final class GenshinPauseMenuHook {
	private GenshinPauseMenuHook() {
	}

	public static void register() {
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof GameMenuScreen)) {
				return;
			}
			Screens.getButtons(screen).add(ButtonWidget.builder(
							Text.translatable("screen.better_enchants.character"),
							b -> client.setScreen(new BetterCharacterScreen(screen)))
					.dimensions(scaledWidth / 2 - 49, 8, 98, 20)
					.build());
		});
	}
}
