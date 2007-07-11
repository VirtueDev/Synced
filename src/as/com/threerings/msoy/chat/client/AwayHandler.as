//
// $Id$

package com.threerings.msoy.chat.client {

import com.threerings.crowd.util.CrowdContext;

import com.threerings.crowd.chat.data.ChatCodes;

import com.threerings.crowd.chat.client.CommandHandler;
import com.threerings.crowd.chat.client.SpeakService;

import com.threerings.msoy.client.WorldContext;

/**
 * Sets the user as being away, giving them 2 seconds of leeway to stop
 * touching the mouse or keyboard.
 */
public class AwayHandler extends CommandHandler
{
    override public function handleCommand (
        ctx :CrowdContext, speakSvc :SpeakService, cmd :String, args :String,
        history :Array) :String
    {
        (ctx as WorldContext).getMsoyController().forceIdle(2);
        return ChatCodes.SUCCESS;
    }
}
}
