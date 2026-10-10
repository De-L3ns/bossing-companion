package com.bossingcompanion.application;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.BossingSession;
import com.bossingcompanion.domain.LootEntry;
import com.bossingcompanion.domain.SessionSnapshot;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Serial, bounded correlation of credited KC with server loot and completed duration. */
public final class SessionTracker
{
	private static final int MAX_PENDING = 32;
	private final Clock clock;
	private final Map<Boss, Long> highWater = new EnumMap<>(Boss.class);
	private final List<Slot> slots = new ArrayList<>();
	private final List<PendingLoot> pendingLoot = new ArrayList<>();
	private final List<PendingTime> pendingTime = new ArrayList<>();
	private BossingSession session;
	private boolean automatic = true;

	public SessionTracker(Clock clock) { this.clock = clock; }
	public void setAutomatic(boolean enabled) { automatic = enabled; }
	public SessionSnapshot snapshot() { return session == null ? null : session.snapshot(); }

	public void start(Boss boss)
	{
		if (session != null && session.isActive()) { return; }
		// Evidence predating manual pre-start may not be assigned to the new session.
		pendingLoot.clear();
		pendingTime.clear();
		session = new BossingSession(boss, clock.instant());
	}

	public void end() { if (session != null) { session.end(clock.instant()); } }
	public void clear()
	{
		end();
		session = null;
		highWater.clear();
		clearPending();
	}
	public void clearPending() { slots.clear(); pendingLoot.clear(); pendingTime.clear(); }

	/** An excluded completion must consume its own unnamed duration, not a supported boss's. */
	public void excludedCompletion(int tick)
	{
		expire(tick);
		pendingTime.clear();
		boundedAdd(slots, new Slot(null, tick, null));
	}

	public boolean creditedKill(Boss boss, long count, int tick)
	{
		expire(tick);
		if (count <= highWater.getOrDefault(boss, 0L)) { return false; }
		highWater.put(boss, count);
		if ((session == null || !session.isActive()) && automatic)
		{
			session = new BossingSession(boss, clock.instant());
		}
		BossingSession.Completion completion = session != null && session.isActive() && session.getBoss() == boss
			? session.complete() : null;
		Slot slot = new Slot(boss, tick, completion);
		boundedAdd(slots, slot);
		// Consume only unambiguous observations; never choose by UI selection or item contents.
		List<PendingLoot> loot = pendingLoot.stream().filter(p -> p.boss == boss).collect(Collectors.toList());
		if (loot.size() == 1 && candidates(s -> s.boss == boss && !s.lootUsed).size() == 1)
		{
			attachLoot(slot, loot.get(0).items);
		}
		pendingLoot.removeAll(loot);
		if (pendingTime.size() == 1 && candidates(s -> !s.timeUsed).size() == 1)
		{
			attachTime(slot, pendingTime.get(0).duration);
		}
		pendingTime.clear();
		return completion != null;
	}

	public void serverLoot(Boss boss, List<LootEntry> items, int tick)
	{
		expire(tick);
		List<Slot> matches = candidates(s -> s.boss == boss && !s.lootUsed);
		if (matches.size() == 1) { attachLoot(matches.get(0), items); }
		else if (matches.isEmpty() && candidates(s -> s.boss == boss).isEmpty())
		{
			boundedAdd(pendingLoot, new PendingLoot(boss, tick, items));
		}
		// Multiple eligible completions deliberately leave loot unassigned.
	}

	public void completedTime(Duration duration, int tick)
	{
		expire(tick);
		List<Slot> matches = candidates(s -> !s.timeUsed);
		if (matches.size() == 1) { attachTime(matches.get(0), duration); }
		else if (matches.isEmpty() && slots.isEmpty()) { boundedAdd(pendingTime, new PendingTime(tick, duration)); }
	}

	public void expire(int tick)
	{
		slots.removeIf(s -> expired(s.tick, tick, horizon(s.boss)));
		pendingLoot.removeIf(p -> expired(p.tick, tick, horizon(p.boss)));
		pendingTime.removeIf(p -> expired(p.tick, tick, 12));
	}

	private static boolean expired(int before, int now, int horizon) { return now < before || now - before > horizon; }
	private static int horizon(Boss boss) { return boss == Boss.ARAXXOR || boss == Boss.NIGHTMARE ? 30 : 12; }
	private List<Slot> candidates(Predicate<Slot> predicate) { return slots.stream().filter(predicate).collect(Collectors.toList()); }
	private static <T> void boundedAdd(List<T> list, T value)
	{
		if (list.size() == MAX_PENDING) { list.remove(0); }
		list.add(value);
	}
	private static void attachLoot(Slot slot, List<LootEntry> items)
	{
		slot.lootUsed = true;
		if (slot.completion != null) { slot.completion.attachLoot(items); }
	}
	private static void attachTime(Slot slot, Duration duration)
	{
		slot.timeUsed = true;
		if (slot.completion != null) { slot.completion.attachTime(duration); }
	}
	private static final class Slot
	{
		final Boss boss;
		final int tick;
		final BossingSession.Completion completion;
		boolean lootUsed;
		boolean timeUsed;
		Slot(Boss boss, int tick, BossingSession.Completion completion) { this.boss = boss; this.tick = tick; this.completion = completion; }
	}
	private static final class PendingLoot
	{
		final Boss boss;
		final int tick;
		final List<LootEntry> items;
		PendingLoot(Boss boss, int tick, List<LootEntry> items) { this.boss = boss; this.tick = tick; this.items = new ArrayList<>(items); }
	}
	private static final class PendingTime
	{
		final int tick;
		final Duration duration;
		PendingTime(int tick, Duration duration) { this.tick = tick; this.duration = duration; }
	}
}
