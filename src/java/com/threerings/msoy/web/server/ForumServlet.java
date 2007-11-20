//
// $Id$

package com.threerings.msoy.web.server;

import java.util.List;
import java.util.logging.Level;

import com.google.common.collect.Lists;

import com.samskivert.io.PersistenceException;
import com.samskivert.util.ArrayIntSet;
import com.samskivert.util.IntSet;

import com.threerings.msoy.data.all.GroupMembership;
import com.threerings.msoy.server.MsoyServer;
import com.threerings.msoy.server.persist.GroupMembershipRecord;
import com.threerings.msoy.server.persist.GroupRecord;
import com.threerings.msoy.server.persist.MemberRecord;

import com.threerings.msoy.fora.data.ForumCodes;
import com.threerings.msoy.fora.data.ForumMessage;
import com.threerings.msoy.fora.data.ForumThread;
import com.threerings.msoy.fora.server.persist.ForumMessageRecord;
import com.threerings.msoy.fora.server.persist.ForumThreadRecord;

import com.threerings.msoy.web.client.ForumService;
import com.threerings.msoy.web.data.Group;
import com.threerings.msoy.web.data.ServiceException;
import com.threerings.msoy.web.data.WebIdent;

import static com.threerings.msoy.Log.log;

/**
 * Provides the server implementation of {@link ForumService}.
 */
public class ForumServlet extends MsoyServiceServlet
    implements ForumService
{
    // from interface ForumService
    public List loadThreads (WebIdent ident, int groupId, int offset, int count)
        throws ServiceException
    {
        MemberRecord mrec = getAuthedUser(ident);

        try {
            // make sure they have read access to this thread
            checkCanRead(mrec, groupId);

            List<ForumThread> threads = Lists.newArrayList();
            return threads;

        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "Failed to load threads [for=" + who(mrec) +
                    ", gid=" + groupId + ", offset=" + offset + ", count=" + count + "].");
            throw new ServiceException(ForumCodes.E_INTERNAL_ERROR);
        }
    }

    // from interface ForumService
    public List loadMessages (WebIdent ident, int threadId, int offset, int count)
        throws ServiceException
    {
        MemberRecord mrec = getAuthedUser(ident);

        try {
            // make sure they have read access to this thread
            ForumThreadRecord ftr = MsoyServer.forumRepo.loadThread(threadId);
            if (ftr == null) {
                throw new ServiceException(ForumCodes.E_INVALID_THREAD);
            }
            checkCanRead(mrec, ftr.groupId);

            // load up the requested set of messages
            List<ForumMessageRecord> msgrecs =
                MsoyServer.forumRepo.loadMessages(threadId, offset, count);

            // enumerate the posters and create member cards for them
            IntSet posters = new ArrayIntSet();
            for (ForumMessageRecord msgrec : msgrecs) {
                posters.add(msgrec.posterId);
            }

            List<ForumMessage> messages = Lists.newArrayList();
            for (ForumMessageRecord fmr : msgrecs) {
            }

            return messages;

        } catch (PersistenceException pe) {
            log.log(Level.WARNING, "Failed to load messages [for=" + who(mrec) +
                    ", tid=" + threadId + ", offset=" + offset + ", count=" + count + "].");
            throw new ServiceException(ForumCodes.E_INTERNAL_ERROR);
        }
    }

    // from interface ForumService
    public ForumThread createThread (WebIdent ident, int groupId, String subject, String message)
        throws ServiceException
    {
        MemberRecord mrec = requireAuthedUser(ident);

        return null;
    }

    // from interface ForumService
    public ForumMessage postMessage (WebIdent ident, int threadId, int inReplyTo, String message)
        throws ServiceException
    {
        MemberRecord mrec = requireAuthedUser(ident);

        return null;
    }

    // from interface ForumService
    public void editMessage (WebIdent ident, int messageId, String message)
        throws ServiceException
    {
        MemberRecord mrec = requireAuthedUser(ident);

    }

    // from interface ForumService
    public void deleteMessage (WebIdent ident, int messageId)
        throws ServiceException
    {
        MemberRecord mrec = requireAuthedUser(ident);

    }

    /**
     * Checks that the supplied caller has read access to the specified group's messages.
     */
    protected void checkCanRead (MemberRecord mrec, int groupId)
        throws PersistenceException, ServiceException
    {
        byte groupRank = getGroupRank(mrec, groupId);

        // if they're not a member, make sure the group is not private
        if (groupRank == GroupMembership.RANK_NON_MEMBER) {
            GroupRecord grec = MsoyServer.groupRepo.loadGroup(groupId);
            if (grec.policy == Group.POLICY_EXCLUSIVE) {
                throw new ServiceException(ForumCodes.E_ACCESS_DENIED);
            }
        }
    }

    /**
     * Determines the rank of the supplied member in the specified group. If the member is null or
     * not a member of the specified group, rank-non-member will be returned.
     */
    protected byte getGroupRank (MemberRecord mrec, int groupId)
        throws PersistenceException
    {
        byte rank = GroupMembership.RANK_NON_MEMBER;
        if (mrec != null) {
            GroupMembershipRecord grm = MsoyServer.groupRepo.getMembership(groupId, mrec.memberId);
            if (grm != null) {
                rank = grm.rank;
            }
        }
        return rank;
    }
}
