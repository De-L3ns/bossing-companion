package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.CollectionItem;
import java.util.List;
import java.util.ArrayList;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import com.bossingcompanion.domain.DropTable;
import com.google.gson.*;
import com.google.inject.Guice;
import okhttp3.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WikiBucketTransportTest
{
	@Test public void publicQueriesEscapeApostrophesAndContainNoCharacterFields()
	{
		assertTrue(WikiBucketTransport.dropQuery(Boss.KREEARRA).contains("Kree\\'arra"));
		String query = WikiBucketTransport.identityQuery(List.of(new CollectionItem(1, "Vorkath's head")));
		assertTrue(query.contains("Vorkath\\'s head"));
		assertFalse(query.contains("quantity")); assertFalse(query.contains("kill")); assertFalse(query.contains("account"));
	}
	@Test(expected = IllegalArgumentException.class) public void emptyDefinitionQueriesAreRejected()
	{
		WikiBucketTransport.identityQuery(List.of());
	}
	@Test public void freeWorldDecorationsNeverEnterPublicQueries()
	{
		String query = WikiBucketTransport.identityQuery(List.of(new CollectionItem(1, "Abyssal dagger (Members)")));
		assertTrue(query.contains("'Abyssal dagger'")); assertFalse(query.contains("Members"));
	}
	@Test public void realSireResponsesTravelThroughTheAsyncHttpPipeline() throws Exception
	{
		List<String> queries = java.util.Collections.synchronizedList(new ArrayList<>());
		JsonArray rows = fixture("sire-drops.json").getAsJsonArray("bucket"); rows.addAll(fixture("unsired-drops.json").getAsJsonArray("bucket"));
		JsonObject dropResponse = new JsonObject(); dropResponse.add("bucket", rows);
		JsonObject identities = fixture("sire-identities.json");
		List<CollectionItem> definitions = new ArrayList<>();
		for (JsonElement element : identities.getAsJsonArray("bucket"))
		{
			JsonObject row = element.getAsJsonObject();
			if (row.get("page_name").getAsString().contains("(")) { continue; }
			definitions.add(new CollectionItem(row.getAsJsonArray("item_id").get(0).getAsInt(), row.get("item_name").getAsString() + " (Members)"));
		}
		OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			String query = chain.request().url().queryParameter("query"); queries.add(query);
			return response(chain.request(), query.contains("dropsline") ? dropResponse.toString() : identities.toString());
		}).build();
		WikiBucketTransport transport = new WikiBucketTransport(http, Guice.createInjector().getInstance(Gson.class), Clock.systemUTC());
		try
		{
			DropTable table = transport.load(Boss.ABYSSAL_SIRE, definitions).get(5, TimeUnit.SECONDS);
			assertEquals(9, table.getEntries().size()); assertEquals(2, queries.size());
			assertTrue(queries.stream().anyMatch(q -> q.contains("'page_name','Unsired'")));
			assertTrue(queries.stream().noneMatch(q -> q.contains("Members")));
			CollectionItem dagger = definitions.stream().filter(i -> i.getName().startsWith("Abyssal dagger")).findFirst().get();
			assertEquals("26/128", table.getEntries().get(dagger.getItemId()).getComponents().get(0).getRarity());
			assertEquals("Unsired", table.getEntries().get(dagger.getItemId()).getComponents().get(0).getRollUnit());
			assertEquals("26/123", table.getEntries().get(dagger.getItemId()).getComponents().get(0).getAlternativeRarity());
		}
		finally { transport.close(); http.dispatcher().executorService().shutdownNow(); http.connectionPool().evictAll(); }
	}
	@Test public void malformedHttpSchemaAndOversizedBodiesFailCleanly() throws Exception
	{
		for (String body : List.of("{\"error\":\"unavailable\"}", "x".repeat(2 * 1024 * 1024 + 1)))
		{
			OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> response(chain.request(), body)).build();
			WikiBucketTransport transport = new WikiBucketTransport(http, Guice.createInjector().getInstance(Gson.class), Clock.systemUTC());
			try
			{
				try { transport.load(Boss.VORKATH, List.of(new CollectionItem(1, "Item"))).get(5, TimeUnit.SECONDS); fail("Invalid response accepted"); }
				catch (ExecutionException expected) { assertNotNull(expected.getCause()); }
			}
			finally { transport.close(); http.dispatcher().executorService().shutdownNow(); http.connectionPool().evictAll(); }
		}
	}
	private static Response response(Request request, String body)
	{
		return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
			.body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
	}
	private static JsonObject fixture(String name) throws Exception
	{
		try (InputStreamReader reader = new InputStreamReader(WikiBucketTransportTest.class.getResourceAsStream("/wiki/" + name), StandardCharsets.UTF_8))
		{
			return new JsonParser().parse(reader).getAsJsonObject();
		}
	}
}
