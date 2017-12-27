package com.mesilat.poi;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Scanned
@Path("/")
@AnonymousAllowed
public class ReferenceDataResource implements Constants {
    private static final Logger LOGGER = LoggerFactory.getLogger(PLUGIN_KEY);

    private final PermissionManager permissionManager;
    private final PageManager pageManager;
    private final PoiServer server;

    @GET
    @Path("/refdata/{page-id}/{file}")
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
    public Response get(
        @PathParam("page-id") Long pageId,
        @PathParam("file") String file,
        @QueryParam("name") String name
    ){
        if (pageId == null){
            return Response.status(Response.Status.BAD_REQUEST).entity("Please specify page ID").build();
        }
        Page page = pageManager.getPage(pageId);
        if (page == null){
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid page ID specified").build();
        }
        //if (!permissionManager.hasPermission(AuthenticatedUserThreadLocal.get(), Permission.VIEW, page)){
        //    return Response.status(Response.Status.FORBIDDEN).entity("Not authorized to view this page").build();
        //}

        try {
            CachedWorkbook workbook = server.getWorkbook(pageId, file);
            List<String> values = PoiServer.nameData(workbook, name);
            return Response.ok(values).build();
        } catch(IOException | StorageServiceException | WorkbookCacheException ex) {
            return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ex.getMessage())
                .build();
        }
    }

    public ReferenceDataResource(
            final @ComponentImport PermissionManager permissionManager,
            final @ComponentImport PageManager pageManager,
            final WorkbookCache workbookCache
    ){
        this.permissionManager = permissionManager;
        this.pageManager = pageManager;
        this.server = new PoiServer(workbookCache);
    }
}