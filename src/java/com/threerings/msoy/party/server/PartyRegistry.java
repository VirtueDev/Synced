//
// $Id$

package com.threerings.msoy.party.server;

import java.util.TreeMap;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import com.samskivert.jdbc.RepositoryUnit;

import com.samskivert.util.Comparators;
import com.samskivert.util.IntMap;
import com.samskivert.util.IntMaps;
import com.samskivert.util.Invoker;
import com.samskivert.util.RandomUtil;
import com.samskivert.util.StringUtil;
import com.samskivert.util.Tuple;

import com.threerings.presents.annotation.MainInvoker;
import com.threerings.presents.client.Client;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.data.InvocationCodes;
import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.InvocationManager;
import com.threerings.presents.peer.data.NodeObject;

import com.threerings.presents.dobj.RootDObjectManager;

import com.threerings.crowd.server.BodyManager;

import com.threerings.whirled.data.ScenePlace;

import com.threerings.msoy.data.MemberObject;
import com.threerings.msoy.data.MsoyCodes;
import com.threerings.msoy.data.all.MediaDesc;
import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.data.all.VizMemberName;
import com.threerings.msoy.server.MemberLocal;
import com.threerings.msoy.server.MemberLocator;

import com.threerings.msoy.group.data.all.Group;
import com.threerings.msoy.group.data.all.GroupMembership;
import com.threerings.msoy.group.server.persist.GroupRecord;
import com.threerings.msoy.group.server.persist.GroupRepository;

import com.threerings.msoy.notify.data.PartyInviteNotification;
import com.threerings.msoy.notify.server.NotificationManager;

import com.threerings.msoy.peer.data.MsoyNodeObject;
import com.threerings.msoy.peer.server.MsoyPeerManager;

import com.threerings.msoy.room.data.MemberInfo;

import com.threerings.msoy.party.client.PartyBoardService;
import com.threerings.msoy.party.client.PeerPartyService;
import com.threerings.msoy.party.data.PartyBoardInfo;
import com.threerings.msoy.party.data.PartyCodes;
import com.threerings.msoy.party.data.PartyDetail;
import com.threerings.msoy.party.data.PartyInfo;
import com.threerings.msoy.party.data.PartyObject;

import static com.threerings.msoy.Log.log;

