//
// $Id$

package com.threerings.msoy.ui {

import flash.display.DisplayObject;
import flash.display.Sprite;

import flash.filters.GlowFilter;

import flash.text.TextField;
import flash.text.TextFieldAutoSize;
import flash.text.TextFormat;

import com.threerings.util.Log;

import com.threerings.flash.TextFieldUtil;

import com.threerings.crowd.data.OccupantInfo;

import com.threerings.msoy.data.MsoyBodyObject;
import com.threerings.msoy.data.MsoyUserOccupantInfo;

public class MsoyNameLabel extends Sprite
{
    public function MsoyNameLabel (ignoreStatus :Boolean = false)
    {
        _ignoreStatus = ignoreStatus;

        _label = TextFieldUtil.createField("",
            { textColor: 0xFFFFFF, selectable: false, autoSize :TextFieldAutoSize.LEFT,
            outlineColor: 0x000000 });

        // It's ok that we modify this later, as it gets cloned anyway when assigned to the field.
        _label.defaultTextFormat = FORMAT;
        _label.x = 0;
        addChild(_label);
    }

    /**
     * Update the name based on an OccupantInfo.
     */
    public function update (info :OccupantInfo) :void
    {
        setName(info.username.toString());
        setSubscriber((info is MsoyUserOccupantInfo) &&
            MsoyUserOccupantInfo(info).isSubscriber());
        setStatus(info.status);
    }

    /**
     * Set the displayed name.
     */
    public function setName (name :String) :void
    { 
        TextFieldUtil.updateText(_label, name);
    }

    /**
     * Set whether we're displaying a subscriber.
     */
    public function setSubscriber (subscriber :Boolean) :void
    {
        if (subscriber == (_subscriberIcon == null)) {
            if (subscriber) {
                _subscriberIcon = new SUBSCRIBER();
                addChild(_subscriberIcon);
                _label.x = _subscriberIcon.width;
            } else {
                removeChild(_subscriberIcon);
                _subscriberIcon = null;
                _label.x = 0;
            }
        }
    }

    // from NameLabel
    public function setStatus (status :int, italicize :Boolean = false) :void
    {
        if (_ignoreStatus) {
            return;
        }

        switch (status) {
        case OccupantInfo.IDLE:
            _label.textColor = 0x777777;
            break;
        case OccupantInfo.DISCONNECTED:
            _label.textColor = 0x80803C;
            break;
        case MsoyBodyObject.AWAY:
            _label.textColor = 0xFFFF77;
            break;
        default:
            _label.textColor = 0x99BFFF;
        }

        // turn on or off italicizing.
        TextFieldUtil.updateFormat(_label, { italic: italicize });
    }

    protected var _ignoreStatus :Boolean;

    protected var _label :TextField;

    protected var _subscriberIcon :DisplayObject;

    protected static const FORMAT :TextFormat =
        TextFieldUtil.createFormat({ font: "_sans", size: 12, bold: true });

    [Embed(source="../../../../../../rsrc/media/subscriber.gif")]
    protected static const SUBSCRIBER :Class;
}
}