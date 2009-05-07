//
// $Id$

package client.frame;

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import client.util.FlashClients;

/**
 * A slimmed down vertical layout for use when Whirled is embedded in an iframe on another
 * site. *cough* *cough* Facebook *cough*.
 */
public class FramedLayout extends Layout
{
    @Override // from Layout
    public boolean haveContent ()
    {
        return _content.getWidget() != null;
    }

    @Override // from Layout
    public void setContent (TitleBar bar, Widget content)
    {
        closeContent(false);
        content.setWidth("100%");

        int avail = Window.getClientHeight();
        if (bar != null) {
            avail -= NAVI_HEIGHT;
            _bar.setWidget(bar);
        }
        // if we have a client, adjust its height...
        if (_client.getWidget() != null) {
            _client.setHeight("300px");
            FlashClients.setClientFullHeight(true);
            avail -= 300;
        }
        content.setHeight(avail + "px");

        _content.setWidget(content);
    }

    @Override // from Layout
    public boolean closeContent (boolean restoreClient)
    {
        if (!haveContent()) {
            return false;
        }
        _bar.setWidget(null);
        _content.setWidget(null);
        if (_client.getWidget() != null) {
            _client.setHeight(FlashClients.CLIENT_HEIGHT + "px");
        }
        return true;
    }

    @Override // from Layout
    public WorldClient.PanelProvider getClientProvider ()
    {
        return new WorldClient.PanelProvider() {
            public Panel get () {
                closeClient();
                _client.setHeight(null);
                FlashClients.setClientFullHeight(false);
                return _client;
            }
        };
    }

    @Override // from Layout
    public boolean closeClient ()
    {
        if (_client == null) {
            return false;
        }
        FlashClients.setClientFullHeight(false);
        _client.setHeight(null);
        _client.setWidget(null);
        return true;
    }

    @Override // from Layout
    public void addNoClientIcon ()
    {
        // not supported
    }

    @Override // from Layout
    protected void init (FrameHeader header, ClickHandler onGoHome)
    {
        super.init(header, onGoHome);
        RootPanel.get(PAGE).add(_client = new SimplePanel());
        RootPanel.get(PAGE).add(_bar = new SimplePanel());
        RootPanel.get(PAGE).add(_content = new SimplePanel());
    }

    protected SimplePanel _client, _bar, _content;
}