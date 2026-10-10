package com.bossingcompanion.application;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.FightTimerSnapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/** Client-thread owned observed attempts, with bounded independent completion correlation. */
public final class FightTimerTracker
{
	private static final int WINDOW = 12;
	private static final int CAPACITY = 8;
	private final LongSupplier nanos;
	private final Map<Boss, Long> highWater = new EnumMap<>(Boss.class);
	private final List<Attempt> ended = new ArrayList<>();
	private final List<Completion> completions = new ArrayList<>();
	private Attempt running;
	private Attempt displayed;
	private Attempt lastCredited;
	private PendingTime earlyTime;
	private boolean earlyAmbiguous;
	private boolean enabled;

	public FightTimerTracker(LongSupplier nanos) { this.nanos = nanos; }
	public boolean isEnabled() { return enabled; }
	public boolean hasRunning() { return running != null; }
	public boolean isRunningEntity(long entity) { return running != null && running.entity == entity; }
	public void setEnabled(boolean enabled)
	{
		if (this.enabled == enabled) { return; }
		interrupt(); this.enabled = enabled;
	}
	public void clearCharacter() { interrupt(); lastCredited = null; displayed = null; highWater.clear(); }
	public void interrupt()
	{
		running = null; ended.clear(); completions.clear(); earlyTime = null; earlyAmbiguous = false; displayed = lastCredited;
	}
	public FightTimerSnapshot snapshot()
	{
		if (displayed == null) { return new FightTimerSnapshot(null, false, 0, Duration.ZERO, false); }
		return new FightTimerSnapshot(displayed.boss, displayed == running, displayed.started,
			displayed.duration == null ? Duration.ZERO : displayed.duration, displayed.official, true);
	}
	public boolean begin(Boss boss, long entity, int tick)
	{
		expire(tick);
		if (!enabled || boss == null || running != null || ended.stream().anyMatch(a -> a.entity == entity)) { return false; }
		running = new Attempt(boss, entity, tick, nanos.getAsLong()); displayed = running; earlyTime = null; earlyAmbiguous = false;
		return true;
	}
	public void terminalDeath(long entity, int tick)
	{
		expire(tick);
		if (enabled && isRunningEntity(entity) && tick >= running.startTick) { freeze(running, tick); }
	}
	public void creditedKill(Boss boss, long count, int tick)
	{
		expire(tick);
		if (boss != null)
		{
			if (count <= highWater.getOrDefault(boss, 0L)) { return; }
			highWater.put(boss, count);
		}
		if (!enabled) { return; }
		List<Attempt> matches = ended.stream().filter(a -> !a.credited && a.boss == boss).collect(Collectors.toList());
		if (running != null && running.boss == boss && tick >= running.startTick) { matches.add(running); }
		Attempt match = matches.size() == 1 ? matches.get(0) : null;
		if (match != null)
		{
			if (match == running) { freeze(match, tick); }
			match.credited = true;
			if (lastCredited == null || match.started >= lastCredited.started) { lastCredited = match; }
			// An older completion cannot overwrite a newer displayed attempt.
			if (displayed == match || displayed == null) { displayed = match; }
		}
		if (completions.size() == CAPACITY) { completions.remove(0); }
		Completion completion = new Completion(tick, match); completions.add(completion);
		if (earlyTime != null && match != null && earlyTime.attempt == match
			&& completions.stream().filter(c -> !c.used).count() == 1)
		{
			apply(completion, earlyTime.duration);
		}
		earlyTime = null; earlyAmbiguous = false;
	}
	public void completedTime(Duration duration, int tick)
	{
		expire(tick);
		if (!enabled || duration == null || duration.isNegative() || duration.isZero() || duration.compareTo(Duration.ofDays(1)) > 0) { return; }
		List<Completion> candidates = completions.stream().filter(c -> !c.used).collect(Collectors.toList());
		if (candidates.size() == 1) { apply(candidates.get(0), duration); return; }
		// Known/ambiguous completions consume their own timing horizon, never replay into a future fight.
		if (!completions.isEmpty()) { return; }
		if (earlyAmbiguous) { return; }
		List<Attempt> attempts = ended.stream().filter(a -> !a.credited).collect(Collectors.toList());
		// Before credit, only a uniquely ended attempt can accept timing. Active-only timing could be late from an old fight.
		if (running == null && attempts.size() == 1 && earlyTime == null && tick >= attempts.get(0).startTick)
		{ earlyTime = new PendingTime(attempts.get(0), tick, duration); }
		else { earlyTime = null; earlyAmbiguous = true; }
	}
	public void expire(int tick)
	{
		completions.removeIf(c -> tick - c.tick > WINDOW || tick < c.tick);
		ended.removeIf(a ->
		{
			boolean expired = tick - a.endedTick > WINDOW || tick < a.endedTick;
			if (expired && !a.credited && displayed == a) { displayed = lastCredited; }
			return expired;
		});
		if (earlyTime != null && (tick - earlyTime.tick > WINDOW || tick < earlyTime.tick)) { earlyTime = null; }
	}
	private void freeze(Attempt attempt, int tick)
	{
		attempt.duration = Duration.ofNanos(Math.max(0, nanos.getAsLong() - attempt.started)); attempt.endedTick = tick;
		running = null;
		if (ended.size() == CAPACITY)
		{
			Attempt removed = ended.remove(0); if (!removed.credited && displayed == removed) { displayed = lastCredited; }
		}
		ended.add(attempt);
	}
	private static void apply(Completion completion, Duration duration)
	{
		completion.used = true;
		if (completion.attempt != null) { completion.attempt.duration = duration; completion.attempt.official = true; }
	}
	private static final class Attempt
	{
		final Boss boss; final long entity; final int startTick; final long started;
		int endedTick; Duration duration; boolean credited; boolean official;
		Attempt(Boss boss, long entity, int tick, long started) { this.boss = boss; this.entity = entity; this.startTick = tick; this.started = started; }
	}
	private static final class Completion
	{
		final int tick; final Attempt attempt; boolean used;
		Completion(int tick, Attempt attempt) { this.tick = tick; this.attempt = attempt; }
	}
	private static final class PendingTime
	{
		final Attempt attempt; final int tick; final Duration duration;
		PendingTime(Attempt attempt, int tick, Duration duration) { this.attempt = attempt; this.tick = tick; this.duration = duration; }
	}
}
