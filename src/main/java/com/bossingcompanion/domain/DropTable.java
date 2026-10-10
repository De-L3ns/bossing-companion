package com.bossingcompanion.domain;

import java.time.Instant;
import java.util.Map;
import lombok.Value;

@Value
public class DropTable
{
	Boss boss;
	String source;
	Instant fetchedAt;
	Map<Integer, DropEntry> entries;
	public DropTable(Boss boss, String source, Instant fetchedAt, Map<Integer, DropEntry> entries)
	{
		this.boss = boss; this.source = source; this.fetchedAt = fetchedAt; this.entries = Map.copyOf(entries);
	}
}
