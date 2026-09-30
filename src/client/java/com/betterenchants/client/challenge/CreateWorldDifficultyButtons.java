package com.betterenchants.client.challenge;

import com.betterenchants.challenge.WorldChallengePending;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.world.Difficulty;

/** 跟踪创建世界界面上的「难度」循环按钮；极限模式保持可点。 */
@Environment(EnvType.CLIENT)
public final class CreateWorldDifficultyButtons {
	private static final Set<CyclingButtonWidget<?>> TRACKED =
			Collections.newSetFromMap(new WeakHashMap<>());
	private static WeakReference<CyclingButtonWidget<?>> latest = new WeakReference<>(null);

	private CreateWorldDifficultyButtons() {
	}

	public static void track(CyclingButtonWidget<?> button) {
		if (button != null) {
			TRACKED.add(button);
			latest = new WeakReference<>(button);
		}
	}

	public static boolean isTracked(Object button) {
		return button instanceof CyclingButtonWidget<?> cb && TRACKED.contains(cb);
	}

	@SuppressWarnings("unchecked")
	public static void applyDisplay(CyclingButtonWidget<?> button, CreateWorldExtendedDifficulty ext) {
		if (button == null || ext == null) {
			return;
		}
		CyclingButtonWidget<Difficulty> typed = (CyclingButtonWidget<Difficulty>) button;
		if (typed.getValue() != ext.vanilla()) {
			typed.setValue(ext.vanilla());
		}
		typed.setMessage(ext.optionMessage());
		typed.setTooltip(Tooltip.of(ext.tooltip()));
	}

	/**
	 * 原版在极限时会把难度按钮 active=false；这里追加监听器重新启用，
	 * 并刷新困难/噩梦/天启显示。
	 */
	public static void installHardcoreUnlock(CreateWorldScreen screen) {
		if (screen == null) {
			return;
		}
		CyclingButtonWidget<?> button = latest.get();
		if (button == null || !TRACKED.contains(button)) {
			return;
		}
		screen.getWorldCreator().addListener(creator -> {
			CyclingButtonWidget<?> btn = latest.get();
			if (btn == null || !TRACKED.contains(btn)) {
				return;
			}
			btn.active = true;
			CreateWorldExtendedDifficulty ext = creator.isHardcore()
					? CreateWorldExtendedDifficulty.forHardcore(WorldChallengePending.get())
					: CreateWorldExtendedDifficulty.current(creator.getDifficulty(), WorldChallengePending.get());
			applyDisplay(btn, ext);
		});
		// 立即刷一次（进界面默认状态）
		var creator = screen.getWorldCreator();
		button.active = true;
		CreateWorldExtendedDifficulty ext = creator.isHardcore()
				? CreateWorldExtendedDifficulty.forHardcore(WorldChallengePending.get())
				: CreateWorldExtendedDifficulty.current(creator.getDifficulty(), WorldChallengePending.get());
		applyDisplay(button, ext);
	}
}
