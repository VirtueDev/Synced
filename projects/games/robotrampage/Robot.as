package {

import flash.display.Sprite;
import flash.display.Scene;

import flash.events.MouseEvent;

import mx.core.BitmapAsset;
import mx.core.MovieClipAsset;
import mx.controls.Label;

import mx.effects.Glow;

import flash.text.TextField;
import flash.text.TextFieldAutoSize;

public class Robot extends Sprite
{

    public function Robot (robotFactory :RobotFactory)
    {
        _robotFactory = robotFactory;

        _style = new Array(PART_COUNT);
        _parts = new Array(PART_COUNT);
        for (var ii :int = 0; ii < PART_COUNT; ii++) {
            _parts[ii] = null;
        }

        _target = null;

        randomizeRobot();

        addEventListener(MouseEvent.MOUSE_OVER, mouseOver);
        addEventListener(MouseEvent.MOUSE_OUT, mouseOut);
        addEventListener(MouseEvent.CLICK, mouseClick);
    }

    /**
     * Point our robot at an unsuspecting moon base.
     */
    public function setTarget (base :MoonBase) : void
    {
        _target = base;
    }

    /**
     * A beat of or cold, robotic heart.
     */
    public function tick () :void
    {
        if (_target != null && _target.isDestroyed()) {
            setTarget(null);
        }

        if (_target == null) {
            paceIdly();
        } else {
            walkTowardsTarget();

            // TODO: Try and fall into rank with my robot bretheren
        }
    }

    public function isNearTarget () :Boolean
    {
        if (_target == null) {
            return false;
        }

        var dx :int = Math.abs(x - _target.x);
        var dy :int = Math.abs(y - _target.y);

        if ((dx*dx + dy*dy) <= DESTRUCTION_RADIUS_SQUARED) {
            return true;
        }

        return false;
    }

    /**
     * Makes the robot walk towards its target.
     */
    protected function walkTowardsTarget () :void
    {
        /* FIXME: Walk less stupidly. Although I suppose they ARE dumb robots,
         * and they DO look pretty amusing jittering along their path....
         */
        if (_target.x < x) {
            x--;
        } else if (_target.x > x) {
            x++;
        }

        if (_target.y < y) {
            y--;
        } else if (_target.y > y) {
            y++;
        }
    }

    /**
     * Makes the robot pace around in no particular direction, because it has
     * no place to go.
     */
    protected function paceIdly () :void 
    {
        /* TODO: Maybe some fancier pacing than total random jitter.
         * But then again, it's pretty funny stuff
         */
        switch (int(Math.random() * 3)) {
        case 0:
            x++;
            break;
        case 1:
            x--;
            break;
        default:
            // Do nothing
        }

        switch (int(Math.random() * 3)) {
        case 0:
            y++;
            break;
        case 1:
            y--;
            break;
        default:
            // Do nothing
        }
    }
    
    /**
     * Randomizes the specified aspect of the robot, or if part is
     * omitted, the whole body and color.
     */
    public function randomizeRobot (part :int = -1) :void
    {
        var parts :Array;

        if (part == -1) {
            parts = [PART_COLOR, PART_HEAD, PART_TORSO, PART_LEGS];
        } else {
            parts = [part];
        }

        for each (part in parts) {
            // FIXME: Maybe enforce that it really changes?
            _style[part] = int(Math.random() * STYLE_OPTIONS);
        }

        if (parts[0] == PART_COLOR) {
            // If color changed, change the whole body
            parts = [PART_HEAD, PART_TORSO, PART_LEGS];
        }

        for each (part in parts) {
            updatePart(part);
        }
    }

    /**
     * Updates the specified part of the robot, swapping to the appropriate 
     * artwork.
     */
    public function updatePart (partType :int) :void
    {
        if (partType == PART_COLOR) {
            return;
        }

        var color :int = _style[PART_COLOR];

        if (_parts[partType] != null) {
            removeChild(_parts[partType]);
        }

        _parts[partType] =
            _robotFactory.getPart(color, partType, _style[partType]);
        _parts[partType].x = - (_parts[partType].width / 2 );
        _parts[partType].y = - _parts[partType].height;
        addChild(_parts[partType]);

        // TODO: cut to the transition animation for this part
    }

    /**
     * Hacky debug function to print some text on the screen.
     */
    protected var _labelY :int = 0;
    protected function makeLabel (text :String) :void
    {
        var label :TextField = new TextField();
        label.autoSize = TextFieldAutoSize.LEFT;
        label.background = true;
        label.selectable = false;
        label.text = text;
        label.x = 0;
        label.y = _labelY;
        label.width = label.textWidth;
        addChild(label);

        _labelY += label.height;
    }

    /**
     * KABOOOOOOOM!
     */
    public function explode () :void
    {
        if (_target == null) {
            // Huh? We were told to explode, but we don't have a target?
            return;
        }

        _target.takeDamage();
    }

    /**
     * Returns whether or not we're done doing our fancy little explosion
     * animation and can be shuffled off this mortal coil.
     */
    public function isDoneExploding () :Boolean
    {
        return true;
    }

    /**
     * Handles things when the mouse comes over the robot.
     */
    protected function mouseOver (event :MouseEvent) :void
    {
        setGlow(true);
    }

    /**
     * Handles things when the mouse leaves from over the robot.
     */
    protected function mouseOut (event :MouseEvent) :void
    {
        setGlow(false);
    }

    /**
     * Handles mouse clicks on the robot.
     */
    protected function mouseClick (event :MouseEvent) :void
    {
        //randomizeRobot();
        randomizeRobot(int(Math.random() * PART_COUNT));
    }

    /**
     * Turn on or off the glow surrounding this robot.
     */
    protected function setGlow (doGlow :Boolean) :void
    {
        // if things are already in the proper state, do nothing
        if (doGlow == (_glow != null)) {
            return;
        }

        // otherwise, enable or disable the glow
        if (doGlow) {
            _glow = new Glow(this);
            _glow.alphaFrom = 0;
            _glow.alphaTo = 1;
            _glow.blurXFrom = 0;
            _glow.blurXTo = GLOW_RADIUS;
            _glow.blurYFrom = 0;
            _glow.blurYTo = GLOW_RADIUS;
            _glow.color = GLOW_COLOR_HOVER;
            _glow.duration = 200;
            _glow.play();

        } else {
            _glow.end();
            _glow = null;

            // remove the GlowFilter that is added
            filters = new Array();
        }
    }

    /** The player this robot is currently targetting, or -1 for none. */
    protected var _target :MoonBase;

    /** An array of ints describing this robot's style. */
    protected var _style :Array;

    /** Our robot's parts. */
    protected var _parts :Array;

    /** A factory for building robots. */
    protected var _robotFactory :RobotFactory;

    /** Identifier for the color of our robot. */
    protected static const PART_COLOR :int = 0;

    /** Identifier for the robot's head. */
    protected static const PART_HEAD :int = 1;

    /** Identifier for the robot's torso. */
    protected static const PART_TORSO :int = 2;

    /** Identifier for the robot's legs. */
    protected static const PART_LEGS :int = 3;

    /** How many different parts make up our robot. */
    protected static const PART_COUNT :int = 4;

    /** How many options do we have for each thing in our robot's style. */
    protected static const STYLE_OPTIONS :int = 3;

    /** The glow effect used for mouse hovering. */
    protected var _glow :Glow;

    /** Radius of the glow that surrounds a robot.*/
    protected static const GLOW_RADIUS :int = 10;

    /** Color to glow when we're mousing over a robot. */
    protected static const GLOW_COLOR_HOVER :uint = 0xffffff;

    /** Color to glow when we've selected a robot for our set. */
    protected static const GLOW_COLOR_SELECTED :uint = 0x000000;

    /** How close we must be to a base to destroy it, squared. */
    protected static const DESTRUCTION_RADIUS_SQUARED :int = 25;
}
}
