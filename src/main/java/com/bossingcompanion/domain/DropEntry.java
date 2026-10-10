package com.bossingcompanion.domain;

import java.util.List;
import lombok.Value;

@Value
public class DropEntry
{
	int itemId;
	List<DropComponent> components;
	List<DropMechanic> mechanics;
	public DropEntry(int itemId, List<DropComponent> components, List<DropMechanic> mechanics)
	{
		this.itemId = itemId; this.components = List.copyOf(components); this.mechanics = List.copyOf(mechanics);
	}
}
