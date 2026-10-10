package com.bossingcompanion.application;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.LootEntry;
import com.bossingcompanion.domain.SessionSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.gameval.ItemID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class SessionTrackerTest
{
	private TestClock clock;
	private SessionTracker tracker;
	@Before public void setup() { clock = new TestClock(); tracker = new SessionTracker(clock); }
	private static LootEntry coins(long quantity)
	{
		return new LootEntry(ItemID.COINS, ItemID.COINS, "Coins", quantity, 1L);
	}
	@Test public void firstLifetimeCountAddsOneAndDoesNotInferMissingKills()
	{
		assertTrue(tracker.creditedKill(Boss.VORKATH, 500, 1));
		assertEquals(1, tracker.snapshot().getKills());
		assertFalse(tracker.creditedKill(Boss.VORKATH, 500, 1));
		assertFalse(tracker.creditedKill(Boss.VORKATH, 499, 2));
		assertTrue(tracker.creditedKill(Boss.VORKATH, 503, 15));
		assertEquals(2, tracker.snapshot().getKills());
	}
	@Test public void preStartClockAndAutoStartClockHaveDifferentOrigins()
	{
		tracker.start(Boss.VORKATH);
		clock.advance(60);
		tracker.creditedKill(Boss.VORKATH, 10, 1);
		assertEquals(Duration.ofSeconds(60), tracker.snapshot().elapsedAt(clock.instant()));
		tracker.clear();
		tracker.creditedKill(Boss.ZULRAH, 100, 2);
		assertEquals(Duration.ZERO, tracker.snapshot().elapsedAt(clock.instant()));
	}
	@Test public void autoDisabledStillAllowsManualRecording()
	{
		tracker.setAutomatic(false);
		assertFalse(tracker.creditedKill(Boss.VORKATH, 1, 1));
		assertNull(tracker.snapshot());
		tracker.start(Boss.VORKATH);
		tracker.creditedKill(Boss.VORKATH, 2, 20);
		assertEquals(1, tracker.snapshot().getKills());
	}
	@Test public void outOfOrderLootAndTimeSettleOntoFirstCreditedKill()
	{
		tracker.serverLoot(Boss.VORKATH, Collections.singletonList(coins(200)), 1);
		tracker.completedTime(Duration.ofMillis(102600), 1);
		assertNull(tracker.snapshot()); // Loot or duration alone never establishes credit.
		tracker.creditedKill(Boss.VORKATH, 500, 2);
		SessionSnapshot result = tracker.snapshot();
		assertEquals(1, result.getKills());
		assertEquals(1, result.getLootKills());
		assertEquals(200, result.getLoot().get(0).getQuantity());
		assertEquals(Duration.ofMillis(102600), result.getLastTime());
	}
	@Test public void sameItemsOnConsecutiveKillsAreNotDeduplicated()
	{
		for (int i = 1; i <= 2; i++)
		{
			tracker.creditedKill(Boss.VORKATH, i, i);
			tracker.serverLoot(Boss.VORKATH, Collections.singletonList(coins(200)), i);
		}
		assertEquals(2, tracker.snapshot().getKills());
		assertEquals(400, tracker.snapshot().getLoot().get(0).getQuantity());
	}
	@Test public void replayAfterSettledEvidenceCannotContaminateNextKill()
	{
		tracker.creditedKill(Boss.VORKATH, 1, 1);
		tracker.serverLoot(Boss.VORKATH, Collections.singletonList(coins(200)), 1);
		tracker.completedTime(Duration.ofSeconds(100), 1);
		tracker.serverLoot(Boss.VORKATH, Collections.singletonList(coins(200)), 2);
		tracker.completedTime(Duration.ofSeconds(100), 2);
		tracker.creditedKill(Boss.VORKATH, 2, 3);
		assertEquals(1, tracker.snapshot().getLootKills());
		assertEquals(1, tracker.snapshot().getTimedKills());
		assertNull(tracker.snapshot().getLastTime());
	}
	@Test public void missingTimeDoesNotDiluteAverageAndLateOlderTimeIsNotLast()
	{
		tracker.creditedKill(Boss.VORKATH, 1, 1);
		tracker.completedTime(Duration.ofSeconds(100), 1);
		tracker.creditedKill(Boss.VORKATH, 2, 2);
		assertEquals(Duration.ofSeconds(100), tracker.snapshot().getAverageTime());
		assertNull(tracker.snapshot().getLastTime());
		assertEquals(1, tracker.snapshot().getTimedKills());
		tracker.completedTime(Duration.ofSeconds(80), 3);
		assertEquals(Duration.ofSeconds(90), tracker.snapshot().getAverageTime());
		assertEquals(Duration.ofSeconds(80), tracker.snapshot().getBestTime());
	}
	@Test public void ambiguousTimeAndLootAreNotAssignedByLatestBoss()
	{
		tracker.creditedKill(Boss.VORKATH, 1, 1);
		tracker.creditedKill(Boss.VORKATH, 2, 2);
		tracker.completedTime(Duration.ofSeconds(80), 3);
		tracker.serverLoot(Boss.VORKATH, Collections.singletonList(coins(200)), 3);
		assertEquals(0, tracker.snapshot().getTimedKills());
		assertEquals(0, tracker.snapshot().getLootKills());
	}
	@Test public void aDifferentBossCannotReplaceOrAddToManualSession()
	{
		tracker.start(Boss.VORKATH);
		tracker.creditedKill(Boss.ZULRAH, 1, 1);
		tracker.completedTime(Duration.ofSeconds(60), 1);
		tracker.serverLoot(Boss.ZULRAH, Collections.singletonList(coins(200)), 1);
		assertEquals(Boss.VORKATH, tracker.snapshot().getBoss());
		assertEquals(0, tracker.snapshot().getKills());
		assertTrue(tracker.snapshot().getLoot().isEmpty());
	}
	@Test public void excludedBossTimeDoesNotAttachToSupportedCompletion()
	{
		tracker.creditedKill(Boss.VORKATH, 1, 1);
		tracker.excludedCompletion(2);
		tracker.completedTime(Duration.ofSeconds(60), 2);
		assertEquals(0, tracker.snapshot().getTimedKills());
	}
	@Test public void endFreezesClockButAllowsOwnedLateEvidence()
	{
		tracker.creditedKill(Boss.VORKATH, 1, 1);
		clock.advance(30);
		tracker.end();
		tracker.serverLoot(Boss.VORKATH, Collections.singletonList(coins(200)), 2);
		tracker.completedTime(Duration.ofSeconds(90), 2);
		clock.advance(30);
		assertFalse(tracker.snapshot().isActive());
		assertEquals(Duration.ofSeconds(30), tracker.snapshot().elapsedAt(clock.instant()));
		assertEquals(1, tracker.snapshot().getLootKills());
		assertEquals(Duration.ofSeconds(90), tracker.snapshot().getLastTime());
	}
	@Test public void lateLootForEndedBossCannotBeStolenByNewSession()
	{
		tracker.creditedKill(Boss.VORKATH, 1, 1);
		tracker.end();
		tracker.start(Boss.ZULRAH);
		tracker.serverLoot(Boss.VORKATH, Collections.singletonList(coins(200)), 2);
		assertEquals(Boss.ZULRAH, tracker.snapshot().getBoss());
		assertTrue(tracker.snapshot().getLoot().isEmpty());
	}
	@Test public void expiryAndWorldHopInvalidatePendingEvidence()
	{
		tracker.serverLoot(Boss.VORKATH, Collections.singletonList(coins(200)), 1);
		tracker.creditedKill(Boss.VORKATH, 1, 20);
		assertTrue(tracker.snapshot().getLoot().isEmpty());
		tracker.clearPending();
		tracker.serverLoot(Boss.VORKATH, Collections.singletonList(coins(200)), 21);
		tracker.clearPending();
		tracker.creditedKill(Boss.VORKATH, 2, 22);
		assertEquals(2, tracker.snapshot().getKills());
		assertTrue(tracker.snapshot().getLoot().isEmpty());
	}
	@Test public void manualStartDoesNotAdoptPreStartEvidence()
	{
		tracker.serverLoot(Boss.VORKATH, Collections.singletonList(coins(200)), 1);
		tracker.start(Boss.VORKATH);
		tracker.creditedKill(Boss.VORKATH, 1, 2);
		assertTrue(tracker.snapshot().getLoot().isEmpty());
	}
	@Test public void unknownPricesKeepKnownValueAndMarkPartial()
	{
		tracker.creditedKill(Boss.VORKATH, 1, 1);
		tracker.serverLoot(Boss.VORKATH, Arrays.asList(coins(200), new LootEntry(ItemID.COINS,
			ItemID.COINS, "Coins", 100, null)), 1);
		assertTrue(tracker.snapshot().getLoot().get(0).isPartialValue());
		assertEquals(300, tracker.snapshot().getLoot().get(0).getQuantity());
		assertEquals(200, tracker.snapshot().getLoot().get(0).getKnownValue());
	}
	@Test public void characterResetClearsCountersAndEvidence()
	{
		tracker.creditedKill(Boss.VORKATH, 1000, 1);
		tracker.clear();
		assertNull(tracker.snapshot());
		assertTrue(tracker.creditedKill(Boss.VORKATH, 1, 2));
		assertEquals(1, tracker.snapshot().getKills());
	}
	@Test public void manyExpiredCompletionsDoNotRetainSessionHistory()
	{
		for (int i = 1; i <= 1000; i++) { tracker.creditedKill(Boss.VORKATH, i, i * 20); }
		assertEquals(1000, tracker.snapshot().getKills());
		assertTrue(tracker.snapshot().getLoot().isEmpty());
	}
	private static final class TestClock extends Clock
	{
		private Instant now = Instant.parse("2026-10-09T12:00:00Z");
		void advance(long seconds) { now = now.plusSeconds(seconds); }
		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return now; }
	}
}
