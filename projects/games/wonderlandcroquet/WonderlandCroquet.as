package {

import flash.display.Bitmap;
import flash.display.Graphics;
import flash.display.Sprite;

import flash.events.MouseEvent;
import flash.events.Event;

import com.threerings.ezgame.EZGameControl;
import com.threerings.ezgame.PropertyChangedEvent;
import com.threerings.ezgame.PropertyChangedEvent;
import com.threerings.ezgame.PropertyChangedListener;
import com.threerings.ezgame.StateChangedEvent;
import com.threerings.ezgame.StateChangedListener;

import mx.core.*;
import mx.utils.ObjectUtil;

import org.cove.ape.*;

[SWF(width="750", height="508")]
public class WonderlandCroquet extends Sprite
    implements PropertyChangedListener, StateChangedListener
{
    /** Our game control object. */
    public var gameCtrl :EZGameControl;

    /** Our map. */
    public var map :WonderlandMap;

    /** The player index for the local player. */
    public var myIdx :int;

    public function WonderlandCroquet ()
    {
        gameCtrl = new EZGameControl(this);
        gameCtrl.registerListener(this);

        _spr = new Sprite();
        _ballLayer = new Sprite();
        addEventListener(Event.ENTER_FRAME, tick);

        addChild(_spr);

        // TODO: support better map loading, choice at table time,
        // or maybe just pick one randomly
        //map = new MapBasic();
        map = new MapFancy();
        _spr.addChild(map.background);
        map.background.addEventListener(MouseEvent.MOUSE_DOWN, mouseDown);
        map.background.addEventListener(MouseEvent.MOUSE_UP, mouseUp);

        _spr.addChild(_ballLayer);

        APEngine.init(1/3);
        APEngine.defaultContainer = this;

        for each (var particle :AbstractParticle in map.particles) {
            APEngine.addParticle(particle);
        }

        _spr.addChild(map.foreground);

        _scroller = new WonderlandScroller(_spr);
        addChild(_scroller);
        _scroller.x = _scroller.width / 2 + 5;
        _scroller.y = _scroller.height / 2 + 5;

        
        _status = new WonderlandStatus(_spr, gameCtrl);
        addChild(_status);

        addEventListener(Event.ENTER_FRAME, firstFrameSetup);
    }

    /**
     * Sets up a couple of things that need to wait until the universe has somewhat
     * settled down.
     */
    protected function firstFrameSetup (event :Event) :void
    {
        removeEventListener(Event.ENTER_FRAME, firstFrameSetup);

        stage.addEventListener(flash.events.Event.RESIZE, stageResize);

        positionStatus();
    }

    /**
     * Moves the status display to the bottom right of our current window.
     */
    protected function positionStatus () :void
    {
        _status.x = stage.stageWidth - 50;
        _status.y = stage.stageHeight - 75;
    }

    protected function stageResize (event :Event) :void
    {
        positionStatus();
    }

    protected function mouseDown (event :MouseEvent) :void
    {
        _spr.startDrag();
    }

    protected function mouseUp (event :MouseEvent) :void
    {
        _spr.stopDrag();
    }

    /**
     * Add some random balls.
     */
    protected function addRandomBalls () :void
    {
        for (var ii: int = 0; ii < 6; ii++) {
            var r :Number = Math.random() * (100 - Ball.RADIUS);
            var angle :Number = Math.random() * 2 * Math.PI;

            var ball: BallParticle = new BallParticle(
                map.startPoint.x + (Math.cos(angle) * r),
                map.startPoint.y + (Math.sin(angle) * r),
                Ball.RADIUS, ii, false);

            APEngine.addParticle(ball);
            _ballLayer.addChild(ball.ball);
        }
    }

    protected function tick (evt :Event) :void
    {
        var particle :AbstractParticle;
        var particles :Array = APEngine.getAll();

        var doneMoving :Boolean = true;

        for each (particle in particles) {
            if (particle is BallParticle) {
                map.applyModifierForce(BallParticle(particle));
            }
        }

        APEngine.step();

        for each (particle in particles) {
            if (particle is BallParticle) {
                if (BallParticle(particle).tick()) {
                    doneMoving = false;
                }
            }
        }

        if (_haveMoved && doneMoving && gameCtrl.isMyTurn()) {
            if (_moveAgain) {
                gameCtrl.localChat("Go again!");
                startTurn();
            } else {
                gameCtrl.endTurn();
            }
        }
    }

    // from StateChangedListener
    public function stateChanged (event :StateChangedEvent) :void
    {
        if (event.type == StateChangedEvent.TURN_CHANGED) {
            var firstTurn :Boolean = false;

            if (_balls == null) {
                firstTurn = true;
                _balls = [];
                _wickets = [];
            }

            if (gameCtrl.isMyTurn()) {
                if (firstTurn) {
                    // FIXME: I'm not quite happy with this, but if I just set it, it doesn't appear
                    // to have taken effect by the time it's my turn and I need to actually add a
                    // ball
                    gameCtrl.setImmediate("balls", _balls);
                    gameCtrl.setImmediate("wickets", _wickets);
                }
                startTurn();
            }
        } else if (event.type == StateChangedEvent.GAME_STARTED) {
            gameCtrl.localChat("Wonderland Croquet!");

            myIdx = gameCtrl.seating.getMyPosition();

        } else if (event.type == StateChangedEvent.GAME_ENDED) {
            gameCtrl.localChat("Off with your head!");

        }
    }

    // from PropertyChangedListener
    public function propertyChanged (event :PropertyChangedEvent) :void
    {
        var name :String = event.name;
        var index :int;
        if (name == "balls") {
            index = event.index;
            if (index != -1 && _balls[index] == null) {
                _balls[index] = new BallParticle(event.newValue[0], event.newValue[1],
                    Ball.RADIUS, index, false);
                _balls[index].wc = this;
                    
                APEngine.addParticle(_balls[index]);
                _ballLayer.addChild(_balls[index].ball);

                if (index == myIdx) {
                    _myBall = _balls[index];
                }

            }

        } else if (name == "lastHit") {
            index = event.newValue[0];
            var x :Number = event.newValue[1];
            var y :Number = event.newValue[2];

            BallParticle(_balls[index]).addHitForce(x, y);

            if (gameCtrl.isMyTurn()) {
                _haveMoved = true;
            }

        } else if (name == "wickets") {
            index = event.index;
            var wicket :int = event.newValue as int;

            _wickets[index] = wicket;
            _status.targetWicket(index, wicket);

        } else {
            gameCtrl.localChat("unhandled prop change: " + name);
        }
    }

    /**
     * Notice that we passed a wicket.
     */
    public function passedWicket () :void
    {
        _moveAgain = true;
        _wickets[myIdx]++;
        if (_wickets[myIdx] >= map.wickets.length) {
            // That was the last one. Yay.
            gameCtrl.sendChat("ZOMG! " + gameCtrl.getOccupantName(gameCtrl.getMyId()) + 
                              " is teh winnar!!");
            gameCtrl.endGame(gameCtrl.getMyId());

        } else {
            gameCtrl.set("wickets", _wickets[myIdx], myIdx);
        }
    }

    /**
     * Pans the view to the specified coordinate.
     */
    protected function panTo (x :Number, y: Number) :void
    {
        // FIXME: Animate the pan to here, don't just snap
        _spr.x = - (x - this.stage.stageWidth/2);
        _spr.y = - (y - this.stage.stageHeight/2);
    }

    /** 
     * Sets things up and starts our own turn.
     */
    protected function startTurn () :void
    {
        gameCtrl.localChat("My turn!");
        var coord :Array = [];
        _moveAgain = false;

        if(_myBall == null) {
            // It's the first time I've gone, so add my ball at the start

            coord = [map.startPoint.x, map.startPoint.y]; 
            gameCtrl.set("balls", coord, myIdx);

            // and target the first wicket
            gameCtrl.set("wickets", 0, myIdx);
        } else {
            coord = [_balls[myIdx].px, _balls[myIdx].py];
        }

        _haveMoved = false;
        panTo(coord[0], coord[1]);
    }


    protected var _haveMoved :Boolean;

    protected var _scroller :WonderlandScroller;

    protected var _status :WonderlandStatus;

    protected var _spr :Sprite;

    protected var _ballLayer :Sprite;

    protected var _board :WonderlandBoard;

    protected var _wickets :Array;

    protected var _balls :Array;

    protected var _myBall :BallParticle;

    protected var _moveAgain :Boolean;

}
}
