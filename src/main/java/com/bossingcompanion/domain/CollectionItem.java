package com.bossingcompanion.domain;

import lombok.Value;

/** Public game-cache definition, independent of character progress. */
@Value
public class CollectionItem
{
	int itemId;
	String name;
}
