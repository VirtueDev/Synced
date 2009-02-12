//
// $Id$

package com.threerings.msoy.game.data {

import com.threerings.io.ObjectInputStream;
import com.threerings.io.ObjectOutputStream;
import com.threerings.util.Name;
import com.threerings.util.StringBuilder;

import com.threerings.msoy.data.MsoyCredentials;

/**
 * Used to authenticate with an MSOY Game server.
 */
public class GameCredentials extends MsoyCredentials
{
    /** The unique tracking id for this client, if one is assigned */
    public var visitorId :String;

    public function GameCredentials (name :Name = null)
    {
        super(name);
    }

    // from interface Streamable
    override public function readObject (ins :ObjectInputStream) :void
    {
        super.readObject(ins);
        visitorId = (ins.readField(String) as String);
    }

    // from interface Streamable
    override public function writeObject (out :ObjectOutputStream) :void
    {
        super.writeObject(out);
        out.writeField(visitorId);
    }

    // from Credentials
    override protected function toStringBuf (buf :StringBuilder) :void
    {
        super.toStringBuf(buf);
        buf.append(", visitorId=", visitorId);
    }
}
}
