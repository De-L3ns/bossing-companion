package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.DropMechanic;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.gameval.ItemID;

/** Central semantic registry. Ordinary fractions/rolls remain Wiki data. */
public final class DropMechanics
{
	private DropMechanics() { }
	public static List<DropMechanic> forItem(Boss boss, int itemId, String source)
	{
		List<DropMechanic> rules = new ArrayList<>();
		if (boss == Boss.ABYSSAL_SIRE && itemId != ItemID.ABYSSALSIRE_UNSIRED)
		{
			rules.add(new DropMechanic(DropMechanic.Kind.CONDITIONAL,
				"Reward from offering an Unsired. Alternative rates require additional context; see the Wiki.",
				"https://oldschool.runescape.wiki/w/Unsired", "2026-10-10", null, false));
		}
		if (boss == Boss.VORKATH && itemId == ItemID.VORKATH_HEAD)
		{
			rules.add(new DropMechanic(DropMechanic.Kind.GUARANTEED_MILESTONE,
				"Guaranteed on the 50th kill; replaces the normal random roll.", source, "2026-10-10", 50L, true));
		}
		if (boss == Boss.ZULRAH && itemId == ItemID.SNAKEBOSS_SCALE)
		{
			rules.add(new DropMechanic(DropMechanic.Kind.MULTIPLE_COMPONENTS,
				"Guaranteed stack plus a possible extra main-table drop. Log quantity can cap.", source, "2026-10-10", null, false));
		}
		if (boss == Boss.NIGHTMARE && (itemId == ItemID.SLEPE_TELEPORT_CONSUMABLE || itemId == ItemID.NIGHTMARE_CHALLENGE_MORPH))
		{
			rules.add(new DropMechanic(DropMechanic.Kind.UNMODELED,
				itemId == ItemID.SLEPE_TELEPORT_CONSUMABLE
					? "Only from Phosani's Nightmare. The tablet has a 25th-kill guarantee; normal Nightmare KC cannot establish that condition."
					: "Only from Phosani's Nightmare. A shared collection unlock does not establish a normal Nightmare drop.",
				"https://oldschool.runescape.wiki/w/Phosani%27s_Nightmare", "2026-10-10", null, false));
		}
		if (boss == Boss.YAMA && itemId == ItemID.DEATH_CHARGE_SCROLL)
		{
			rules.add(new DropMechanic(DropMechanic.Kind.CONDITIONAL,
				"Guaranteed in the first dossier opened after 100 Yama kills. Replaced if already owned or used; that context is not inferred.",
				"https://oldschool.runescape.wiki/w/Dossier", "2026-10-10", null, false));
		}
		if (boss == Boss.DUKE_SUCELLUS || boss == Boss.VARDORVIS || boss == Boss.LEVIATHAN || boss == Boss.WHISPERER)
		{
			rules.add(new DropMechanic(DropMechanic.Kind.UNMODELED,
				"This boss has conditional/hidden drop mechanics. The displayed Wiki rate is not a complete probability model.", source, "2026-10-10", null, false));
		}
		if (boss == Boss.NEX || boss == Boss.NIGHTMARE || boss == Boss.YAMA || boss == Boss.HUEYCOATL)
		{
			rules.add(new DropMechanic(DropMechanic.Kind.CONDITIONAL,
				"Team, contribution, or encounter conditions can affect personal odds. See the Wiki conditions.", source, "2026-10-10", null, false));
		}
		return List.copyOf(rules);
	}
}

