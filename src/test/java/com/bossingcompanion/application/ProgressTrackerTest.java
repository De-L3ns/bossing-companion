package com.bossingcompanion.application;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.CollectionItem;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressTrackerTest
{
	private final ProgressTracker progress = new ProgressTracker();
	private final CollectionItem item = new CollectionItem(1, "Shared item");
	@Test public void partialEvidenceNeverConfirmsMissing()
	{
		progress.define(Boss.VORKATH, List.of(item, new CollectionItem(2, "Other")));
		progress.completePage(Boss.VORKATH, Map.of(1, 0L), 400L);
		assertNull(progress.snapshot(Boss.VORKATH).getTotalKills());
		assertFalse(progress.snapshot(Boss.VORKATH).getSlots().get(0).isKnown());
		progress.observedItem(1, 2); progress.observedItem(2, 0);
		assertEquals(Long.valueOf(2), progress.snapshot(Boss.VORKATH).getSlots().get(0).getQuantity());
		assertFalse(progress.snapshot(Boss.VORKATH).getSlots().get(1).isKnown());
	}
	@Test public void sharedItemsAreAccountStateAndKillsStaySeparate()
	{
		progress.define(Boss.VORKATH, List.of(item)); progress.define(Boss.ZULRAH, List.of(item));
		progress.completePage(Boss.VORKATH, Map.of(1, 0L), 500L);
		assertTrue(progress.snapshot(Boss.ZULRAH).getSlots().get(0).isKnown());
		assertNull(progress.snapshot(Boss.ZULRAH).getTotalKills());
		progress.observedItem(1, 3); progress.completePage(Boss.VORKATH, Map.of(1, 0L), 400L);
		assertEquals(Long.valueOf(3), progress.snapshot(Boss.ZULRAH).getSlots().get(0).getQuantity());
		assertEquals(Long.valueOf(500), progress.snapshot(Boss.VORKATH).getTotalKills());
	}
	@Test public void logoutClearsPrivateDataAndKeepsPublicDefinitions()
	{
		progress.define(Boss.VORKATH, List.of(item)); progress.observedItem(1, 4); progress.totalKills(Boss.VORKATH, 100);
		progress.clearCharacter();
		assertEquals(1, progress.items(Boss.VORKATH).size());
		assertNull(progress.snapshot(Boss.VORKATH).getTotalKills());
		assertFalse(progress.snapshot(Boss.VORKATH).getSlots().get(0).isKnown());
	}
}
