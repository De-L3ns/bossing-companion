package com.bossingcompanion.infrastructure;

import com.bossingcompanion.domain.LootEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;

public final class NativeLoot
{
	private final ItemManager items;
	public NativeLoot(ItemManager items) { this.items = items; }

	/** Called on ClientThread; no client composition survives the boundary. */
	public List<LootEntry> describe(Collection<ItemStack> stacks)
	{
		List<LootEntry> result = new ArrayList<>();
		for (ItemStack stack : stacks)
		{
			if (stack.getQuantity() <= 0) { continue; }
			ItemComposition raw = items.getItemComposition(stack.getId());
			int displayId = raw.getNote() == -1 ? stack.getId() : raw.getLinkedNoteId();
			ItemComposition display = items.getItemComposition(displayId);
			long price = displayId == ItemID.COINS ? 1 : items.getItemPrice(displayId);
			Long value = unitValue(price, display.isTradeable());
			result.add(new LootEntry(stack.getId(), displayId, display.getName(), stack.getQuantity(), value));
		}
		return result;
	}

	static Long unitValue(long price, boolean tradeable)
	{
		if (price > 0) { return price; }
		if (!tradeable) { return 0L; }
		return null;
	}
}
