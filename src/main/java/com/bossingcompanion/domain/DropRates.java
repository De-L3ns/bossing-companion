package com.bossingcompanion.domain;

import lombok.Value;

@Value
public class DropRates
{
	public enum State { NEED_ITEMS, LOADING, READY, UNAVAILABLE, UNSUPPORTED_WORLD }
	State state;
	DropTable table;
}
