//
// $Id$

package client.room;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.orth.data.MediaDesc;

import com.threerings.msoy.room.gwt.RoomInfo;
import com.threerings.msoy.web.gwt.Pages;

import client.ui.MsoyUI;
import client.ui.Stars;
import client.util.Link;

public class RoomWidget extends FlowPanel
{
    /**
     * Create a normal RoomWidget with all the info.
     */
    public RoomWidget (RoomInfo room)
    {
        if (room.winnerRank != null) {
            add(MsoyUI.createLabel(room.winnerRank, "WinnerRank"));
        }
        init(room.sceneId, room.name, room.thumbnail,
            room.hopping ? new Image("/images/rooms/dj_featured.png") : null);
        add(new Stars(room.rating, true, true, null));
        if (room.population > 0) {
            add(MsoyUI.createLabel(_msgs.rwRoomPopulation(""+room.population), null));
        }
    }

    /**
     * Create a "plain vanilla" RoomWidget, which is used when mailing the room, etc.
     */
    public RoomWidget (int sceneId, String name, MediaDesc thumbnail)
    {
        init(sceneId, name, thumbnail, null);
    }

    /**
     * Configure the UI.
     */
    protected void init (int sceneId, String name, MediaDesc thumbnail, Image banner)
    {
        setStyleName("Room");

        FlowPanel box = new FlowPanel();
        box.setStyleName("snapshot");

        Widget thumb = SceneUtil.createSceneThumbView(
            thumbnail, Link.createHandler(Pages.WORLD, "s" + sceneId));
        thumb.setTitle(_msgs.rwThumbTip());
        box.add(thumb);
        if (banner != null) {
            banner.setStyleName("banner");
            box.add(banner);
        }
        add(box);

        Widget nameLink = Link.create(name, Pages.ROOMS, "room", sceneId);
        nameLink.setTitle(_msgs.rwNameTip());
        add(nameLink);
    }

    protected static final RoomMessages _msgs = GWT.create(RoomMessages.class);
}
