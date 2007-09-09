//
// $Id$

package com.threerings.msoy.swiftly.client;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

import com.samskivert.swing.GroupLayout;
import com.samskivert.swing.HGroupLayout;
import com.samskivert.swing.VGroupLayout;
import com.samskivert.util.StringUtil;
import com.threerings.crowd.client.OccupantAdapter;
import com.threerings.crowd.client.PlacePanel;
import com.threerings.crowd.data.OccupantInfo;
import com.threerings.crowd.data.PlaceObject;
import com.threerings.micasa.client.ChatPanel;
import com.threerings.micasa.client.OccupantList;
import com.threerings.msoy.item.data.all.MediaDesc;
import com.threerings.msoy.swiftly.data.BuildResult;
import com.threerings.msoy.swiftly.data.PathElement;
import com.threerings.msoy.swiftly.data.ProjectRoomObject;
import com.threerings.msoy.swiftly.data.SwiftlyCodes;
import com.threerings.msoy.swiftly.data.SwiftlyDocument;
import com.threerings.msoy.swiftly.data.SwiftlyImageDocument;
import com.threerings.msoy.swiftly.data.SwiftlyTextDocument;
import com.threerings.msoy.swiftly.util.SwiftlyContext;
import com.threerings.presents.client.InvocationService.ConfirmListener;
import com.threerings.presents.client.InvocationService.InvocationListener;
import com.threerings.presents.dobj.AttributeChangeListener;
import com.threerings.presents.dobj.AttributeChangedEvent;
import com.threerings.presents.dobj.ChangeListener;
import com.threerings.presents.dobj.EntryAddedEvent;
import com.threerings.presents.dobj.EntryRemovedEvent;
import com.threerings.presents.dobj.EntryUpdatedEvent;
import com.threerings.presents.dobj.MessageEvent;
import com.threerings.presents.dobj.MessageListener;
import com.threerings.presents.dobj.SetListener;
import com.threerings.util.MessageBundle;

