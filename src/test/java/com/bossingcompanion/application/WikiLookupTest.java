package com.bossingcompanion.application;

import com.bossingcompanion.domain.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;
import static org.junit.Assert.*;

public class WikiLookupTest
{
	private final TestClock clock = new TestClock();
	private final FakeTransport transport = new FakeTransport();
	private final WikiLookup lookup = new WikiLookup(transport, clock, Runnable::run, () -> {});
	private final List<CollectionItem> definitions = List.of(new CollectionItem(1, "Public item"));
	private DropTable table(Boss boss) { return new DropTable(boss, "Wiki", clock.instant(), Map.of(1, new DropEntry(1, List.of(), List.of()))); }
	@Test public void noRequestsWhenDisabledUnknownOrModifiedWorld()
	{
		lookup.select(Boss.VORKATH, definitions, false); assertEquals(0, transport.requests.size());
		lookup.configure(true, false); lookup.select(Boss.VORKATH, definitions, false);
		assertEquals(WikiRates.State.UNSUPPORTED_WORLD, lookup.snapshot().getState());
		lookup.configure(true, true); lookup.select(Boss.VORKATH, List.of(), false);
		assertEquals(WikiRates.State.NEED_ITEMS, lookup.snapshot().getState()); assertEquals(0, transport.requests.size());
	}
	@Test public void obsoleteResponsesAndOptOutCannotPublish()
	{
		lookup.configure(true, true); lookup.select(Boss.VORKATH, definitions, false);
		lookup.select(Boss.ZULRAH, definitions, false); assertTrue(transport.requests.get(0).isCancelled());
		transport.requests.get(0).complete(table(Boss.VORKATH));
		assertEquals(WikiRates.State.LOADING, lookup.snapshot().getState());
		lookup.configure(false, true); assertTrue(transport.requests.get(1).isCancelled());
		transport.requests.get(1).complete(table(Boss.ZULRAH));
		assertEquals(WikiRates.State.DISABLED, lookup.snapshot().getState());
	}
	@Test public void cacheExpiresAndFailureRequiresExplicitRetry()
	{
		lookup.configure(true, true); lookup.select(Boss.VORKATH, definitions, false);
		transport.requests.get(0).complete(table(Boss.VORKATH));
		lookup.select(Boss.VORKATH, definitions, false); assertEquals(1, transport.requests.size());
		clock.now = clock.now.plus(Duration.ofHours(6)); lookup.select(Boss.VORKATH, definitions, false);
		transport.requests.get(1).completeExceptionally(new IllegalStateException());
		lookup.select(Boss.VORKATH, definitions, false); assertEquals(2, transport.requests.size());
		lookup.select(Boss.VORKATH, definitions, true); assertEquals(3, transport.requests.size());
		lookup.close(); assertTrue(transport.closed); assertTrue(transport.requests.get(2).isCancelled());
	}
	@Test public void completedResponseQueuedBeforeOptOutIsDiscarded()
	{
		List<Runnable> callbacks = new ArrayList<>();
		WikiLookup queued = new WikiLookup(transport, clock, callbacks::add, () -> fail("Obsolete callback published"));
		queued.configure(true, true); queued.select(Boss.VORKATH, definitions, false);
		transport.requests.get(0).complete(table(Boss.VORKATH));
		assertEquals(1, callbacks.size()); queued.configure(false, true); callbacks.get(0).run();
		assertEquals(WikiRates.State.DISABLED, queued.snapshot().getState());
	}
	@Test public void emptyJoinedDataShowsUnavailableAndCanBeRetried()
	{
		lookup.configure(true, true); lookup.select(Boss.ABYSSAL_SIRE, definitions, false);
		transport.requests.get(0).complete(new DropTable(Boss.ABYSSAL_SIRE, "Wiki", clock.instant(), Map.of()));
		assertEquals(WikiRates.State.UNAVAILABLE, lookup.snapshot().getState());
		lookup.select(Boss.ABYSSAL_SIRE, definitions, true); assertEquals(2, transport.requests.size());
	}
	private static class FakeTransport implements WikiTransport
	{
		final List<CompletableFuture<DropTable>> requests = new ArrayList<>(); boolean closed;
		public CompletableFuture<DropTable> load(Boss boss, List<CollectionItem> definitions)
		{
			CompletableFuture<DropTable> future = new CompletableFuture<>(); requests.add(future); return future;
		}
		public void close() { closed = true; }
	}
	private static class TestClock extends Clock
	{
		Instant now = Instant.parse("2026-10-10T00:00:00Z");
		public Instant instant() { return now; } public ZoneId getZone() { return ZoneOffset.UTC; }
		public Clock withZone(ZoneId zone) { return this; }
	}
}
