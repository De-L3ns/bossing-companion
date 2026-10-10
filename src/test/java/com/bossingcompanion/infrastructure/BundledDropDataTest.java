package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.*;
import com.google.gson.*;
import com.google.inject.Guice;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class BundledDropDataTest
{
	private final Gson gson = Guice.createInjector().getInstance(Gson.class);
	private static InputStream resource() { return BundledDropData.class.getResourceAsStream("/com/bossingcompanion/data/boss-drops.json"); }
	private JsonObject root() throws IOException
	{
		try (Reader reader = new InputStreamReader(resource(), StandardCharsets.UTF_8)) { return gson.fromJson(reader, JsonObject.class); }
	}
	@Test public void completeResourcePreservesKnownCollectionRates() throws Exception
	{
		BundledDropData data = new BundledDropData(gson);
		try
		{
			DropTable vorkath = data.load(Boss.VORKATH, List.of(new CollectionItem(ItemID.VORKATH_HEAD, "Vorkath's head"),
				new CollectionItem(ItemID.SKELETAL_VISAGE, "Skeletal visage"))).get(5, TimeUnit.SECONDS);
			assertEquals("1/50", vorkath.getEntries().get(ItemID.VORKATH_HEAD).getComponents().get(0).getRarity());
			assertTrue(vorkath.getEntries().get(ItemID.VORKATH_HEAD).getMechanics().get(0).isReplacesRandomRoll());
			DropTable zulrah = data.load(Boss.ZULRAH, List.of(new CollectionItem(ItemID.BLOWPIPE_FANG, "Tanzanite fang"),
				new CollectionItem(ItemID.SNAKEBOSS_SCALE, "Zulrah's scales"))).get(5, TimeUnit.SECONDS);
			assertEquals(2, zulrah.getEntries().get(ItemID.BLOWPIPE_FANG).getComponents().get(0).getRolls());
			assertEquals(2, zulrah.getEntries().get(ItemID.SNAKEBOSS_SCALE).getComponents().size());
			DropTable sire = data.load(Boss.ABYSSAL_SIRE, List.of(new CollectionItem(ItemID.ABYSSAL_DAGGER, "Abyssal dagger"))).get(5, TimeUnit.SECONDS);
			assertEquals("Unsired", sire.getEntries().get(ItemID.ABYSSAL_DAGGER).getComponents().get(0).getRollUnit());
			assertEquals("26/123", sire.getEntries().get(ItemID.ABYSSAL_DAGGER).getComponents().get(0).getAlternativeRarity());
			DropTable obor = data.load(Boss.OBOR, List.of(new CollectionItem(ItemID.HILLGIANT_BOSS_CLUB, "Hill giant club"))).get(5, TimeUnit.SECONDS);
			assertTrue(obor.getEntries().get(ItemID.HILLGIANT_BOSS_CLUB).getComponents().stream().allMatch(c -> "chest opening".equals(c.getRollUnit())));
			DropTable yama = data.load(Boss.YAMA, List.of(new CollectionItem(ItemID.DEATH_CHARGE_SCROLL, "Rite of vile transference"))).get(5, TimeUnit.SECONDS);
			assertEquals("dossier", yama.getEntries().get(ItemID.DEATH_CHARGE_SCROLL).getComponents().get(0).getRollUnit());
			assertTrue(yama.getEntries().get(ItemID.DEATH_CHARGE_SCROLL).getMechanics().stream().anyMatch(m -> m.getDescription().contains("100 Yama kills")));
			DropTable nightmare = data.load(Boss.NIGHTMARE, List.of(new CollectionItem(ItemID.SLEPE_TELEPORT_CONSUMABLE, "Slepey tablet"))).get(5, TimeUnit.SECONDS);
			assertEquals("Phosani's Nightmare", nightmare.getEntries().get(ItemID.SLEPE_TELEPORT_CONSUMABLE).getComponents().get(0).getSourceVersion());
		}
		finally { data.close(); }
	}
	@Test public void resourceReadsOnceOnWorkerAndCloseCancelsPendingWork() throws Exception
	{
		AtomicInteger reads = new AtomicInteger(); ExecutorService worker = Executors.newSingleThreadExecutor();
		Thread caller = Thread.currentThread();
		BundledDropData data = new BundledDropData(gson, () ->
		{ assertNotSame(caller, Thread.currentThread()); reads.incrementAndGet(); return resource(); }, worker);
		try
		{
			data.load(Boss.VORKATH, List.of(new CollectionItem(ItemID.VORKATH_HEAD, "Vorkath's head"))).get(5, TimeUnit.SECONDS);
			data.load(Boss.ZULRAH, List.of(new CollectionItem(ItemID.SNAKEBOSS_SCALE, "Zulrah's scales"))).get(5, TimeUnit.SECONDS);
			assertEquals(1, reads.get());
			CompletableFuture<Void> gate = new CompletableFuture<>();
			worker.submit(gate::join);
			CompletableFuture<DropTable> waiting = data.load(Boss.VORKATH, List.of(new CollectionItem(ItemID.VORKATH_HEAD, "Vorkath's head")));
			data.close(); gate.complete(null);
			assertTrue(waiting.isCancelled()); assertTrue(worker.isShutdown());
			assertTrue(data.load(Boss.VORKATH, List.of()).isCompletedExceptionally());
		}
		finally { data.close(); }
	}
	@Test public void schemaPartialHashAndPrivateFieldsAreRejected() throws Exception
	{
		JsonObject file = root(); file.addProperty("schemaVersion", 2); reject(file);
		file = root(); file.addProperty("completeCatalogue", false); reject(file);
		file = root(); file.addProperty("mode", "fixture"); reject(file);
		file = root(); file.addProperty("datasetVersion", "bad"); reject(file);
		file = root(); file.addProperty("playerName", "unexpected"); reject(file);
		file = root(); file.getAsJsonObject("bosses").remove("VORKATH"); rehash(file); reject(file);
	}
	@Test public void invalidIdentityRollAndSourceCannotReachPresentation() throws Exception
	{
		JsonObject file = root(); JsonObject record = file.getAsJsonObject("bosses").getAsJsonObject("VORKATH");
		JsonArray ids = new JsonArray(); ids.add(-1); record.getAsJsonArray("identities").get(0).getAsJsonObject().add("item_id", ids);
		rehash(file); reject(file);
		file = root(); record = file.getAsJsonObject("bosses").getAsJsonObject("VORKATH"); record.addProperty("sourceUrl", "https://example.org/");
		rehash(file); reject(file);
		file = root(); record = file.getAsJsonObject("bosses").getAsJsonObject("VORKATH");
		JsonObject row = record.getAsJsonArray("drops").get(0).getAsJsonObject();
		JsonObject rate = gson.fromJson(row.get("drop_json").getAsString(), JsonObject.class); rate.addProperty("Rolls", 0); row.addProperty("drop_json", rate.toString());
		rehash(file); reject(file);
	}
	@Test public void absentCorruptAndOversizedResourcesFailWithoutFallback() throws Exception
	{
		for (java.util.function.Supplier<InputStream> resource : List.<java.util.function.Supplier<InputStream>>of(
			() -> null, () -> new ByteArrayInputStream("broken".getBytes(StandardCharsets.UTF_8)),
			() -> new ByteArrayInputStream(new byte[BundledDropData.MAX_BYTES + 1])))
		{
			BundledDropData data = new BundledDropData(gson, resource, Executors.newSingleThreadExecutor());
			try
			{
				try { data.load(Boss.VORKATH, List.of(new CollectionItem(ItemID.VORKATH_HEAD, "Vorkath's head"))).get(5, TimeUnit.SECONDS); fail("Must reject resource"); }
				catch (ExecutionException expected) { assertNotNull(expected.getCause()); }
			}
			finally { data.close(); }
		}
	}
	private void reject(JsonObject file) throws Exception
	{
		try { BundledDropData.decode(gson, new ByteArrayInputStream(file.toString().getBytes(StandardCharsets.UTF_8))); fail("Expected invalid schema"); }
		catch (IOException expected) { assertNotNull(expected.getMessage()); }
	}
	private static void rehash(JsonObject file) throws Exception
	{
		byte[] bytes = MessageDigest.getInstance("SHA-256").digest(file.get("bosses").toString().getBytes(StandardCharsets.UTF_8));
		StringBuilder hash = new StringBuilder(); for (byte value : bytes) { hash.append(String.format("%02x", value & 0xff)); } file.addProperty("datasetVersion", hash.toString());
	}
}
