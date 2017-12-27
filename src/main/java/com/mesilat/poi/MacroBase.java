package com.mesilat.poi;

import com.atlassian.confluence.core.DateFormatter;
import com.atlassian.confluence.core.FormatSettingsManager;
import com.atlassian.confluence.languages.LocaleManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.ConfluenceUserPreferences;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.upm.api.license.PluginLicenseManager;
import com.atlassian.user.User;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MacroBase extends BaseMacro {
    public static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.poi-forms");
    
    private final I18nResolver resolver;
    private final UserManager userManager;
    private final PluginSettingsFactory pluginSettingsFactory;
    private final SettingsManager settingsManager;
    private final PluginLicenseManager licenseManager;
    private final UserAccessor userAccessor;
    private final FormatSettingsManager formatSettingsManager;
    private final LocaleManager localeManager;

    public I18nResolver getResolver() {
        return resolver;
    }
    public UserManager getUserManager() {
        return userManager;
    }
    public PluginSettingsFactory getPluginSettingsFactory() {
        return pluginSettingsFactory;
    }
    public PluginLicenseManager getLicenseManager() {
        return licenseManager;
    }

    public boolean isAuthenticated(UserKey userKey) {
        return userKey != null;
    }
    public boolean isAdmin(UserKey userKey) {
        return userManager.isAdmin(userKey);
    }
    protected boolean isLicensed() {
        try {
            return licenseManager.getLicense().get().isValid();
        } catch(Throwable ignore) {
            return false;
        }
    }
    public String getBaseUrl() {
        return settingsManager.getGlobalSettings().getBaseUrl();
    }
    public String blockNotAuthenticated() {
        return (new StringBuilder())
            .append("<div class='aui-message aui-message-error'>")
            .append("<p class='title'><strong>")
            .append(getResolver().getText("com.mesilat.zabbix-plugin.error"))
            .append("</strong></p><p>")
            .append(getResolver().getText("com.mesilat.zabbix-plugin.error.not-authenticated"))
            .append("</p></div>")
            .toString();
    }
    public String blockNotAuthorized() {
        return (new StringBuilder())
            .append("<div class='aui-message aui-message-error'>")
            .append("<p class='title'><strong>")
            .append(getResolver().getText("com.mesilat.zabbix-plugin.error"))
            .append("</strong></p><p>")
            .append(getResolver().getText("com.mesilat.zabbix-plugin.error.not-authorized"))
            .append("</p></div>")
            .toString();
    }
    public Locale getLocale(UserKey userKey) {
        User user = userAccessor.getUserByKey(userKey);
        return localeManager.getLocale(user);
    }
    public ConfluenceUserPreferences getPreferences(UserKey userKey) {
        return userAccessor.getConfluenceUserPreferences(
                userAccessor.getExistingUserByKey(userKey)
        );
    }
    public DateFormatter getDateFormatter(ConfluenceUserPreferences preferences) {
        return preferences.getDateFormatter(formatSettingsManager, localeManager);
    }

    public MacroBase(final UserManager userManager,
            final PluginSettingsFactory pluginSettingsFactory, final I18nResolver resolver,
            final SettingsManager settingsManager, final PluginLicenseManager licenseManager,
            final UserAccessor userAccessor, final FormatSettingsManager formatSettingsManager,
            final LocaleManager localeManager) {
        this.userManager = userManager;
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.resolver = resolver;
        this.settingsManager = settingsManager;
        this.licenseManager = licenseManager;
        this.userAccessor = userAccessor;
        this.formatSettingsManager = formatSettingsManager;
        this.localeManager = localeManager;
    }
}
