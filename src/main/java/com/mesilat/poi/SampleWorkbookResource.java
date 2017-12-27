package com.mesilat.poi;

import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.I18nResolver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;

@Scanned
@Path("/sample")
public class SampleWorkbookResource {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.poi-forms");

    private final I18nResolver resolver;
    private final PermissionManager permissionManager;
    private final PageManager pageManager;
    private final AttachmentManager attachmentManager;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response get(@QueryParam("page-id") Long pageId) {
        if (pageId == null){
            return Response.status(Response.Status.BAD_REQUEST).entity("Page id cannot be null").build();
        }
        Page page = pageManager.getPage(pageId);
        if (page == null){
            return Response.status(Response.Status.NOT_FOUND).entity("Page could not be found").build();
        }
        Attachment attachment = page.getAttachmentNamed("Sample.xls");
        if (attachment != null){
            return Response.status(Response.Status.CONFLICT).entity("File already exists").build();
        }

        if (permissionManager.hasPermission(AuthenticatedUserThreadLocal.get(), Permission.EDIT, page)){
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = this.getClass().getResourceAsStream("/templates/Sample.xls")){
                StreamUtils.copy(in, buf);
            } catch (IOException ex) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
            }
            try {
                attachment = new Attachment();
                attachment.setFileName("Sample.xls");
                attachment.setTitle("Sample.xls");
                attachment.setFileSize(buf.size());
                attachment.setContentType("application/vnd.ms-excel");
                attachment.setContent(page);
                attachmentManager.saveAttachment(attachment, null, new ByteArrayInputStream(buf.toByteArray()));
            } catch (IOException ex) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
            }
            return Response.status(Response.Status.OK).entity("Attachment created successfully").build();
        } else {
            return Response.status(Response.Status.FORBIDDEN).entity("You are not authorised to modify this page").build();
        }
    }

    @Inject
    public SampleWorkbookResource(
            final @ComponentImport I18nResolver resolver,
            final @ComponentImport PermissionManager permissionManager,
            final @ComponentImport PageManager pageManager,
            final @ComponentImport AttachmentManager attachmentManager
    ) {
        this.resolver = resolver;
        this.permissionManager = permissionManager;
        this.pageManager = pageManager;
        this.attachmentManager = attachmentManager;
    }
}