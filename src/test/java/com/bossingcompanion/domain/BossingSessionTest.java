package com.bossingcompanion.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class BossingSessionTest
{
	@Test public void completionAttachmentIsIdempotentAndSnapshotIsImmutable()
	{
		BossingSession session = new BossingSession(Boss.VORKATH, Instant.EPOCH);
		BossingSession.Completion kill = session.complete();
		kill.attachTime(Duration.ofSeconds(100));
		kill.attachTime(Duration.ofSeconds(50));
		kill.attachLoot(Collections.singletonList(new LootEntry(ItemID.COINS, ItemID.COINS, "Coins", 100, 1L)));
		SessionSnapshot snapshot = session.snapshot();
		kill.attachLoot(Collections.singletonList(new LootEntry(ItemID.COINS, ItemID.COINS, "Coins", 200, 1L)));
		assertEquals(100, snapshot.getLoot().get(0).getQuantity());
		assertEquals(1, session.snapshot().getLootKills());
		assertEquals(Duration.ofSeconds(100), session.snapshot().getAverageTime());
		try { snapshot.getLoot().clear(); fail("Snapshot must not be mutable"); }
		catch (UnsupportedOperationException expected) { }
	}
	@Test public void lateEarlierKillTimeDoesNotReplaceLatestKillTime()
	{
		BossingSession session = new BossingSession(Boss.VORKATH, Instant.EPOCH);
		BossingSession.Completion earlier = session.complete();
		BossingSession.Completion latest = session.complete();
		latest.attachTime(Duration.ofSeconds(100));
		earlier.attachTime(Duration.ofSeconds(80));
		assertEquals(Duration.ofSeconds(100), session.snapshot().getLastTime());
		assertEquals(Duration.ofSeconds(80), session.snapshot().getBestTime());
		assertEquals(Duration.ofSeconds(90), session.snapshot().getAverageTime());
	}
	@Test public void rawIdsAreRetainedWhileDisplayIdsAggregate()
	{
		BossingSession session = new BossingSession(Boss.VORKATH, Instant.EPOCH);
		BossingSession.Completion completion = session.complete();
		completion.attachLoot(Arrays.asList(new LootEntry(100, 200, "Fixture", 1, 10L),
			new LootEntry(200, 200, "Fixture", 2, 10L)));
		assertEquals(2, completion.getEntries().size());
		assertEquals(100, completion.getEntries().get(0).getRawItemId());
		assertEquals(3, session.snapshot().getLoot().get(0).getQuantity());
		assertEquals(30, session.snapshot().getLoot().get(0).getKnownValue());
	}
}
