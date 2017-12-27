package com.mesilat.poi;

import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Scanned
@Path("/settings")
public class SettingsResource extends ResourceBase {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.poi-forms");

    private final PluginSettingsFactory pluginSettingsFactory;
    private final TransactionTemplate transactionTemplate;
    private final I18nResolver resolver;
    private final WorkbookCache workbookCache;

    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(
        @Context HttpServletRequest request,
        @Context HttpServletResponse response
    ){
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        if (!isUserAdmin(userKey)){
            return Response.status(Response.Status.UNAUTHORIZED).entity(resolver.getText("com.mesilat.poi-forms.error.not-admin")).build();
        }
        return Response.ok(WorkbookCacheSettings.getSettings(pluginSettingsFactory.createGlobalSettings())).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response put(
        Map<String,String> settings,
        @Context HttpServletRequest request
    ){
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        if (!isUserAdmin(userKey)){
            return Response.status(Response.Status.UNAUTHORIZED).entity(resolver.getText("com.mesilat.poi-forms.error.not-admin")).build();
        }

        return transactionTemplate.execute(()->{
            WorkbookCacheSettings cs;
            try {
                cs = WorkbookCacheSettings.fromSettings(settings);
            } catch(SettingsException ex) {
                return Response.status(Response.Status.NOT_ACCEPTABLE).entity(ex.getMessage()).build();
            }
            putSettings(settings);
            cs.apply(workbookCache);
            return Response.ok(resolver.getText("com.mesilat.poi-forms.msg.settings-update-success")).build();
        });
    }

    @Autowired
    public SettingsResource(
            final @ComponentImport UserManager userManager,
            final @ComponentImport PluginSettingsFactory pluginSettingsFactory,
            final @ComponentImport TransactionTemplate transactionTemplate,
            final @ComponentImport I18nResolver resolver,
            final WorkbookCache workbookCache
    ){
        super(userManager);
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.transactionTemplate = transactionTemplate;
        this.resolver = resolver;
        this.workbookCache = workbookCache;
    }


    private void putSettings(Map<String,String> settings){
        PluginSettings pluginSettings = pluginSettingsFactory.createGlobalSettings();
        for (Entry<String,String> e: settings.entrySet()){
            pluginSettings.put(e.getKey(), e.getValue());
        }
    }
}