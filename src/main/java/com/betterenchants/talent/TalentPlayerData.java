package com.betterenchants.talent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.random.Random;

/** 玩家体质 / 天赋与成长数据（UUID 缓存 + NBT 持久化；通关刷新靠 copyFrom 迁移）。 */
public final class TalentPlayerData {
	/** 每次随机一组天赋数量。 */
	public static final int MAX_TALENTS = 6;
	/** 初始 1 次 + 可刷新 2 次。 */
	public static final int MAX_ROLLS = 3;
	public static final int MAX_REFRESHES = 2;

	private static final String NBT_ROOT = "BetterEnchantsTalent";
	private static final Map<UUID, Data> DATA = new ConcurrentHashMap<>();

	private TalentPlayerData() {
	}

	public static Data data(ServerPlayerEntity player) {
		return DATA.computeIfAbsent(player.getUuid(), u -> new Data());
	}

	/**
	 * 清空本 JVM 内的天赋/体质会话缓存。
	 * 单人同 UUID 换存档时若不清理，会把上一档结果继承到新档。
	 */
	public static void clearSessionCache() {
		DATA.clear();
	}

	public static Constitution getConstitution(ServerPlayerEntity player) {
		return data(player).constitution;
	}

	public static void setConstitution(ServerPlayerEntity player, Constitution constitution) {
		data(player).constitution = constitution;
	}

	public static boolean hasConstitution(ServerPlayerEntity player) {
		return data(player).constitution != null;
	}

	public static Set<Talent> getTalents(ServerPlayerEntity player) {
		return EnumSet.copyOf(data(player).talents);
	}

	public static boolean hasTalent(ServerPlayerEntity player, Talent talent) {
		return talent != null && data(player).talents.contains(talent);
	}

	public static boolean talentsLocked(ServerPlayerEntity player) {
		return data(player).talentsLocked;
	}

	/** 尚未从随机组中确认天赋。 */
	public static boolean needsTalentSelection(ServerPlayerEntity player) {
		return !data(player).talentsLocked;
	}

	public static List<List<Talent>> getTalentRolls(ServerPlayerEntity player) {
		Data d = data(player);
		List<List<Talent>> out = new ArrayList<>(d.talentRolls.size());
		for (List<Talent> roll : d.talentRolls) {
			out.add(List.copyOf(roll));
		}
		return out;
	}

	public static int refreshesRemaining(ServerPlayerEntity player) {
		Data d = data(player);
		return Math.max(0, MAX_REFRESHES - d.talentRefreshesUsed);
	}

	public static int refreshesUsed(ServerPlayerEntity player) {
		return data(player).talentRefreshesUsed;
	}

	/** 确保至少有一组初始随机天赋（不可刷新体质）。 */
	public static void ensureInitialTalentRoll(ServerPlayerEntity player) {
		Data d = data(player);
		if (d.talentsLocked || !d.talentRolls.isEmpty()) {
			return;
		}
		d.talentRolls.add(rollSix(player.getRandom()));
	}

	public static boolean tryRefreshTalentRoll(ServerPlayerEntity player) {
		Data d = data(player);
		if (d.talentsLocked || d.talentRefreshesUsed >= MAX_REFRESHES || d.talentRolls.size() >= MAX_ROLLS) {
			return false;
		}
		d.talentRolls.add(rollSix(player.getRandom()));
		d.talentRefreshesUsed++;
		return true;
	}

	public static boolean confirmTalentRoll(ServerPlayerEntity player, int rollIndex) {
		Data d = data(player);
		if (d.talentsLocked || rollIndex < 0 || rollIndex >= d.talentRolls.size()) {
			return false;
		}
		List<Talent> chosen = d.talentRolls.get(rollIndex);
		d.talents.clear();
		d.talents.addAll(chosen);
		enforceConflicts(d.talents);
		d.talentsLocked = true;
		d.selectedRollIndex = rollIndex;
		return true;
	}

	public static void setTalents(ServerPlayerEntity player, Set<Talent> talents, boolean lock) {
		Data d = data(player);
		d.talents.clear();
		if (talents != null) {
			for (Talent t : talents) {
				if (t != null) {
					d.talents.add(t);
				}
			}
		}
		enforceConflicts(d.talents);
		while (d.talents.size() > MAX_TALENTS) {
			Talent drop = d.talents.iterator().next();
			d.talents.remove(drop);
		}
		d.talentsLocked = lock;
	}

