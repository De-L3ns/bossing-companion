package com.bossingcompanion.infrastructure;

import net.runelite.api.coords.WorldPoint;

/** Narrow passive API boundary. Opaque actor identities never leave infrastructure. */
interface ObservedFightClient
{
	Object player();
	Object playerTarget();
	boolean npc(Object actor);
	int npcId(Object actor);
	boolean dead(Object actor);
	int world(Object actor);
	WorldPoint location(Object actor);
	int tick();
}
