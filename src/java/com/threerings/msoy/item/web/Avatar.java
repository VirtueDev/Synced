//
// $Id$

package com.threerings.msoy.item.web;

/**
 * Represents an avatar that's usable in the msoy system.
 */
public class Avatar extends Item
{
    /** The avatar media. */
    public MediaDesc avatarMedia;

    /**
     * Returns a {@link MediaDesc} configured to display the default non-guest avatar.
     */
    public static MediaDesc getDefaultMemberAvatarMedia ()
    {
        return new StaticMediaDesc(MediaDesc.APPLICATION_SHOCKWAVE_FLASH, AVATAR, "member");
    }

    /**
     * Returns a {@link MediaDesc} configured to display the default guest avatar.
     */
    public static MediaDesc getDefaultGuestAvatarMedia ()
    {
        return new StaticMediaDesc(MediaDesc.APPLICATION_SHOCKWAVE_FLASH, AVATAR, "guest");
    }

    // @Override // from Item
    public byte getType ()
    {
        return AVATAR;
    }

    // @Override // from Item
    public MediaDesc getPreviewMedia ()
    {
        return avatarMedia;
    }

    // @Override // from Item
    public boolean isConsistent ()
    {
        return super.isConsistent() && (avatarMedia != null);
    }

    // @Override // from Item
    protected MediaDesc getDefaultFurniMedia ()
    {
        return avatarMedia;
    }
}