	public static void clearTalents(ServerPlayerEntity player) {
		Data d = data(player);
		d.talents.clear();
		d.talentsLocked = false;
		d.talentRolls.clear();
		d.talentRefreshesUsed = 0;
		d.selectedRollIndex = -1;
	}

	/**
	 * 测试自选：直接写入 1 体质 + 恰好 6 天赋并锁定（无次数限制，由 /yandere restart 触发）。
	 */
	public static boolean applyFreeSelect(ServerPlayerEntity player, Constitution constitution, Set<Talent> talents) {
		if (player == null || constitution == null || talents == null || talents.size() != MAX_TALENTS) {
			return false;
		}
		if (talents.contains(Talent.BLOOD_RITE) && talents.contains(Talent.CLEAR_HEART)) {
			return false;
		}
		for (Talent t : talents) {
			if (t == null) {
				return false;
			}
		}
		Data d = data(player);
		d.constitution = constitution;
		d.talents.clear();
		d.talents.addAll(talents);
		enforceConflicts(d.talents);
		if (d.talents.size() != MAX_TALENTS) {
			return false;
		}
		d.talentsLocked = true;
		d.talentRolls.clear();
		d.talentRefreshesUsed = 0;
		d.selectedRollIndex = -1;
		return true;
	}

	/** 随机 6 个天赋（稀有度权重），自动避开血祭/清心互斥。 */
	public static List<Talent> rollSix(Random random) {
		return Talent.rollWeighted(random, MAX_TALENTS);
	}

	private static void enforceConflicts(Set<Talent> set) {
		if (set.contains(Talent.BLOOD_RITE) && set.contains(Talent.CLEAR_HEART)) {
			set.remove(Talent.CLEAR_HEART);
		}
	}

	public static boolean tryMarkFood(ServerPlayerEntity player, String foodId) {
		return data(player).eatenFoods.add(foodId);
	}

	public static int foodVariety(ServerPlayerEntity player) {
		return data(player).eatenFoods.size();
	}

	public static Set<String> swordKinds(ServerPlayerEntity player) {
		return data(player).swordKinds;
	}

	public static void addSwordKind(ServerPlayerEntity player, String kind) {
		data(player).swordKinds.add(kind);
	}

	public static long phoenixReadyAt(ServerPlayerEntity player) {
		return data(player).phoenixReadyAt;
	}

	public static void setPhoenixReadyAt(ServerPlayerEntity player, long worldTime) {
		data(player).phoenixReadyAt = worldTime;
	}

	public static long timeShardReadyAt(ServerPlayerEntity player) {
		return data(player).timeShardReadyAt;
	}

	public static void setTimeShardReadyAt(ServerPlayerEntity player, long worldTime) {
		data(player).timeShardReadyAt = worldTime;
	}

	public static UUID shadowTarget(ServerPlayerEntity player) {
		return data(player).arrowTarget;
	}

	public static int arrowStacks(ServerPlayerEntity player) {
		return data(player).arrowStacks;
	}

	public static long arrowStackExpire(ServerPlayerEntity player) {
		return data(player).arrowStackExpire;
	}

	public static void setArrowCombo(ServerPlayerEntity player, UUID target, int stacks, long expire) {
		Data d = data(player);
		d.arrowTarget = target;
		d.arrowStacks = stacks;
		d.arrowStackExpire = expire;
	}

	public static int swordCombo(ServerPlayerEntity player) {
		return data(player).swordCombo;
	}

	public static void setSwordCombo(ServerPlayerEntity player, int combo) {
		data(player).swordCombo = combo;
	}

	public static long swordCritReadyAt(ServerPlayerEntity player) {
		return data(player).swordCritReadyAt;
	}

	public static void setSwordCritReadyAt(ServerPlayerEntity player, long t) {
		data(player).swordCritReadyAt = t;
	}

	public static long lastCombatAt(ServerPlayerEntity player) {
		return data(player).lastCombatAt;
	}

	public static void setLastCombatAt(ServerPlayerEntity player, long t) {
		data(player).lastCombatAt = t;
	}

	public static long shadowStrikeReadyAt(ServerPlayerEntity player) {
		return data(player).shadowStrikeReadyAt;
	}

	public static void setShadowStrikeReadyAt(ServerPlayerEntity player, long t) {
		data(player).shadowStrikeReadyAt = t;
	}

	public static long oreSenseCooldown(ServerPlayerEntity player) {
		return data(player).oreSenseCooldown;
	}

	public static void setOreSenseCooldown(ServerPlayerEntity player, long t) {
		data(player).oreSenseCooldown = t;
	}

	public static boolean craftsmanMarked(ServerPlayerEntity player, String itemKey) {
		return data(player).craftsmanItems.contains(itemKey);
	}

