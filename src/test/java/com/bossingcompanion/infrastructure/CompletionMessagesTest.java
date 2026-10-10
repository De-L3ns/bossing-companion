package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.Boss;
import java.time.Duration;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompletionMessagesTest
{
	@Test public void formattedGameKcAndAliasAreRecognized()
	{
		CompletionMessages.KillCount count = CompletionMessages.killCount("Your Vorkath kill count is: <col=ff0000>1,234</col>.");
		assertEquals(Boss.VORKATH, count.getBoss());
		assertEquals(1234, count.getCount());
		assertEquals(Boss.NIGHTMARE, CompletionMessages.killCount("Your Nightmare killcount is: 10.").getBoss());
		assertEquals(Boss.HUEYCOATL, CompletionMessages.killCount("Your Hueycoatl kill count is: 10.").getBoss());
	}
	@Test public void excludedVariantsAndDoomProduceOnlyAnExclusionBarrier()
	{
		for (String name : new String[]{"Phosani's Nightmare", "Demonic Brutus", "Doom of Mokhaiotl", "Tempoross", "The Gauntlet"})
		{
			CompletionMessages.KillCount count = CompletionMessages.killCount("Your " + name + " kill count is: 10.");
			assertNotNull(count);
			assertNull(count.getBoss());
		}
	}
	@Test public void chatCommandsLookupsAndMalformedCountsCannotConfirmKills()
	{
		assertNull(CompletionMessages.killCount("Vorkath kill count: 1,234"));
		assertNull(CompletionMessages.killCount("!kc vorkath"));
		assertNull(CompletionMessages.killCount("Other player: Your Vorkath kill count is: 10."));
		assertNull(CompletionMessages.killCount("Your Vorkath kill count is: 0."));
		assertNull(CompletionMessages.killCount("Your Vorkath kill count is: 9999999999999999999999999."));
	}
	@Test public void fightDurationIsNotPersonalBest()
	{
		assertEquals(Duration.ofSeconds(102), CompletionMessages.completedTime("Fight duration: <col=ff0000>1:42</col>. Personal best: <col=ff0000>1:20</col>"));
		assertEquals(Duration.ofMillis(80600), CompletionMessages.completedTime("Fight duration: 1:20.6 (new personal best)."));
		assertEquals(Duration.ofMillis(3722600), CompletionMessages.completedTime("Fight duration: 1:02:02.6. Personal best: 1:00:00."));
	}
	@Test public void unrelatedAndMalformedTimersAreRejected()
	{
		assertNull(CompletionMessages.completedTime("Personal best: 1:20"));
		assertNull(CompletionMessages.completedTime("Duration: 1:20. Personal best: 1:10."));
		assertNull(CompletionMessages.completedTime("Fight duration: 1:99. Personal best: 1:10."));
		assertNull(CompletionMessages.completedTime("Fight duration: 0:00. Personal best: 0:00."));
	}
}
