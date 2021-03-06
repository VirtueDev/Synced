//
// $Id$

package client.shell;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.LinkElement;
import com.google.gwt.dom.client.NodeList;

import client.util.events.FlashEvents;
import client.util.events.ThemeChangeEvent;

/**
 * Allows index.html files for the top-level frame and pages to refer to stylesheet URLs on
 * the form /themed/css/foo.css?themeId=0 while we inject the user's theme as it loads/changes.
 *
 * NOTE WELL: Whirled falls apart if we ever reload the styles directly governing the layout
 * of the flash client. For this reason, gwt.css (which has a display: block style for various
 * block level elements including 'embed' and 'object') is NOT among the themed CSS files.
 */
public class ThemedStylesheets
{
    public ThemedStylesheets ()
    {
        // listen for theme changes (one which will likely be triggered by the didLogon below)
        FlashEvents.addListener(new ThemeChangeEvent.Listener() {
            public void themeChanged (ThemeChangeEvent event) {
                if (event.getGroupId() != _themeId) {
                    _themeId = event.getGroupId();
                    inject();
                }
            }
        });
    }

    public int getThemeId ()
    {
        return _themeId;
    }

    /**
     * Injects the src tags of all known scripts using the given application id where appropriate.
     */
    protected void inject ()
    {
        NodeList<Element> heads = Document.get().getElementsByTagName("head");
        if (heads.getLength() == 0) {
            return;
        }
        NodeList<Element> links = heads.getItem(0).getElementsByTagName("link");
        for (int ii = links.getLength()-1; ii >= 0; ii --) {
            LinkElement link = LinkElement.as(links.getItem(ii));
            if ("stylesheet".equalsIgnoreCase(link.getRel())) {
                String href = link.getHref();
                int ix = href.indexOf("?themeId=");
                if (ix > 0) {
                    href = (href.substring(0, ix) + "?themeId=" + _themeId);
                    link.setHref(href);
                }
            }
        }
    }

    protected int _themeId;
}