	public static void markCraftsman(ServerPlayerEntity player, String itemKey) {
		data(player).craftsmanItems.add(itemKey);
	}

	public static void copyFrom(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
		if (oldPlayer == newPlayer) {
			return;
		}
		Data old = DATA.get(oldPlayer.getUuid());
		if (old == null) {
			return;
		}
		DATA.put(newPlayer.getUuid(), old.copy());
	}

	public static void writeNbt(ServerPlayerEntity player, NbtCompound nbt) {
		Data d = data(player);
		NbtCompound root = new NbtCompound();
		if (d.constitution != null) {
			root.putString("constitution", d.constitution.id());
		}
		NbtList talents = new NbtList();
		for (Talent t : d.talents) {
			talents.add(NbtString.of(t.id()));
		}
		root.put("talents", talents);
		root.putBoolean("talentsLocked", d.talentsLocked);
		root.putInt("talentRefreshesUsed", d.talentRefreshesUsed);
		root.putInt("selectedRollIndex", d.selectedRollIndex);
		NbtList rollsNbt = new NbtList();
		for (List<Talent> roll : d.talentRolls) {
			NbtList one = new NbtList();
			for (Talent t : roll) {
				one.add(NbtString.of(t.id()));
			}
			rollsNbt.add(one);
		}
		root.put("talentRolls", rollsNbt);

		root.putInt("bodyTotalPoints", d.bodyTotalPoints);
		root.putInt("bodyPower", d.bodyPower);
		root.putInt("bodyHealth", d.bodyHealth);
		root.putInt("bodySpeed", d.bodySpeed);
		root.putInt("bodyBody", d.bodyBody);
		root.putInt("bodySpell", d.bodySpell);
		root.putInt("bodyXpBucketsGranted", d.bodyXpBucketsGranted);
		root.putInt("bodyRespecCount", d.bodyRespecCount);
		root.putLong("bodyRespecReadyAt", d.bodyRespecReadyAt);
		NbtList claimed = new NbtList();
		for (String c : d.bodyClaimedRewards) {
			claimed.add(NbtString.of(c));
		}
		root.put("bodyClaimed", claimed);

		NbtList maidBodiesNbt = new NbtList();
		for (Map.Entry<UUID, MaidBodyData> e : d.maidBodies.entrySet()) {
			maidBodiesNbt.add(e.getValue().writeNbt(e.getKey()));
		}
		root.put("maidBodies", maidBodiesNbt);

		NbtList foods = new NbtList();
		for (String f : d.eatenFoods) {
			foods.add(NbtString.of(f));
		}
		root.put("foods", foods);
		NbtList swords = new NbtList();
		for (String s : d.swordKinds) {
			swords.add(NbtString.of(s));
		}
		root.put("swords", swords);
		root.putLong("phoenixReadyAt", d.phoenixReadyAt);
		root.putLong("timeShardReadyAt", d.timeShardReadyAt);
		root.putLong("eternalEmberDay", d.eternalEmberDay);
		NbtList craft = new NbtList();
		for (String c : d.craftsmanItems) {
			craft.add(NbtString.of(c));
		}
		root.put("craftsman", craft);
		nbt.put(NBT_ROOT, root);
	}

