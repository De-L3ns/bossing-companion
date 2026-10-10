package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.Boss;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;
import static org.junit.Assert.*;

public class BossCatalogTest
{
	@Test public void scopeHas41BossesWithNativeIcons()
	{
		assertEquals(41, BossCatalog.supported().size());
		for (Boss boss : BossCatalog.supported())
		{
			assertEquals(Boss.Type.COMBAT, boss.getType());
			assertEquals(Boss.Encounter.INDIVIDUAL, boss.getEncounter());
			assertEquals(boss, BossCatalog.fromName(boss.getDisplayName()));
			assertTrue(BossCatalog.icon(boss).getSpriteId() >= 0);
		}
	}
	@Test public void phasesMapButMinionsPetsQuestAndChallengeVersionsDoNot()
	{
		assertEquals(Boss.KALPHITE_QUEEN, BossCatalog.fromNpc(NpcID.KALPHITE_QUEEN));
		assertEquals(Boss.KALPHITE_QUEEN, BossCatalog.fromNpc(NpcID.KALPHITE_FLYINGQUEEN));
		assertEquals(Boss.ARAXXOR, BossCatalog.fromNpc(NpcID.ARAXXOR_DEAD));
		assertEquals(Boss.NEX, BossCatalog.fromNpc(NpcID.NEX_DYING));
		assertNull(BossCatalog.fromNpc(NpcID.VORKATH_QUEST));
		assertNull(BossCatalog.fromNpc(NpcID.VORKATH_PET));
		assertNull(BossCatalog.fromNpc(NpcID.VORKATH_SPAWN));
		assertNull(BossCatalog.fromNpc(NpcID.NIGHTMARE_CHALLENGE_DYING));
		assertNull(BossCatalog.fromNpc(NpcID.COWBOSS_HARDMODE));
		assertNull(BossCatalog.fromNpc(NpcID.GODWARS_SERGEANT_GOBLIN1));
	}
}
