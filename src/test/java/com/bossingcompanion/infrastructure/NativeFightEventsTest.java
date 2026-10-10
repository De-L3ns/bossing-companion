package com.bossingcompanion.infrastructure;

import com.bossingcompanion.application.FightTimerTracker;
import com.bossingcompanion.domain.Boss;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native source state through a passive primitive API port, without reflection or game client. */
public class NativeFightEventsTest
{
	private final AtomicLong nanos = new AtomicLong();
	private final FakeClient client = new FakeClient();
	private FightTimerTracker timer;
	private NativeFightEvents events;
	private ActorData boss;
	@Before public void setup()
	{
		timer = new FightTimerTracker(nanos::get); events = new NativeFightEvents(client, timer);
		boss = new ActorData(NpcID.HILLGIANT_BOSS); events.spawned(boss); events.setEnabled(true);
	}
	private void target(ActorData actor) { client.target = actor; events.interacting(client.player, actor); }
	private static Hitsplat hit(int type)
	{
		return new Hitsplat()
		{
			public int getHitsplatType() { return type; } public int getAmount() { return 0; } public int getDisappearsOnGameCycle() { return 0; }
		};
	}
	@Test public void targetAndSpawnAreNotCombatButAnOwnedBlockedHitIs()
	{
		target(boss); assertFalse(timer.hasRunning());
		assertFalse(events.hitsplat(boss, hit(HitsplatID.DAMAGE_OTHER))); assertFalse(timer.hasRunning());
		assertTrue(events.hitsplat(boss, hit(HitsplatID.BLOCK_ME))); assertTrue(timer.hasRunning());
		nanos.addAndGet(Duration.ofSeconds(2).toNanos()); assertFalse(events.hitsplat(boss, hit(HitsplatID.DAMAGE_ME)));
		assertEquals(Duration.ofSeconds(2), timer.snapshot().elapsedAt(nanos.get()));
	}
	@Test public void minionsQuestVariantsAndUnreviewedBossesCannotStart()
	{
		for (int id : new int[]{NpcID.VORKATH_SPAWN, NpcID.VORKATH_QUEST, NpcID.SNAKEBOSS_BOSS_RANGED})
		{
			ActorData excluded = new ActorData(id); target(excluded); assertFalse(events.hitsplat(excluded, hit(HitsplatID.DAMAGE_ME)));
		}
		assertFalse(timer.hasRunning());
	}
	@Test public void enablePartwayWaitsForAFreshNpcIdentity()
	{
		events.setEnabled(false); target(boss); assertFalse(events.hitsplat(boss, hit(HitsplatID.DAMAGE_ME)));
		events.setEnabled(true); assertFalse(events.hitsplat(boss, hit(HitsplatID.DAMAGE_ME)));
		events.despawned(boss); ActorData fresh = new ActorData(NpcID.HILLGIANT_BOSS); events.spawned(fresh); target(fresh);
		assertTrue(events.hitsplat(fresh, hit(HitsplatID.DAMAGE_ME)));
	}
	@Test public void targetedAtEnableIsConservativelyBlocked()
	{
		events.setEnabled(false); client.target = boss; events.setEnabled(true);
		assertFalse(events.hitsplat(boss, hit(HitsplatID.DAMAGE_ME)));
	}
	@Test public void lossOfTargetAndMovementDoNotPauseButPlayerDeathInterrupts()
	{
		target(boss); events.hitsplat(boss, hit(HitsplatID.DAMAGE_ME)); client.target = null;
		events.interacting(client.player, null); client.player.location = new WorldPoint(1008, 1008, 0); client.tick++;
		nanos.addAndGet(Duration.ofSeconds(4).toNanos()); events.tick(); assertTrue(timer.hasRunning());
		events.died(client.player); assertFalse(timer.hasRunning()); assertEquals(Duration.ZERO, timer.snapshot().getFrozen());
		target(boss); assertFalse("Cannot resume an interrupted living encounter", events.hitsplat(boss, hit(HitsplatID.DAMAGE_ME)));
	}
	@Test public void verifiedFinalDeathFreezesButMinionDeathDoesNot()
	{
		target(boss); events.hitsplat(boss, hit(HitsplatID.DAMAGE_ME)); nanos.addAndGet(Duration.ofSeconds(5).toNanos());
		assertFalse(events.died(new ActorData(NpcID.VORKATH_SPAWN))); assertTrue(timer.hasRunning());
		assertTrue(events.died(boss)); assertFalse(timer.hasRunning()); assertFalse(timer.snapshot().isOfficial());
		timer.creditedKill(Boss.OBOR, 1, client.tick); timer.completedTime(Duration.ofSeconds(6), client.tick);
		assertEquals(Duration.ofSeconds(6), timer.snapshot().getFrozen());
	}
	@Test public void reusedNpcObjectGetsANewLifeToken()
	{
		target(boss); events.hitsplat(boss, hit(HitsplatID.DAMAGE_ME)); events.died(boss); timer.creditedKill(Boss.OBOR, 1, client.tick);
		client.tick += 20; events.spawned(boss); target(boss);
		assertTrue(events.hitsplat(boss, hit(HitsplatID.DAMAGE_ME))); assertTrue(timer.hasRunning());
	}
	@Test public void dormantReturnAndWakeRearmVorkathWithoutStartingOnTransform()
	{
		ActorData vorkath = new ActorData(NpcID.VORKATH); events.spawned(vorkath); target(vorkath); events.hitsplat(vorkath, hit(HitsplatID.DAMAGE_ME));
		nanos.addAndGet(Duration.ofSeconds(30).toNanos()); vorkath.id = NpcID.VORKATH_SLEEPING;
		assertTrue(events.changed(vorkath, NpcID.VORKATH)); assertFalse(timer.hasRunning());
		timer.creditedKill(Boss.VORKATH, 1, client.tick); client.tick += 20;
		vorkath.id = NpcID.VORKATH; assertFalse(events.changed(vorkath, NpcID.VORKATH_SLEEPING)); assertFalse(timer.hasRunning());
		target(vorkath); assertTrue(events.hitsplat(vorkath, hit(HitsplatID.DAMAGE_ME)));
	}
	@Test public void missingDeathWithDeadDespawnCanFreezeButLiveDespawnAborts()
	{
		target(boss); events.hitsplat(boss, hit(HitsplatID.DAMAGE_ME)); nanos.addAndGet(Duration.ofSeconds(3).toNanos()); boss.dead = true;
		assertTrue(events.despawned(boss)); assertEquals(Duration.ofSeconds(3), timer.snapshot().getFrozen());
		client.tick += 20; events.tick(); ActorData fresh = new ActorData(NpcID.HILLGIANT_BOSS); events.spawned(fresh); target(fresh);
		events.hitsplat(fresh, hit(HitsplatID.DAMAGE_ME)); assertTrue(events.despawned(fresh)); assertEquals(Duration.ZERO, timer.snapshot().getFrozen());
	}
	@Test public void sceneExitAndDifferentWorldContextsCannotContinueOrStart()
	{
		target(boss); boss.world = 2; assertFalse(events.hitsplat(boss, hit(HitsplatID.DAMAGE_ME)));
		boss.world = 1; assertTrue(events.hitsplat(boss, hit(HitsplatID.DAMAGE_ME)));
		client.player.location = new WorldPoint(2000, 2000, 0); events.tick(); assertFalse(timer.hasRunning());
	}
	@Test public void deferredSpawnDoesNotResetAFirstHitAlreadyObserved()
	{
		ActorData fresh = new ActorData(NpcID.GB_MOSSGIANT); target(fresh);
		assertTrue(events.hitsplat(fresh, hit(HitsplatID.DAMAGE_ME))); nanos.addAndGet(Duration.ofSeconds(2).toNanos());
		events.spawned(fresh); assertTrue(timer.hasRunning()); assertEquals(Duration.ofSeconds(2), timer.snapshot().elapsedAt(nanos.get()));
		assertTrue(events.died(fresh));
	}
	@Test public void wakeNotificationAfterFirstHitDoesNotDiscardTheRunningLife()
	{
		ActorData fresh = new ActorData(NpcID.VORKATH_SLEEPING); events.spawned(fresh); fresh.id = NpcID.VORKATH;
		target(fresh); assertTrue(events.hitsplat(fresh, hit(HitsplatID.DAMAGE_ME)));
		events.changed(fresh, NpcID.VORKATH_SLEEPING); assertTrue(events.died(fresh)); assertFalse(timer.hasRunning());
	}
	@Test public void aLaterSpawnRearmsAnActorFirstSeenAfterPluginStartup()
	{
		ActorData existing = new ActorData(NpcID.GB_MOSSGIANT); target(existing); events.hitsplat(existing, hit(HitsplatID.DAMAGE_ME));
		events.died(existing); timer.creditedKill(Boss.BRYOPHYTA, 1, client.tick); client.tick += 20;
		events.spawned(existing); target(existing); assertTrue(events.hitsplat(existing, hit(HitsplatID.DAMAGE_ME)));
	}
	@Test public void aKillingFirstHitIsAZeroLengthObservedAttemptUntilOfficialTime()
	{
		target(boss); boss.dead = true;
		assertTrue(events.hitsplat(boss, hit(HitsplatID.DAMAGE_ME))); assertFalse(timer.hasRunning());
		assertEquals(Duration.ZERO, timer.snapshot().getFrozen()); assertTrue(timer.snapshot().isApproximate());
		timer.creditedKill(Boss.OBOR, 1, client.tick); timer.completedTime(Duration.ofMillis(600), client.tick);
		assertEquals(Duration.ofMillis(600), timer.snapshot().getFrozen()); assertTrue(timer.snapshot().isOfficial());
	}
	private static class ActorData
	{
		int id; int world = 1; boolean dead; WorldPoint location = new WorldPoint(1000, 1000, 0);
		ActorData(int id) { this.id = id; }
	}
	private static class FakeClient implements ObservedFightClient
	{
		final ActorData player = new ActorData(-1); Object target; int tick = 1;
		public Object player() { return player; } public Object playerTarget() { return target; }
		public boolean npc(Object actor) { return actor instanceof ActorData && actor != player; }
		public int npcId(Object actor) { return ((ActorData) actor).id; } public boolean dead(Object actor) { return ((ActorData) actor).dead; }
		public int world(Object actor) { return ((ActorData) actor).world; } public WorldPoint location(Object actor) { return ((ActorData) actor).location; }
		public int tick() { return tick; }
	}
}
