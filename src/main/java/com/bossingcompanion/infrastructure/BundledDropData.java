package com.bossingcompanion.infrastructure;

import com.bossingcompanion.application.DropDataProvider;
import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.CollectionItem;
import com.bossingcompanion.domain.DropTable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/** Trusted packaged public resource only: no filesystem paths, player data or HTTP. */
@Slf4j
public final class BundledDropData implements DropDataProvider
{
	static final int MAX_BYTES = 1024 * 1024;
	static final int MAX_ROWS = 10000;
	private final ExecutorService worker;
	private final CompletableFuture<Dataset> dataset;
	private final Set<CompletableFuture<DropTable>> loads = ConcurrentHashMap.newKeySet();
	private volatile boolean closed;

	public BundledDropData(Gson gson)
	{
		this(gson, () -> BundledDropData.class.getResourceAsStream("/com/bossingcompanion/data/boss-drops.json"),
			Executors.newSingleThreadExecutor(command ->
			{
				Thread thread = new Thread(command, "bossing-companion-drop-data"); thread.setDaemon(true); return thread;
			}));
	}
	BundledDropData(Gson gson, Supplier<InputStream> resource, ExecutorService worker)
	{
		this.worker = worker;
		dataset = CompletableFuture.supplyAsync(() ->
		{
			try (InputStream stream = resource.get())
			{
				if (stream == null) { throw new IOException("Missing packaged drop data"); }
				return decode(gson, stream);
			}
			catch (IOException | RuntimeException ex)
			{
				log.debug("Bundled drop data unavailable", ex); throw new CompletionException(ex);
			}
		}, worker);
	}
	@Override public CompletableFuture<DropTable> load(Boss boss, List<CollectionItem> definitions)
	{
		if (closed || definitions.isEmpty() || definitions.size() > 128)
		{
			return CompletableFuture.failedFuture(new IllegalStateException("Drop data closed or definitions unavailable"));
		}
		List<CollectionItem> items = List.copyOf(definitions);
		CompletableFuture<DropTable> result = dataset.thenApplyAsync(data ->
		{
			if (closed) { throw new IllegalStateException("Drop data closed"); }
			JsonObject record = data.bosses.get(boss);
			return DropParser.parse(boss, record.get("sourceUrl").getAsString(), items,
				record.getAsJsonArray("drops"), record.getAsJsonArray("identities"), data.generatedAt);
		}, worker);
		loads.add(result);
		result.whenComplete((table, failure) -> loads.remove(result));
		if (closed) { result.cancel(true); }
		return result;
	}
	@Override public void close()
	{
		closed = true; dataset.cancel(true); loads.forEach(future -> future.cancel(true)); worker.shutdownNow();
	}

