//
// $Id$

package com.threerings.msoy.web.client;

import java.util.Date;

import com.google.gwt.user.client.rpc.AsyncCallback;

import com.threerings.msoy.web.data.MemberAdminInfo;
import com.threerings.msoy.web.data.MemberInviteResult;
import com.threerings.msoy.web.data.WebIdent;

/**
 * The asynchronous (client-side) version of {@link AdminService}.
 */
public interface AdminServiceAsync
{
    /** 
     * The asynchronous version of {@link AdminService#grantInvitations}.
     */
    public void grantInvitations (WebIdent ident, int numberInvitations, Date activeSince, 
                                  AsyncCallback<Void> callback);

    /** 
     * The asynchronous version of {@link AdminService#grantInvitations}.
     */
    public void grantInvitations (WebIdent ident, int numberInvitations, int memberId, 
                                  AsyncCallback<Void> callback);

    /**
     * The asynchronous version of {@link AdminService#getMemberInfo}.
     */
    public void getMemberInfo (WebIdent ident, int memberId, 
                               AsyncCallback<MemberAdminInfo> callback);

    /**
     * The asynchronous version of {@link AdminService#getPlayerList}.
     */
    public void getPlayerList (WebIdent ident, int inviterId, 
                               AsyncCallback<MemberInviteResult> callback);

    /**
     * The asynchronous version of {@link AdminService#spamPlayers}.
     */
    public void spamPlayers (WebIdent ident, String subject, String body, int startId, int endId, 
                             AsyncCallback<int[]> callback);

    /**
     * The asynchronous version of {@link AdminService#setIsSupport}.
     */
    public void setIsSupport (WebIdent ident, int memberId, boolean isSupport, 
                              AsyncCallback<Void> callback);
}
