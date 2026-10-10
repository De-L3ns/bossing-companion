package com.bossingcompanion.application;

import com.bossingcompanion.domain.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropLookupTest
{
	private final FakeProvider provider = new FakeProvider();
	private final DropLookup lookup = new DropLookup(provider, Runnable::run, () -> {});
	private final List<CollectionItem> items = List.of(new CollectionItem(1, "Public item"));
	private SessionSnapshot session(Boss boss) { return new SessionSnapshot(boss, Instant.EPOCH, null, 0, 0, 0, null, null, null, List.of()); }
	private DropTable table(Boss boss) { return new DropTable(boss, "Source", Instant.EPOCH, Map.of(1, new DropEntry(1, List.of(), List.of()))); }
	@Test public void localDataNeedsOnlyDefinitionsAndStandardWorld()
	{
		lookup.select(null, items); assertTrue(provider.loads.isEmpty());
		lookup.select(session(Boss.VORKATH), List.of()); assertTrue(provider.loads.isEmpty());
		lookup.configure(false); lookup.select(session(Boss.VORKATH), items);
		assertEquals(DropRates.State.UNSUPPORTED_WORLD, lookup.snapshot().getState()); assertTrue(provider.loads.isEmpty());
		lookup.configure(true); lookup.select(session(Boss.VORKATH), items);
		provider.loads.get(0).complete(table(Boss.VORKATH));
		assertEquals(DropRates.State.READY, lookup.snapshot().getState());
		for (int i = 0; i < 20; i++) { lookup.configure(true); lookup.select(session(Boss.VORKATH), items); }
		assertEquals(1, provider.loads.size()); // No TTL, network toggle or per-refresh load.
	}
	@Test public void noSessionClearsDataWhileEndedSummaryKeepsItsOwnBoss()
	{
		lookup.select(session(Boss.VORKATH), items); provider.loads.get(0).complete(table(Boss.VORKATH));
		lookup.select(new SessionSnapshot(Boss.VORKATH, Instant.EPOCH, Instant.EPOCH.plusSeconds(60), 1, 0, 0, null, null, null, List.of()), items);
		assertEquals(1, provider.loads.size()); assertEquals(Boss.VORKATH, lookup.snapshot().getTable().getBoss());
		lookup.select(null, items);
		assertEquals(DropRates.State.NEED_ITEMS, lookup.snapshot().getState()); assertNull(lookup.snapshot().getTable());
		lookup.select(session(Boss.ZULRAH), items); lookup.select(null, items);
		assertTrue(provider.loads.get(1).isCancelled());
	}
	@Test public void selectionChangesCancelObsoleteWork()
	{
		lookup.select(session(Boss.VORKATH), items); lookup.select(session(Boss.ZULRAH), items);
		assertTrue(provider.loads.get(0).isCancelled());
		provider.loads.get(0).complete(table(Boss.VORKATH));
		assertEquals(DropRates.State.LOADING, lookup.snapshot().getState());
		provider.loads.get(1).complete(table(Boss.ZULRAH));
		assertEquals(Boss.ZULRAH, lookup.snapshot().getTable().getBoss());
	}
	@Test public void characterResetAndWorldTransitionRejectQueuedResponses()
	{
		List<Runnable> callbacks = new ArrayList<>();
		DropLookup queued = new DropLookup(provider, callbacks::add, () -> fail("Obsolete callback published"));
		queued.select(session(Boss.VORKATH), items); provider.loads.get(0).complete(table(Boss.VORKATH));
		queued.resetSelection(); callbacks.remove(0).run();
		assertEquals(DropRates.State.NEED_ITEMS, queued.snapshot().getState());
		queued.select(session(Boss.VORKATH), items); provider.loads.get(1).complete(table(Boss.VORKATH));
		queued.configure(false); callbacks.remove(0).run();
		assertEquals(DropRates.State.UNSUPPORTED_WORLD, queued.snapshot().getState());
	}
	@Test public void unavailableDataDoesNotLoopAndChangedDefinitionsReload()
	{
		lookup.select(session(Boss.VORKATH), items); provider.loads.get(0).completeExceptionally(new IllegalStateException());
		assertEquals(DropRates.State.UNAVAILABLE, lookup.snapshot().getState());
		lookup.select(session(Boss.VORKATH), items); assertEquals(1, provider.loads.size());
		lookup.select(session(Boss.VORKATH), List.of(new CollectionItem(2, "Other public item"))); assertEquals(2, provider.loads.size());
		provider.loads.get(1).complete(new DropTable(Boss.VORKATH, "Source", Instant.EPOCH, Map.of()));
		assertEquals(DropRates.State.UNAVAILABLE, lookup.snapshot().getState());
	}
	@Test public void wrongBossAndClosedCallbacksCannotPublish()
	{
		lookup.select(session(Boss.VORKATH), items); provider.loads.get(0).complete(table(Boss.ZULRAH));
		assertEquals(DropRates.State.UNAVAILABLE, lookup.snapshot().getState());
		lookup.select(session(Boss.ZULRAH), items); lookup.close();
		assertTrue(provider.closed); assertTrue(provider.loads.get(1).isCancelled());
		lookup.select(session(Boss.ABYSSAL_SIRE), items); assertEquals(2, provider.loads.size());
	}
	private static final class FakeProvider implements DropDataProvider
	{
		private final List<CompletableFuture<DropTable>> loads = new ArrayList<>(); private boolean closed;
		public CompletableFuture<DropTable> load(Boss boss, List<CollectionItem> items)
		{ CompletableFuture<DropTable> request = new CompletableFuture<>(); loads.add(request); return request; }
		public void close() { closed = true; }
	}
}
