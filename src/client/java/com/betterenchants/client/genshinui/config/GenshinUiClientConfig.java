package com.betterenchants.client.genshinui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

/** 客户端仿原神 UI 轻量配置（主题等）。 */
@Environment(EnvType.CLIENT)
public final class GenshinUiClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("better_enchants_genshin_ui.json");

	/** 0 默认灰 … 7 岩。 */
	public static int themeIndex = 0;

	private GenshinUiClientConfig() {
	}

	public static void load() {
		if (!Files.isRegularFile(PATH)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(PATH)) {
			JsonObject obj = GSON.fromJson(reader, JsonObject.class);
			if (obj != null && obj.has("themeIndex")) {
				themeIndex = Math.floorMod(obj.get("themeIndex").getAsInt(), 8);
			}
		} catch (IOException ignored) {
		}
	}

	public static void save() {
		JsonObject obj = new JsonObject();
		obj.addProperty("themeIndex", themeIndex);
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(obj, writer);
			}
		} catch (IOException ignored) {
		}
	}

	public static void setTheme(int index) {
		themeIndex = Math.floorMod(index, 8);
		save();
	}
}
