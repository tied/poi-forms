package com.mesilat.poi.web;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.user.User;
import com.mesilat.poi.CachedWorkbook;
import com.mesilat.poi.PoiServer;
import com.mesilat.poi.ResourceBase;
import com.mesilat.poi.WorkbookCache;
import com.mesilat.poi.WorkbookCacheException;
import com.mesilat.poi.WorkbookException;
import com.mesilat.poi.WorkbookVersionException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@AnonymousAllowed
@Scanned
@Path("/form")
public class PoiResource extends ResourceBase {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.poi-forms");

    private final I18nResolver resolver;
    private final PoiServer server;
    private final PageManager pageManager;
    private final PermissionManager permissionManager;
    private final UserAccessor userAccessor;


    private static void nocache(final HttpServletResponse response){
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }
    private Page getPage(long pageId) throws PoiWebException {
        if (pageId <= 0){
            throw new PoiWebException(Response.Status.BAD_REQUEST, resolver.getText("com.mesilat.poi-forms.poi-form.nopage"));
        }
        Page page = pageManager.getPage(pageId);
        if (page == null){
            throw new PoiWebException(Response.Status.BAD_REQUEST, resolver.getText("com.mesilat.poi-forms.poi-form.nopage"));
        }
        return page;
    }
    private User verifyPermission(final HttpServletRequest request, Page page, Permission permission) throws PoiWebException {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        if (userKey == null) {
            throw new PoiWebException(Response.Status.FORBIDDEN, resolver.getText("com.mesilat.poi-forms.error.not-authenticated"));
        }
        User user = userAccessor.getUserByKey(userKey);
        if (!permissionManager.hasPermission(user, permission, page)) {
            throw new PoiWebException(Response.Status.FORBIDDEN, resolver.getText("com.mesilat.poi-forms.error.not-authorized"));
        }
        return user;
    }

    @GET
    @Path("attachments/{page-id}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getPageAttachments(
        @PathParam("page-id") Long pageId,
        @Context HttpServletRequest request,
        @Context HttpServletResponse response
    ){
        nocache(response);
        Page page;
        try {
            page = getPage(pageId);
            verifyPermission(request, page, Permission.VIEW);
        } catch (PoiWebException ex) {
            LOGGER.warn(String.format("Request failed: %s", request.getPathInfo()), ex);
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        }

        List<Map<String,Object>> result = new ArrayList<>();
        page.getAttachments().stream().filter((a) -> {
            return !a.isDeleted();
        }).sorted((a, b) -> {
            return a.getDisplayTitle().compareTo(b.getDisplayTitle());
        }).forEach((a) -> {
            Map<String,Object> map = new HashMap<>();
            map.put("title", a.getDisplayTitle());
            map.put("id", a.getId());
            result.add(map);
        });
        LOGGER.debug(String.format("Page %d (%s) has %d attachment(s)", page.getId(), page.getTitle(), result.size()));
        return Response.ok(result).build();
    }

    @GET
    @Path("workbook/{page-id}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getWorkbook(
        @PathParam("page-id") Long pageId,
        @QueryParam("file") String file,
        @Context HttpServletRequest request,
        @Context HttpServletResponse response
    ){
        nocache(response);
        try {
            if (isAuthenticationRequired()){
                Page page = getPage(pageId);
                verifyPermission(request, page, Permission.VIEW);
            }
        } catch (PoiWebException ex) {
            LOGGER.warn(String.format("Request failed: %s", request.getPathInfo()), ex);
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        }

        try {
            CachedWorkbook cachedWorkbook = server.getWorkbook(pageId, file);
            Map<String,Object> result = cachedWorkbook.getInfo();
            return Response.ok(result).build();
        } catch (WorkbookCacheException | WorkbookException ex) {
            LOGGER.warn(String.format("Request failed: %s", request.getPathInfo()), ex);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }
    }

    @GET
    @Path("changes/{page-id}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getChanges(
        @PathParam("page-id") final Long pageId,
        @QueryParam("file") final String file,
        @QueryParam("version") final Long version,
        @Context HttpServletRequest request,
        @Context HttpServletResponse response
    ){
        nocache(response);
        try {
            if (isAuthenticationRequired()){
                Page page = getPage(pageId);
                verifyPermission(request, page, Permission.VIEW);
            }
        } catch (PoiWebException ex) {
            //LOGGER.warn(String.format("Request failed: %s", request.getPathInfo()), ex);
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        }

        try {
            CachedWorkbook cachedWorkbook = server.getWorkbook(pageId, file);
            if (cachedWorkbook == null){
                return Response.status(Response.Status.GONE).entity("Not ready yet").build();
            }
            Map<String,Object> result = cachedWorkbook.getChanges(version);
            return Response.ok(result).build();
        } catch(WorkbookVersionException ex){
            return Response.status(Response.Status.CONFLICT).entity(resolver.getText("com.mesilat.poi-forms.error.reload-workbook")).build();
        } catch (WorkbookCacheException | WorkbookException ex){
            LOGGER.warn(String.format("Request failed: %s", request.getPathInfo()), ex);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }
    }