@Singleton
public class PartyRegistry
    implements PartyBoardProvider, PeerPartyProvider
{
    @Inject public PartyRegistry (InvocationManager invmgr)
    {
        invmgr.registerDispatcher(new PartyBoardDispatcher(this), MsoyCodes.WORLD_GROUP);
    }

    /**
     * Called to initialize the PartyRegistry after server startup.
     */
    public void init ()
    {
        ((MsoyNodeObject) _peerMgr.getNodeObject()).setPeerPartyService(
            _invmgr.registerDispatcher(new PeerPartyDispatcher(this)));

        _peerMgr.memberFwdObs.add(new MsoyPeerManager.MemberForwardObserver() {
            public void memberWillBeSent (String nodeName, MemberObject member) {
                playerLeavingNode(member);
            }
        });
        _locator.addObserver(new MemberLocator.Observer() {
            public void memberLoggedOn (MemberObject member) {
                playerArrivingNode(member);
            }
            public void memberLoggedOff (MemberObject member) {
                // nada
            }
        });
    }

    /**
     * Returns the manager for the specified party or null.
     */
    public PartyManager getPartyManager (int partyId)
    {
        return _parties.get(partyId);
    }

    /**
     * Called on the server that hosts the passed-in player, not necessarily on the server
     * hosting the party.
     */
    public void issueInvite (MemberObject member, MemberName inviter, int partyId, String partyName)
    {
        // record that the member got an invite
        member.getLocal(MemberLocal.class).notePartyInvite(partyId, inviter.getMemberId());
        // send it
        _notifyMan.notify(member, new PartyInviteNotification(inviter, partyId, partyName));
    }

    /**
     * Called by the MsoySceneRegistry when a player changes scenes.
     */
    public void playerWillMove (MemberObject member, int sceneId)
    {
        if (member.partyId != 0) {
            PartyManager mgr = _parties.get(member.partyId);
            if (mgr != null) {
                mgr.playerWillMove(member, sceneId);
            }
        }
    }

    /**
     * Called here and by PartyManager to update a member's party id.
     */
    public void updatePartyId (MemberObject member, final int newPartyId)
    {
        member.setPartyId(newPartyId);
        _bodyMan.updateOccupantInfo(member, new MemberInfo.Updater<MemberInfo>() {
            public boolean update (MemberInfo info) {
                return info.updatePartyId(newPartyId);
            }
        });
    }

    // from PartyBoardProvider
    public void locateParty (ClientObject caller, int partyId, PartyBoardService.JoinListener jl)
        throws InvocationException
    {
        // TODO
    }

    // from PartyBoardProvider
    public void locateMyParty (ClientObject caller, InvocationService.ResultListener rl)
        throws InvocationException
    {
        MemberObject member = (MemberObject)caller;

        if (member.partyId == 0) {
            // TODO: throw no error, or just ignore it on the client
            throw new InvocationException(InvocationCodes.E_INTERNAL_ERROR);
        }

        // see if we have the party here
        PartyManager mgr = _parties.get(member.partyId);
        if (mgr != null) {
            rl.requestProcessed(new int[] { mgr.getPartyObject().getOid() });
            return;
        }

        Tuple<Client,PeerPartyService> tuple = locatePeerService(member.partyId);
        if (tuple == null) {
            log.warning("Member in party that cannot be found",
                "member", member.who(), "partyId", member.partyId);
            throw new InvocationException(InvocationCodes.E_INTERNAL_ERROR);
        }

        tuple.right.getPartyScene(tuple.left, member.partyId, rl);
    }

    // from PartyBoardProvider
    public void getPartyBoard (
        ClientObject caller, String query, final InvocationService.ResultListener rl)
        throws InvocationException
    {
        final MemberObject member = (MemberObject)caller;

        // add every party we have access to to a collection sorted by score
        // Note: maybe we use a KeyValue, and create a top-N data structure that only even
        // saves the top-N candidates
        final TreeMap<PartySort,PartyInfo> visParties = Maps.newTreeMap();
        _peerMgr.applyToNodes(new Function<NodeObject,Void>() {
            public Void apply (NodeObject node) {
                for (PartyInfo info : ((MsoyNodeObject) node).parties) {
                    // TODO: We could actually show parties in which you have an invitation,
                    // and or a leader invite...
                    if (info.isVisible(member)) {
                        visParties.put(computePartySort(info, member), info);
                    }
                }
                return null; // Void
            }
        });

        final IntMap<MediaDesc> icons = IntMaps.newHashIntMap();
        final List<PartyBoardInfo> results = Lists.newArrayList(Iterables.transform(
            Iterables.limit(visParties.values(), PARTIES_PER_BOARD),
            new Function<PartyInfo,PartyBoardInfo>() {
                public PartyBoardInfo apply (PartyInfo info) {
                    icons.put(info.groupId, null);
                    return new PartyBoardInfo(info);
                }
            }));

        _invoker.postUnit(new RepositoryUnit("loadPartyGroup") {
            public void invokePersist () throws Exception {
                for (GroupRecord rec : _groupRepo.loadGroups(icons.keySet())) {
                    icons.put(rec.groupId, rec.toLogo());
                }
            }

            public void handleSuccess () {
                for (PartyBoardInfo party : results) {
                    party.icon = icons.get(party.info.groupId);
                }
                rl.requestProcessed(results);
            }

            @Override public void handleFailure (Exception e) {
                super.handleFailure(e);
                rl.requestFailed(InvocationCodes.E_INTERNAL_ERROR);
            }
        });
    }

    // from PartyBoardProvider
    public void joinParty (
        ClientObject caller, final int partyId, final InvocationService.ResultListener rl)
        throws InvocationException
    {
        final MemberObject member = (MemberObject)caller;

        // reject them if they're already in a party
        if (member.partyId != 0) {
            if (member.partyId == partyId) {
                throw new InvocationException(PartyCodes.E_ALREADY_IN_PARTY);
            }
            throw new InvocationException(InvocationCodes.E_ACCESS_DENIED);
        }

        // figure out their rank in the specified party's group
        PartyInfo info = _peerMgr.getPartyInfo(partyId);
        if (info == null) {
            throw new InvocationException(PartyCodes.E_NO_SUCH_PARTY);
        }

        byte rank = member.getGroupRank(info.groupId);
        boolean hasLeaderInvite = member.getLocal(MemberLocal.class).hasPartyInvite(
            partyId, info.leaderId);

        // pass the buck
        joinParty(null, partyId, member.memberName, rank, hasLeaderInvite,
            new InvocationService.ResultListener() {
                public void requestFailed (String cause) {
                    rl.requestFailed(cause);
                }

                public void requestProcessed (Object result) {
                    rl.requestProcessed(result); // send along the sceneId first
                    updatePartyId(member, partyId); // set the partyId
                }
            });
    }

    // from PeerPartyProvider
    public void joinParty (
        ClientObject caller, int partyId, VizMemberName name, byte groupRank,
        boolean hasLeaderInvite, InvocationService.ResultListener rl)
        throws InvocationException
    {
        PartyManager mgr = _parties.get(partyId);
        if (mgr != null) {
            // we can satisfy this request directly!
            mgr.addPlayer(name, groupRank, hasLeaderInvite, rl);
            return;
        }

        Tuple<Client,PeerPartyService> tuple = locatePeerService(partyId);
        if (tuple == null) {
            throw new InvocationException(PartyCodes.E_NO_SUCH_PARTY);
        }
        tuple.right.joinParty(tuple.left, partyId, name, groupRank, hasLeaderInvite, rl);
    }

    // from PeerPartyProvider
    public void getPartyScene (
        ClientObject caller, int partyId, InvocationService.ResultListener rl)
        throws InvocationException
    {
        // this will only be run on the appropriate node
        PartyManager mgr = _parties.get(partyId);
        if (mgr == null) {
            log.warning("Party not found on node, should be present", "partyId", partyId);
            throw new InvocationException(InvocationCodes.E_INTERNAL_ERROR);
        }
        rl.requestProcessed(mgr.getPartyObject().sceneId);
    }

    // from PartyBoardProvider
    public void createParty (
        ClientObject caller, final String name, final int groupId, final boolean inviteAllFriends,
        final InvocationService.ResultListener rl)
        throws InvocationException
    {
        final MemberObject member = (MemberObject)caller;

        if (member.partyId != 0) {
            // TODO: possibly a better error? Surely this will be blocked on the client
            throw new InvocationException(InvocationCodes.E_INTERNAL_ERROR);
        }
        // verify that the user is at least a member of the specified group
        final GroupMembership groupInfo = member.groups.get(groupId);
        if (groupInfo == null) {
            throw new InvocationException(InvocationCodes.E_INTERNAL_ERROR); // shouldn't happen
        }

        _invoker.postUnit(new RepositoryUnit("loadPartyGroup") {
            public void invokePersist () throws Exception {
                _group = _groupRepo.loadGroup(groupId);
            }

            public void handleSuccess () {
                finishCreateParty(member, name, _group, groupInfo, inviteAllFriends, rl);
            }

            protected GroupRecord _group;
        });
    }

    // from PartyBoardProvider & PeerPartyProvider
    public void getPartyDetail (
        ClientObject caller, int partyId, final InvocationService.ResultListener rl)
        throws InvocationException
    {
        // see if we can handle it locally
        PartyManager mgr = _parties.get(partyId);
        if (mgr != null) {
            final PartyDetail detail = mgr.getPartyDetail();
            _invoker.postUnit(new RepositoryUnit("loadPartyGroup") {
                public void invokePersist () throws Exception {
                    GroupRecord rec = _groupRepo.loadGroup(detail.info.groupId);
                    detail.groupName = rec.name;
                    detail.icon = rec.toLogo();
                }

                public void handleSuccess () {
                    rl.requestProcessed(detail);
                }
            });
            return;
        }

        Tuple<Client,PeerPartyService> tuple = locatePeerService(partyId);
        if (tuple == null) {
            throw new InvocationException(PartyCodes.E_NO_SUCH_PARTY);
        }
        tuple.right.getPartyDetail(tuple.left, partyId, rl);
    }

    /**
     * Called by a PartyManager when it's removed.
     */
    void partyWasRemoved (int partyId)
    {
        // TODO: this will get more complicated
        _parties.remove(partyId);
    }

    /**
     * Finish creating a new party.
     */
    protected void finishCreateParty (
        MemberObject member, String name, GroupRecord group, GroupMembership groupInfo,
        boolean inviteAllFriends, InvocationService.ResultListener rl)
    {
        if (!(member.location instanceof ScenePlace)) {
            log.warning("Where the heck are they starting a party?", "who", member.who(),
                "location", member.location);
            rl.requestFailed(InvocationCodes.E_INTERNAL_ERROR);
            return;
        }

        PartyObject pobj = null;
        PartyManager mgr = null;
        try {
            // validate that they can create the party with this group
            if ((group.partyPerms == Group.PERM_MANAGER) &&
                    (groupInfo.rank < GroupMembership.RANK_MANAGER)) {
                rl.requestFailed(PartyCodes.E_GROUP_MGR_REQUIRED);
                return;
            }

            // set up the new PartyObject
            pobj = _omgr.registerObject(new PartyObject());
            pobj.id = _peerMgr.getNextPartyId();
            pobj.name = StringUtil.truncate(name, PartyCodes.MAX_NAME_LENGTH);
            pobj.group = groupInfo.group;
            pobj.icon = group.toLogo();
            pobj.leaderId = member.getMemberId();
            pobj.sceneId = ((ScenePlace) member.location).sceneId;

            // Create the PartyManager and add the member
            mgr = _injector.getInstance(PartyManager.class);
            mgr.init(pobj);

            // This can throw an InvocationException, or will send the response to the user..
            mgr.addPlayer(member.memberName, groupInfo.rank, true, rl);

        } catch (Exception e) {
            log.warning("Problem creating party", e);
            if (e instanceof InvocationException) {
                rl.requestFailed(e.getMessage());
            } else {
                rl.requestFailed(InvocationCodes.E_INTERNAL_ERROR);
            }

            // kill the party object we created
            if (mgr != null) {
                mgr.shutdown();
            }
            if (pobj != null) {
                _omgr.destroyObject(pobj.getOid());
            }
            return;
        }

        // And now do any final party registration and setup
        // register the party
        _parties.put(pobj.id, mgr);
        // set the partyId
        updatePartyId(member, pobj.id);
        if (inviteAllFriends) {
            mgr.inviteAllFriends(member);
        }
    }

    /**
     * Called prior to a member leaving a node.
     */
    protected void playerLeavingNode (MemberObject member)
    {
        if (member.partyId != 0) {
            PartyManager mgr = _parties.get(member.partyId);
            if (mgr != null) {
                PartyObject pobj = mgr.getPartyObject();
                if (pobj.leaderId == member.getMemberId()) {
                    log.info("Dehydrating party", "partyId", pobj.id);
                    _parties.remove(member.partyId);
                    member.setLocal(PartyObject.class, (PartyObject)mgr.getPartyObject().clone());
                    mgr.shutdown();
                }
            }
        }
    }

    protected void playerArrivingNode (MemberObject member)
    {
        PartyObject pobj = member.getLocal(PartyObject.class);
        if (pobj != null) {
            log.info("Rehydrating party", "partyId", pobj.id);
            member.setLocal(PartyObject.class, null);
            _omgr.registerObject(pobj);
            PartyManager mgr = _injector.getInstance(PartyManager.class);
            mgr.init(pobj);
            _parties.put(pobj.id, mgr);
        }
    }

    /**
     * Compute the score for the specified party, or return null if the user
     * does not have access to it.
     */
    protected PartySort computePartySort (PartyInfo info, MemberObject member)
    {
        // start by giving every party a random score between 0 and 1
        float score = RandomUtil.rand.nextFloat();
        // add your rank in the group. (0, 1, or 2)
        score += member.getGroupRank(info.groupId);
        // add 3 if your friend is leading the party. (To make it more important than groups)
        if (member.isFriend(info.leaderId)) {
            score += 3;
        }
        // now, each party is in a "band" determined by group/friend, and then has a random
        // position within that band.
        return new PartySort(score, info.id);
    }

    /**
     * Find and return the PeerPartyService for the specified partyId.
     */
    protected Tuple<Client,PeerPartyService> locatePeerService (int partyId)
    {
        final Integer partyKey = partyId;
        final Object[] result = new Object[1];

        _peerMgr.invokeOnNodes(new Function<Tuple<Client,NodeObject>,Void>() {
            public Void apply (Tuple<Client,NodeObject> clinode) {
                if (result[0] == null) {
                    MsoyNodeObject mnode = (MsoyNodeObject)clinode.right;
                    if (mnode.parties.containsKey(partyKey)) {
                        result[0] = new Tuple<Client,PeerPartyService>(
                            clinode.left, mnode.peerPartyService);
                    }
                }
                return null; // Void
            }
        });

        @SuppressWarnings("unchecked") // fucking A
        Tuple<Client,PeerPartyService> retval = (Tuple<Client,PeerPartyService>)result[0];
        return retval;
    }

    /** Holds compared order between parties without having to recompute it for
     * every comparison. */
    protected static class PartySort
        implements Comparable<PartySort>
    {
        public PartySort (float score, int id)
        {
            _score = score;
            _id = id;
        }

        // NOTE: we do not implement equals or hashCode. All scores are not equal.

        // from Comparable
        public int compareTo (PartySort other)
        {
            // reverse the order, so that higher scores are first
            int cmp = Float.compare(other._score, _score);
            if (cmp == 0) {
                // but lower partyIds take priority
                cmp = Comparators.compare(_id, other._id);
            }
            return cmp;
        }

        protected float _score;
        protected int _id;
    } // end: class PartySort

    protected IntMap<PartyManager> _parties = IntMaps.newHashIntMap();

    protected static final int PARTIES_PER_BOARD = 16;

    @Inject protected InvocationManager _invmgr;
    @Inject protected Injector _injector;
    @Inject protected @MainInvoker Invoker _invoker;
    @Inject protected RootDObjectManager _omgr;
    @Inject protected MsoyPeerManager _peerMgr;
    @Inject protected MemberLocator _locator;
    @Inject protected GroupRepository _groupRepo;
    @Inject protected BodyManager _bodyMan;
    @Inject protected NotificationManager _notifyMan;
}
