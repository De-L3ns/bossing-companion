package com.bossingcompanion.application;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.CollectionItem;
import com.bossingcompanion.domain.DropRates;
import com.bossingcompanion.domain.DropTable;
import com.bossingcompanion.domain.SessionSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Serial selection coordinator. No network setting, expiry or retry loop. */
public final class DropLookup
{
	private final DropDataProvider provider;
	private final Executor serial;
	private final Runnable changed;
	private boolean eligibleWorld = true;
	private boolean closed;
	private long generation;
	private CompletableFuture<DropTable> pending;
	private Boss selected;
	private List<CollectionItem> definitions = List.of();
	private DropRates snapshot = new DropRates(DropRates.State.NEED_ITEMS, null);

	public DropLookup(DropDataProvider provider, Executor serial, Runnable changed)
	{
		this.provider = provider; this.serial = serial; this.changed = changed;
	}
	public DropRates snapshot() { return snapshot; }
	public void configure(boolean eligibleWorld)
	{
		if (closed || this.eligibleWorld == eligibleWorld) { return; }
		this.eligibleWorld = eligibleWorld;
		resetSelection();
		snapshot = new DropRates(eligibleWorld ? DropRates.State.NEED_ITEMS : DropRates.State.UNSUPPORTED_WORLD, null);
	}
	public void select(SessionSnapshot session, List<CollectionItem> items)
	{
		if (closed) { return; }
		if (session == null) { if (selected != null || pending != null) { resetSelection(); } return; }
		if (!eligibleWorld) { return; }
		Boss boss = session.getBoss();
		if (selected == boss && Objects.equals(definitions, items)) { return; }
		cancel(); selected = boss; definitions = List.copyOf(items);
		if (definitions.isEmpty()) { snapshot = new DropRates(DropRates.State.NEED_ITEMS, null); return; }
		long token = generation;
		snapshot = new DropRates(DropRates.State.LOADING, null);
		CompletableFuture<DropTable> request;
		try { request = provider.load(boss, definitions); }
		catch (RuntimeException ex) { snapshot = new DropRates(DropRates.State.UNAVAILABLE, null); return; }
		pending = request;
		request.whenComplete((table, failure) -> serial.execute(() ->
		{
			if (closed || token != generation || selected != boss) { return; }
			pending = null;
			boolean available = failure == null && table != null && table.getBoss() == boss && !table.getEntries().isEmpty();
			snapshot = new DropRates(available ? DropRates.State.READY : DropRates.State.UNAVAILABLE, available ? table : null);
			changed.run();
		}));
	}
	public void resetSelection() { cancel(); selected = null; definitions = List.of(); snapshot = new DropRates(eligibleWorld ? DropRates.State.NEED_ITEMS : DropRates.State.UNSUPPORTED_WORLD, null); }
	private void cancel()
	{
		generation++;
		if (pending != null) { pending.cancel(true); pending = null; }
	}
	public void close() { closed = true; cancel(); provider.close(); }
}
