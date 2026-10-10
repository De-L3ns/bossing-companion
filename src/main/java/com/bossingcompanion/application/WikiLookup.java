package com.bossingcompanion.application;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.CollectionItem;
import com.bossingcompanion.domain.DropTable;
import com.bossingcompanion.domain.WikiRates;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Serial application coordinator. Late callbacks cannot resurrect disabled/obsolete loads. */
public final class WikiLookup
{
	private final WikiTransport transport;
	private final Clock clock;
	private final Executor serial;
	private final Runnable changed;
	private final Map<Boss, DropTable> cache = new EnumMap<>(Boss.class);
	private boolean enabled;
	private boolean eligibleWorld = true;
	private boolean closed;
	private long generation;
	private CompletableFuture<DropTable> pending;
	private Boss selected;
	private WikiRates snapshot = new WikiRates(WikiRates.State.DISABLED, null);

	public WikiLookup(WikiTransport transport, Clock clock, Executor serial, Runnable changed)
	{
		this.transport = transport; this.clock = clock; this.serial = serial; this.changed = changed;
	}
	public WikiRates snapshot() { return snapshot; }
	public void configure(boolean enabled, boolean eligibleWorld)
	{
		if (this.enabled == enabled && this.eligibleWorld == eligibleWorld) { return; }
		this.enabled = enabled; this.eligibleWorld = eligibleWorld;
		cancel(); selected = null;
		snapshot = new WikiRates(!enabled ? WikiRates.State.DISABLED : !eligibleWorld ? WikiRates.State.UNSUPPORTED_WORLD : WikiRates.State.NEED_ITEMS, null);
	}
	public void select(Boss boss, List<CollectionItem> definitions, boolean retry)
	{
		if (closed || !enabled || !eligibleWorld) { return; }
		if (!retry && selected == boss && (pending != null || snapshot.getState() == WikiRates.State.UNAVAILABLE)) { return; }
		if (definitions.isEmpty()) { cancel(); selected = boss; snapshot = new WikiRates(WikiRates.State.NEED_ITEMS, null); return; }
		DropTable cached = cache.get(boss);
		if (!retry && cached != null && Duration.between(cached.getFetchedAt(), clock.instant()).compareTo(Duration.ofHours(6)) < 0)
		{
			if (selected != boss) { cancel(); }
			selected = boss; snapshot = new WikiRates(WikiRates.State.READY, cached); return;
		}
		if (!retry && selected == boss && pending != null) { return; }
		cancel(); selected = boss;
		long token = generation;
		snapshot = new WikiRates(WikiRates.State.LOADING, null);
		CompletableFuture<DropTable> request;
		try { request = transport.load(boss, List.copyOf(definitions)); }
		catch (RuntimeException ex) { snapshot = new WikiRates(WikiRates.State.UNAVAILABLE, null); return; }
		pending = request;
		request.whenComplete((table, failure) -> serial.execute(() ->
		{
			if (closed || !enabled || token != generation || selected != boss) { return; }
			pending = null;
			if (failure == null && table != null && !table.getEntries().isEmpty())
			{
				cache.put(boss, table); snapshot = new WikiRates(WikiRates.State.READY, table);
			}
			else { snapshot = new WikiRates(WikiRates.State.UNAVAILABLE, null); }
			changed.run();
		}));
	}
	public void resetSelection() { cancel(); selected = null; }
	private void cancel()
	{
		generation++;
		CompletableFuture<DropTable> old = pending; pending = null;
		if (old != null) { old.cancel(true); }
	}
	public void close() { closed = true; cancel(); cache.clear(); transport.close(); }
}
