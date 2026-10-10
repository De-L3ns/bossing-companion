package com.bossingcompanion.application;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.FightTimerSnapshot;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class FightTimerTrackerTest
{
	private final AtomicLong nanos = new AtomicLong();
	private FightTimerTracker timer;
	@Before public void setup() { timer = new FightTimerTracker(nanos::get); timer.setEnabled(true); }
	private void seconds(long value) { nanos.addAndGet(Duration.ofSeconds(value).toNanos()); }
	private void finish(Boss boss, long entity, long kc, int tick)
	{
		timer.terminalDeath(entity, tick); timer.creditedKill(boss, kc, tick); timer.completedTime(Duration.ofSeconds(30), tick);
	}
	@Test public void firstHitCountsUpMonotonicallyAndDuplicateStartDoesNotReset()
	{
		assertEquals(Duration.ZERO, timer.snapshot().getFrozen()); assertNull(timer.snapshot().getBoss());
		assertTrue(timer.begin(Boss.OBOR, 1, 1)); seconds(5);
		assertFalse(timer.begin(Boss.OBOR, 1, 2)); assertFalse(timer.begin(Boss.BRYOPHYTA, 2, 2));
		assertEquals(Duration.ofSeconds(5), timer.snapshot().elapsedAt(nanos.get())); assertTrue(timer.snapshot().isApproximate());
	}
	@Test public void terminalDeathIsProvisionalAndNoCreditRestoresPreviousResult()
	{
		timer.begin(Boss.OBOR, 1, 1); seconds(5); timer.terminalDeath(1, 2);
		assertFalse(timer.snapshot().isRunning()); assertEquals(Duration.ofSeconds(5), timer.snapshot().getFrozen());
		timer.expire(15); assertEquals(Duration.ZERO, timer.snapshot().getFrozen());
		timer.begin(Boss.OBOR, 2, 16); seconds(30); finish(Boss.OBOR, 2, 1, 20);
		timer.begin(Boss.OBOR, 3, 40); seconds(4); timer.terminalDeath(3, 41); timer.expire(54);
		assertEquals(Duration.ofSeconds(30), timer.snapshot().getFrozen()); assertTrue(timer.snapshot().isOfficial());
	}
	@Test public void creditedResultStaysFrozenUntilNextStartAndOfficialDurationCanCorrectIt()
	{
		timer.begin(Boss.VORKATH, 1, 1); seconds(40); timer.terminalDeath(1, 50); timer.creditedKill(Boss.VORKATH, 100, 51);
		assertEquals(Duration.ofSeconds(40), timer.snapshot().getFrozen());
		timer.completedTime(Duration.ofMillis(41200), 52); assertTrue(timer.snapshot().isOfficial());
		seconds(300); timer.expire(550);
		assertEquals(Duration.ofMillis(41200), timer.snapshot().elapsedAt(nanos.get()));
		assertTrue(timer.begin(Boss.VORKATH, 2, 551)); assertEquals(Duration.ZERO, timer.snapshot().elapsedAt(nanos.get()));
	}
	@Test public void durationBeforeCreditRequiresAnEndedAttemptAndRemainsUnique()
	{
		timer.begin(Boss.OBOR, 1, 1); seconds(20); timer.terminalDeath(1, 30);
		timer.completedTime(Duration.ofSeconds(22), 30); timer.creditedKill(Boss.OBOR, 1, 31);
		assertEquals(Duration.ofSeconds(22), timer.snapshot().getFrozen()); assertTrue(timer.snapshot().isOfficial());
	}
	@Test public void activeOnlyDurationDoesNotImportAnOldFightTime()
	{
		timer.begin(Boss.OBOR, 1, 10); timer.completedTime(Duration.ofSeconds(80), 11); seconds(3);
		timer.creditedKill(Boss.OBOR, 1, 12);
		assertEquals(Duration.ofSeconds(3), timer.snapshot().getFrozen()); assertFalse(timer.snapshot().isOfficial());
	}
	@Test public void creditedCompletionFallbackStopsWithoutDeathButCannotCreateAnUnobservedTimer()
	{
		timer.creditedKill(Boss.OBOR, 5, 1); timer.completedTime(Duration.ofSeconds(80), 1);
		assertNull(timer.snapshot().getBoss());
		timer.begin(Boss.OBOR, 1, 20); seconds(10); timer.creditedKill(Boss.OBOR, 6, 40);
		assertFalse(timer.snapshot().isRunning()); assertEquals(Duration.ofSeconds(10), timer.snapshot().getFrozen());
	}
	@Test public void mismatchedDeathAndDuplicateCreditsNeverFinishTheCurrentAttempt()
	{
		timer.creditedKill(Boss.OBOR, 10, 1); timer.begin(Boss.OBOR, 1, 20);
		timer.terminalDeath(2, 21); timer.creditedKill(Boss.OBOR, 10, 21); timer.creditedKill(Boss.VORKATH, 1, 21);
		assertTrue(timer.snapshot().isRunning());
		timer.completedTime(Duration.ofSeconds(50), 21); assertFalse(timer.snapshot().isOfficial());
	}
	@Test public void ambiguousOldDeathAndNewFightCannotBindCreditOrAnonymousTime()
	{
		timer.begin(Boss.OBOR, 1, 1); seconds(5); timer.terminalDeath(1, 6);
		timer.begin(Boss.OBOR, 2, 7); timer.creditedKill(Boss.OBOR, 1, 8); timer.completedTime(Duration.ofSeconds(30), 8);
		assertTrue(timer.snapshot().isRunning()); assertFalse(timer.snapshot().isOfficial());
	}
	@Test public void lateOfficialTimeForAnOlderResultCannotOverwriteNewRunningDisplay()
	{
		timer.begin(Boss.OBOR, 1, 1); seconds(30); timer.terminalDeath(1, 40); timer.creditedKill(Boss.OBOR, 1, 40);
		timer.begin(Boss.OBOR, 2, 41); seconds(1); timer.completedTime(Duration.ofSeconds(31), 42);
		assertTrue(timer.snapshot().isRunning()); assertEquals(Duration.ofSeconds(1), timer.snapshot().elapsedAt(nanos.get()));
		timer.interrupt(); assertEquals(Duration.ofSeconds(31), timer.snapshot().getFrozen());
	}
	@Test public void excludedAndOtherCompletionsAreTimingBarriers()
	{
		timer.begin(Boss.OBOR, 1, 1); seconds(30); timer.terminalDeath(1, 40); timer.creditedKill(Boss.OBOR, 1, 40);
		timer.creditedKill(null, 1, 41); timer.completedTime(Duration.ofSeconds(80), 41);
		assertFalse(timer.snapshot().isOfficial()); assertEquals(Duration.ofSeconds(30), timer.snapshot().getFrozen());
	}
	@Test public void disableInterruptAndCharacterClearHaveDifferentRetentionRules()
	{
		timer.begin(Boss.OBOR, 1, 1); seconds(30); finish(Boss.OBOR, 1, 1, 40);
		timer.begin(Boss.VORKATH, 2, 70); seconds(3); timer.setEnabled(false);
		assertEquals(Boss.OBOR, timer.snapshot().getBoss()); assertEquals(Duration.ofSeconds(30), timer.snapshot().getFrozen());
		assertFalse(timer.begin(Boss.VORKATH, 2, 71)); timer.setEnabled(true); assertFalse(timer.hasRunning());
		timer.clearCharacter(); assertNull(timer.snapshot().getBoss()); assertEquals(Duration.ZERO, timer.snapshot().getFrozen());
		timer.terminalDeath(2, 72); timer.completedTime(Duration.ofSeconds(200), 72); assertNull(timer.snapshot().getBoss());
	}
	@Test public void duplicateOrExpiredDurationNeverCorrectsANewResult()
	{
		timer.begin(Boss.OBOR, 1, 1); seconds(30); finish(Boss.OBOR, 1, 1, 40);
		timer.completedTime(Duration.ofSeconds(200), 41); assertEquals(Duration.ofSeconds(30), timer.snapshot().getFrozen());
		timer.expire(60); timer.completedTime(Duration.ofSeconds(300), 60); assertEquals(Duration.ofSeconds(30), timer.snapshot().getFrozen());
	}
	@Test public void formattingPreservesTenthsAndHandlesLongRuns()
	{
		assertEquals("00:00.0", FightTimerSnapshot.format(Duration.ZERO));
		assertEquals("01:04.2", FightTimerSnapshot.format(Duration.ofMillis(64299)));
		assertEquals("125:00.0", FightTimerSnapshot.format(Duration.ofMinutes(125)));
	}
	@Test public void repeatedEarlyDurationsStayAmbiguousUntilCompletion()
	{
		timer.begin(Boss.OBOR, 1, 1); seconds(10); timer.terminalDeath(1, 12);
		timer.completedTime(Duration.ofSeconds(80), 12); timer.completedTime(Duration.ofSeconds(80), 12); timer.completedTime(Duration.ofSeconds(80), 12);
		timer.creditedKill(Boss.OBOR, 1, 13); assertFalse(timer.snapshot().isOfficial()); assertEquals(Duration.ofSeconds(10), timer.snapshot().getFrozen());
	}
	@Test public void liveTimingNeverCreatesSessionKillsOrEstimatedCompletedTimes()
	{
		SessionTracker sessions = new SessionTracker(java.time.Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
		sessions.start(Boss.VORKATH); assertFalse(timer.hasRunning());
		timer.begin(Boss.VORKATH, 1, 1); seconds(20); timer.terminalDeath(1, 30);
		assertEquals(0, sessions.snapshot().getKills()); assertNull(sessions.snapshot().getAverageTime());
		timer.creditedKill(Boss.VORKATH, 10, 31); sessions.creditedKill(Boss.VORKATH, 10, 31);
		assertEquals(1, sessions.snapshot().getKills()); assertEquals(0, sessions.snapshot().getTimedKills());
		Duration official = Duration.ofSeconds(22); timer.completedTime(official, 31); sessions.completedTime(official, 31);
		assertEquals(official, timer.snapshot().getFrozen()); assertEquals(official, sessions.snapshot().getAverageTime());
	}
}