	static Dataset decode(Gson gson, InputStream stream) throws IOException
	{
		try
		{
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
			while ((count = stream.read(buffer)) != -1)
			{
				if (Thread.currentThread().isInterrupted()) { throw new IOException("Drop loading interrupted"); }
				if (bytes.size() + count > MAX_BYTES) { throw new IOException("Packaged data too large"); }
				bytes.write(buffer, 0, count);
			}
			JsonObject root = gson.fromJson(bytes.toString(StandardCharsets.UTF_8.name()), JsonObject.class);
			if (root == null || root.get("schemaVersion").getAsBigDecimal().intValueExact() != 1
				|| !root.get("completeCatalogue").getAsBoolean() || !"live".equals(text(root, "mode")) && !"cache".equals(text(root, "mode")))
			{ throw new IOException("Unsupported or partial packaged data"); }
			keys(root, Set.of("schemaVersion", "datasetVersion", "generatedAt", "mode", "completeCatalogue", "source", "bosses"));
			Instant generatedAt = Instant.parse(text(root, "generatedAt"));
			JsonObject records = root.getAsJsonObject("bosses");
			if (records.size() != BossCatalog.supported().size()) { throw new IOException("Incomplete packaged catalogue"); }
			String version = text(root, "datasetVersion");
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			StringBuilder hash = new StringBuilder();
			for (byte value : digest.digest(records.toString().getBytes(StandardCharsets.UTF_8))) { hash.append(String.format("%02x", value & 0xff)); }
			if (!version.equals(hash.toString())) { throw new IOException("Packaged data hash mismatch"); }
			Map<Boss, JsonObject> bosses = new EnumMap<>(Boss.class);
			for (Boss boss : BossCatalog.supported())
			{
				JsonObject record = records.getAsJsonObject(boss.name());
				keys(record, Set.of("displayName", "sourceUrl", "sourcePages", "drops", "identities"));
				String url = "https://oldschool.runescape.wiki/w/" + boss.getDisplayName().replace(' ', '_');
				if (!url.equals(text(record, "sourceUrl")) || !boss.getDisplayName().equals(text(record, "displayName")))
				{ throw new IOException("Invalid packaged source identity"); }
				Set<String> pages = new HashSet<>(); record.getAsJsonArray("sourcePages").forEach(page -> pages.add(page.getAsString()));
				if (!pages.equals(new HashSet<>(DropSources.pages(boss)))) { throw new IOException("Unreviewed source relationship"); }
				validateRows(record.getAsJsonArray("drops"), true, pages);
				validateRows(record.getAsJsonArray("identities"), false, pages);
				bosses.put(boss, record);
			}
			return new Dataset(Map.copyOf(bosses), generatedAt);
		}
		catch (IOException ex) { throw ex; }
		catch (Exception ex) { throw new IOException("Invalid packaged drop schema", ex); }
	}
	private static void validateRows(JsonArray rows, boolean drops, Set<String> sourcePages) throws IOException
	{
		if (rows == null || rows.size() == 0 || rows.size() > MAX_ROWS) { throw new IOException("Invalid packaged row count"); }
		for (JsonElement element : rows)
		{
			JsonObject row = element.getAsJsonObject();
			keys(row, drops ? Set.of("page_name", "page_name_sub", "item_name", "drop_json") : Set.of("page_name", "page_name_sub", "item_name", "item_id"));
			for (String field : List.of("page_name", "page_name_sub", "item_name"))
			{ if (text(row, field).isEmpty() || text(row, field).length() > 1000) { throw new IOException("Invalid packaged row identity"); } }
			if (drops)
			{
				if (sourcePages.stream().noneMatch(page -> page.equalsIgnoreCase(text(row, "page_name"))))
				{ throw new IOException("Unexpected drop source page"); }
				JsonObject rate = new com.google.gson.JsonParser().parse(text(row, "drop_json")).getAsJsonObject();
				keys(rate, Set.of("Alt Rarity", "Approx", "Drop Quantity", "Name Notes", "Rarity", "Rarity Notes", "Rolls"));
				if (text(rate, "Rarity").isEmpty()) { throw new IOException("Missing packaged rarity"); }
				if (rate.has("Rolls"))
				{
					int rolls = rate.get("Rolls").getAsBigDecimal().intValueExact();
					if (rolls < 1 || rolls > 10000) { throw new IOException("Invalid packaged rolls"); }
				}
				if (rate.has("Approx") && (!rate.get("Approx").isJsonPrimitive() || !rate.get("Approx").getAsJsonPrimitive().isBoolean()))
				{ throw new IOException("Invalid approximation flag"); }
			}
			else
			{
				JsonArray ids = row.getAsJsonArray("item_id");
				if (ids == null || ids.size() > 512) { throw new IOException("Invalid packaged identities"); }
				for (JsonElement id : ids)
				{
					boolean unresolved = id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()
						&& !id.getAsString().matches("[+-]?[0-9]+") && !id.getAsString().isEmpty() && id.getAsString().length() <= 64;
					if (!unresolved && id.getAsBigDecimal().intValueExact() <= 0)
					{ throw new IOException("Invalid native item ID"); }
				}
			}
		}
	}
	private static String text(JsonObject object, String key)
	{
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
	}
	private static void keys(JsonObject object, Set<String> allowed) throws IOException
	{
		if (object == null || !allowed.containsAll(object.keySet())) { throw new IOException("Unexpected packaged fields"); }
	}
	static final class Dataset
	{
		private final Map<Boss, JsonObject> bosses;
		private final Instant generatedAt;
		Dataset(Map<Boss, JsonObject> bosses, Instant generatedAt) { this.bosses = bosses; this.generatedAt = generatedAt; }
	}
}
