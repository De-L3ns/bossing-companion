package com.bossingcompanion.domain;

import lombok.Value;

@Value
public class WikiRates
{
	public enum State { DISABLED, NEED_ITEMS, LOADING, READY, UNAVAILABLE, UNSUPPORTED_WORLD }
	State state;
	DropTable table;
}
