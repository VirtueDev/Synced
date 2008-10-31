//
// $Id$

package com.threerings.msoy.server;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.samskivert.jdbc.RepositoryUnit;
import com.samskivert.jdbc.WriteOnlyUnit;
import com.samskivert.util.Interval;
import com.samskivert.util.Invoker;
import com.samskivert.util.ObjectUtil;
import com.samskivert.util.ResultListener;
import com.samskivert.util.StringUtil;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.threerings.underwire.server.persist.EventRecord;
import com.threerings.underwire.web.data.Event;
import com.threerings.util.MessageBundle;

import com.threerings.presents.annotation.EventThread;
import com.threerings.presents.annotation.MainInvoker;
import com.threerings.presents.client.InvocationService;
import com.threerings.presents.data.ClientObject;
import com.threerings.presents.data.InvocationCodes;
import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.AttributeChangedEvent;
import com.threerings.presents.dobj.DSet;
import com.threerings.presents.server.ClientManager;
import com.threerings.presents.server.InvocationException;
import com.threerings.presents.server.InvocationManager;
import com.threerings.presents.server.PresentsClient;
import com.threerings.presents.server.PresentsDObjectMgr;
import com.threerings.presents.util.PersistingUnit;

import com.threerings.crowd.chat.data.ChatCodes;
import com.threerings.crowd.chat.data.ChatMessage;
import com.threerings.crowd.chat.data.UserMessage;
import com.threerings.crowd.chat.server.SpeakUtil;
import com.threerings.crowd.data.OccupantInfo;
import com.threerings.crowd.server.BodyManager;
import com.threerings.crowd.server.PlaceManager;
import com.threerings.crowd.server.PlaceRegistry;

import com.threerings.msoy.data.BasicNavItemData;
import com.threerings.msoy.data.HomePageItem;
import com.threerings.msoy.data.MemberExperience;
import com.threerings.msoy.data.MemberLocation;
import com.threerings.msoy.data.MemberObject;
import com.threerings.msoy.data.MsoyBodyObject;
import com.threerings.msoy.data.MsoyCodes;
import com.threerings.msoy.data.all.FriendEntry;
import com.threerings.msoy.data.all.MediaDesc;
import com.threerings.msoy.data.all.MemberName;
import com.threerings.msoy.data.all.NavItemData;
import com.threerings.msoy.data.all.StaticMediaDesc;
import com.threerings.msoy.server.MemberLocal;
import com.threerings.msoy.server.PopularPlacesSnapshot.Place;
import com.threerings.msoy.server.persist.MemberExperienceRecord;
import com.threerings.msoy.server.persist.MemberRepository;
import com.threerings.msoy.server.util.MailSender;

import com.threerings.msoy.peer.server.MsoyPeerManager;

import com.threerings.msoy.badge.data.BadgeType;
import com.threerings.msoy.badge.data.all.EarnedBadge;
import com.threerings.msoy.badge.data.all.InProgressBadge;
import com.threerings.msoy.badge.server.BadgeLogic;
import com.threerings.msoy.badge.server.BadgeManager;
import com.threerings.msoy.game.server.persist.MsoyGameRepository;
import com.threerings.msoy.group.server.persist.GroupRecord;
import com.threerings.msoy.group.server.persist.GroupRepository;
import com.threerings.msoy.item.data.all.Avatar;
import com.threerings.msoy.item.data.all.Item;
import com.threerings.msoy.item.data.all.ItemIdent;
import com.threerings.msoy.item.server.ItemManager;
import com.threerings.msoy.item.server.persist.GameRecord;
import com.threerings.msoy.item.server.persist.GameRepository;
import com.threerings.msoy.mail.server.MailLogic;
import com.threerings.msoy.notify.data.LevelUpNotification;
import com.threerings.msoy.notify.server.NotificationManager;
import com.threerings.msoy.person.server.persist.FeedRepository;
import com.threerings.msoy.person.server.persist.ProfileRepository;
import com.threerings.msoy.person.util.FeedMessageType;
import com.threerings.msoy.profile.gwt.Profile;
import com.threerings.msoy.room.data.MsoySceneModel;
import com.threerings.msoy.room.server.persist.MsoySceneRepository;
import com.threerings.msoy.room.server.persist.SceneRecord;
import com.threerings.msoy.underwire.server.SupportLogic;

import static com.threerings.msoy.Log.log;

/**
 * Manage msoy members.
 */
