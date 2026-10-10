package com.bossingcompanion.domain;

import lombok.Value;

/** One received stack; null unit value denotes an unknown tradeable price. */
@Value
public class LootEntry
{
	int rawItemId;
	int displayItemId;
	String name;
	long quantity;
	Long unitValue;
}
