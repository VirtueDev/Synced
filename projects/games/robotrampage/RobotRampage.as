package {

import flash.display.Sprite;
import flash.events.KeyboardEvent;
import flash.external.ExternalInterface;
import flash.ui.Keyboard;

import com.threerings.ezgame.Game;
import com.threerings.ezgame.EZGame;
import com.threerings.ezgame.MessageReceivedEvent;
import com.threerings.ezgame.MessageReceivedListener;
import com.threerings.ezgame.PropertyChangedEvent;
import com.threerings.ezgame.PropertyChangedListener;
import com.threerings.ezgame.StateChangedEvent;
import com.threerings.ezgame.StateChangedListener;

import flash.text.StyleSheet;
import flash.text.TextField;
import flash.text.TextFieldAutoSize;

[SWF(width="640", height="480")]
public class RobotRampage extends Sprite
    implements Game, PropertyChangedListener, StateChangedListener
{
    public function RobotRampage ()
    {
        _robotFactory = new RobotFactory();
    }

    public static function log (msg :String) :void
    {
        ExternalInterface.call("console.debug", msg);
    }

    // from Game
    public function setGameObject (gameObj :EZGame) :void
    {
        _gameObj = gameObj;
        _myIndex = _gameObj.getMyIndex();
        _robotInterval = INITIAL_ROBOT_INTERVAL;

        graphics.clear();
        graphics.beginFill(0xCCCCFF);
        graphics.drawRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        graphics.endFill();

        addMoonBases();

        _gameObj.addEventListener(MessageReceivedEvent.TYPE, msgReceived);

        if (isMaster()) {
            for (var ii :int = 0; ii < INITIAL_ROBOTS; ii++) {
                addRobot(true);
            }
            _gameObj.startTicker("tick", 50);
        }
    }

    // from StateChangedListener
    public function stateChanged (event :StateChangedEvent) :void
    {
    }

    // from PropertyChangedListener
    public function propertyChanged (event :PropertyChangedEvent) :void
    {
    }

    /**
     * Handles MessageReceivedEvents.
     */
    protected function msgReceived (event :MessageReceivedEvent) :void
    {
        var name :String = event.name;
        if (name == "tick") {
            doTick();
        }
    }

    protected function doTick () :void
    {
        var robot :Robot;
        for each (robot in _robots) {
            robot.tick();
        }

        if (isMaster()) {
            _ticksSinceRobot++;

            var tmpRobots :Array;

            // Look for successful attacks
            tmpRobots = [];
            for each (robot in _robots) {
                if (robot.isNearTarget()) {
                    // Kaboom!
                    robot.explode();
                    _explodingRobots.push(robot);
                } else {
                    tmpRobots.push(robot);
                }
            }
            _robots = tmpRobots;

            // Reap finished exploders
            tmpRobots = [];
            for each (robot in _explodingRobots) {
                if (robot.isDoneExploding()) {
                    removeChild(robot);
                } else {
                    tmpRobots.push(robot);
                }
            }
            _explodingRobots = tmpRobots;

            // Add more robots as appropriate
            if (_ticksSinceRobot >= _robotInterval) {
                if (_robots.length < MAX_ROBOTS) {
                    addRobot(true);
                }
            }
        }

        // TODO: Resort our robots render order to match their coordinates
    }


    /**
     * Creates our moon bases.
     */
    public function addMoonBases () : void
    {
        var names :Array = _gameObj.getPlayerNames();
        var count :int = names.length;
        _bases = new Array(count);

        var radius :int = (Math.min(SCREEN_WIDTH, SCREEN_HEIGHT) / 2) - 
            MoonBase.MOON_BASE_RADIUS;

        for (var ii :int = 0; ii < count; ii++) {
            // Make new base
            var base :MoonBase = new MoonBase(names[ii], ii);
            _bases[ii] = base;
            var angle :Number = ii * 2 * Math.PI / count;

            // Place it someplace sane-ish
            /* FIXME: This needs some fancier brains since we want to throw 
             * some perspective on this, so the area immediately in front of 
             * you is bigger.
             * Plus, with that, we always want ourselves to be at the bottom, 
             * viewing the kite (or triangle, or...single line) shaped playfield
             * from that perspective.
             * For now though, meh.
             */
            base.x = (SCREEN_WIDTH/2) - (radius * Math.sin(angle));
            base.y = (SCREEN_HEIGHT/2) + (radius * Math.cos(angle));

            addChild(base);
        }
    }

    /**
     * Adds a new robot with random attributes.
     */
    protected function addRobot (pickTarget :Boolean=false) : void
    {
        var robot :Robot = new Robot(_robotFactory);

        robot.x = (width/2) + 
            (Math.random() * 2 * CENTER_RADIUS) - CENTER_RADIUS;
        robot.y = (height/2) + 
            (Math.random() * 2 * CENTER_RADIUS) - CENTER_RADIUS;
        _robots.push(robot);


        if (pickTarget) {
            var bases :Array = [];
            var base :MoonBase;

            for each (base in _bases) {
                if (!base.isDestroyed()) {
                    bases.push(base);
                }
            }

            if (bases.length > 0) {
                setRobotTarget(robot, bases[int(Math.random() * bases.length)]);
            } else {
                // All bases are destroyed, but we're still adding robots?
            }
        }

        _ticksSinceRobot = 0;
        addChild(robot);
    }

    /**
     * Turns a robot's steely glare upon a moon base.
     */
    protected function setRobotTarget (robot :Robot, base :MoonBase) :void
    {
        robot.setTarget(base);
    }

    /** 
     * Returns whether we're serving as the master, forcing our will on all
     * the other clients with an iron fist.
     */
    protected function isMaster () :Boolean
    {
        return _myIndex == 0;
    }

    /** Our game object. */
    protected var _gameObj :EZGame;

    /** The index of this particular player. */
    protected var _myIndex :int;

    /** An array of moonbases. */
    protected var _bases :Array;

    /** Number of ticks before we should add another robot. */
    protected var _robotInterval :int;

    /** Number of ticks since we last added a robot. */
    protected var _ticksSinceRobot :int = 0;

    /** An array of robots. */
    protected var _robots :Array = [];

    /** An array of exploding robots. */
    protected var _explodingRobots :Array = [];

    /** A factory for building robots. */
    protected var _robotFactory :RobotFactory;

    /** Width of our play area. */
    protected static const SCREEN_WIDTH :int = 640;

    /** Height of our play area. */
    protected static const SCREEN_HEIGHT :int = 480;

    /** The radius of the circle at the middle of our screen for new robots. */
    protected static const CENTER_RADIUS :int = 25;

    /** Initial robot interval. */
    protected static const INITIAL_ROBOT_INTERVAL :int = 50;

    /** Initial number of robots. */
    protected static const INITIAL_ROBOTS :int = 5;

    /** Maximum number of robots to have at any given time. */
    /* FIXME: This should probably vary based on the number of players at a 
     * time. And maybe, grow as the game goes on... By raising the spawn speed
     * and the max robots allowed, it could get pretty psycho and force the
     * game to end. Yay for increasing difficulty.
     */
    protected static const MAX_ROBOTS :int = 20;

}
}
