package net.runelite.client.plugins.httpplug;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;
import java.util.Arrays;
import java.util.stream.LongStream;

public class ProfitTracker
{
	private ItemManager itemManager;
	private Client client;

	public void ProfitTracker(Client client, ItemManager itemManager) {
		this.client = client;
		this.itemManager = itemManager;
	}

	public long calculateContainerValue(InventoryID ContainerID)
	{
        /*
        calculate total inventory value
         */

		long newInventoryValue;

		ItemContainer container = client.getItemContainer(ContainerID);

		if (container == null)
		{
			return 0;
		}

		Item[] items = container.getItems();

		newInventoryValue = Arrays.stream(items).parallel().flatMapToLong(item ->
				LongStream.of(calculateItemValue(item))
		).sum();

		return newInventoryValue;
	}


	public long calculateInventoryValue()
	{
        /*
        calculate total inventory value
         */

		return calculateContainerValue(InventoryID.INVENTORY);

	}

	private long calculateItemValue(Item item)
	{
        /*
        Calculate GE value of single item
         */

		int itemId = item.getId();

		if (itemId < -1)
		{
			return 0;
		}

		if (itemId == -1)
		{
			return 0;
		}

		// log.info(String.format("calculateItemValue itemId = %d", itemId));

		// multiply quantity  by GE value
		return item.getQuantity() * (itemManager.getItemPrice(itemId));
	}
}
