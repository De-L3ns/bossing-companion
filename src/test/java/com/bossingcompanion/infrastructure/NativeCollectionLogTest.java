package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.CollectionItem;
import com.bossingcompanion.application.ProgressTracker;
import net.runelite.api.gameval.InterfaceID;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeCollectionLogTest
{
	private final List<CollectionItem> items = List.of(new CollectionItem(1, "Item"));
	@Test public void requiresExactCompleteItemSetAndConsistentObtainedHeader()
	{
		assertNull(NativeCollectionLog.validatePage(Boss.VORKATH, items, List.of("Obtained: 0/1"), Map.of(2, 0L)));
		assertNull(NativeCollectionLog.validatePage(Boss.VORKATH, items, List.of("Obtained: 1/1"), Map.of(1, 0L)));
		assertNull(NativeCollectionLog.validatePage(Boss.VORKATH, items, List.of("Kills: 100"), Map.of(1, 1L)));
		assertNotNull(NativeCollectionLog.validatePage(Boss.VORKATH, items, List.of("Obtained: 0/1"), Map.of(1, 0L)));
	}
	@Test public void genericAndNamedCountersKeepOtherVariantsSeparate()
	{
		assertEquals(Long.valueOf(1234), NativeCollectionLog.validatePage(Boss.VORKATH, items,
			List.of("Obtained: 1/1", "Kills: 1,234"), Map.of(1, 1L)).getTotalKills());
		NativeCollectionLog.Page page = NativeCollectionLog.validatePage(Boss.NIGHTMARE, items,
			List.of("Obtained: 1/1", "The Nightmare kills: 120", "Phosani's Nightmare kills: 500"), Map.of(1, 1L));
		assertEquals(Long.valueOf(120), page.getTotalKills());
		assertNull(NativeCollectionLog.validatePage(Boss.NIGHTMARE, items,
			List.of("Obtained: 1/1", "Kills: 120", "Completions: 500"), Map.of(1, 1L)).getTotalKills());
	}
	@Test public void nativeHeaderTextChildrenSupplyThePageAndCount()
	{
		ProgressTracker progress = new ProgressTracker(); progress.define(Boss.ABYSSAL_SIRE, items);
		Map<Integer, List<String>> widgets = Map.of(InterfaceID.Collection.HEADER, List.of(),
			InterfaceID.Collection.HEADER_TEXT, List.of("Abyssal Sire", "Obtained: <col=ffff00>1/1</col>", "Abyssal Sire kills: <col=ffffff>123</col>"));
		NativeCollectionLog.Page page = NativeCollectionLog.readPage(progress,
			id -> widgets.getOrDefault(id, List.of()), id -> id == InterfaceID.Collection.ITEMS_CONTENTS ? Map.of(1, 2L) : Map.of());
		assertNotNull(page); assertEquals(Boss.ABYSSAL_SIRE, page.getBoss()); assertEquals(Long.valueOf(123), page.getTotalKills());
		progress.completePage(page.getBoss(), page.getQuantities(), page.getTotalKills());
		assertEquals(Long.valueOf(2), progress.snapshot(Boss.ABYSSAL_SIRE).getSlots().get(0).getQuantity());
	}
}
