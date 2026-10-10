package com.bossingcompanion.domain;

import java.time.Duration;
import lombok.Value;

/** Immutable render data. Observation time is separate from official completed duration. */
@Value
public class FightTimerSnapshot
{
	Boss boss;
	boolean running;
	long startedNanos;
	Duration frozen;
	boolean official;
	boolean observed;
	public FightTimerSnapshot(Boss boss, boolean running, long startedNanos, Duration frozen, boolean official)
	{
		this(boss, running, startedNanos, frozen, official, running || !frozen.isZero() || official);
	}
	public FightTimerSnapshot(Boss boss, boolean running, long startedNanos, Duration frozen, boolean official, boolean observed)
	{
		this.boss = boss; this.running = running; this.startedNanos = startedNanos; this.frozen = frozen;
		this.official = official; this.observed = observed || running || official;
	}

	public Duration elapsedAt(long nowNanos)
	{
		return running ? Duration.ofNanos(Math.max(0, nowNanos - startedNanos)) : frozen;
	}
	public boolean isApproximate() { return observed && !official; }
	public static String format(Duration elapsed)
	{
		long tenths = elapsed.toMillis() / 100;
		long minutes = tenths / 600, seconds = tenths / 10 % 60;
		return (minutes < 10 ? "0" : "") + minutes + ":" + (seconds < 10 ? "0" : "") + seconds + "." + tenths % 10;
	}
}
