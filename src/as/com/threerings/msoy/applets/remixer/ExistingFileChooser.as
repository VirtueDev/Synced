//
// $Id$

package com.threerings.msoy.applets.remixer {

import flash.events.Event;

import flash.utils.ByteArray;

import mx.controls.ButtonBar;
import mx.controls.Label;

import mx.containers.Grid;
import mx.containers.TitleWindow;
import mx.containers.VBox;

import mx.core.Application;
import mx.core.UIComponent;

import mx.events.ListEvent;

import mx.managers.PopUpManager;

import com.threerings.util.ValueEvent;

import com.threerings.flex.CommandButton;
import com.threerings.flex.GridUtil;

import com.whirled.remix.data.EditableDataPack;

import com.threerings.msoy.applets.image.ImagePreview;

public class ExistingFileChooser extends TitleWindow
{
    public function ExistingFileChooser (pack :EditableDataPack, filenames :Array)
    {
        title = "Choose existing file";

        var box :VBox = new VBox();
        addChild(box);

        var grid :Grid = new Grid();
        grid.maxHeight = 400;
        box.addChild(grid);

        for each (var filename :String in filenames) {
            addFile(grid, pack, filename);
        }

        var bar :ButtonBar = new ButtonBar();
        bar.addChild(new CommandButton("Cancel", close));
        box.addChild(bar);

        PopUpManager.addPopUp(this, Application(Application.application), true);
        // fuck centering, let's just be in the upper-left, since it resizes
        // as each image is added
        x = 0;
        y = 0;
    }

    protected function addFile (grid :Grid, pack :EditableDataPack, filename :String) :void
    {
        var bytes :ByteArray = pack.getFileByFilename(filename);

        var image :ImagePreview = new ImagePreview();
        image.maxWidth = 100;
        image.maxHeight = 100;
        image.setImage(bytes);

        // we wrap the array arg of a command button in another array
        GridUtil.addRow(grid, new CommandButton(filename, close, [ [ filename, bytes ] ]), image);
    }

    protected function close (returnValue :Object = null) :void
    {
        PopUpManager.removePopUp(this);
        dispatchEvent(new ValueEvent(Event.COMPLETE, returnValue));
    }
}
}
