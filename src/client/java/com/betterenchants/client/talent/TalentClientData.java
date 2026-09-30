package com.betterenchants.client.talent;

import com.betterenchants.network.BodyCultivationNetworking;
import com.betterenchants.talent.BodyCultivationStat;
import com.betterenchants.talent.Constitution;
import com.betterenchants.talent.Talent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class TalentClientData {
	private static Constitution constitution;
	private static final Set<Talent> talents = EnumSet.noneOf(Talent.class);
	private static boolean talentsLocked;
	private static boolean needsSelection;
	private static final List<List<Talent>> rolls = new ArrayList<>();
	private static int refreshesRemaining;

	private static int bodyTotalPoints;
	private static int bodyPower;
	private static int bodyHealth;
	private static int bodySpeed;
	private static int bodyBody;
	private static int bodySpell;
	private static int bodyUnspent;
	private static int bodyRespecCount;
	private static long bodyRespecReadyAt;

	/** null = 玩家自身。 */
	@Nullable
	private static UUID selectedMaidId;
	private static final List<BodyCultivationNetworking.MaidBodyEntry> maidEntries = new ArrayList<>();

	private TalentClientData() {
	}

	public static void apply(
			String constitutionId,
			Iterable<String> talentIds,
			boolean locked,
			boolean needs,
			List<List<String>> rollIds,
			int refreshes
	) {
		constitution = Constitution.byId(constitutionId);
		talents.clear();
		if (talentIds != null) {
			for (String id : talentIds) {
				Talent t = Talent.byId(id);
				if (t != null) {
					talents.add(t);
				}
			}
		}
		talentsLocked = locked;
		needsSelection = needs;
		rolls.clear();
		if (rollIds != null) {
			for (List<String> roll : rollIds) {
				List<Talent> parsed = new ArrayList<>();
				for (String id : roll) {
					Talent t = Talent.byId(id);
					if (t != null) {
						parsed.add(t);
					}
				}
				if (!parsed.isEmpty()) {
					rolls.add(parsed);
				}
			}
		}
		refreshesRemaining = Math.max(0, refreshes);
	}

	public static void applyBody(
			int totalPoints,
			int power,
			int health,
			int speed,
			int body,
			int spell,
			int unspent,
			int respecCount,
			long respecReadyAt
	) {
		bodyTotalPoints = totalPoints;
		bodyPower = power;
		bodyHealth = health;
		bodySpeed = speed;
		bodyBody = body;
		bodySpell = spell;
		bodyUnspent = unspent;
		bodyRespecCount = Math.max(0, respecCount);
		bodyRespecReadyAt = Math.max(0L, respecReadyAt);
	}

	public static void applyMaids(List<BodyCultivationNetworking.MaidBodyEntry> entries) {
		maidEntries.clear();
		if (entries != null) {
			maidEntries.addAll(entries);
		}
		if (selectedMaidId != null) {
			boolean still = false;
			for (BodyCultivationNetworking.MaidBodyEntry e : maidEntries) {
				if (selectedMaidId.equals(e.maidId())) {
					still = true;
					break;
				}
			}
			if (!still) {
				selectedMaidId = null;
			}
		}
	}

	public static Constitution constitution() {
		return constitution;
	}

	public static Set<Talent> talents() {
		return Collections.unmodifiableSet(talents);
	}

	public static List<List<Talent>> rolls() {
		return Collections.unmodifiableList(rolls);
	}

	public static int refreshesRemaining() {
		return refreshesRemaining;
	}

	public static boolean talentsLocked() {
		return talentsLocked;
	}

	public static boolean needsSelection() {
		return needsSelection;
	}

	public static int bodyTotalPoints() {
		BodyCultivationNetworking.MaidBodyEntry maid = selectedMaid();
		return maid != null ? maid.totalPoints() : bodyTotalPoints;
	}

	public static int bodyUnspent() {
		BodyCultivationNetworking.MaidBodyEntry maid = selectedMaid();
		return maid != null ? maid.unspent() : bodyUnspent;
	}

	public static int bodySpent() {
		BodyCultivationNetworking.MaidBodyEntry maid = selectedMaid();
		if (maid != null) {
			return maid.power() + maid.health() + maid.speed() + maid.body() + maid.spell();
		}
		return bodyPower + bodyHealth + bodySpeed + bodyBody + bodySpell;
	}

	public static int bodyRespecCount() {
		return bodyRespecCount;
	}

	public static long bodyRespecReadyAt() {
		return bodyRespecReadyAt;
	}

	public static int bodyStat(BodyCultivationStat stat) {
		BodyCultivationNetworking.MaidBodyEntry maid = selectedMaid();
		if (maid != null) {
			return switch (stat) {
				case POWER -> maid.power();
				case HEALTH -> maid.health();
				case SPEED -> maid.speed();
				case BODY -> maid.body();
				case SPELL -> maid.spell();
			};
		}
		return switch (stat) {
			case POWER -> bodyPower;
			case HEALTH -> bodyHealth;
			case SPEED -> bodySpeed;
			case BODY -> bodyBody;
			case SPELL -> bodySpell;
		};
	}

	@Nullable
	public static UUID selectedMaidId() {
		return selectedMaidId;
	}

	public static void selectSelf() {
		selectedMaidId = null;
	}

	public static void selectMaid(@Nullable UUID maidId) {
		selectedMaidId = maidId;
	}

	public static boolean isMaidSelected() {
		return selectedMaidId != null;
	}

	public static boolean selectedFollowOwner() {
		BodyCultivationNetworking.MaidBodyEntry maid = selectedMaid();
		return maid != null && maid.followOwner();
	}

	@Nullable
	public static BodyCultivationNetworking.MaidBodyEntry selectedMaid() {
		if (selectedMaidId == null) {
			return null;
		}
		for (BodyCultivationNetworking.MaidBodyEntry e : maidEntries) {
			if (selectedMaidId.equals(e.maidId())) {
				return e;
			}
		}
		return null;
	}

	public static List<BodyCultivationNetworking.MaidBodyEntry> maidEntries() {
		return Collections.unmodifiableList(maidEntries);
	}

	public static void clear() {
		constitution = null;
		talents.clear();
		talentsLocked = false;
		needsSelection = false;
		rolls.clear();
		refreshesRemaining = 0;
		bodyTotalPoints = 0;
		bodyPower = 0;
		bodyHealth = 0;
		bodySpeed = 0;
		bodyBody = 0;
		bodySpell = 0;
		bodyUnspent = 0;
		bodyRespecCount = 0;
		bodyRespecReadyAt = 0L;
		selectedMaidId = null;
		maidEntries.clear();
	}
}
