package com.bossingcompanion.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/** Client-thread-owned session. Only immutable snapshots cross into presentation. */
public final class BossingSession
{
	@Getter private final Boss boss;
	private final Instant startedAt;
	private Instant endedAt;
	private long kills;
	private long timedKills;
	private long lootKills;
	private Duration totalTime = Duration.ZERO;
	private Duration lastTime;
	private Duration bestTime;
	private final Map<Integer, LootTotal> loot = new HashMap<>();

	public BossingSession(Boss boss, Instant startedAt)
	{
		this.boss = boss;
		this.startedAt = startedAt;
	}

	public boolean isActive() { return endedAt == null; }
	public void end(Instant now) { if (isActive()) { endedAt = now; } }

	public Completion complete()
	{
		if (!isActive()) { throw new IllegalStateException("Session has ended"); }
		lastTime = null;
		return new Completion(++kills);
	}

	public SessionSnapshot snapshot()
	{
		List<LootTotal> totals = new ArrayList<>(loot.values());
		totals.sort(Comparator.comparing(LootTotal::isPartialValue)
			.thenComparing(Comparator.comparingLong(LootTotal::getKnownValue).reversed())
			.thenComparing(LootTotal::getName).thenComparingInt(LootTotal::getItemId));
		return new SessionSnapshot(boss, startedAt, endedAt, kills, timedKills, lootKills,
			lastTime, bestTime, timedKills == 0 ? null : totalTime.dividedBy(timedKills),
			Collections.unmodifiableList(totals));
	}

	/** Retains session ownership while late completion evidence settles. */
	public final class Completion
	{
		private final long sequence;
		private boolean hasTime;
		private boolean hasLoot;
		@Getter private List<LootEntry> entries = Collections.emptyList();

		private Completion(long sequence) { this.sequence = sequence; }

		public void attachTime(Duration duration)
		{
			if (hasTime || duration == null || duration.isNegative() || duration.isZero()) { return; }
			hasTime = true;
			timedKills++;
			totalTime = totalTime.plus(duration);
			if (sequence == kills) { lastTime = duration; }
			if (bestTime == null || duration.compareTo(bestTime) < 0) { bestTime = duration; }
		}

		public void attachLoot(List<LootEntry> received)
		{
			if (hasLoot) { return; }
			hasLoot = true;
			lootKills++;
			entries = Collections.unmodifiableList(new ArrayList<>(received));
			for (LootEntry entry : received)
			{
				if (entry.getQuantity() <= 0) { continue; }
				LootTotal old = loot.get(entry.getDisplayItemId());
				long value = entry.getUnitValue() == null ? 0 : entry.getQuantity() * entry.getUnitValue();
				loot.put(entry.getDisplayItemId(), new LootTotal(entry.getDisplayItemId(), entry.getName(),
					entry.getQuantity() + (old == null ? 0 : old.getQuantity()),
					value + (old == null ? 0 : old.getKnownValue()),
					entry.getUnitValue() == null || old != null && old.isPartialValue()));
			}
		}
	}
}
