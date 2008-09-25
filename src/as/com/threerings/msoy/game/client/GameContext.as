//
// $Id$

package com.threerings.msoy.game.client {

import com.threerings.parlor.util.ParlorContext;

import com.threerings.msoy.client.MsoyContext;

import com.threerings.msoy.game.data.PlayerObject;

/**
 * Provides access to our various game services.
 */
public interface GameContext extends ParlorContext
{
    /**
     * Returns the context we use to obtain basic client services.
     */
    function getMsoyContext () :MsoyContext;

    /**
     * Returns our client object casted as a PlayerObject.
     */
    function getPlayerObject () :PlayerObject;

    /**
     * Requests that we return to Whirled, optionally redisplaying the game lobby.
     */
    function backToWhirled (showLobby :Boolean) :void;

    /**
     * Displays the active game's instructions.
     */
    function showGameInstructions () :void;

    /**
     * Requests that we open the appropriate area of the game's shop.
     */
    function showGameShop (itemType :int, catalogId :int = 0) :void;

    /**
     * Returns an array of FriendEntry records for this player's online friends.
     */
    function getOnlineFriends () :Array;
}
}
