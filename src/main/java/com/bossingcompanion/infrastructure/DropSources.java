package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.Boss;
import java.util.List;

/** Reviewed public source relationships, not copies of numeric drop tables. */
final class DropSources
{
	private DropSources() { }
	static List<String> pages(Boss boss)
	{
		if (boss == Boss.ABYSSAL_SIRE) { return List.of(boss.getDisplayName(), "Unsired"); }
		if (boss == Boss.OBOR) { return List.of(boss.getDisplayName(), "Chest (Obor's lair)"); }
		if (boss == Boss.BRYOPHYTA) { return List.of(boss.getDisplayName(), "Chest (Bryophyta's lair)"); }
		if (boss == Boss.NIGHTMARE) { return List.of(boss.getDisplayName(), "Nightmare", "Phosani's Nightmare"); }
		if (boss == Boss.YAMA) { return List.of(boss.getDisplayName(), "Dossier"); }
		String name = boss.getDisplayName();
		return name.startsWith("The ") ? List.of(name, name.substring(4)) : List.of(name);
	}
	static boolean isRelatedReward(Boss boss, String version) { return boss == Boss.ABYSSAL_SIRE && "Unsired".equalsIgnoreCase(version.split("#", 2)[0]); }
	static boolean isRelatedSource(Boss boss, String version)
	{
		String page = version.split("#", 2)[0];
		return pages(boss).stream().anyMatch(source -> source.equalsIgnoreCase(page));
	}
	static String rollUnit(Boss boss, String version)
	{
		if (isRelatedReward(boss, version)) { return "Unsired"; }
		if (version.regionMatches(true, 0, "Chest (", 0, 7)) { return "chest opening"; }
		if (boss == Boss.YAMA && version.split("#", 2)[0].equalsIgnoreCase("Dossier")) { return "dossier"; }
		return "kill";
	}
}
