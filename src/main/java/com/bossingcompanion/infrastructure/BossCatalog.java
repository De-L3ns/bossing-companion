package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.Boss;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.hiscore.HiscoreSkill;

/** Explicit supported source mappings; no reflection or NPC-name-only fallback. */
public final class BossCatalog
{
	private static final Map<Integer, Boss> NPCS = new HashMap<>();
	private static final Map<Boss, HiscoreSkill> ICONS = new EnumMap<>(Boss.class);
	private static final Map<String, Boss> NAMES = new HashMap<>();
	private static final List<Boss> SUPPORTED;

	static
	{
		add(Boss.ABYSSAL_SIRE, HiscoreSkill.ABYSSAL_SIRE, NpcID.ABYSSALSIRE_SIRE_STASIS_SLEEPING,
			NpcID.ABYSSALSIRE_SIRE_STASIS_AWAKE, NpcID.ABYSSALSIRE_SIRE_STASIS_STUNNED,
			NpcID.ABYSSALSIRE_SIRE_PUPPET, NpcID.ABYSSALSIRE_SIRE_WANDERING,
			NpcID.ABYSSALSIRE_SIRE_PANICKING, NpcID.ABYSSALSIRE_SIRE_APOCALYPSE);
		add(Boss.ALCHEMICAL_HYDRA, HiscoreSkill.ALCHEMICAL_HYDRA, NpcID.HYDRABOSS, NpcID.HYDRABOSS_2,
			NpcID.HYDRABOSS_3, NpcID.HYDRABOSS_4, NpcID.HYDRABOSS_FINALDEATH,
			NpcID.HYDRABOSS_P1_TRANSITION, NpcID.HYDRABOSS_P2_TRANSITION, NpcID.HYDRABOSS_P3_TRANSITION);
		add(Boss.AMOXLIATL, HiscoreSkill.AMOXLIATL, NpcID.AMOXLIATL);
		add(Boss.ARAXXOR, HiscoreSkill.ARAXXOR, NpcID.ARAXXOR, NpcID.ARAXXOR_DEAD);
		add(Boss.BRUTUS, HiscoreSkill.BRUTUS, NpcID.COWBOSS, NpcID.COWBOSS_ROUTEFIND);
		add(Boss.BRYOPHYTA, HiscoreSkill.BRYOPHYTA, NpcID.GB_MOSSGIANT);
		add(Boss.CERBERUS, HiscoreSkill.CERBERUS, NpcID.CERBERUS_ATTACKING, NpcID.CERBERUS_SITTING, NpcID.CERBERUS_RESETTING);
		add(Boss.CHAOS_ELEMENTAL, HiscoreSkill.CHAOS_ELEMENTAL, NpcID.CHAOSELEMENTAL);
		add(Boss.CHAOS_FANATIC, HiscoreSkill.CHAOS_FANATIC, NpcID.CHAOS_FANATIC);
		add(Boss.COMMANDER_ZILYANA, HiscoreSkill.COMMANDER_ZILYANA, NpcID.GODWARS_SARADOMIN_AVATAR);
		add(Boss.CORPOREAL_BEAST, HiscoreSkill.CORPOREAL_BEAST, NpcID.CORP_BEAST);
		add(Boss.CRAZY_ARCHAEOLOGIST, HiscoreSkill.CRAZY_ARCHAEOLOGIST, NpcID.CRAZY_ARCHAEOLOGIST);
		add(Boss.DERANGED_ARCHAEOLOGIST, HiscoreSkill.DERANGED_ARCHAEOLOGIST, NpcID.FOSSIL_CRAZY_ARCHAEOLOGIST);
		add(Boss.DUKE_SUCELLUS, HiscoreSkill.DUKE_SUCELLUS, NpcID.DUKE_SUCELLUS_INACTIVE,
			NpcID.DUKE_SUCELLUS_ASLEEP, NpcID.DUKE_SUCELLUS_AWAKE, NpcID.DUKE_SUCELLUS_DEAD);
		add(Boss.GENERAL_GRAARDOR, HiscoreSkill.GENERAL_GRAARDOR, NpcID.GODWARS_BANDOS_AVATAR);
		add(Boss.GIANT_MOLE, HiscoreSkill.GIANT_MOLE, NpcID.MOLE_GIANT);
		add(Boss.HESPORI, HiscoreSkill.HESPORI, NpcID.HESPORI);
		add(Boss.HUEYCOATL, HiscoreSkill.THE_HUEYCOATL, NpcID.HUEY_HEAD, NpcID.HUEY_HEAD_INVULNERABLE,
			NpcID.HUEY_HEAD_ENRAGED, NpcID.HUEY_HEAD_DEFEATED);
		add(Boss.KALPHITE_QUEEN, HiscoreSkill.KALPHITE_QUEEN, NpcID.KALPHITE_QUEEN, NpcID.KALPHITE_FLYINGQUEEN);
		add(Boss.KING_BLACK_DRAGON, HiscoreSkill.KING_BLACK_DRAGON, NpcID.KING_DRAGON);
		add(Boss.KRAKEN, HiscoreSkill.KRAKEN, NpcID.SLAYER_KRAKEN_BOSS);
		add(Boss.KREEARRA, HiscoreSkill.KREEARRA, NpcID.GODWARS_ARMADYL_AVATAR);
		add(Boss.KRIL_TSUTSAROTH, HiscoreSkill.KRIL_TSUTSAROTH, NpcID.GODWARS_ZAMORAK_AVATAR);
		add(Boss.LEVIATHAN, HiscoreSkill.THE_LEVIATHAN, NpcID.LEVIATHAN);
		add(Boss.MAD_ANGEL, HiscoreSkill.MAD_ANGEL, NpcID.MAD_ANGEL, NpcID.MAD_ANGEL_INITIAL, NpcID.MAD_ANGEL_ANIM, NpcID.MAD_ANGEL_DEAD);
		add(Boss.MAGGOT_KING, HiscoreSkill.MAGGOT_KING, NpcID.MAGGOT_KING, NpcID.MAGGOT_KING_CORPSE);
		add(Boss.NEX, HiscoreSkill.NEX, NpcID.NEX, NpcID.NEX_SPAWNING, NpcID.NEX_SOULSPLIT, NpcID.NEX_DEFLECT, NpcID.NEX_DYING);
		add(Boss.NIGHTMARE, HiscoreSkill.NIGHTMARE, NpcID.NIGHTMARE_INITIAL, NpcID.NIGHTMARE_DYING,
			NpcID.NIGHTMARE_PHASE_01, NpcID.NIGHTMARE_PHASE_02, NpcID.NIGHTMARE_PHASE_03,
			NpcID.NIGHTMARE_WEAK_PHASE_01, NpcID.NIGHTMARE_WEAK_PHASE_02, NpcID.NIGHTMARE_WEAK_PHASE_03, NpcID.NIGHTMARE_BLAST);
		add(Boss.OBOR, HiscoreSkill.OBOR, NpcID.HILLGIANT_BOSS);
		add(Boss.PHANTOM_MUSPAH, HiscoreSkill.PHANTOM_MUSPAH, NpcID.MUSPAH, NpcID.MUSPAH_MELEE,
			NpcID.MUSPAH_SOULSPLIT, NpcID.MUSPAH_FINAL, NpcID.MUSPAH_TELEPORT);
		add(Boss.SARACHNIS, HiscoreSkill.SARACHNIS, NpcID.SARACHNIS);
		add(Boss.SCORPIA, HiscoreSkill.SCORPIA, NpcID.SCORPIA);
		add(Boss.SCURRIUS, HiscoreSkill.SCURRIUS, NpcID.RAT_BOSS_NORMAL, NpcID.RAT_BOSS_INSTANCE);
		add(Boss.SHELLBANE_GRYPHON, HiscoreSkill.SHELLBANE_GRYPHON, NpcID.GRYPHON_BOSS);
		add(Boss.SKOTIZO, HiscoreSkill.SKOTIZO, NpcID.CATA_BOSS);
		add(Boss.THERMONUCLEAR_SMOKE_DEVIL, HiscoreSkill.THERMONUCLEAR_SMOKE_DEVIL, NpcID.SMOKE_DEVIL_BOSS);
		add(Boss.VARDORVIS, HiscoreSkill.VARDORVIS, NpcID.VARDORVIS);
		add(Boss.VORKATH, HiscoreSkill.VORKATH, NpcID.VORKATH);
		add(Boss.WHISPERER, HiscoreSkill.THE_WHISPERER, NpcID.WHISPERER, NpcID.WHISPERER_MELEE);
		add(Boss.YAMA, HiscoreSkill.YAMA, NpcID.YAMA);
		add(Boss.ZULRAH, HiscoreSkill.ZULRAH, NpcID.SNAKEBOSS_BOSS_RANGED, NpcID.SNAKEBOSS_BOSS_MELEE, NpcID.SNAKEBOSS_BOSS_MAGIC);
		SUPPORTED = Collections.unmodifiableList(new ArrayList<>(ICONS.keySet()));
	}

	private BossCatalog() { }
	private static void add(Boss boss, HiscoreSkill icon, int... ids)
	{
		ICONS.put(boss, icon);
		NAMES.put(key(boss.getDisplayName()), boss);
		NAMES.put(key(icon.getName()), boss);
		for (int id : ids)
		{
			if (NPCS.put(id, boss) != null) { throw new IllegalStateException("Duplicate NPC mapping"); }
		}
	}
	public static List<Boss> supported() { return SUPPORTED; }
	public static Boss fromName(String name) { return NAMES.get(key(name)); }
	public static Boss fromNpc(int id) { return NPCS.get(id); }
	public static HiscoreSkill icon(Boss boss) { return ICONS.get(boss); }
	private static String key(String name)
	{
		return name == null ? "" : name.toLowerCase(Locale.ROOT).trim().replaceFirst("^the\\s+", "")
			.replace('’', '\'').replaceAll("[^a-z0-9]", "");
	}
}
