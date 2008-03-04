//
// $Id$

package client.stuff;

import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.SubItem;

import client.item.ItemActivator;
import client.shell.Args;
import client.shell.Page;
import client.util.FlashClients;
import client.util.ItemBox;
import client.util.ItemUtil;

/**
 * Displays a thumbnail version of an item.
 */
public class ItemEntry extends ItemBox
{
    public ItemEntry (Item item)
    {
        super(item.getThumbnailMedia(), ItemUtil.getName(item, true), Page.STUFF,
              Args.compose("d", ""+item.getType(), ""+item.itemId));

        if (item.itemId > 0) { // if this item is an original, style it slightly differently
            getFlexCellFormatter().addStyleName(1, 0, "Original");
        }

        if (!(item instanceof SubItem) && FlashClients.clientExists()) {
            addWidget(new ItemActivator(item), getColumns(), "Activator");
        }
    }
}
