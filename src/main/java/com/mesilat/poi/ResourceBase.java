package com.mesilat.poi;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.security.ContentPermission;
import com.atlassian.confluence.security.ContentPermissionSet;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;

public abstract class ResourceBase {
    private final UserManager userManager;

    public UserManager getUserManager() {
        return userManager;
    }
    public boolean isUserAdmin(UserKey userKey) {
        return (userKey == null)? false: userManager.isSystemAdmin(userKey);
    }
    public boolean isUserAuthorized(UserKey userKey, Page page) {
        if (userKey == null) {
            return false;
        }
        ContentPermissionSet permissions = page.getContentPermissionSet(ContentPermission.EDIT_PERMISSION);
        if (permissions != null) {
            if (permissions.getUserKeys().contains(userKey)) {
                return true;
            }
            for (String groupName : permissions.getGroupNames()) {
                if (userManager.isUserInGroup(userKey, groupName)) {
                    return true;
                }
            }
        }
        permissions = page.getContentPermissionSet(ContentPermission.VIEW_PERMISSION);
        if (permissions != null) {
            if (permissions.getUserKeys().contains(userKey)) {
                return true;
            }
            for (String groupName : permissions.getGroupNames()) {
                if (userManager.isUserInGroup(userKey, groupName)) {
                    return true;
                }
            }
        }
        return false;
    }
    public String getFullName(UserKey userKey) {
        UserProfile profile = userManager.getUserProfile(userKey);
        return profile == null? null: profile.getFullName();
    }
    public boolean isAuthenticationRequired() {
        return false;
    }

    public ResourceBase(final UserManager userManager) {
        this.userManager = userManager;
    }
}
