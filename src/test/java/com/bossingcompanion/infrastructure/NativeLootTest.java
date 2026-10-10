package com.bossingcompanion.infrastructure;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeLootTest
{
	@Test public void aMissingTradeablePriceRemainsUnknownRatherThanZero()
	{
		assertNull(NativeLoot.unitValue(0, true));
		assertNull(NativeLoot.unitValue(-1, true));
		assertEquals(Long.valueOf(0), NativeLoot.unitValue(0, false));
		assertEquals(Long.valueOf(100), NativeLoot.unitValue(100, true));
	}
}
