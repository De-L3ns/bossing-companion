package com.bossingcompanion.infrastructure;

import com.bossingcompanion.application.WikiTransport;
import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.CollectionItem;
import com.bossingcompanion.domain.DropTable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import lombok.extern.slf4j.Slf4j;

/** Only public boss/item definitions leave the client. Each load owns its calls. */
@Slf4j
public final class WikiBucketTransport implements WikiTransport
{
	private static final int PAGE_SIZE = 200;
	private static final int MAX_PAGES = 20;
	private static final long MAX_BYTES = 2 * 1024 * 1024;
	private final OkHttpClient http;
	private final Gson gson;
	private final Clock clock;
	private final Set<Load> loads = ConcurrentHashMap.newKeySet();
	private volatile boolean closed;

	public WikiBucketTransport(OkHttpClient http, Gson gson, Clock clock)
	{
		this.http = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build();
		this.gson = gson; this.clock = clock;
	}
	@Override public CompletableFuture<DropTable> load(Boss boss, List<CollectionItem> definitions)
	{
		Load load = new Load();
		if (closed) { load.result.completeExceptionally(new IOException("Transport closed")); return load.result; }
		loads.add(load);
		load.result.whenComplete((value, failure) -> { load.cancel(); loads.remove(load); });
		if (closed) { load.result.cancel(true); return load.result; }
		CompletableFuture<JsonArray> drops = new CompletableFuture<>();
		CompletableFuture<JsonArray> identities = new CompletableFuture<>();
		page(load, dropQuery(boss), 0, new JsonArray(), drops);
		page(load, identityQuery(definitions), 0, new JsonArray(), identities);
		CompletableFuture.allOf(drops, identities).whenComplete((ignored, failure) ->
		{
			if (failure != null) { load.result.completeExceptionally(failure); return; }
			try
			{
				String source = "https://oldschool.runescape.wiki/w/" + boss.getDisplayName().replace(' ', '_');
				load.result.complete(WikiDropParser.parse(boss, source, definitions, drops.join(), identities.join(), clock.instant()));
			}
			catch (RuntimeException ex) { load.result.completeExceptionally(ex); }
		});
		load.result.whenComplete((table, failure) ->
		{
			if (failure != null && !load.result.isCancelled()) { log.debug("Wiki rate lookup failed for {}", boss, failure); }
		});
		return load.result;
	}
	static String dropQuery(Boss boss)
	{
		List<String> pages = WikiSources.pages(boss);
		String filter = pages.size() == 1 ? "'page_name'," + lua(pages.get(0)) : "bucket.Or("
			+ pages.stream().map(page -> "{'page_name'," + lua(page) + "}").collect(Collectors.joining(",")) + ")";
		return "bucket('dropsline').select('page_name','page_name_sub','item_name','drop_json').where(" + filter + ")";
	}
	static String identityQuery(List<CollectionItem> definitions)
	{
		if (definitions.isEmpty() || definitions.size() > 128) { throw new IllegalArgumentException("Invalid public definitions"); }
		return "bucket('infobox_item').select('item_name','item_id','page_name','page_name_sub').where(bucket.Or("
			+ definitions.stream().map(i -> "{'item_name'," + lua(publicItemName(i.getName())) + "}").distinct().collect(Collectors.joining(",")) + "))";
	}
	static String publicItemName(String name) { return name.replaceFirst(" (?i:\\(members\\))$", ""); }
	private static String lua(String value) { return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ") + "'"; }
	private void page(Load load, String query, int page, JsonArray rows, CompletableFuture<JsonArray> result)
	{
		if (load.result.isDone()) { result.cancel(true); return; }
		if (page >= MAX_PAGES) { result.completeExceptionally(new IOException("Wiki row limit exceeded")); return; }
		HttpUrl url = HttpUrl.parse("https://oldschool.runescape.wiki/api.php").newBuilder()
			.addQueryParameter("action", "bucket").addQueryParameter("format", "json")
			.addQueryParameter("query", query + ".limit(" + PAGE_SIZE + ").offset(" + page * PAGE_SIZE + ").run()").build();
		Call call = http.newCall(new Request.Builder().url(url).header("User-Agent", "BossingCompanion/collection-progress (RuneLite plugin)").build());
		load.calls.add(call);
		if (load.result.isDone()) { call.cancel(); result.cancel(true); return; }
		call.enqueue(new Callback()
		{
			@Override public void onFailure(Call call, IOException error) { load.calls.remove(call); result.completeExceptionally(error); }
			@Override public void onResponse(Call call, Response response)
			{
				try (Response closedResponse = response)
				{
					ResponseBody body = response.body();
					if (!response.isSuccessful() || body == null || body.contentLength() > MAX_BYTES) { throw new IOException("Wiki response unavailable"); }
					Buffer buffer = new Buffer();
					long bytes = body.source().readAll(new okio.ForwardingSink(buffer)
					{
						private long count;
						@Override public void write(Buffer source, long size) throws IOException
						{
							count += size;
							if (count > MAX_BYTES) { throw new IOException("Wiki response too large"); }
							super.write(source, size);
						}
					});
					JsonObject json = gson.fromJson(buffer.readUtf8(bytes), JsonObject.class);
					if (json == null || !json.has("bucket") || !json.get("bucket").isJsonArray()) { throw new IOException("Missing Wiki rows"); }
					JsonArray batch = json.getAsJsonArray("bucket");
					if (batch.size() > PAGE_SIZE) { throw new IOException("Wiki page too large"); }
					rows.addAll(batch);
					if (batch.size() < PAGE_SIZE) { result.complete(rows); }
					else { page(load, query, page + 1, rows, result); }
				}
				catch (IOException | RuntimeException ex) { result.completeExceptionally(ex); }
				finally { load.calls.remove(call); }
			}
		});
	}
	@Override public void close() { closed = true; loads.forEach(load -> load.result.cancel(true)); }
	private static final class Load
	{
		final CompletableFuture<DropTable> result = new CompletableFuture<>();
		final Set<Call> calls = ConcurrentHashMap.newKeySet();
		void cancel() { calls.forEach(Call::cancel); }
	}
}
