package com.threerings.msoy.world.data {

import com.threerings.io.ObjectInputStream;
import com.threerings.io.ObjectOutputStream;
import com.threerings.io.TypedArray;

import com.threerings.whirled.data.SceneModel;
import com.threerings.whirled.data.SceneUpdate;

public class ModifyFurniUpdate extends SceneUpdate
{
    public var furniRemoved :TypedArray;

    public var furniAdded :TypedArray;

    public function initialize (
            targetId :int, targetVersion :int, removed :Array , added :Array)
            :void
    {
        init(targetId, targetVersion);

        var furni :FurniData;
        if (removed != null && removed.length > 0) {
            furniRemoved = TypedArray.create(FurniData);
            for each (furni in removed) {
                furniRemoved.push(furni);
            }
        }
        if (added != null && added.length > 0) {
            furniAdded = TypedArray.create(FurniData);
            for each (furni in added) {
                furniAdded.push(furni);
            }
        }
    }

    // documentation inherited
    override public function apply (model :SceneModel) :void
    {
        super.apply(model);

        // cast it to our model type
        var mmodel :MsoySceneModel = (model as MsoySceneModel);

        // remove old furni, add the new
        var furni :FurniData;
        if (furniRemoved != null) {
            for each (furni in furniRemoved) {
                mmodel.removeFurni(furni);
            }
        }
        if (furniAdded != null) {
            for each (furni in furniAdded) {
                mmodel.addFurni(furni);
            }
        }
    }

    // documentation inherited
    override public function writeObject (out :ObjectOutputStream) :void
    {
        super.writeObject(out);

        out.writeObject(furniRemoved);
        out.writeObject(furniAdded);
    }

    // documentation inherited
    override public function readObject (ins :ObjectInputStream) :void
    {
        super.readObject(ins);

        furniRemoved = (ins.readObject() as TypedArray);
        furniAdded = (ins.readObject() as TypedArray);
    }
}
}
