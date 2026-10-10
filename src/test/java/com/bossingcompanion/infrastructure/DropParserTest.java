package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.*;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropParserTest
{
	private static JsonArray fixture(String name)
	{
		try (InputStreamReader reader = new InputStreamReader(DropParserTest.class.getResourceAsStream("/wiki/" + name), StandardCharsets.UTF_8))
		{
			return new JsonParser().parse(reader).getAsJsonObject().getAsJsonArray("bucket");
		}
		catch (Exception ex) { throw new AssertionError(ex); }
	}
	private static JsonArray identities(CollectionItem... items)
	{
		JsonArray result = new JsonArray();
		for (CollectionItem item : items)
		{
			JsonObject row = new JsonObject(); row.addProperty("item_name", item.getName());
			JsonArray ids = new JsonArray(); ids.add(Integer.toString(item.getItemId())); row.add("item_id", ids); result.add(row);
		}
		return result;
	}
	@Test public void recordedWikiIdentitiesJoinPostQuestVorkath()
	{
		DropTable table = DropParser.parse(Boss.VORKATH, "Wiki", List.of(new CollectionItem(ItemID.SKELETAL_VISAGE, "Skeletal visage"),
			new CollectionItem(ItemID.VORKATHPET, "Vorki")), fixture("vorkath-drops.json"), fixture("item-identities.json"), Instant.EPOCH);
		assertEquals(2, table.getEntries().size());
		DropComponent visage = table.getEntries().get(ItemID.SKELETAL_VISAGE).getComponents().get(0);
		assertEquals("1/5,000", visage.getRarity()); assertEquals("Vorkath#Post-quest", visage.getSourceVersion());
		assertEquals("Very rare", visage.rarityLabel());
	}
	@Test public void zulrahKeepsTwoRollsAndSeparateGuaranteedAndExtraStacks()
	{
		CollectionItem fang = new CollectionItem(ItemID.BLOWPIPE_FANG, "Tanzanite fang");
		CollectionItem scales = new CollectionItem(ItemID.SNAKEBOSS_SCALE, "Zulrah's scales");
		DropTable table = DropParser.parse(Boss.ZULRAH, "Wiki", List.of(fang, scales), fixture("zulrah-drops.json"), identities(fang, scales), Instant.EPOCH);
		DropComponent component = table.getEntries().get(fang.getItemId()).getComponents().get(0);
		assertEquals("1/1,024", component.getRarity()); assertEquals(2, component.getRolls());
		assertEquals(2, table.getEntries().get(scales.getItemId()).getComponents().size());
		assertEquals(DropMechanic.Kind.MULTIPLE_COMPONENTS, table.getEntries().get(scales.getItemId()).getMechanics().get(0).getKind());
	}
	@Test public void ambiguousIdsDoNotBecomeInventedMatches()
	{
		CollectionItem one = new CollectionItem(1, "Skeletal visage"); CollectionItem two = new CollectionItem(2, "Skeletal visage");
		assertTrue(DropParser.parse(Boss.VORKATH, "Wiki", List.of(one, two), fixture("vorkath-drops.json"), identities(one, two), Instant.EPOCH).getEntries().isEmpty());
	}
	@Test public void variantsAreNotInterchangeable()
	{
		assertFalse(DropParser.compatibleVersion(Boss.VORKATH, "Vorkath#Quest"));
		assertTrue(DropParser.compatibleVersion(Boss.NIGHTMARE, "Phosani's Nightmare"));
		assertFalse(DropParser.compatibleVersion(Boss.VARDORVIS, "Vardorvis#Awakened"));
		assertTrue(DropParser.compatibleVersion(Boss.VORKATH, "Vorkath#Post-quest"));
	}
	@Test public void rarityDoesNotInventExactOddsFromWords()
	{
		DropComponent rare = new DropComponent("Boss", "Rare", 1, "1", false, "");
		assertNull(rare.probabilityPerRoll()); assertEquals("Rare", rare.rarityLabel());
		assertEquals("Common", new DropComponent("Boss", "1/25", 1, "1", false, "").rarityLabel());
		assertEquals("Uncommon", new DropComponent("Boss", "1/99", 1, "1", false, "").rarityLabel());
		assertEquals("Rare", new DropComponent("Boss", "1/999", 1, "1", false, "").rarityLabel());
		assertNull(new DropComponent("Boss", "1/0", 1, "1", false, "").probabilityPerRoll());
	}
	@Test public void milestoneAndDependentMechanicsStayExplicit()
	{
		DropMechanic head = DropMechanics.forItem(Boss.VORKATH, ItemID.VORKATH_HEAD, "Wiki").get(0);
		assertEquals(Long.valueOf(50), head.getGuaranteedKill()); assertTrue(head.isReplacesRandomRoll());
		assertEquals(DropMechanic.Kind.UNMODELED, DropMechanics.forItem(Boss.VARDORVIS, 1, "Wiki").get(0).getKind());
		assertEquals(DropMechanic.Kind.CONDITIONAL, DropMechanics.forItem(Boss.NEX, 1, "Wiki").get(0).getKind());
		assertTrue(DropMechanics.forItem(Boss.VORKATH, ItemID.SKELETAL_VISAGE, "Wiki").isEmpty());
	}
	@Test public void immutableSnapshotProtectsPresentation()
	{
		Map<Integer, DropEntry> entries = new java.util.HashMap<>();
		DropTable table = new DropTable(Boss.VORKATH, "Wiki", Instant.EPOCH, entries); entries.put(1, null);
		assertTrue(table.getEntries().isEmpty());
	}
	@Test public void approximationAndConditionsArePreserved()
	{
		CollectionItem item = new CollectionItem(1, "Item");
		JsonObject data = new JsonObject(); data.addProperty("Rarity", "1/100"); data.addProperty("Approx", true);
		data.addProperty("Rolls", 2); data.addProperty("Rarity Notes", "Requires an unlock."); data.addProperty("Name Notes", "Team contribution applies.");
		JsonObject row = new JsonObject(); row.addProperty("page_name_sub", "Nex"); row.addProperty("item_name", "Item"); row.addProperty("drop_json", data.toString());
		JsonArray rows = new JsonArray(); rows.add(row);
		DropComponent component = DropParser.parse(Boss.NEX, "Wiki", List.of(item), rows, identities(item), Instant.EPOCH)
			.getEntries().get(1).getComponents().get(0);
		assertTrue(component.isApproximate()); assertEquals(2, component.getRolls());
		assertEquals("Requires an unlock. Team contribution applies.", component.getConditions());
	}
	@Test(expected = RuntimeException.class) public void malformedSchemaIsNotUsableRateData()
	{
		CollectionItem item = new CollectionItem(1, "Item");
		JsonObject row = new JsonObject(); row.addProperty("page_name_sub", "Vorkath#Post-quest");
		row.addProperty("item_name", "Item"); row.addProperty("drop_json", "broken JSON");
		JsonArray rows = new JsonArray(); rows.add(row);
		DropParser.parse(Boss.VORKATH, "Wiki", List.of(item), rows, identities(item), Instant.EPOCH);
	}
}