    @GET
    @Path("sheet/{page-id}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response getSheet(
        @PathParam("page-id") Long pageId,
        @QueryParam("file") String file,
        @QueryParam("sheet-id") Integer sheetId,
        @Context HttpServletRequest request,
        @Context HttpServletResponse response
    ){
        nocache(response);
        try {
            if (isAuthenticationRequired()){
                Page page = getPage(pageId);
                verifyPermission(request, page, Permission.VIEW);
            }
        } catch (PoiWebException ex) {
            LOGGER.warn(String.format("Request failed: %s", request.getPathInfo()), ex);
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        }

        try {
            CachedWorkbook cachedWorkbook = server.getWorkbook(pageId, file);
            Map<String,Object> result = cachedWorkbook.getSheet(sheetId);
            return Response.ok(result).build();
        } catch (WorkbookCacheException | WorkbookException ex) {
            LOGGER.warn(String.format("Request failed: %s", request.getPathInfo()), ex);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }
    }

    @PUT
    @Path("value/{page-id}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response putValues(
        @PathParam("page-id") Long pageId,
        Object data,
        @Context HttpServletRequest request,
        @Context HttpServletResponse response
    ){
        nocache(response);
        try {
            if (isAuthenticationRequired()){
                Page page = getPage(pageId);
                verifyPermission(request, page, Permission.VIEW);
            }
        } catch (PoiWebException ex) {
            LOGGER.warn(String.format("Request failed: %s", request.getPathInfo()), ex);
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        }

        try {
            List<CellModificationInfo> cmi = CellModificationInfo.parse(data);
            if (cmi.isEmpty()){
                return Response.status(Response.Status.BAD_REQUEST).entity("Failed to parse data").build();
            }
            CachedWorkbook cachedWorkbook = server.getWorkbook(pageId, cmi.get(0).getFile());
            if (cachedWorkbook.isLocked()){
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("This workbook is locked and cannot be edited").build();
            }
            
            Map<String,Object> result = cachedWorkbook.putValues(CellModificationInfo.convert(cmi), cmi.get(0).getVersion());
            return Response.ok(result).build();
        } catch (WorkbookVersionException ex) {
            return Response.status(Response.Status.CONFLICT).entity(resolver.getText("com.mesilat.poi-forms.error.reload-workbook")).build();
        } catch (WorkbookCacheException | WorkbookException ex) {
            LOGGER.warn(String.format("Request failed: %s", request.getPathInfo()), ex);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }
    }

    @PUT
    @Path("addrows/{page-id}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addRows(
        @PathParam("page-id") Long pageId,
        Object data,
        @Context HttpServletRequest request,
        @Context HttpServletResponse response
    ){
        nocache(response);
        try {
            if (isAuthenticationRequired()){
                Page page = getPage(pageId);
                verifyPermission(request, page, Permission.VIEW);
            }
        } catch (PoiWebException ex) {
            LOGGER.warn(String.format("Request failed: %s", request.getPathInfo()), ex);
            return Response.status(ex.getStatus()).entity(ex.getMessage()).build();
        }

        try {
            AddRowsInfo ari = new AddRowsInfo(data);
            CachedWorkbook cachedWorkbook = server.getWorkbook(pageId, ari.getFile());
            if (cachedWorkbook.isLocked()){
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("This workbook is locked and cannot be edited").build();
            }

            Map<String,Object> result = cachedWorkbook.addRows(ari.getSheetId(), ari.getBefore(), ari.getName(), ari.getVersion());
            return Response.ok(result).build();
        } catch (WorkbookVersionException ex) {
            return Response.status(Response.Status.CONFLICT).entity(resolver.getText("com.mesilat.poi-forms.error.reload-workbook")).build();
        } catch (WorkbookCacheException | WorkbookException ex) {
            LOGGER.warn(String.format("Request failed: %s", request.getPathInfo()), ex);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }
    }

    @Autowired
    public PoiResource(final @ComponentImport UserManager userManager,
            final @ComponentImport I18nResolver resolver,
            final @ComponentImport PageManager pageManager,
            final @ComponentImport PermissionManager permissionManager,
            final @ComponentImport UserAccessor userAccessor,
            final WorkbookCache workbookCache) {
        super(userManager);
        this.resolver = resolver;
        this.pageManager = pageManager;
        this.permissionManager = permissionManager;
        this.userAccessor = userAccessor;
        this.server = new PoiServer(workbookCache);
    }
}