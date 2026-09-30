package com.betterenchants.talent;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;

/** 女仆体质展示缓存（实际加点始终跟随主人）。 */
public final class MaidBodyData {
	/** 展示名缓存（同步用）。 */
	public String displayName = "";

	public MaidBodyData copy() {
		MaidBodyData n = new MaidBodyData();
		n.displayName = displayName;
		return n;
	}

	public NbtCompound writeNbt(UUID maidId) {
		NbtCompound tag = new NbtCompound();
		tag.putUuid("id", maidId);
		tag.putBoolean("followOwner", true);
		if (displayName != null && !displayName.isEmpty()) {
			tag.putString("name", displayName);
		}
		return tag;
	}

	public static MaidBodyData readNbt(NbtCompound tag) {
		MaidBodyData d = new MaidBodyData();
		d.displayName = tag.contains("name") ? tag.getString("name") : "";
		return d;
	}
}
