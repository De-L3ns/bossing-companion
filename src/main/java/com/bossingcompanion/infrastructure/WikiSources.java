package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.Boss;
import java.util.List;

/** Reviewed public source relationships, not copies of numeric drop tables. */
final class WikiSources
{
	private WikiSources() { }
	static List<String> pages(Boss boss)
	{
		return boss == Boss.ABYSSAL_SIRE ? List.of(boss.getDisplayName(), "Unsired") : List.of(boss.getDisplayName());
	}
	static boolean isRelatedReward(Boss boss, String version) { return boss == Boss.ABYSSAL_SIRE && "Unsired".equals(version); }
	static String rollUnit(Boss boss, String version) { return isRelatedReward(boss, version) ? "Unsired" : "kill"; }
}
