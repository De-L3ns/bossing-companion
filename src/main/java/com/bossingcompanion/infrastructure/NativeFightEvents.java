package com.bossingcompanion.infrastructure;

import com.bossingcompanion.application.FightTimerTracker;
import com.bossingcompanion.domain.Boss;
import java.util.IdentityHashMap;
import java.util.Map;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;

/** Passive pilot source adapter, client-thread only. Never reads an NPC's player target. */
public final class NativeFightEvents
{
	private static final int RECENT_TARGET_TICKS = 3;
	// The three pilot encounter rooms fit within one 64-tile span. This is a context guard, not a combat range helper.
	private static final int MAX_ROOM_DISTANCE = 64;
	private final ObservedFightClient client;
	private final FightTimerTracker timer;
	private final Map<Object, Entity> entities = new IdentityHashMap<>();
	private long sequence;
	private Object recentTarget;
	private int recentTargetTick;
	private Entity active;
	private WorldPoint origin;
	private int worldView;

	public NativeFightEvents(Client client, FightTimerTracker timer)
	{
		this(new ObservedFightClient()
		{
			public Object player() { return client.getLocalPlayer(); }
			public Object playerTarget() { return client.getLocalPlayer() == null ? null : client.getLocalPlayer().getInteracting(); }
			public boolean npc(Object actor) { return actor instanceof NPC; }
			public int npcId(Object actor) { return ((NPC) actor).getId(); }
			public boolean dead(Object actor) { return ((Actor) actor).isDead(); }
			public int world(Object actor) { return ((Actor) actor).getWorldView() == null ? Integer.MIN_VALUE : ((Actor) actor).getWorldView().getId(); }
			public WorldPoint location(Object actor) { return ((Actor) actor).getWorldLocation(); }
			public int tick() { return client.getTickCount(); }
		}, timer);
	}
	NativeFightEvents(ObservedFightClient client, FightTimerTracker timer) { this.client = client; this.timer = timer; }
	public void setEnabled(boolean enabled)
	{
		if (timer.isEnabled() == enabled) { return; }
		timer.setEnabled(enabled); active = null; origin = null;
		if (enabled) { blockCurrentEngagement(); }
	}
	public void reset()
	{
		entities.clear(); recentTarget = null; active = null; origin = null;
		if (timer.isEnabled()) { blockCurrentEngagement(); }
	}
	public void interrupt()
	{
		timer.interrupt(); active = null; origin = null; recentTarget = null;
		// Existing engagement flags remain latched so a later hit cannot start midway.
	}
	public void interacting(Object source, Object target)
	{
		if (source != client.player() || !client.npc(target)) { return; }
		Object npc = target;
		if (FightSources.activeBoss(client.npcId(npc)) != null)
		{
			recentTarget = npc; recentTargetTick = client.tick(); entity(npc);
		}
	}
	public boolean hitsplat(Object actor, Hitsplat hit)
	{
		if (!client.npc(actor) || hit == null || !hit.isMine()) { return false; }
		Object npc = actor;
		Boss boss = FightSources.activeBoss(client.npcId(npc));
		Object player = client.player();
		int tick = client.tick();
		if (boss == null || player == null || client.location(player) == null
			|| client.world(player) == Integer.MIN_VALUE || client.world(player) != client.world(npc)
			|| client.playerTarget() != npc && (recentTarget != npc || tick - recentTargetTick > RECENT_TARGET_TICKS || tick < recentTargetTick)) { return false; }
		Entity entity = entity(npc);
		if (entity.engaged) { return false; }
		entity.engaged = true;
		if (timer.begin(boss, entity.key, tick))
		{
			active = entity; origin = client.location(player); worldView = client.world(player);
			// The killing first hit can arrive after the engine marks the actor dead.
			if (client.dead(npc)) { timer.terminalDeath(entity.key, tick); active = null; origin = null; }
			return true;
		}
		return false;
	}
	public void spawned(Object npc)
	{
		if (!client.npc(npc) || FightSources.identity(client.npcId(npc)) == null) { return; }
		Entity observed = entities.get(npc);
		// RuneLite can defer NpcSpawned until the tick; a first hitsplat may already have registered this actor.
		if (observed != null && !observed.spawnSeen && observed.firstSeenTick == client.tick()) { observed.spawnSeen = true; return; }
		Entity old = entities.remove(npc);
		if (old != null && timer.isRunningEntity(old.key)) { interrupt(); }
		entity(npc).spawnSeen = true;
	}
	public boolean changed(Object npc, int oldId)
	{
		Entity entity = entities.get(npc);
		if (entity == null) { return false; }
		Boss now = FightSources.identity(client.npcId(npc));
		if (now != entity.boss)
		{
			boolean changed = timer.isRunningEntity(entity.key);
			if (changed) { interrupt(); }
			entities.remove(npc); return changed;
		}
		if (FightSources.dormantVorkath(oldId) && now == Boss.VORKATH && FightSources.activeBoss(client.npcId(npc)) == now)
		{
			if (!timer.isRunningEntity(entity.key))
			{
				Entity fresh = new Entity(++sequence, now, client.tick()); fresh.spawnSeen = entity.spawnSeen; entities.put(npc, fresh);
			}
			return false;
		}
		if (FightSources.activeBoss(oldId) == Boss.VORKATH && FightSources.dormantVorkath(client.npcId(npc)))
		{
			// Dormant return ends this observed encounter, but is not proof of kill credit.
			boolean changed = timer.isRunningEntity(entity.key);
			if (changed) { timer.terminalDeath(entity.key, client.tick()); }
			Entity fresh = new Entity(++sequence, entity.boss, client.tick()); fresh.spawnSeen = entity.spawnSeen; entities.put(npc, fresh);
			return changed;
		}
		return false;
	}
	public boolean died(Object actor)
	{
		if (actor == client.player()) { interrupt(); return true; }
		if (!client.npc(actor)) { return false; }
		Object npc = actor; Entity entity = entities.get(npc);
		if (entity != null && timer.isRunningEntity(entity.key) && FightSources.terminal(entity.boss, client.npcId(npc)))
		{
			timer.terminalDeath(entity.key, client.tick()); return true;
		}
		return false;
	}
	public boolean despawned(Object npc)
	{
		Entity entity = entities.remove(npc);
		boolean changed = entity != null && timer.isRunningEntity(entity.key);
		if (changed)
		{
			if (client.dead(npc) && FightSources.terminal(entity.boss, client.npcId(npc))) { timer.terminalDeath(entity.key, client.tick()); }
			else { interrupt(); }
		}
		if (recentTarget == npc) { recentTarget = null; }
		return changed;
	}
	public void tick()
	{
		timer.expire(client.tick());
		if (active == null || !timer.isRunningEntity(active.key)) { active = null; origin = null; return; }
		Object player = client.player();
		if (player == null || client.dead(player) || client.world(player) != worldView || client.location(player) == null
			|| origin != null && client.location(player).distanceTo(origin) > MAX_ROOM_DISTANCE) { interrupt(); }
	}
	private void blockCurrentEngagement()
	{
		Object player = client.player();
		if (player != null && client.npc(client.playerTarget()))
		{
			Object npc = client.playerTarget();
			if (FightSources.identity(client.npcId(npc)) != null) { entity(npc).engaged = true; }
		}
	}
	private Entity entity(Object npc)
	{
		return entities.computeIfAbsent(npc, key -> new Entity(++sequence, FightSources.identity(client.npcId(npc)), client.tick()));
	}
	private static final class Entity
	{
		final long key; final Boss boss; final int firstSeenTick; boolean engaged; boolean spawnSeen;
		Entity(long key, Boss boss, int firstSeenTick) { this.key = key; this.boss = boss; this.firstSeenTick = firstSeenTick; }
	}
}