	public static void readNbt(ServerPlayerEntity player, NbtCompound nbt) {
		if (!nbt.contains(NBT_ROOT, NbtElement.COMPOUND_TYPE)) {
			// 新存档无本模组 NBT：必须丢弃同 UUID 的旧会话缓存，否则会继承上一档
			DATA.put(player.getUuid(), new Data());
			return;
		}
		Data d = data(player);
		NbtCompound root = nbt.getCompound(NBT_ROOT);
		d.constitution = Constitution.byId(root.getString("constitution"));
		d.talents.clear();
		NbtList talents = root.getList("talents", NbtElement.STRING_TYPE);
		for (int i = 0; i < talents.size(); i++) {
			Talent t = Talent.byId(talents.getString(i));
			if (t != null) {
				d.talents.add(t);
			}
		}
		enforceConflicts(d.talents);
		d.talentsLocked = root.getBoolean("talentsLocked");
		d.talentRefreshesUsed = root.contains("talentRefreshesUsed") ? root.getInt("talentRefreshesUsed") : 0;
		d.selectedRollIndex = root.contains("selectedRollIndex") ? root.getInt("selectedRollIndex") : -1;
		d.talentRolls.clear();
		if (root.contains("talentRolls", NbtElement.LIST_TYPE)) {
			NbtList rollsNbt = root.getList("talentRolls", NbtElement.LIST_TYPE);
			for (int i = 0; i < rollsNbt.size(); i++) {
				if (!(rollsNbt.get(i) instanceof NbtList one)) {
					continue;
				}
				List<Talent> roll = new ArrayList<>();
				for (int j = 0; j < one.size(); j++) {
					Talent t = Talent.byId(one.getString(j));
					if (t != null) {
						roll.add(t);
					}
				}
				if (!roll.isEmpty()) {
					d.talentRolls.add(roll);
				}
			}
		}

		d.bodyTotalPoints = root.contains("bodyTotalPoints") ? root.getInt("bodyTotalPoints") : 0;
		d.bodyPower = root.contains("bodyPower") ? root.getInt("bodyPower") : 0;
		d.bodyHealth = root.contains("bodyHealth") ? root.getInt("bodyHealth") : 0;
		d.bodySpeed = root.contains("bodySpeed") ? root.getInt("bodySpeed") : 0;
		d.bodyBody = root.contains("bodyBody") ? root.getInt("bodyBody") : 0;
		d.bodySpell = root.contains("bodySpell") ? root.getInt("bodySpell") : 0;
		d.bodyXpBucketsGranted = root.contains("bodyXpBucketsGranted") ? root.getInt("bodyXpBucketsGranted") : 0;
		d.bodyRespecCount = root.contains("bodyRespecCount") ? root.getInt("bodyRespecCount") : 0;
		d.bodyRespecReadyAt = root.contains("bodyRespecReadyAt") ? root.getLong("bodyRespecReadyAt") : 0L;
		d.bodyClaimedRewards.clear();
		if (root.contains("bodyClaimed", NbtElement.LIST_TYPE)) {
			NbtList claimed = root.getList("bodyClaimed", NbtElement.STRING_TYPE);
			for (int i = 0; i < claimed.size(); i++) {
				d.bodyClaimedRewards.add(claimed.getString(i));
			}
		}

		d.maidBodies.clear();
		if (root.contains("maidBodies", NbtElement.LIST_TYPE)) {
			NbtList maidBodiesNbt = root.getList("maidBodies", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < maidBodiesNbt.size(); i++) {
				NbtCompound tag = maidBodiesNbt.getCompound(i);
				if (!tag.containsUuid("id")) {
					continue;
				}
				UUID maidId = tag.getUuid("id");
				d.maidBodies.put(maidId, MaidBodyData.readNbt(tag));
			}
		}

		d.eatenFoods.clear();
		NbtList foods = root.getList("foods", NbtElement.STRING_TYPE);
		for (int i = 0; i < foods.size(); i++) {
			d.eatenFoods.add(foods.getString(i));
		}
		d.swordKinds.clear();
		NbtList swords = root.getList("swords", NbtElement.STRING_TYPE);
		for (int i = 0; i < swords.size(); i++) {
			d.swordKinds.add(swords.getString(i));
		}
		d.phoenixReadyAt = root.getLong("phoenixReadyAt");
		d.timeShardReadyAt = root.getLong("timeShardReadyAt");
		d.eternalEmberDay = root.contains("eternalEmberDay") ? root.getLong("eternalEmberDay") : -1L;
		d.craftsmanItems.clear();
		NbtList craft = root.getList("craftsman", NbtElement.STRING_TYPE);
		for (int i = 0; i < craft.size(); i++) {
			d.craftsmanItems.add(craft.getString(i));
		}
	}

	public static List<String> describe(ServerPlayerEntity player) {
		Data d = data(player);
		List<String> out = new ArrayList<>();
		out.add("体质：" + (d.constitution == null ? "未赋予" : d.constitution.labelZh()));
		if (d.talents.isEmpty()) {
			out.add("天赋：无" + (d.talentsLocked ? "" : "（未确认）"));
		} else {
			StringBuilder sb = new StringBuilder("天赋：");
			boolean first = true;
			for (Talent t : d.talents) {
				if (!first) {
					sb.append("、");
				}
				sb.append(t.labelZh());
				first = false;
			}
			out.add(sb.toString());
		}
		out.add(String.format(
				"体质升级：点%d 力量%d 血量%d 速度%d 肉体%d 法术%d",
				d.bodyUnspent(), d.bodyPower, d.bodyHealth, d.bodySpeed, d.bodyBody, d.bodySpell));
		return out;
	}

