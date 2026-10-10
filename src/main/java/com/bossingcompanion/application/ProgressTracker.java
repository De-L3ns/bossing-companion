package com.bossingcompanion.application;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.BossProgress;
import com.bossingcompanion.domain.CollectionItem;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Client-thread owned. Collection quantities are shared item state, not boss drop history. */
public final class ProgressTracker
{
	private final Map<Boss, List<CollectionItem>> definitions = new EnumMap<>(Boss.class);
	private final Map<Boss, Long> totalKills = new EnumMap<>(Boss.class);
	private final Map<Integer, Long> quantities = new HashMap<>();

	public void define(Boss boss, List<CollectionItem> items) { definitions.put(boss, List.copyOf(items)); }
	public List<CollectionItem> items(Boss boss) { return definitions.getOrDefault(boss, Collections.emptyList()); }
	public boolean hasDefinitions() { return !definitions.isEmpty(); }
	public void totalKills(Boss boss, long count)
	{
		if (count >= 0) { totalKills.merge(boss, count, Math::max); }
	}
	public void observedItem(int id, long quantity)
	{
		if (quantity > 0) { quantities.merge(id, quantity, Math::max); }
	}
	public void completePage(Boss boss, Map<Integer, Long> page, Long kc)
	{
		List<CollectionItem> expected = items(boss);
		if (expected.isEmpty() || page.size() != expected.size() || expected.stream().anyMatch(i -> !page.containsKey(i.getItemId()))) { return; }
		for (Map.Entry<Integer, Long> entry : page.entrySet())
		{
			if (entry.getValue() >= 0) { quantities.merge(entry.getKey(), entry.getValue(), Math::max); }
		}
		if (kc != null) { totalKills(boss, kc); }
	}
	public BossProgress snapshot(Boss boss)
	{
		return new BossProgress(boss, totalKills.get(boss), List.copyOf(items(boss).stream()
			.map(i -> new BossProgress.Slot(i, quantities.get(i.getItemId()))).collect(Collectors.toList())));
	}
	public void clearCharacter() { totalKills.clear(); quantities.clear(); }
}