@Singleton @EventThread
public class MemberManager
    implements MemberLocator.Observer, MemberProvider
{
    @Inject public MemberManager (
        InvocationManager invmgr, MsoyPeerManager peerMan, MemberLocator locator)
    {
        // register our bootstrap invocation service
        invmgr.registerDispatcher(new MemberDispatcher(this), MsoyCodes.MEMBER_GROUP);

        // register to hear when members logon and off
        locator.addObserver(this);

        // register to hear when members are forwarded between nodes
        peerMan.memberFwdObs.add(new MsoyPeerManager.MemberForwardObserver() {
            public void memberWillBeSent (String node, MemberObject memobj) {
                // flush the transient bits in our metrics as we will snapshot and send this data
                // before we depart our current room (which is when the are normally saved)
                MemberLocal mlocal = memobj.getLocal(MemberLocal.class);
                mlocal.metrics.save(memobj);

                // update the number of active seconds they've spent online
                MsoyClient mclient = (MsoyClient)_clmgr.getClient(memobj.username);
                if (mclient != null) {
                    mlocal.sessionSeconds += mclient.getSessionSeconds();
                    // let our client handler know that the session is not over but rather is being
                    // forwarded to another server
                    mclient.setSessionForwarded(true);
                }
            }
        });

        // intialize our internal array of memoized flow values per level.  Start with 256
        // calculated levels
        _levelForFlow = new int[256];
        for (int ii = 0; ii < BEGINNING_FLOW_LEVELS.length; ii++) {
            _levelForFlow[ii] = BEGINNING_FLOW_LEVELS[ii];
        }
        calculateLevelsForFlow(BEGINNING_FLOW_LEVELS.length);
    }

    /**
     * Prepares our member manager for operation.
     */
    public void init ()
    {
        _ppSnapshot = PopularPlacesSnapshot.takeSnapshot(_omgr, _peerMan);
        _ppInvalidator = new Interval(_omgr) {
            @Override public void expired() {
                final PopularPlacesSnapshot newSnapshot =
                    PopularPlacesSnapshot.takeSnapshot(_omgr, _peerMan);
                synchronized(MemberManager.this) {
                    _ppSnapshot = newSnapshot;
                }
            }
        };
        _ppInvalidator.schedule(POP_PLACES_REFRESH_PERIOD, true);
    }

    /**
     * Returns the most recently generated popular places snapshot.
     */
    public PopularPlacesSnapshot getPPSnapshot ()
    {
        synchronized(this) {
            return _ppSnapshot;
        }
    }

    /**
     * Update the user's occupant info.
     */
    public void updateOccupantInfo (final MemberObject user)
    {
        final PlaceManager pmgr = _placeReg.getPlaceManager(user.getPlaceOid());
        if (pmgr != null) {
            pmgr.updateOccupantInfo(user.createOccupantInfo(pmgr.getPlaceObject()));
        }
    }

    public void addExperience (final MemberObject memObj, final MemberExperience newExp)
    {
        memObj.startTransaction();
        try {
            // If we're at our limit of experiences, remove the oldest.
            if (memObj.experiences.size() >= MAX_EXPERIENCES) {
                MemberExperience oldest = null;
                for (MemberExperience experience : memObj.experiences) {
                    if (oldest == null ||
                            experience.getDateOccurred().compareTo(oldest.getDateOccurred()) < 0) {
                        oldest = experience;
                    }
                }
                memObj.removeFromExperiences(oldest.getKey());
            }

            // Add the new experience
            memObj.addToExperiences(newExp);
        } finally {
            memObj.commitTransaction();
        }
    }

    // from interface MemberLocator.Observer
    public void memberLoggedOn (final MemberObject member)
    {
        if (member.isGuest()) {
            return;
        }

        //  add a listener for changes to accumulated flow so that the member's level can be
        // updated as necessary
        member.addListener(new AttributeChangeListener() {
            public void attributeChanged (final AttributeChangedEvent event) {
                if (MemberObject.ACC_COINS.equals(event.getName())) {
                    checkCurrentLevel(member);
                }
            }
        });

        // check their current level now in case they got flow while they were offline
        checkCurrentLevel(member);

        // update badges
        _badgeMan.updateBadges(member);
    }

    // from interface MemberLocator.Observer
    public void memberLoggedOff (final MemberObject member)
    {
        // nada
    }

    // from interface MemberProvider
    public void inviteToBeFriend (final ClientObject caller, final int friendId,
                                  final InvocationService.ConfirmListener listener)
        throws InvocationException
    {
        final MemberObject user = (MemberObject) caller;
        ensureNotGuest(user);
        _invoker.postUnit(new PersistingUnit("inviteToBeFriend", listener) {
            @Override public void invokePersistent () throws Exception {
                _mailLogic.sendFriendInvite(user.getMemberId(), friendId);
            }
            @Override public void handleSuccess () {
                ((InvocationService.ConfirmListener)_listener).requestProcessed();
            }
        });
    }

    // from interface MemberProvider
    public void bootFromPlace (final ClientObject caller, final int booteeId,
                               final InvocationService.ConfirmListener listener)
        throws InvocationException
    {
        final MemberObject user = (MemberObject) caller;
        if (user.location == null) {
            throw new InvocationException(InvocationCodes.INTERNAL_ERROR);
//            // TEST: let's pretend that we KNOW that they're in a game... just move them home
//            MemberObject bootee = _locator.lookupMember(booteeId);
//            _screg.moveBody(bootee, bootee.getHomeSceneId());
//            listener.requestProcessed();
//            return;
        }

        final PlaceManager pmgr = _placeReg.getPlaceManager(user.location.placeOid);
        if (!(pmgr instanceof BootablePlaceManager)) {
            throw new InvocationException(InvocationCodes.INTERNAL_ERROR);
        }

        final String response = ((BootablePlaceManager) pmgr).bootFromPlace(user, booteeId);
        if (response == null) {
            listener.requestProcessed();
        } else {
            listener.requestFailed(response);
        }
    }

    // from interface MemberProvider
    public void getHomeId (final ClientObject caller, final byte ownerType, final int ownerId,
                           final InvocationService.ResultListener listener)
        throws InvocationException
    {
        _invoker.postUnit(new PersistingUnit(listener) {
            @Override public void invokePersistent () throws Exception {
                _homeId = _memberLogic.getHomeId(ownerType, ownerId);
            }
            @Override public void handleSuccess () {
                if (_homeId == null) {
                    handleFailure(new InvocationException("m.no_such_user"));
                } else {
                    ((InvocationService.ResultListener)_listener).requestProcessed(_homeId);
                }
            }
            protected Integer _homeId;
        });
    }

    // from interface MemberProvider
    public void getCurrentMemberLocation (final ClientObject caller, final int memberId,
                                          final InvocationService.ResultListener listener)
        throws InvocationException
    {
        final MemberObject user = (MemberObject) caller;

        // ensure that the other member is a full friend
        final FriendEntry entry = user.friends.get(memberId);
        if (null == entry) {
            throw new InvocationException("e.not_a_friend");
        }

        final MemberLocation memloc = _peerMan.getMemberLocation(memberId);
        if (memloc == null) {
            throw new InvocationException(MessageBundle.tcompose("e.not_online", entry.name));
        }
        listener.requestProcessed(memloc);
    }

    // from interface MemberProvider
    public void updateAvailability (final ClientObject caller, final int availability)
    {
        final MemberObject user = (MemberObject) caller;
        user.setAvailability(availability);
    }

    // from interface MemberProvider
    public void inviteToFollow (final ClientObject caller, final int memberId,
                                final InvocationService.InvocationListener listener)
        throws InvocationException
    {
        final MemberObject user = (MemberObject) caller;

        // make sure the target member is online and in the same room as the requester
        final MemberObject target = _locator.lookupMember(memberId);
        if (target == null || !ObjectUtil.equals(user.location, target.location)) {
            throw new InvocationException("e.follow_not_in_room");
        }

        // make sure the target is accepting invitations from the requester
        if (!target.isAvailableTo(user.getMemberId())) {
            throw new InvocationException("e.follow_not_available");
        }

        // issue the follow invitation to the target
        _notifyMan.notifyFollowInvite(target, user.memberName);
    }

    // from interface MemberProvider
    public void followMember (final ClientObject caller, final int memberId,
                              final InvocationService.InvocationListener listener)
        throws InvocationException
    {
        final MemberObject user = (MemberObject) caller;

        // if the caller is requesting to clear their follow, do so
        if (memberId == 0) {
            if (user.following != null) {
                final MemberObject followee = _locator.lookupMember(user.following.getMemberId());
                if (followee != null) {
                    followee.removeFromFollowers(user.getMemberId());
                }
                user.setFollowing(null);
            }
            return;
        }

        // Make sure the target isn't bogus
        final MemberObject target = _locator.lookupMember(memberId);
        if (target == null) {
            throw new InvocationException("e.follow_invite_expired");
        }

        // Wire up both the leader and follower
        if (!target.followers.containsKey(target.getMemberId())) {
            log.info("Adding follower " + user.memberName + " to " + target.memberName + ".");
            target.addToFollowers(user.memberName);
        }
        user.setFollowing(target.memberName);
    }

    // from interface MemberProvider
    public void ditchFollower (ClientObject caller, int memberId,
                               InvocationService.InvocationListener listener)
        throws InvocationException
    {
        MemberObject leader = (MemberObject) caller;

        if (memberId == 0) {
            // Clear all followers
            for (MemberName follower : leader.followers) {
                MemberObject fmo = _locator.lookupMember(follower.getMemberId());
                if (fmo != null) {
                    fmo.setFollowing(null);
                }
            }
            leader.setFollowers(new DSet<MemberName>());

        } else {
            // Ditch a single follower
            MemberObject follower = _locator.lookupMember(memberId);
            if (follower != null && leader.followers.containsKey(follower.getMemberId())) {
                follower.setFollowing(null);
                leader.removeFromFollowers(memberId);
            }
        }
    }

    // from interface MemberProvider
    public void setAway (final ClientObject caller, final boolean away, final String message)
        //throws InvocationException
    {
        final MemberObject user = (MemberObject) caller;
        user.setAwayMessage(away ? message : null);
        _bodyMan.updateOccupantStatus(
            user, user.location, away ? MsoyBodyObject.AWAY : OccupantInfo.ACTIVE);
    }

    // from interface MemberProvider
    public void setAvatar (final ClientObject caller, final int avatarItemId, final float newScale,
                           final InvocationService.ConfirmListener listener)
        throws InvocationException
    {
        final MemberObject user = (MemberObject) caller;
        ensureNotGuest(user);

        if (avatarItemId == 0) {
            // a request to return to the default avatar
            finishSetAvatar(user, null, newScale, listener);
            return;
        }

        // otherwise, make sure it exists and we own it
        final ItemIdent ident = new ItemIdent(Item.AVATAR, avatarItemId);
        _itemMan.getItem(ident, new ResultListener<Item>() {
            public void requestCompleted (final Item item) {
                final Avatar avatar = (Avatar) item;
                if (user.getMemberId() != avatar.ownerId) { // ensure that they own it
                    final String errmsg = "Not user's avatar [ownerId=" + avatar.ownerId + "]";
                    requestFailed(new Exception(errmsg));
                } else {
                    finishSetAvatar(user, avatar, newScale, listener);
                }
            }
            public void requestFailed (final Exception cause) {
                log.warning("Unable to setAvatar [for=" + user.getMemberId() +
                        ", avatar=" + ident + "].", cause);
                listener.requestFailed(InvocationCodes.INTERNAL_ERROR);
            }
        });
    }

    // from interface MemberProvider
    public void setDisplayName (final ClientObject caller, final String name,
                                final InvocationService.InvocationListener listener)
        throws InvocationException
    {
        final MemberObject user = (MemberObject) caller;
        ensureNotGuest(user);

        // TODO: verify entered string

        final String uname = "setDisplayName(" + user.who() + ", " + name + ")";
        _invoker.postUnit(new PersistingUnit(uname, listener) {
            @Override public void invokePersistent ()
                throws Exception
            {
                _memberRepo.configureDisplayName(user.getMemberId(), name);
            }
            @Override public void handleSuccess ()
            {
                user.updateDisplayName(name);
                updateOccupantInfo(user);
            }
        });
    }

    // from interface MemberProvider
    public void getDisplayName (final ClientObject caller, final int memberId,
                                final InvocationService.ResultListener listener)
        throws InvocationException
    {
        final String uname = "getDisplayName(" + memberId + ")";
        _invoker.postUnit(new PersistingUnit(uname, listener) {
            @Override public void invokePersistent ()
                throws Exception
            {
                _displayName = String.valueOf(_memberRepo.loadMemberName(memberId));
            }
            @Override public void handleSuccess ()
            {
                reportRequestProcessed(_displayName);
            }
            protected String _displayName;
        });
    }

    // from interface MemberProvider
    public void getGroupName (final ClientObject caller, final int groupId,
                              final InvocationService.ResultListener listener)
    {
        _invoker.postUnit(new PersistingUnit("getGroupName(" + groupId + ")", listener) {
            @Override public void invokePersistent ()
                throws Exception
            {
                final GroupRecord rec = _groupRepo.loadGroup(groupId);
                _groupName = (rec == null) ? "" : rec.name;
            }
            @Override public void handleSuccess ()
            {
                reportRequestProcessed(_groupName);
            }
            protected String _groupName;
        });
    }

    // from interface MemberProvider
    public void acknowledgeWarning (final ClientObject caller)
    {
        final MemberObject user = (MemberObject) caller;
        _invoker.postUnit(new WriteOnlyUnit("acknowledgeWarning(" + user.getMemberId() + ")") {
            @Override public void invokePersist ()
                throws Exception
            {
                _memberRepo.clearMemberWarning(user.getMemberId());
            }
        });
    }

    // from interface MemberProvider
    public void setHomeSceneId (final ClientObject caller, final int ownerType, final int ownerId,
                                final int sceneId, final InvocationService.ConfirmListener listener)
        throws InvocationException
    {
        final MemberObject member = (MemberObject) caller;
        ensureNotGuest(member);

        final String uname = "setHomeSceneId(" + member.getMemberId() + ")";
        _invoker.postUnit(new PersistingUnit(uname, listener) {
            @Override
            public void invokePersistent () throws Exception {
                final int memberId = member.getMemberId();
                final SceneRecord scene = _sceneRepo.loadScene(sceneId);
                if (scene.ownerType == MsoySceneModel.OWNER_TYPE_MEMBER) {
                    if (scene.ownerId == memberId) {
                        _memberRepo.setHomeSceneId(memberId, sceneId);
                    } else {
                        throw new InvocationException("e.not_room_owner");
                    }
                } else if (scene.ownerType == MsoySceneModel.OWNER_TYPE_GROUP) {
                    if (member.isGroupManager(scene.ownerId)) {
                        _groupRepo.setHomeSceneId(scene.ownerId, sceneId);
                    } else {
                        throw new InvocationException("e.not_room_manager");
                    }
                } else {
                    log.warning("Unknown scene model owner type [sceneId=" +
                        scene.sceneId + ", ownerType=" + scene.ownerType + "]");
                    throw new InvocationException(InvocationCodes.INTERNAL_ERROR);
                }
            }
            @Override
            public void handleSuccess () {
                if (ownerType == MsoySceneModel.OWNER_TYPE_MEMBER) {
                    member.setHomeSceneId(sceneId);
                }
                reportRequestProcessed();
            }
        });
    }

    // from interface MemberProvider
    public void getGroupHomeSceneId (final ClientObject caller, final int groupId,
                                     final InvocationService.ResultListener listener)
        throws InvocationException
    {
        final String uname = "getHomeSceneId(" + groupId + ")";
        _invoker.postUnit(new PersistingUnit(uname, listener) {
            @Override public void invokePersistent ()
                throws Exception
            {
                _homeSceneId = _groupRepo.getHomeSceneId(groupId);
            }
            @Override public void handleSuccess ()
            {
                reportRequestProcessed(_homeSceneId);
            }
            protected int _homeSceneId;
        });
    }

    // from interface MemberProvider
    public void complainMember (ClientObject caller, final int memberId, String complaint)
    {
        final MemberObject source = (MemberObject)caller;

        final EventRecord event = new EventRecord();
        event.source = Integer.toString(source.memberName.getMemberId());
        event.sourceHandle = source.memberName.toString();
        event.status = Event.OPEN;
        event.subject = complaint;

        // format and provide the complainer's chat history
        SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
        StringBuilder chatHistory = new StringBuilder();
        for (ChatMessage msg : SpeakUtil.getChatHistory(source.memberName)) {
            UserMessage umsg = (UserMessage)msg;
            chatHistory.append(df.format(new Date(umsg.timestamp))).append(' ');
//             if (umsg instanceof ChannelMessage) {
//                 ChannelMessage cmsg = (ChannelMessage)umsg;
//                 chatHistory.append('[').append(ChatChannel.XLATE_TYPE[cmsg.channel.type]);
//                 chatHistory.append(':').append(cmsg.channel.ident).append("] ");
//             } else {
                chatHistory.append(StringUtil.pad(ChatCodes.XLATE_MODES[umsg.mode], 10)).append(' ');
//             }
            chatHistory.append(umsg.speaker);
            if (umsg.speaker instanceof MemberName) {
                chatHistory.append('(').append(((MemberName)umsg.speaker).getMemberId()).append(')');
            }
            chatHistory.append(": ").append(umsg.message).append('\n');
        }
        event.chatHistory = chatHistory.toString();

        // if the target is online, get thir name from their member object
        MemberObject target = _locator.lookupMember(memberId);
        if (target != null) {
            event.targetHandle = target.memberName.toString();
            event.target = Integer.toString(target.memberName.getMemberId());
        }

        _invoker.postUnit(new Invoker.Unit("addComplaint") {
            @Override public boolean invoke () {
                try {
                    _supportLogic.addComplaint(event, memberId);
                } catch (Exception e) {
                    log.warning("Failed to add complaint event [event=" + event + "].", e);
                    _failed = true;
                }
                return true;
            }
            @Override public void handleResult () {
                SpeakUtil.sendFeedback(source, MsoyCodes.GENERAL_MSGS,
                        _failed ? "m.complain_fail" : "m.complain_success");
            }
            protected boolean _failed = false;
        });
    }

    // from interface MemberProvider
    public void updateStatus (final ClientObject caller, final String status,
                              final InvocationService.InvocationListener listener)
        throws InvocationException
    {
        final MemberObject member = (MemberObject) caller;
        ensureNotGuest(member);

        final String commitStatus = StringUtil.truncate(status, Profile.MAX_STATUS_LENGTH);
        final String uname = "updateStatus(" + member.getMemberId() + ")";
        _invoker.postUnit(new PersistingUnit(uname, listener) {
            @Override public void invokePersistent () throws Exception {
                _profileRepo.updateHeadline(member.getMemberId(), commitStatus);
            }
            @Override public void handleSuccess () {
                member.setHeadline(commitStatus);
                MemberNodeActions.updateFriendEntries(member);
            }
        });
    }

    // from interface MemberProvider
    public void trackVectorAssociation (final ClientObject caller, final String vector)
    {
        final MemberObject memObj = (MemberObject) caller;
        _eventLog.vectorAssociated(memObj.visitorInfo, vector);
    }

    // from interface MemberProvider
    public void emailShare (ClientObject caller, boolean isGame, String placeName, int placeId,
                            String[] emails, String message, InvocationService.ConfirmListener cl)
    {
        final MemberObject memObj = (MemberObject) caller;
        String page;
        if (isGame) {
            page = "world-game_g_" + placeId;
        } else {
            page = "world-s" + placeId;
        }
        String url = ServerConfig.getServerURL();
        if (memObj.isGuest()) {
            url += "#" + page;
        } else {
            // set them up with the affiliate info
            url += "welcome/" + memObj.getMemberId() + "/" + StringUtil.encode(page);
        }

        final String template = isGame ? "shareGameInvite" : "shareRoomInvite";
        // username is their authentication username which is their email address
        final String from = memObj.username.toString();
        for (final String recip : emails) {
            // this just passes the buck to an executor, so we can call it from the dobj thread
            _mailer.sendTemplateEmail(recip, from, template, "inviter", memObj.memberName,
                                      "name", placeName, "message", message, "link", url);
        }

        cl.requestProcessed();
    }

    // from interface MemberProvider
    public void getABTestGroup (final ClientObject caller, final String testName,
        final boolean logEvent, final InvocationService.ResultListener listener)
    {
        final MemberObject memObj = (MemberObject) caller;
        _invoker.postUnit(new PersistingUnit(listener) {
            @Override public void invokePersistent () throws Exception {
                _testGroup = new Integer(_memberLogic.getABTestGroup(testName,
                    memObj.visitorInfo, logEvent));
            }
            @Override public void handleSuccess () {
                ((InvocationService.ResultListener)_listener).requestProcessed(_testGroup);
            }
            protected Integer _testGroup;
        });
    }

    // from interface MemberProvider
    public void trackClientAction (final ClientObject caller, final String actionName,
        final String details)
    {
        final MemberObject memObj = (MemberObject) caller;
        if (memObj.visitorInfo == null) {
            log.warning("Failed to log client action with null referral", "caller", caller.who(),
                        "actionName", actionName);
            return;
        }
        _eventLog.clientAction(memObj.getVisitorId(), actionName, details);
    }

    // from interface MemberProvider
    public void trackTestAction (final ClientObject caller, final String actionName,
        final String testName)
    {
        final MemberObject memObj = (MemberObject) caller;
        if (memObj.visitorInfo == null) {
            log.warning("Failed to log test action with null visitorInfo", "caller", caller.who(),
                        "actionName", actionName);
            return;
        }

        _invoker.postUnit(new Invoker.Unit("getABTestGroup") {
            @Override public boolean invoke () {
                int abTestGroup = -1;
                String actualTestName;
                if (testName != null) {
                    // grab the group without logging a tracking event about it
                    abTestGroup = _memberLogic.getABTestGroup(testName, memObj.visitorInfo, false);
                    actualTestName = testName;
                } else {
                    actualTestName = "";
                }
                _eventLog.testAction(memObj.getVisitorId(), actionName, actualTestName,
                    abTestGroup);
                return false;
            }
        });
    }

    // from interface MemberProvider
    public void loadAllBadges(ClientObject caller, InvocationService.ResultListener listener)
        throws InvocationException
    {
        long now = System.currentTimeMillis();
        List<EarnedBadge> badges = Lists.newArrayList();
        for (BadgeType type : BadgeType.values()) {
            int code = type.getCode();
            for (int ii = 0; ii < type.getNumLevels(); ii++) {
                String levelUnits = type.getRequiredUnitsString(ii);
                int coinValue = type.getCoinValue(ii);
                badges.add(new EarnedBadge(code, ii, levelUnits, coinValue, now));
            }
        }

        listener.requestProcessed(badges.toArray(new EarnedBadge[badges.size()]));
    }

    // from interface MemberProvider
    public void dispatchDeferredNotifications (ClientObject caller)
    {
        _notifyMan.dispatchDeferredNotifications((MemberObject)caller);
    }

    // from interface MemberProvider
    public void getHomePageGridItems (
        ClientObject caller, InvocationService.ResultListener listener)
        throws InvocationException
    {
        final MemberObject memObj = (MemberObject) caller;

        _invoker.postUnit(new PersistingUnit(listener) {
            @Override public void invokePersistent () throws Exception {
                HomePageItem[] items = new HomePageItem[MWP_COUNT];
                int curItem = 0;

                // The first item on the home page is always a whirled tour unless already onTour
                if (!memObj.onTour) {
                    items[curItem++] = EXPLORE_ITEM;
                }

                // The next 2 or 3 items are badges
                List<InProgressBadge> badges = _badgeLogic.getNextSuggestedBadges(
                    memObj.getMemberId(), memObj.getLocal(MemberLocal.class).badgesVersion,
                    3 - curItem);
                for (InProgressBadge badge : badges) {
                    items[curItem++] = new HomePageItem(
                        HomePageItem.ACTION_BADGE, badge, badge.imageMedia());
                }

                // The last 6 are determined by the user-specific home page items, depending on
                // where they were last in Whirled.
                Set<Integer> haveRooms = new HashSet<Integer>();
                Set<Integer> haveGames = new HashSet<Integer>();
                for(HomePageItem item : getHomePageItems(memObj, 6)) {
                    items[curItem++] = item;
                    int id = ((BasicNavItemData)item.getNavItemData()).getId();
                    if (item.getAction() == HomePageItem.ACTION_ROOM) {
                        haveRooms.add(id);
                    } else if (item.getAction() == HomePageItem.ACTION_GAME) {
                        haveGames.add(id);
                    }
                }

                // If there are still not enough places, fill in with some currently popular places
                // to go.  Half will be games, the other half rooms.
                if (curItem < items.length) {
                    // TODO: This is similar to some code in GalaxyServlet and GameServlet.
                    // refactor?
                    int startTopItems = curItem;
                    int roomCount = (items.length - curItem) / 2;
                    PopularPlacesSnapshot pps = getPPSnapshot();
                    for (Place place : pps.getTopScenes()) {
                        if (!haveRooms.contains(place.placeId)) {
                            SceneRecord scene = _sceneRepo.loadScene(place.placeId);
                            MediaDesc media = scene.getSnapshot();
                            if (media == null) {
                                media = DEFAULT_ROOM_SNAPSHOT;
                            }
                            items[curItem++] = new HomePageItem(HomePageItem.ACTION_ROOM,
                                new BasicNavItemData(place.placeId, place.name), media);
                            haveRooms.add(place.placeId);
                        }
                        if (curItem == startTopItems + roomCount) {
                            break;
                        }
                    }

                    // Load top games
                    for (Place place : pps.getTopGames()) {
                        if (!haveGames.contains(place.placeId)) {
                            GameRecord game = _msoyGameRepo.loadGameRecord(place.placeId);
                            items[curItem++] = new HomePageItem(HomePageItem.ACTION_GAME,
                                new BasicNavItemData(game.gameId, game.name),
                                game.getThumbMediaDesc());
                            haveGames.add(game.gameId);
                        }
                        if (curItem == items.length) {
                            break;
                        }
                    }

                    // If we don't have enough games, pull from the list of all games.
                    if (curItem < items.length) {
                        for(GameRecord game : _gameRepo.loadGenre((byte)-1, items.length)) {
                            if (!haveGames.contains(game.gameId)) {
                                items[curItem++] = new HomePageItem(HomePageItem.ACTION_GAME,
                                    new BasicNavItemData(game.gameId, game.name),
                                    game.getThumbMediaDesc());
                            }
                            if (curItem == items.length) {
                                break;
                            }
                        }
                    }
                }

                // If there still aren't enough places, fill in with null objects.
                while (curItem < items.length) {
                    items[curItem++] = new HomePageItem(HomePageItem.ACTION_NONE, null, null);
                }

                reportRequestProcessed(items);
            }
        });
    }

    /**
     * Check if the member's accumulated flow level matches up with their current level, and update
     * their current level if necessary
     */
    public void checkCurrentLevel (final MemberObject member)
    {
        int level = Arrays.binarySearch(_levelForFlow, member.accCoins);
        if (level < 0) {
            level = -1 * level - 1;
            final int length = _levelForFlow.length;
            // if the _levelForFlow array isn't big enough, double its size and flesh out the new
            // half
            if (level == length) {
                final int[] temp = new int[length*2];
                System.arraycopy(_levelForFlow, 0, temp, 0, length);
                _levelForFlow = temp;
                calculateLevelsForFlow(length);
                checkCurrentLevel(member);
                return;
            }
            // level was equal to what would be the insertion point of accFlow, which is actually
            // one greater than the real level.
            level--;
        }
        // the flow value at array index ii cooresponds to level ii+1
        level++;

        if (member.level != level) {
            // update their level now so that we don't come along and do this again while the
            // invoker is off writing things to the database
            member.setLevel(level);

            final int newLevel = level;
            _invoker.postUnit(new RepositoryUnit("updateLevel") {
                @Override public void invokePersist () throws Exception {
                    final int memberId = member.getMemberId();
                    // record the new level, and grant a new invite
                    _memberRepo.setUserLevel(memberId, newLevel);
                    _memberRepo.grantInvites(memberId, 1);
                    // mark the level gain in their feed
                    _feedRepo.publishMemberMessage(
                        memberId, FeedMessageType.FRIEND_GAINED_LEVEL, String.valueOf(newLevel));
                }
                @Override public void handleSuccess () {
                    _notifyMan.notify(member, new LevelUpNotification(newLevel));
                }
                @Override public void handleFailure (final Exception pe) {
                    log.warning("Unable to set user level.",
                        "memberId", member.getMemberId(), "level", newLevel);
                }
            });
        }
    }

    /**
     * Boots a player from the server.  Must be called on the dobjmgr thread.
     *
     * @return true if the player was found and booted successfully
     */
    public boolean bootMember (final int memberId)
    {
        final MemberObject mobj = _locator.lookupMember(memberId);
        if (mobj != null) {
            final PresentsClient pclient = _clmgr.getClient(mobj.username);
            if (pclient != null) {
                pclient.endSession();
                return true;
            }
        }
        return false;
    }

    /**
     * A member experience that has been scored.
     *
     * @author Kyle Sampson <kyle@threerings.net>
     */
    protected static class ScoredExperience
    {
        public final MemberExperience experience;
        public final float score;

        /**
         * Creates a scored experience based on the information from the given
         * {@link MemberExperienceRecord}.
         */
        public ScoredExperience (MemberExperience experience)
        {
            this.experience = experience;

            // The score for a standard record starts at 14 and decrements by 1 for every day
            // since the experience occurred.  Cap at 0; thus, anything older than 2 weeks has
            // the same score.
            float newScore = 14f -
                (System.currentTimeMillis() - experience.dateOccurred) /
                (1000f * 60f * 60f * 24f);
            score = (newScore < 0) ? 0f : newScore;
        }

        /**
         * Combines two identical (i.e., {@link #isSameExperience(ScoredExperience)} returns true})
         * scored experiences into one, combining their scores.
         */
        public ScoredExperience (ScoredExperience exp1, ScoredExperience exp2)
        {
            experience = exp1.experience;   // exp2.item should be the same.
            score = exp1.score + exp2.score;    // Both scores positive
        }

        /**
         * Null experience
         */
        public ScoredExperience ()
        {
            experience = new MemberExperience(new Date(), HomePageItem.ACTION_NONE, 0);
            score = 0f;
        }

        /**
         * Returns true if the given scored experience represents the same experience as this one.
         * They may have different scores, but this indicates the user did the same thing twice.
         */
        public boolean isSameExperience (ScoredExperience other)
        {
            return this.experience.action == other.experience.action &&
                this.experience.data == other.experience.data;
        }
    }

    /**
     * Retrieves a list of experiences to be displayed on the home page.  Each experience the
     * member has had recently will be given a weighted score to determine the order of the
     * experience.  Only the number of experiences requested will be returned as home page
     * items.  If there are not enough experiences, or the experiences have a low score
     * (too old, etc.), they will not be included here.
     *
     * @param memObj Member object to get home page items for
     * @param count Number of home page items to retrieve.
     * @return List of the home page items.
     */
    protected List<HomePageItem> getHomePageItems (MemberObject memObj, int count)
    {
        List<ScoredExperience> scores = new ArrayList<ScoredExperience>();
        for (MemberExperience experience : memObj.experiences) {
            ScoredExperience newExp = new ScoredExperience(experience);

            // Has this member experienced this more than once?  If so, combine.
            for (Iterator<ScoredExperience> itor = scores.iterator(); itor.hasNext(); ) {
                ScoredExperience thisExp = itor.next();
                if (thisExp.isSameExperience(newExp)) {
                    newExp = new ScoredExperience(newExp, thisExp);
                    itor.remove();
                    break;
                }
            }

            scores.add(newExp);
        }

        // Sort by scores (highest score first), limit it to count, and return the list.
        Collections.sort(scores, new Comparator<ScoredExperience>() {
            public int compare (ScoredExperience exp1, ScoredExperience exp2) {
                return (exp1.score > exp2.score) ? -1 : ((exp1.score < exp2.score) ? 1 : 0);
            }
        });
        while (scores.size() > count) {
            scores.remove(scores.size() - 1);
        }
        return Lists.transform(scores, new Function<ScoredExperience, HomePageItem>() {
            public HomePageItem apply (ScoredExperience se) {
                MediaDesc media;
                final NavItemData data;
                switch (se.experience.action) {
                case HomePageItem.ACTION_ROOM: {
                    SceneRecord scene = _sceneRepo.loadScene(se.experience.data);
                    media = scene.getSnapshot();
                    if (media == null) {
                        media = DEFAULT_ROOM_SNAPSHOT;
                    }
                    data = new BasicNavItemData(se.experience.data, scene.name);
                    break;
                }
                case HomePageItem.ACTION_GAME:
                    GameRecord game = _msoyGameRepo.loadGameRecord(se.experience.data);
                    media = game.getThumbMediaDesc();
                    data = new BasicNavItemData(se.experience.data, game.name);
                    break;
                default:
                    media = null;
                    data = null;
                }
                return se.experience.getHomePageItem(media, data);
            }
        });
    }

    /**
     * Convenience method to ensure that the specified caller is not a guest.
     */
    protected void ensureNotGuest (final MemberObject caller)
        throws InvocationException
    {
        if (caller.isGuest()) {
            throw new InvocationException(InvocationCodes.ACCESS_DENIED);
        }
    }

    /**
     * Finish configuring the user's avatar.
     *
     * @param avatar may be null to revert to the default member avatar.
     */
    protected void finishSetAvatar (
        final MemberObject user, final Avatar avatar, final float newScale,
        final InvocationService.ConfirmListener listener)
    {
        _invoker.postUnit(new RepositoryUnit("setAvatarPt2") {
            @Override public void invokePersist () throws Exception {
                _memberRepo.configureAvatarId(user.getMemberId(),
                    (avatar == null) ? 0 : avatar.itemId);
                if (newScale != 0 && avatar != null && avatar.scale != newScale) {
                    _itemMan.getAvatarRepository().updateScale(avatar.itemId, newScale);
                }
            }

            @Override public void handleSuccess () {
                final Avatar prev = user.avatar;
                if (newScale != 0 && avatar != null) {
                    avatar.scale = newScale;
                }
                _itemMan.updateItemUsage(
                    user.getMemberId(), prev, avatar, new ResultListener.NOOP<Object>() {
                    @Override
                    public void requestFailed (final Exception cause) {
                        log.warning("Unable to update usage from an avatar change.");
                    }
                });

                // now we need to make sure that the two avatars have a reasonable touched time
                user.startTransaction();
                try {
                    // unset the current avatar to avoid busy-work in avatarUpdatedOnPeer, but
                    // we'll set the new avatar at the bottom...
                    user.avatar = null;

                    // NOTE: we are not updating the used/location fields of the cached avatars,
                    // I don't think it's necessary, but it'd be a simple matter here...
                    final long now = System.currentTimeMillis();
                    if (prev != null) {
                        prev.lastTouched = now;
                        _itemMan.avatarUpdatedOnPeer(user, prev);
                    }
                    if (avatar != null) {
                        avatar.lastTouched = now + 1; // the new one should be more recently touched
                        _itemMan.avatarUpdatedOnPeer(user, avatar);
                    }

                    // now set the new avatar
                    user.setAvatar(avatar);
                    user.actorState = null; // clear out their state
                    updateOccupantInfo(user);

                } finally {
                    user.commitTransaction();
                }
                listener.requestProcessed();
            }

            @Override public void handleFailure (final Exception pe) {
                log.warning("Unable to set avatar [user=" + user.which() +
                            ", avatar='" + avatar + "', " + "error=" + pe + "].");
                log.warning(pe);
                listener.requestFailed(InvocationCodes.INTERNAL_ERROR);
            }
        });

    }

    protected void calculateLevelsForFlow (final int fromIndex)
    {
        // This equation governs the total flow requirement for a given level (n):
        // flow(n) = flow(n-1) + ((n-1) * 17.8 - 49) * (3000 / 60)
        // where (n-1) * 17.8 - 49 is the equation discovered by PARC researchers that correlates
        // to the time (in minutes) it takes the average WoW player to get from level n-1 to level
        // n, and 3000 is the expected average flow per hour that we hope to drive our system on.
        for (int ii = fromIndex; ii < _levelForFlow.length; ii++) {
            // this array gets filled here with values for levels 1 through _levelForFlow.length...
            // the flow requirement for level n is at array index n-1.  Also, this function will
            // never be called before _levelForFlow has been inialized with 1+ entries.
            _levelForFlow[ii] = _levelForFlow[ii-1] + (int)((ii * 17.8 - 49) * (3000 / 60));
        }
    }

    /** An interval that updates the popular places snapshot every so often. */
    protected Interval _ppInvalidator;

    /** The most recent summary of popular places in the whirled. */
    protected PopularPlacesSnapshot _ppSnapshot;

    /** The array of memoized flow values for each level.  The first few levels are hard coded, the
     * rest are calculated according to the equation in calculateLevelsForFlow() */
    protected int[] _levelForFlow;

    // dependencies
    @Inject protected ClientManager _clmgr;
    @Inject protected PresentsDObjectMgr _omgr;
    @Inject protected PlaceRegistry _placeReg;
    @Inject protected MailLogic _mailLogic;
    @Inject protected MailSender _mailer;
    @Inject protected MemberLogic _memberLogic;
    @Inject protected SupportLogic _supportLogic;
    @Inject protected BodyManager _bodyMan;
    @Inject protected BadgeManager _badgeMan;
    @Inject protected BadgeLogic _badgeLogic;
    @Inject protected NotificationManager _notifyMan;
    @Inject protected ItemManager _itemMan;
    @Inject protected MsoyPeerManager _peerMan;
    @Inject protected MemberLocator _locator;
    @Inject protected MemberRepository _memberRepo;
    @Inject protected GroupRepository _groupRepo;
    @Inject protected MsoyGameRepository _msoyGameRepo;
    @Inject protected GameRepository _gameRepo;
    @Inject protected ProfileRepository _profileRepo;
    @Inject protected FeedRepository _feedRepo;
    @Inject protected MsoySceneRepository _sceneRepo;
    @Inject protected MsoyEventLogger _eventLog;
    @Inject protected @MainInvoker Invoker _invoker;

    /** The required flow for the first few levels is hard-coded */
    protected static final int[] BEGINNING_FLOW_LEVELS = { 0, 300, 900, 1800, 3000, 5100, 8100 };

    /** The frequency with which we recalculate our popular places snapshot. */
    protected static final long POP_PLACES_REFRESH_PERIOD = 30*1000;

    /** The whirled tour home page item. */
    protected static final HomePageItem EXPLORE_ITEM = new HomePageItem(
        HomePageItem.ACTION_EXPLORE, null, new StaticMediaDesc(
            MediaDesc.IMAGE_PNG, "icon", "home_page_tour"));

    /** Static media descriptor for the default room snapshot. */
    protected static final MediaDesc DEFAULT_ROOM_SNAPSHOT = new StaticMediaDesc(
        // It's not obvious from the docs in StaticMediaDesc that you can do this,
        // but this is what Group.getDefaultGroupLogoMedia() does.
        MediaDesc.IMAGE_JPEG, "snapshot", "default_t",
        // we know that we're 66x60
        MediaDesc.HALF_VERTICALLY_CONSTRAINED);

    /** Maximum number of experiences we will keep track of per user. */
    protected static final int MAX_EXPERIENCES = 20;

    /** The number of slots we have in My Whired Places. */
    protected static final int MWP_COUNT = 9;
}