	public static final class Data {
		public Constitution constitution;
		public final Set<Talent> talents = EnumSet.noneOf(Talent.class);
		public boolean talentsLocked;
		public final List<List<Talent>> talentRolls = new ArrayList<>();
		public int talentRefreshesUsed;
		public int selectedRollIndex = -1;

		public int bodyTotalPoints;
		public int bodyPower;
		public int bodyHealth;
		public int bodySpeed;
		public int bodyBody;
		public int bodySpell;
		public int bodyXpBucketsGranted;
		public int bodyRespecCount;
		public long bodyRespecReadyAt;
		public final Set<String> bodyClaimedRewards = new HashSet<>();
		/** 女仆体质加点（按女仆 UUID）。 */
		public final Map<UUID, MaidBodyData> maidBodies = new ConcurrentHashMap<>();

		final Set<String> eatenFoods = new HashSet<>();
		final Set<String> swordKinds = new HashSet<>();
		final Set<String> craftsmanItems = new HashSet<>();
		long phoenixReadyAt;
		long timeShardReadyAt;
		long eternalEmberDay = -1L;
		long chronoSlowReadyAt;
		long dimensionWarpReadyAt;
		long bossKillBuffUntil;
		int warBurnStacks;
		long warBurnLastHit;
		int bloodSurgeStacks;
		long bloodSurgeExpire;
		long undyingWallReadyAt;
		long undyingWallUntil;
		long finalWillReadyAt;
		long finalWillBuffUntil;
		UUID heavenJudgmentTarget;
		long heavenJudgmentReadyAt;
		UUID arrowTarget;
		int arrowStacks;
		long arrowStackExpire;
		int swordCombo;
		long swordCritReadyAt;
		long lastCombatAt;
		long shadowStrikeReadyAt;
		long oreSenseCooldown;
		/** 本 tick 体质减伤（0~1）。 */
		public float tmpDamageReduce;
		/** 体质属性刷新缓存：条件未变则跳过 clear/reapply。 */
		public int constCondKey = Integer.MIN_VALUE;
		public float constCachedMult = Float.NaN;
		long phoenixBoostUntil;
		long shadowIdleSince;
		float chaosShieldAbs;

		public int bodyStat(BodyCultivationStat stat) {
			return switch (stat) {
				case POWER -> bodyPower;
				case HEALTH -> bodyHealth;
				case SPEED -> bodySpeed;
				case BODY -> bodyBody;
				case SPELL -> bodySpell;
			};
		}

		public void setBodyStat(BodyCultivationStat stat, int value) {
			int v = Math.max(0, value);
			switch (stat) {
				case POWER -> bodyPower = v;
				case HEALTH -> bodyHealth = v;
				case SPEED -> bodySpeed = v;
				case BODY -> bodyBody = v;
				case SPELL -> bodySpell = v;
			}
		}

		public int bodySpent() {
			return bodyPower + bodyHealth + bodySpeed + bodyBody + bodySpell;
		}

		public int bodyUnspent() {
			return Math.max(0, bodyTotalPoints - bodySpent());
		}

		Data copy() {
			Data n = new Data();
			n.constitution = constitution;
			n.talents.addAll(talents);
			n.talentsLocked = talentsLocked;
			for (List<Talent> roll : talentRolls) {
				n.talentRolls.add(new ArrayList<>(roll));
			}
			n.talentRefreshesUsed = talentRefreshesUsed;
			n.selectedRollIndex = selectedRollIndex;
			n.bodyTotalPoints = bodyTotalPoints;
			n.bodyPower = bodyPower;
			n.bodyHealth = bodyHealth;
			n.bodySpeed = bodySpeed;
			n.bodyBody = bodyBody;
			n.bodySpell = bodySpell;
			n.bodyXpBucketsGranted = bodyXpBucketsGranted;
			n.bodyRespecCount = bodyRespecCount;
			n.bodyRespecReadyAt = bodyRespecReadyAt;
			n.bodyClaimedRewards.addAll(bodyClaimedRewards);
			for (Map.Entry<UUID, MaidBodyData> e : maidBodies.entrySet()) {
				n.maidBodies.put(e.getKey(), e.getValue().copy());
			}
			n.eatenFoods.addAll(eatenFoods);
			n.swordKinds.addAll(swordKinds);
			n.craftsmanItems.addAll(craftsmanItems);
			n.phoenixReadyAt = phoenixReadyAt;
			n.timeShardReadyAt = timeShardReadyAt;
			n.eternalEmberDay = eternalEmberDay;
			n.chronoSlowReadyAt = chronoSlowReadyAt;
			n.phoenixBoostUntil = phoenixBoostUntil;
			return n;
		}
	}
}
