package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.Boss;
import net.runelite.api.gameval.NpcID;

/** Conservative first-hit pilot. No exact encounter-start or arbitrary NPC-name inference. */
public final class FightSources
{
	private FightSources() { }
	public static boolean supports(Boss boss) { return boss == Boss.OBOR || boss == Boss.BRYOPHYTA || boss == Boss.VORKATH; }
	public static Boss activeBoss(int id)
	{
		switch (id)
		{
			case NpcID.HILLGIANT_BOSS: return Boss.OBOR;
			case NpcID.GB_MOSSGIANT: return Boss.BRYOPHYTA;
			case NpcID.VORKATH: return Boss.VORKATH;
			default: return null;
		}
	}
	public static boolean dormantVorkath(int id) { return id == NpcID.VORKATH_SLEEPING || id == NpcID.VORKATH_SLEEPING_NOOP; }
	public static Boss identity(int id) { return dormantVorkath(id) ? Boss.VORKATH : activeBoss(id); }
	public static boolean terminal(Boss boss, int id) { return activeBoss(id) == boss; }
}
