//
// $Id$

package com.threerings.msoy.utils;

import java.applet.Applet;

import com.samskivert.util.Interval;
import com.samskivert.util.RunQueue;

import netscape.javascript.JSObject;

import org.apache.commons.codec.binary.Base64;

public class Base64Sender
{
    public Base64Sender (Applet containingApplet, String targetName)
    {
        this(containingApplet, targetName, "setBytes");
    }

    public Base64Sender (Applet containingApplet, String targetName, String functionName)
    {
        this(containingApplet, targetName, functionName, 81920, 10);
    }

    public Base64Sender (
        Applet containingApplet, String targetName, String functionName,
        int maxChunkSize, int chunksPerSecond)
    {
        _app = containingApplet;
        _targetName = targetName;
//        JSObject win = JSObject.getWindow(containingApplet);
//        JSObject doc = (JSObject) win.getMember("document");
//        _target = (JSObject) doc.getMember(targetName);
//
//        if (_target == null) {
//            throw new IllegalArgumentException("Unable to find target: " + targetName);
//        }

        _funcName = functionName;
        _maxChunkSize = maxChunkSize * 4 / 3; // convert pre-encoded byte chunk size to post-size
        _chunksPerSecond = chunksPerSecond;

        _interval = new Interval(RunQueue.AWT) {
            public void expired () {
                if (!doChunk()) {
                    cancel(); // this interval
                }
            }
        };
    }

    public void sendBytes (byte[] bytes)
    {
//        if (_target == null) {
//            JSObject win = JSObject.getWindow(_app);
//            _target = (JSObject) win.getMember(_targetName);
//            if (_target == null) {
//                System.err.println("Trying to get target from document....");
//                JSObject doc = (JSObject) win.getMember("document");
//                _target = (JSObject) doc.getMember(_targetName);
//            }
//            System.err.println("Got js _target: " + _target);
//        }

        if (_bytes != null) {
            _interval.cancel();
            send(".reset");
        }

        _bytes = Base64.encodeBase64(bytes);
        _position = 0;
        if (doChunk()) {
            _interval.schedule(1000 / _chunksPerSecond, true);
        }
    }

    /**
     * Do a chunk. Return true if there's more to do.
     */
    protected boolean doChunk ()
    {
        int length = Math.min(_bytes.length - _position, _maxChunkSize);
        if (length > 0) {
            if (send(new String(_bytes, _position, length))) {
                _position += length;

            } else {
                // we did not succeed in sending this chunk..
                System.err.println("Did not send. Waiting...");
            }
        }

        if (_position == _bytes.length) {
            // we're done sending
            _bytes = null;
            send(null);
            return false;
        }

        return true; // more to send
    }

    protected boolean send (String s)
    {
        JSObject win = JSObject.getWindow(_app);
        Object resultValue = win.call("setMediaBytes", new Object[] { s });
        // resultValue may be null or a Boolean
//        boolean result = Boolean.TRUE.equals(resultValue);
        boolean result = true; // TODO: FIXME!
        if (result) {
            if (s == null) {
                System.err.println("Sent a null.");
            } else {
                System.err.println("Sent a String (" + s.length() + ")");
            }
        }

//        _target.call(_funcName, new Object[] { s });

        return result;
    }

    protected int _position;

    protected byte[] _bytes;

    protected String _funcName;

    protected JSObject _target;

    protected int _maxChunkSize;

    protected int _chunksPerSecond;

    protected Interval _interval;


    protected String _targetName;
    protected Applet _app;
}
