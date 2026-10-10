package com.bossingcompanion.infrastructure;

import com.bossingcompanion.application.ProgressTracker;
import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.CollectionItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/** Passive, client-thread cache/page reader. Never opens pages or invokes game actions. */
@Slf4j
public final class NativeCollectionLog
{
	// 1.13.1 exposes no named constants for these cache/script schema identities.
	// Schema reference: evansloan/collection-log CollectionLogManager; RuneProfile item subscriber.
	private static final int BOSS_PAGES_ENUM = 2103;
	private static final int PAGE_NAME_PARAM = 689;
	private static final int PAGE_ITEMS_ENUM_PARAM = 690;
	public static final int ITEM_TRANSMIT_SCRIPT = 4100;
	public static final int CATEGORY_SEND_SCRIPT = 1212;
	private static final Pattern OBTAINED = Pattern.compile("^Obtained: ?([0-9,]+)\\s*/\\s*([0-9,]+)\\.?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern COUNT = Pattern.compile("^(.*?)\\s*(?:kills|kill count|killcount|completions|completion count): ?([0-9,]+)\\.?$", Pattern.CASE_INSENSITIVE);
	private final Client client;
	private final ItemManager items;
	private final ProgressTracker progress;
	private final Set<Integer> knownIds = new HashSet<>();
	private boolean dirty = true;
	private Page previous;
	private int previousTick = -1;
	private boolean initialized;
	private int nextInitializationTick;

	public NativeCollectionLog(Client client, ItemManager items, ProgressTracker progress)
	{
		this.client = client;
		this.items = items;
		this.progress = progress;
	}
	public void initialize()
	{
		if (initialized || client.getGameState() != GameState.LOGGED_IN || client.getTickCount() < nextInitializationTick) { return; }
		nextInitializationTick = client.getTickCount() + 10;
		try
		{
			EnumComposition pages = client.getEnum(BOSS_PAGES_ENUM);
			int[] ids = pages.getIntVals();
			if (ids == null || ids.length > 512) { return; }
			for (int id : ids)
			{
				StructComposition page = client.getStructComposition(id);
				Boss boss = BossCatalog.fromName(page.getStringValue(PAGE_NAME_PARAM));
				if (boss == null) { continue; }
				int enumId = page.getIntValue(PAGE_ITEMS_ENUM_PARAM);
				if (enumId <= 0) { continue; }
				int[] itemIds = client.getEnum(enumId).getIntVals();
				if (itemIds == null || itemIds.length == 0 || itemIds.length > 128) { continue; }
				List<CollectionItem> definitions = new ArrayList<>();
				Set<Integer> unique = new HashSet<>();
				for (int itemId : itemIds)
				{
					int canonical = canonicalId(itemId);
					if (canonical <= 0 || !unique.add(canonical)) { continue; }
					String name = items.getItemComposition(canonical).getMembersName();
					if (name == null || name.equals("null")) { definitions.clear(); break; }
					definitions.add(new CollectionItem(canonical, Text.removeTags(name)));
				}
				if (!definitions.isEmpty())
				{
					progress.define(boss, definitions);
					definitions.forEach(i -> knownIds.add(i.getItemId()));
				}
			}
			initialized = progress.hasDefinitions();
		}
		catch (RuntimeException ex) { log.debug("Collection cache definitions unavailable", ex); }
	}
	public boolean ownsOpenLog()
	{
		Widget frame = client.getWidget(InterfaceID.Collection.FRAME);
		return client.getGameState() == GameState.LOGGED_IN
			&& client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 0
			&& frame != null && !frame.isHidden();
	}
	public void changed() { dirty = true; previous = null; previousTick = -1; }
	public void reset() { dirty = true; previous = null; previousTick = -1; }
	public void itemTransmitted(Object[] args)
	{
		if (!ownsOpenLog() || args == null || args.length < 3 || !(args[1] instanceof Integer) || !(args[2] instanceof Integer)) { return; }
		initialize();
		int id = canonicalId((Integer) args[1]);
		int quantity = (Integer) args[2];
		if (knownIds.contains(id) && quantity > 0) { progress.observedItem(id, quantity); }
		// No guessed full-account completion: absence from this stream remains unknown.
		dirty = true;
	}
	public boolean tick()
	{
		if (!dirty) { return false; }
		initialize();
		if (!ownsOpenLog()) { dirty = false; previous = null; return false; }
		Page current = readPage();
		int tick = client.getTickCount();
		if (current != null && current.equals(previous) && tick != previousTick)
		{
			progress.completePage(current.boss, current.quantities, current.totalKills);
			dirty = false;
			return true;
		}
		previous = current;
		previousTick = tick;
		return false;
	}
	private Page readPage()
	{
		return readPage(progress, this::readLabels, this::readItems);
	}
	private List<String> readLabels(int component)
	{
		// HEADER is the static background. Dynamic title/count labels live under HEADER_TEXT.
		Widget header = client.getWidget(component);
		if (header == null || header.isHidden()) { return List.of(); }
		List<String> texts = new ArrayList<>();
		texts.add(clean(header.getText()));
		Widget[] labels = header.getDynamicChildren();
		if (labels != null) { for (Widget label : labels) { if (label != null) { texts.add(clean(label.getText())); } } }
		return texts;
	}
	private Map<Integer, Long> readItems(int component)
	{
		Widget container = client.getWidget(component);
		if (container == null || container.isHidden()) { return Map.of(); }
		Widget[] children = container.getDynamicChildren();
		if (children == null) { children = container.getChildren(); }
		if (children == null) { return Map.of(); }
		Map<Integer, Long> quantities = new HashMap<>();
		for (Widget child : children)
		{
			if (child == null || child.getItemId() <= 0) { continue; }
			int id = canonicalId(child.getItemId());
			if (quantities.put(id, child.getOpacity() > 0 ? 0L : Math.max(0L, child.getItemQuantity())) != null) { return Map.of(); }
		}
		return quantities;
	}
	static Page readPage(ProgressTracker progress, IntFunction<List<String>> labels, IntFunction<Map<Integer, Long>> items)
	{
		List<String> texts = labels.apply(InterfaceID.Collection.HEADER_TEXT);
		Set<Boss> bosses = new HashSet<>();
		for (String text : texts) { Boss boss = BossCatalog.fromName(clean(text)); if (boss != null) { bosses.add(boss); } }
		if (bosses.size() != 1) { return null; }
		Boss boss = bosses.iterator().next();
		List<CollectionItem> expected = progress.items(boss);
		Map<Integer, Long> quantities = items.apply(InterfaceID.Collection.ITEMS_CONTENTS);
		return validatePage(boss, expected, texts, quantities);
	}
	static Page validatePage(Boss boss, List<CollectionItem> expected, List<String> texts, Map<Integer, Long> quantities)
	{
		if (expected.isEmpty() || quantities.size() != expected.size() || expected.stream().anyMatch(i -> !quantities.containsKey(i.getItemId()))
			|| quantities.values().stream().anyMatch(q -> q == null || q < 0)) { return null; }
		long visibleObtained = quantities.values().stream().filter(q -> q > 0).count();
		boolean consistentHeader = false;
		Long total = null;
		List<Long> genericCounts = new ArrayList<>();
		int allCounters = 0;
		for (String text : texts)
		{
			text = clean(text);
			Matcher obtained = OBTAINED.matcher(text);
			if (obtained.matches() && number(obtained.group(1)) == visibleObtained && number(obtained.group(2)) == expected.size()) { consistentHeader = true; }
			Matcher count = COUNT.matcher(text);
			if (!count.matches()) { continue; }
			allCounters++;
			String name = count.group(1).trim();
			long value = number(count.group(2));
			if (BossCatalog.fromName(name) == boss) { total = value; }
			else if (name.isEmpty()) { genericCounts.add(value); }
		}
		if (!consistentHeader) { return null; }
		if (total == null && allCounters == 1 && genericCounts.size() == 1) { total = genericCounts.get(0); }
		return new Page(boss, Map.copyOf(quantities), total);
	}
	static int canonicalId(int id) { return id == ItemID.ABYSSALSIRE_UNSIRED_DUMMY ? ItemID.ABYSSALSIRE_UNSIRED : id; }
	private static String clean(String text) { return text == null ? "" : Text.removeTags(text).replace('\u00a0', ' ').trim(); }
	private static long number(String value)
	{
		try { return Long.parseLong(value.replace(",", "")); }
		catch (NumberFormatException ex) { return -1; }
	}
	@Value static class Page { Boss boss; Map<Integer, Long> quantities; Long totalKills; }
}
