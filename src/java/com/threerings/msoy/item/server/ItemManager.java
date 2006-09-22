//
// $Id$

package com.threerings.msoy.item.server;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Level;

import com.samskivert.io.PersistenceException;
import com.samskivert.jdbc.ConnectionProvider;
import com.samskivert.jdbc.RepositoryListenerUnit;
import com.samskivert.util.ResultListener;
import com.samskivert.util.SoftCache;
import com.samskivert.util.Tuple;

import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.data.InvocationCodes;
import com.threerings.presents.server.InvocationException;

import com.threerings.msoy.data.MemberObject;
import com.threerings.msoy.server.MsoyServer;

import com.threerings.msoy.item.server.persist.CatalogRecord;
import com.threerings.msoy.item.server.persist.ItemRecord;
import com.threerings.msoy.item.server.persist.DocumentRepository;
import com.threerings.msoy.item.server.persist.FurnitureRepository;
import com.threerings.msoy.item.server.persist.GameRepository;
import com.threerings.msoy.item.server.persist.ItemRepository;
import com.threerings.msoy.item.server.persist.PhotoRepository;
import com.threerings.msoy.item.util.ItemEnum;

import static com.threerings.msoy.Log.log;

/**
 * Manages digital items and their underlying repositories.
 */
public class ItemManager
    implements ItemProvider
{
    /**
     * Initializes the item manager, which will establish database connections
     * for all of its item repositories.
     */
    public void init (ConnectionProvider conProv) throws PersistenceException
    {
        _repos.put(ItemEnum.DOCUMENT, new DocumentRepository(conProv));
        _repos.put(ItemEnum.FURNITURE, new FurnitureRepository(conProv));
        _repos.put(ItemEnum.GAME, new GameRepository(conProv));
        _repos.put(ItemEnum.PHOTO, new PhotoRepository(conProv));

        // register our invocation service
        MsoyServer.invmgr.registerDispatcher(new ItemDispatcher(this), true);
    }

    // from ItemProvider
    public void getInventory (ClientObject caller, String type,
            final InvocationService.ResultListener listener)
        throws InvocationException
    {
        MemberObject memberObj = (MemberObject) caller;
        if (memberObj.isGuest()) {
            throw new InvocationException(InvocationCodes.ACCESS_DENIED);
        }
        // go ahead and throw a RuntimeException if 'type' is bogus
        ItemEnum etype = Enum.valueOf(ItemEnum.class, type);

        // then, load that type
        // TODO: not everything!
        loadInventory(
            memberObj.getMemberId(), etype,
            new ResultListener<ArrayList<ItemRecord>>() {
                public void requestCompleted (ArrayList<ItemRecord> result)
                {
                    ItemRecord[] items = new ItemRecord[result.size()];
                    result.toArray(items);
                    listener.requestProcessed(items);
                }

                public void requestFailed (Exception cause)
                {
                    log.warning("Unable to retrieve inventory " + "[cause="
                        + cause + "].");
                    listener.requestFailed(InvocationCodes.INTERNAL_ERROR);
                }
            });
    }

    /**
     * Inserts the supplied item into the system. The item should be fully
     * configured, and an item id will be assigned during the insertion
     * process. Success or failure will be communicated to the supplied result
     * listener.
     */
    public void insertItem (final ItemRecord item,
            ResultListener<ItemRecord> rlist)
    {
        ItemEnum type = item.getType();

        // locate the appropriate repository
        final ItemRepository<ItemRecord> repo = _repos.get(type);
        if (repo == null) {
            rlist.requestFailed(new Exception("No repository registered for "
                + type + "."));
            return;
        }

        // and insert the item; notifying the listener on success or failure
        MsoyServer.invoker.postUnit(new RepositoryListenerUnit<ItemRecord>(
            rlist) {
            public ItemRecord invokePersistResult ()
                throws PersistenceException
            {
                repo.insertItem(item);
                return item;
            }

            public void handleSuccess ()
            {
                super.handleSuccess();
                // add the item to the user's cached inventory
                updateUserCache(item);
            }
        });
    }

    /**
     * Loads up the inventory of items of the specified type for the specified
     * member. The results may come from the cache and will be cached after
     * being loaded from the database.
     */
    public void loadInventory (final int memberId, ItemEnum type,
            ResultListener<ArrayList<ItemRecord>> rlist)
    {
        // first check the cache
        final Tuple<Integer, ItemEnum> key =
            new Tuple<Integer, ItemEnum>(memberId, type);
//      TODO: Disable cache for the moment
        if (false) {
        Collection<ItemRecord> items = _itemCache.get(key);
        if (false && items != null) {
            rlist.requestCompleted(new ArrayList<ItemRecord>(items));
            return;
        }
        }
        // locate the appropriate repository
        final ItemRepository<ItemRecord> repo = _repos.get(type);
        if (repo == null) {
            rlist.requestFailed(new Exception("No repository registered for "
                + type + "."));
            return;
        }

        // and load their items; notifying the listener on success or failure
        MsoyServer.invoker
                .postUnit(new RepositoryListenerUnit<ArrayList<ItemRecord>>(
                    rlist) {
                    public ArrayList<ItemRecord> invokePersistResult ()
                        throws PersistenceException
                    {
                        Collection<ItemRecord> list =
                            repo.loadOriginalItems(memberId);
                        list.addAll(repo.loadClonedItems(memberId));
                        return new ArrayList(list);
                    }

                    public void handleSuccess ()
                    {
                        _itemCache.put(key, _result);
                        super.handleSuccess();
                    }
                });
    }

    /**
     * Fetches the entire catalog of listed items of the given type.
     */
    public void loadCatalog (int memberId, ItemEnum type,
            ResultListener<ArrayList<CatalogRecord>> rlist)
    {
        // locate the appropriate repository
        final ItemRepository<ItemRecord> repo = _repos.get(type);
        if (repo == null) {
            rlist.requestFailed(new Exception("No repository registered for "
                + type + "."));
            return;
        }

        // and load the catalog
        MsoyServer.invoker
                .postUnit(new RepositoryListenerUnit<ArrayList<CatalogRecord>>(
                    rlist) {
                    public ArrayList<CatalogRecord> invokePersistResult ()
                        throws PersistenceException
                    {
                        // TODO: Should just change service/servlet to Collection
                        return new ArrayList<CatalogRecord>(repo.loadCatalog());
                    }
                });
    }

    /**
     * Purchases a given item for a given member from the catalog by
     * creating a new clone row in the appropriate database table.
     */
    public void purchaseItem (final int memberId, final int itemId,
            ItemEnum type, ResultListener<ItemRecord> rlist)
    {
        // locate the appropriate repository
        final ItemRepository<ItemRecord> repo = _repos.get(type);
        if (repo == null) {
            rlist.requestFailed(new Exception("No repository registered for "
                + type + "."));
            return;
        }

        // and perform the purchase
        MsoyServer.invoker.postUnit(new RepositoryListenerUnit<ItemRecord>(
            rlist) {
            public ItemRecord invokePersistResult ()
                throws PersistenceException
            {
                // load the item being purchased
                ItemRecord item = repo.loadItem(itemId);
                // sanity check it
                if (item.ownerId != -1) {
                    throw new PersistenceException(
                        "Can only clone listed items [itemId=" +
                        item.itemId + "]");
                }
                if (item.parentId != -1) {
                    throw new PersistenceException(
                        "Can't clone a clone [itemId=" + item.itemId + "]");
                }
                // create the clone row in the database!
                int cloneId = repo.insertClone(item.itemId, memberId);
                // then dress the loaded item up as a clone
                item.ownerId = memberId;
                item.parentId = item.itemId;
                item.itemId = cloneId;
                return item;
            }
        });
    }

    /**
     * Lists the given item in the catalog by creating a new item row and
     * a new catalog row and returning the immutable form of the item.
     */

    public void listItem (final int itemId, ItemEnum type,
            ResultListener<CatalogRecord> rlist)
    {
        // locate the appropriate repository
        final ItemRepository<ItemRecord> repo = _repos.get(type);
        if (repo == null) {
            rlist.requestFailed(new Exception("No repository registered for "
                + type + "."));
            return;
        }

        // and perform the listing
        MsoyServer.invoker.postUnit(new RepositoryListenerUnit<CatalogRecord>(
            rlist) {
            public CatalogRecord invokePersistResult ()
                throws PersistenceException
            {
                // load a copy of the original item
                ItemRecord listItem = repo.loadItem(itemId);
                if (listItem == null) {
                    throw new PersistenceException(
                        "Can't find object to list [itemId = " + itemId + "]");
                }
                if (listItem.parentId != -1) {
                    throw new PersistenceException(
                        "Can't list a cloned object [itemId=" + itemId + "]");
                }
                if (listItem.ownerId == -1) {
                    throw new PersistenceException(
                        "Object is already listed [itemId=" + itemId + "]");
                }
                // reset the owner
                listItem.ownerId = -1;
                // and the iD
                listItem.itemId = 0;
                // then insert it as the immutable copy we list
                repo.insertItem(listItem);
                // and finally create & insert the catalog record
                CatalogRecord record = repo.insertListing(
                    listItem, new Timestamp(System.currentTimeMillis()));
                return record;
            }
        });
    }

    /**
     * Remix a clone, turning it back into a full-featured original.
     */
    public void remixItem (final int itemId, ItemEnum type,
            ResultListener<ItemRecord> rlist)
    {
        // locate the appropriate repository
        final ItemRepository<ItemRecord> repo = _repos.get(type);
        if (repo == null) {
            rlist.requestFailed(new Exception("No repository registered for "
                + type + "."));
            return;
        }
        // and perform the remixing
        MsoyServer.invoker.postUnit(new RepositoryListenerUnit<ItemRecord>(
            rlist) {
            public ItemRecord invokePersistResult ()
                throws PersistenceException
            {
                // load a copy of the clone to modify
                _item = repo.loadClone(itemId);
                // make it ours
                _item.creatorId = _item.ownerId;
                // forget whence it came
                _item.parentId = -1;
                // insert it as a genuinely new item
                _item.itemId = 0;
                repo.insertItem(_item);
                // and finally delete the old clone
                repo.deleteClone(itemId);
                return _item;
            }

            public void handleSuccess ()
            {
                super.handleSuccess();
                // add the item to the user's cached inventory
                updateUserCache(_item);
            }

            protected ItemRecord _item;
        });

    }

    /**
     * Called when an item is newly created and should be inserted into the
     * owning user's inventory cache.
     */
    protected void updateUserCache (ItemRecord item)
    {
        ItemEnum type = item.getType();
        Collection<ItemRecord> items =
            _itemCache.get(new Tuple<Integer, ItemEnum>(item.ownerId, type));
        if (items != null) {
            items.add(item);
        }
    }

    /** Maps string identifier to repository for all digital item types. */
    protected HashMap<ItemEnum, ItemRepository> _repos =
        new HashMap<ItemEnum, ItemRepository>();

    /** A soft reference cache of item list indexed on (user,type). */
    protected SoftCache<Tuple<Integer, ItemEnum>, Collection<ItemRecord>> _itemCache =
        new SoftCache<Tuple<Integer, ItemEnum>, Collection<ItemRecord>>();
}
