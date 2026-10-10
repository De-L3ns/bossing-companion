package com.bossingcompanion.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
public class SessionSnapshot
{
	Boss boss;
	Instant startedAt;
	Instant endedAt;
	long kills;
	long timedKills;
	long lootKills;
	Duration lastTime;
	Duration bestTime;
	Duration averageTime;
	List<LootTotal> loot;

	public boolean isActive() { return endedAt == null; }
	public Duration elapsedAt(Instant now)
	{
		Duration elapsed = Duration.between(startedAt, endedAt == null ? now : endedAt);
		return elapsed.isNegative() ? Duration.ZERO : elapsed;
	}
}
