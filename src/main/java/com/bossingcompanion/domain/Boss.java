package com.bossingcompanion.domain;

import lombok.Getter;

/** Stable domain identities. Client NPC/sprite identities belong to infrastructure. */
@Getter
public enum Boss
{
	ABYSSAL_SIRE("Abyssal Sire"), ALCHEMICAL_HYDRA("Alchemical Hydra"),
	AMOXLIATL("Amoxliatl"), ARAXXOR("Araxxor"), BRUTUS("Brutus"),
	BRYOPHYTA("Bryophyta"), CERBERUS("Cerberus"), CHAOS_ELEMENTAL("Chaos Elemental"),
	CHAOS_FANATIC("Chaos Fanatic"), COMMANDER_ZILYANA("Commander Zilyana"),
	CORPOREAL_BEAST("Corporeal Beast"), CRAZY_ARCHAEOLOGIST("Crazy Archaeologist"),
	DERANGED_ARCHAEOLOGIST("Deranged Archaeologist"), DUKE_SUCELLUS("Duke Sucellus"),
	GENERAL_GRAARDOR("General Graardor"), GIANT_MOLE("Giant Mole"), HESPORI("Hespori"),
	HUEYCOATL("The Hueycoatl"), KALPHITE_QUEEN("Kalphite Queen"),
	KING_BLACK_DRAGON("King Black Dragon"), KRAKEN("Kraken"), KREEARRA("Kree'arra"),
	KRIL_TSUTSAROTH("K'ril Tsutsaroth"), LEVIATHAN("The Leviathan"),
	MAD_ANGEL("The Mad Angel"), MAGGOT_KING("Maggot King"), NEX("Nex"),
	NIGHTMARE("The Nightmare"), OBOR("Obor"), PHANTOM_MUSPAH("Phantom Muspah"),
	SARACHNIS("Sarachnis"), SCORPIA("Scorpia"), SCURRIUS("Scurrius"),
	SHELLBANE_GRYPHON("Shellbane Gryphon"), SKOTIZO("Skotizo"),
	THERMONUCLEAR_SMOKE_DEVIL("Thermonuclear Smoke Devil"), VARDORVIS("Vardorvis"),
	VORKATH("Vorkath"), WHISPERER("The Whisperer"), YAMA("Yama"), ZULRAH("Zulrah");

	public enum Type { COMBAT, SKILLING }
	public enum Encounter { INDIVIDUAL, MULTIPLE_BOSSES, WAVES, ACTIVITY }
	public enum Rewards { STANDARD_DROPS, REWARD_CHEST, SPECIAL }

	private final String displayName;
	private final Type type;
	private final Encounter encounter;
	private final Rewards rewards;
	private final String collectionLogGroup;
	private final String variant;

	Boss(String name)
	{
		this(name, Type.COMBAT, Encounter.INDIVIDUAL, Rewards.STANDARD_DROPS, name, "standard");
	}

	Boss(String name, Type type, Encounter encounter, Rewards rewards, String group, String variant)
	{
		this.displayName = name;
		this.type = type;
		this.encounter = encounter;
		this.rewards = rewards;
		this.collectionLogGroup = group;
		this.variant = variant;
	}

	@Override
	public String toString() { return displayName; }
}
