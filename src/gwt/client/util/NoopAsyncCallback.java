//
// $Id$

package client.util;

import com.google.gwt.user.client.rpc.AsyncCallback;

import client.shell.CShell;

/**
 * Noop implementation of an AsyncCallback of type Void.  Does nothing on success, and logs a
 * message on failure.
 */
public class NoopAsyncCallback implements AsyncCallback<Void>
{
    public void onSuccess (Void result)
    {
        // no op!
    }

    public void onFailure (Throwable caught)
    {
        CShell.log("Failed to communicate with the server: " + caught);
    }
}