public class SwiftlyEditor extends PlacePanel
    implements SwiftlyDocumentEditor, AttributeChangeListener, MessageListener, SetListener
{
    public SwiftlyEditor (ProjectRoomController ctrl, SwiftlyContext ctx)
    {
        super(ctrl);
        _ctx = ctx;
        _ctrl = ctrl;
        _msgs = _ctx.getMessageManager().getBundle(SwiftlyCodes.SWIFTLY_MSGS);

        // setup the list of access and build result listeners
        _accessListeners = new HashSet<AccessControlListener>();
        _buildResultListeners = new HashSet<BuildResultListener>();

        setLayout(new VGroupLayout(
                      GroupLayout.STRETCH, GroupLayout.STRETCH, 5, GroupLayout.TOP));
        // let's not jam ourselves up against the edges of the window
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // add our toolbar
        add(_toolbar = new EditorToolBar(ctrl, _ctx, this), GroupLayout.FIXED);

        // set up the left pane: the tabbed editor
        _editorTabs = new TabbedEditor(_ctx, this);
        _editorTabs.setMinimumSize(new Dimension(400, 400));

        // set up the right pane: project panel and chat
        _projectPanel = new ProjectPanel(_ctx, this);
        _projectPanel.setMinimumSize(new Dimension(200, 200));

        JPanel chatPanel = new JPanel(
            new HGroupLayout(GroupLayout.STRETCH, GroupLayout.STRETCH, 5, GroupLayout.LEFT));
        chatPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        OccupantList ol;
        chatPanel.add(ol = new OccupantList(_ctx), GroupLayout.FIXED);
        ol.setPreferredSize(new Dimension(50, 0));
        chatPanel.add(new ChatPanel(_ctx, false));
        chatPanel.setMinimumSize(new Dimension(0, 0));
        chatPanel.setPreferredSize(new Dimension(200, 200));

        _rightPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, _projectPanel, chatPanel);
        _rightPane.setOneTouchExpandable(true);
        // give the top pane any extra space
        _rightPane.setResizeWeight(1);

        // add an OccupantObserver to reveal the chat panel if someone joins
        _ctx.getOccupantDirector().addOccupantObserver(new OccupantAdapter() {
            @Override // from OccupantAdapter
            public void occupantEntered (OccupantInfo info) {
                showChatPanel();
            }
        });

        // add the console window which starts hidden
        _console = new Console(_ctx, this);

        _contentPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, _editorTabs, _rightPane);
        _contentPane.setOneTouchExpandable(true);
        // give the left pane any extra space
        _contentPane.setResizeWeight(1);
        add(_contentPane);

        initFileTypes();
    }

    @Override // from Component
    public void doLayout ()
    {
        super.doLayout();

        // set up our divider location when we are first laid out
        if (getWidth() != 0 && _contentPane.getLastDividerLocation() == 0) {
            _contentPane.resetToPreferredSizes();

            // start with the chat panel hidden if no one else is in the room
            if (_roomObj.occupants.size() > 1) {
                showChatPanel();
            } else {
                hideChatPanel();
            }
        }
    }

    /** Requests that the given path element be opened in the editor. */
    public void openPathElement (final PathElement pathElement)
    {
        // If the tab already exists, then select it and be done.
        if (_editorTabs.selectTab(pathElement) != null) {
            return;
        }

        // otherwise ask that the element be opened at the starting position
        openPathElement(pathElement, 1, 1, false);
    }

    /**
      * Requests that the given path element be opened in the editor, at the supplied
      * row and column.
      * @param highlight indicates whether the new location should be highlighted briefly
      */
    public void openPathElement (final PathElement pathElement, final int row, final int column,
                                 final boolean highlight)
    {
        // If the tab already exists, then select it and tell it to move to row and column.
        TabbedEditorComponent tab;
        if ((tab = _editorTabs.selectTab(pathElement)) != null) {
            tab.gotoLocation(row, column, highlight);
            return;
        }

        // If the document is already in the dset, load that.
        SwiftlyDocument doc = _roomObj.getDocument(pathElement);
        if (doc != null) {
            doc.loadInEditor(this, row, column, highlight);
            return;
        }

        // Otherwise load the document from the backend.
        _roomObj.service.loadDocument(_ctx.getClient(), pathElement, new ConfirmListener() {
            public void requestProcessed () {
                SwiftlyDocument doc = _roomObj.getDocument(pathElement);
                if (doc == null) {
                    _ctx.showErrorMessage(_msgs.get("e.load_document_failed"));
                } else {
                    doc.loadInEditor(SwiftlyEditor.this, row, column, highlight);
                }
            }
            public void requestFailed (String reason) {
                _ctx.showErrorMessage(_msgs.xlate(reason));
            }
        });
    }

    /** Requests that the given path element be closed, if open, in the editor. */
    public void closePathElement (PathElement element)
    {
        _editorTabs.closePathElementTab(element);
    }

    // from SwiftlyDocumentEditor
    public void editTextDocument (SwiftlyTextDocument document, int row, int column,
                                  boolean highlight)
    {
        PathElement pathElement = document.getPathElement();
        SwiftlyTextPane textPane = new SwiftlyTextPane(_ctx, this, document);
        TabbedEditorScroller scroller = new TabbedEditorScroller(textPane, pathElement);
        EditorGutter gutter = new EditorGutter(this, textPane, scroller);
        scroller.setRowHeaderView(gutter);

        // disable editing if the user does not have write access on the project
        if (_roomObj.hasWriteAccess(_ctx.getMemberObject().memberName)) {
            textPane.writeAccessGranted();
        } else {
            textPane.readOnlyAccessGranted();
        }

        // if we have a current build result, inform the gutter
        if (_roomObj.results.containsKey(_ctx.getMemberObject().memberName)) {
            gutter.gotResult(_roomObj.results.get(_ctx.getMemberObject().memberName));
        }

        // add the tab
        _editorTabs.addEditorTab(scroller, pathElement);

        // goto the starting location
        textPane.gotoLocation(row, column, highlight);

        // add the text pane as a listener for document set updates and documentupdate events
        _roomObj.addListener(textPane);
    }

    // from SwiftlyDocumentEditor
    public void editImageDocument (SwiftlyImageDocument document)
    {
        PathElement pathElement = document.getPathElement();
        SwiftlyImagePane imagePane = new SwiftlyImagePane(_ctx, document);
        TabbedEditorScroller scroller = new TabbedEditorScroller(imagePane, pathElement);

        // add the tab
        _editorTabs.addEditorTab(scroller, pathElement);

        // add the image pane as a set listener for document updates
        _roomObj.addListener(imagePane);
    }

    // from SwiftlyDocumentEditor
    public List<FileTypes> getCreateableFileTypes ()
    {
        return _createableFileTypes;
    }

    /** Should be called when a path element is changed to update the tab title if open */
    public void pathElementChanged (PathElement pathElement)
    {
        _editorTabs.updateTabTitleAt(pathElement);
    }

    /** Should be called when a tab is removed to remove any listeners */
    public void tabRemoved (TabbedEditorComponent tab)
    {
        // TODO: remove when the textpane is no longer the document listener
        Component comp = tab.getEditingComponent();
        if (comp instanceof ChangeListener) {
            _roomObj.removeListener((ChangeListener)comp);
        }
    }

    /** Should be called when an AccessControlListener component is added to the UI. */
    public void addAccessControlListener (AccessControlListener listener)
    {
        _accessListeners.add(listener);
    }

    /** Should be called when an AccessControlListener component is removed from the UI. */
    public void removeAccessControlListener (AccessControlListener listener)
    {
        _accessListeners.remove(listener);
    }

    /** Should be called when an BuildResultListener component is added to the UI. */
    public void addBuildResultListener (BuildResultListener listener)
    {
        _buildResultListeners.add(listener);
    }

    /** Should be called when an BuildResultListener component is removed from the UI. */
    public void removeBuildResultListener (BuildResultListener listener)
    {
        _buildResultListeners.remove(listener);
    }

    public AbstractAction createCloseCurrentTabAction ()
    {
        return _editorTabs.createCloseCurrentTabAction();
    }

    public Action createShowConsoleAction ()
    {
        return new AbstractAction(_msgs.get("m.action.show_console")) {
            public void actionPerformed (ActionEvent e) {
                _console.setVisible(true);
            }
        };
    }

    /**
     * Sends a message to the server reporting that the given document element should have its
     * text replaced with the supplied string.
     */
    public void updateDocument (int elementId, String text)
    {
        _roomObj.service.updateDocument(_ctx.getClient(), elementId, text,
            new InvocationListener () {
            public void requestFailed (String reason)
            {
                _ctx.showErrorMessage(_msgs.get(reason));
            }
        });
    }

    /**
     * Shows a modal, internal frame dialog prompting the user to name a {@link PathElement}
     * @param pathElementType the type of {@link PathElement} to name
     * @return the name of the path element. null if the user clicked cancel
     */
     // TODO: this is only being used to name directories. Consider simplifying
    public String showSelectPathElementNameDialog (PathElement.Type pathElementType)
    {
        return JOptionPane.showInternalInputDialog(
            this, _msgs.get("m.dialog.select_name." + pathElementType));
    }

    /**
     * Shows a modal, external frame dialog prompting the user to name a {@link PathElement.FILE}
     * and select the mime type for this file.
     * @param parentElement the PathElement that will be the parent of the returned PathElement
     * @return the new path element. null if the user clicked cancel
     */
    public CreateFileDialog showCreateFileDialog (PathElement parentElement)
    {
        CreateFileDialog dialog = new CreateFileDialog(this, _projectPanel, _msgs);
        // return null if the user hit cancelled or did not set a file name
        if (dialog.wasCancelled() || StringUtil.isBlank(dialog.getName())) {
            return null;
        }
        return dialog;
    }

    /**
     * Shows a modal, internal frame dialog asking for user confirmation.
     * Returns true if the user clicked Yes, false if they clicked No.
     */
    public boolean showConfirmDialog (String message)
    {
        int response = JOptionPane.showInternalConfirmDialog(
            this, message, _msgs.get("m.dialog.confirm.title"), JOptionPane.YES_NO_OPTION);
        return response == JOptionPane.YES_OPTION;
    }

    @Override // from PlacePanel
    public void willEnterPlace (PlaceObject plobj)
    {
        _roomObj = (ProjectRoomObject)plobj;
        _roomObj.addListener(this);

        // Raise any documents from the dead, re-binding transient
        // instance variables.
        for (SwiftlyDocument document : _roomObj.documents) {
            document.lazarus(_roomObj.pathElements);
        }

        // load the resolved project into the project panel
        _projectPanel.setProject(_roomObj);
        updateEditorAccess();

        // set the room object in the console now that it is available
        _console.setRoomObject(_roomObj);
    }

    @Override // from PlacePanel
    public void didLeavePlace (PlaceObject plobj)
    {
        if (_roomObj != null) {
            _roomObj.removeListener(this);
            _roomObj = null;
        }

        // destroy the console window
        _console.dispose();

        // TODO: shutdown the project panel?
    }

    // from MessageListener
    public void messageReceived (MessageEvent event)
    {
        if (event.getName().equals(ProjectRoomObject.ACCESS_CONTROL_CHANGE)) {
            updateEditorAccess();
        }
    }

    // from AttributeChangeListener
    public void attributeChanged (AttributeChangedEvent event)
    {
        // the project has been loaded or changed.
        if (event.getName().equals(ProjectRoomObject.PROJECT)) {
            // nada
        }
    }

    // from interface SetListener
    public void entryAdded (EntryAddedEvent event)
    {
        if (event.getName().equals(ProjectRoomObject.DOCUMENTS)) {
            final SwiftlyDocument element = (SwiftlyDocument)event.getEntry();
            // Re-bind transient instance variables
            element.lazarus(_roomObj.pathElements);

        } else if (event.getName().equals(ProjectRoomObject.RESULTS)) {
            final BuildResult result = (BuildResult)event.getEntry();
            if (result.getMember().equals(_ctx.getMemberObject().memberName)) {
                displayBuildResult(result);
            }
        }
    }

    // from interface SetListener
    public void entryUpdated (EntryUpdatedEvent event)
    {
        if (event.getName().equals(ProjectRoomObject.DOCUMENTS)) {
            final SwiftlyDocument element = (SwiftlyDocument)event.getEntry();
            // Re-bind transient instance variables
            element.lazarus(_roomObj.pathElements);

        } else if (event.getName().equals(ProjectRoomObject.RESULTS)) {
            final BuildResult result = (BuildResult)event.getEntry();
            if (result.getMember().equals(_ctx.getMemberObject().memberName)) {
                displayBuildResult(result);
            }
        }
    }

    // from interface SetListener
    public void entryRemoved (EntryRemovedEvent event)
    {
        // TODO: if that document is still open FREAK OUT. Server is going to refcount so shouldn't
        // ever happen.
        if (event.getName().equals(ProjectRoomObject.DOCUMENTS)) {
            // final int elementId = (Integer)event.getKey();

        } else if (event.getName().equals(ProjectRoomObject.RESULTS)) {
            // nada
        }
    }

    /** Initialize the file types that can be created */
    protected void initFileTypes ()
    {
        _createableFileTypes = new ArrayList<SwiftlyDocumentEditor.FileTypes>();
        _createableFileTypes.add(
            new FileTypes(_msgs.get("m.filetypes." + MediaDesc.TEXT_ACTIONSCRIPT),
                          MediaDesc.mimeTypeToString(MediaDesc.TEXT_ACTIONSCRIPT)));
        _createableFileTypes.add(new FileTypes(_msgs.get("m.filetypes." + MediaDesc.TEXT_PLAIN),
                                               MediaDesc.mimeTypeToString(MediaDesc.TEXT_PLAIN)));
    }

    protected void showChatPanel ()
    {
        // only show the chat panel if the split pane has hidden it completely
        if (_rightPane.getDividerLocation() == _rightPane.getMaximumDividerLocation()) {
            _rightPane.resetToPreferredSizes();
        }
    }

    protected void hideChatPanel ()
    {
        // this is a bit of a hack, but a better way has not presented itself
        _rightPane.setDividerLocation(getHeight());
    }

    /**
     * Called whenever any data changes that would affect the client's rights in the project
     * being edited.
     */
    protected void updateEditorAccess ()
    {
        if (_roomObj.hasWriteAccess(_ctx.getMemberObject().memberName)) {
            for (AccessControlListener listener : _accessListeners) {
                listener.writeAccessGranted();
            }

        } else if (_roomObj.hasReadAccess(_ctx.getMemberObject().memberName)) {
            for (AccessControlListener listener : _accessListeners) {
                listener.readOnlyAccessGranted();
            }

        } else {
            // the user no longer has access to anything, log them off.
            _ctx.getClient().logoff(false);
        }
    }

    /** Displays the build result on the console */
    protected void displayBuildResult (BuildResult result)
    {
        if (result.buildSuccessful()) {
            _ctx.showInfoMessage(_msgs.get("m.build_succeeded"));
        } else {
            _ctx.showErrorMessage(_msgs.get("m.build_failed"));
        }
        for (BuildResultListener listener : _buildResultListeners) {
            listener.gotResult(result);
        }
    }

    /** A list of files that can be created by this SwiftlyDocumentEditor. */
    protected ArrayList<SwiftlyDocumentEditor.FileTypes> _createableFileTypes;

    /** A set of components listening for access control change events. */
    protected Set<AccessControlListener> _accessListeners;

    /** A set of components listening for new BuildResults. */
    protected Set<BuildResultListener> _buildResultListeners;

    protected SwiftlyContext _ctx;
    protected ProjectRoomController _ctrl;
    protected MessageBundle _msgs;
    protected ProjectRoomObject _roomObj;

    protected JSplitPane _contentPane;
    protected JSplitPane _rightPane;
    protected TabbedEditor _editorTabs;
    protected Console _console;
    protected EditorToolBar _toolbar;
    protected ProjectPanel _projectPanel;
}
