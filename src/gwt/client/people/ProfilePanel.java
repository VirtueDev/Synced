//
// $Id$

package client.people;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlowPanel;
import com.threerings.msoy.data.all.Friendship;

import com.threerings.msoy.profile.gwt.ProfileService;
import com.threerings.msoy.profile.gwt.ProfileServiceAsync;
import com.threerings.msoy.web.gwt.WebMemberService;
import com.threerings.msoy.web.gwt.WebMemberServiceAsync;

import client.shell.CShell;
import client.ui.MsoyUI;

/**
 * Displays a member's profile.
 */
public class ProfilePanel extends FlowPanel
{
    public ProfilePanel (int memberId)
    {
        setStyleName("profile");
        add(MsoyUI.createNowLoading());

        _memberId = memberId;
        // issue a request for this member's profile page data
        _profilesvc.loadProfile(memberId, new AsyncCallback<ProfileService.ProfileResult>() {
            public void onSuccess (ProfileService.ProfileResult result) {
                init(result);
            }
            public void onFailure (Throwable cause) {
                CShell.log("Failed to load profile data [for=" + _memberId + "].", cause);
                clear();
                add(MsoyUI.createLabel(CShell.serverError(cause), "Error"));
            }
        });
    }

    protected void init (final ProfileService.ProfileResult pdata)
    {
        clear();
		
		//if the player has enabled "friends only" profile viewing, then only allow friends or support to view
		boolean isFriends = false;
		boolean friendsOnlyProfile = false;
		_membersvc.getFriendship(_memberId, new InfoCallback<Friendship>() {
                public void onSuccess (Friendship result) {
                    if(result == Friendship.FRIENDS) {
						isFriends = true;
					}
                }
        });
		_profilesvc.getProfilePreference(_memberId, new InfoCallback<Boolean>() {
                public void onSuccess (Boolean result) {
					friendsOnlyProfile = result;
                }
        });
		if(friendsOnlyProfile && (!isFriends) && (!CShell.isSupport()) && _memberId != CShell.getMemberId()) {
			add(MsoyUI.createLabel("This user has restricted his or her profile viewing to friends only.", "Error"));
            return;
		}
		
		//initiate the player profile!
        if (pdata == null) {
            add(MsoyUI.createLabel(_msgs.profileNoSuchMember(), "Error"));
            return;
        }

        CShell.frame.setTitle((_memberId == CShell.getMemberId()) ? _msgs.profileSelfTitle() :
                              _msgs.profileOtherTitle(pdata.name.toString()));

        for (Blurb _blurb : _blurbs) {
            if (_blurb.shouldDisplay(pdata)) {
                _blurb.init(pdata);
                add(_blurb);
            }
        }
    }

    /** The id of the member who's profile we're displaying. */
    protected int _memberId;

    /** The blurbs we'll display on our profile. */
    protected Blurb[] _blurbs = {
        new ProfileBlurb(), new InterestsBlurb(), new CommentsBlurb(), new GalleriesBlurb(),
        new FriendsBlurb(), new TrophiesBlurb(), new MedalsBlurb(), new StampsBlurb(),
        new RatingsBlurb(), new GroupsBlurb(), new FavoritesBlurb()
    };

    protected static final PeopleMessages _msgs = GWT.create(PeopleMessages.class);
    protected static final ProfileServiceAsync _profilesvc = GWT.create(ProfileService.class);
	protected static final WebMemberServiceAsync _membersvc = GWT.create(WebMemberService.class);
}