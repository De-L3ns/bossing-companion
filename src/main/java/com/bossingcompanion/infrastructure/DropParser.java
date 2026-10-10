package com.bossingcompanion.infrastructure;


import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.CollectionItem;
import com.bossingcompanion.domain.DropComponent;
import com.bossingcompanion.domain.DropEntry;
import com.bossingcompanion.domain.DropTable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure parsing and stable item-ID join; no client objects or network calls. */
public final class DropParser
{
	private DropParser() { }
	public static DropTable parse(Boss boss, String source, List<CollectionItem> items,
		JsonArray dropRows, JsonArray identityRows, Instant now)
	{
		Set<Integer> expected = new HashSet<>();
		items.forEach(i -> expected.add(i.getItemId()));
		Map<String, Set<Integer>> identities = new HashMap<>();
		for (JsonElement element : identityRows)
		{
			JsonObject row = element.getAsJsonObject();
			JsonElement ids = row.get("item_id");
			if (ids == null || !ids.isJsonArray()) { continue; }
			Set<Integer> matches = new HashSet<>();
			for (JsonElement id : ids.getAsJsonArray())
			{
				try { int value = id.getAsInt(); if (expected.contains(value)) { matches.add(value); } }
				catch (NumberFormatException ignored) { }
			}
			for (String field : new String[]{"item_name", "page_name", "page_name_sub"})
			{
				String name = text(row, field);
				if (!name.isEmpty()) { identities.computeIfAbsent(key(name), k -> new HashSet<>()).addAll(matches); }
			}
		}
		Map<Integer, List<DropComponent>> components = new HashMap<>();
		Set<String> seen = new HashSet<>();
		for (JsonElement element : dropRows)
		{
			JsonObject row = element.getAsJsonObject();
			String version = text(row, "page_name_sub");
			if (!compatibleVersion(boss, version)) { continue; }
			Set<Integer> matches = identities.getOrDefault(key(text(row, "item_name")), Set.of());
			if (matches.size() != 1) { continue; } // Do not guess between charge/variant identities.
			int id = matches.iterator().next();
			JsonObject data = new JsonParser().parse(text(row, "drop_json")).getAsJsonObject();
			String rarity = text(data, "Rarity");
			if (rarity.isEmpty()) { continue; }
			int rolls = data.has("Rolls") ? data.get("Rolls").getAsInt() : 1;
			if (rolls < 1 || rolls > 10000) { continue; }
			String rawKey = id + "|" + version + "|" + data;
			if (!seen.add(rawKey)) { continue; }
			DropComponent component = new DropComponent(version, plain(rarity), rolls,
				plain(text(data, "Drop Quantity")), data.has("Approx") && data.get("Approx").getAsBoolean(),
				plain(notes(data)), DropSources.rollUnit(boss, version), plain(text(data, "Alt Rarity")));
			components.computeIfAbsent(id, k -> new ArrayList<>()).add(component);
		}
		Map<Integer, DropEntry> entries = new HashMap<>();
		components.forEach((id, values) -> entries.put(id,
			new DropEntry(id, List.copyOf(values), DropMechanics.forItem(boss, id, source))));
		return new DropTable(boss, source, now, Map.copyOf(entries));
	}
	private static String notes(JsonObject data)
	{
		String rarityNotes = text(data, "Rarity Notes");
		String nameNotes = text(data, "Name Notes");
		return rarityNotes + (rarityNotes.isEmpty() || nameNotes.isEmpty() ? "" : " ") + nameNotes;
	}
	static boolean compatibleVersion(Boss boss, String version)
	{
		if (DropSources.isRelatedReward(boss, version)) { return true; }
		String lower = version.toLowerCase(Locale.ROOT);
		if (lower.contains("quest") && !lower.contains("post-quest")) { return false; }
		if (lower.contains("awakened") || lower.contains("hard mode") || lower.contains("demonic")) { return false; }
		if (lower.contains("phosani")) { return boss == Boss.NIGHTMARE && DropSources.isRelatedSource(boss, version); }
		if (DropSources.isRelatedSource(boss, version)) { return true; }
		String page = version.split("#", 2)[0];
		return BossCatalog.fromName(page) == boss;
	}
	static String text(JsonObject object, String field)
	{
		JsonElement value = object.get(field);
		return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? "" : value.getAsString();
	}
	private static String key(String text) { return text.trim().replace('’', '\'').toLowerCase(Locale.ROOT); }
	private static String plain(String text)
	{
		if (text.contains("{{")) { return "Additional conditions: see the Wiki."; }
		String result = text.replaceAll("<[^>]*>", "").trim();
		return result.length() > 500 ? result.substring(0, 500) : result;
	}
}

