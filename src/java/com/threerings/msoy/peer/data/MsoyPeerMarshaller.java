//
// $Id$

package com.threerings.msoy.peer.data;

import com.threerings.msoy.data.MemberObject;
import com.threerings.msoy.peer.client.MsoyPeerService;
import com.threerings.presents.client.Client;
import com.threerings.presents.data.InvocationMarshaller;
import com.threerings.presents.net.Transport;

/**
 * Provides the implementation of the {@link MsoyPeerService} interface
 * that marshalls the arguments and delivers the request to the provider
 * on the server. Also provides an implementation of the response listener
 * interfaces that marshall the response arguments and deliver them back
 * to the requesting client.
 */
public class MsoyPeerMarshaller extends InvocationMarshaller
    implements MsoyPeerService
{
    /** The method id used to dispatch {@link #forwardMemberObject} requests. */
    public static final int FORWARD_MEMBER_OBJECT = 1;

    // from interface MsoyPeerService
    public void forwardMemberObject (Client arg1, MemberObject arg2, String[] arg3, Object[] arg4)
    {
        sendRequest(arg1, FORWARD_MEMBER_OBJECT, new Object[] {
            arg2, arg3, arg4
        }, Transport.DEFAULT);
    }
}
