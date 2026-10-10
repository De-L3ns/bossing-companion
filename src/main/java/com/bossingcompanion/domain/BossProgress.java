package com.bossingcompanion.domain;

import java.util.List;
import lombok.Value;

@Value
public class BossProgress
{
	Boss boss;
	Long totalKills;
	List<Slot> slots;
	public BossProgress(Boss boss, Long totalKills, List<Slot> slots)
	{
		this.boss = boss; this.totalKills = totalKills; this.slots = List.copyOf(slots);
	}
	@Value
	public static class Slot
	{
		CollectionItem item;
		Long quantity; // null = not observed, 0 = confirmed missing.
		public boolean isKnown() { return quantity != null; }
		public boolean isObtained() { return quantity != null && quantity > 0; }
	}
}
