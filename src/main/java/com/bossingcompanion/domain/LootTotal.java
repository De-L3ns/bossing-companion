package com.bossingcompanion.domain;

import lombok.Value;

@Value
public class LootTotal
{
	int itemId;
	String name;
	long quantity;
	long knownValue;
	boolean partialValue;
}
