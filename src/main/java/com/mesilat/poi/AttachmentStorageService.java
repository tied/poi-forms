package com.mesilat.poi;

import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Scanned
@Named("poiAttachmentStorageService")
public class AttachmentStorageService implements StorageService {
    private final AttachmentManager attachmentManager;
    private final TransactionTemplate transactionTemplate;
    private final PageManager pageManager;
    private final PermissionManager permissionManager;

    @Override
    public boolean exists(long pageId, String file) throws StorageServiceException {
        Page page = pageManager.getPage(pageId);
        if (page == null){
            throw new StorageServiceException("Page not found");
        }
        if (!permissionManager.hasPermission(AuthenticatedUserThreadLocal.get(), Permission.VIEW, page)){
            throw new StorageServiceException("Not authorized");
        }
        return page.getAttachmentNamed(file) != null;
    }
    @Override
    public InputStream getData(long pageId, String file) throws StorageServiceException {
        Page page = pageManager.getPage(pageId);
        if (page == null){
            throw new StorageServiceException("Page not found");
        }
        if (!permissionManager.hasPermission(AuthenticatedUserThreadLocal.get(), Permission.VIEW, page)){
            throw new StorageServiceException("Not authorized");
        }

        List<Attachment> attachment = page.getLatestVersionsOfAttachments().stream().filter((a) -> {
            return a.getNameForComparison().equals(file);
        }).collect(Collectors.toList());

        if (attachment.size() > 0){
            return attachmentManager.getAttachmentData(attachment.get(0));
        } else {
            return null;
        }
    }
    @Override
    public long getSize(long pageId, String file) throws StorageServiceException {
        Page page = pageManager.getPage(pageId);
        if (page == null){
            throw new StorageServiceException("Page not found");
        }
        if (!permissionManager.hasPermission(AuthenticatedUserThreadLocal.get(), Permission.VIEW, page)){
            throw new StorageServiceException("Not authorized");
        }
        Attachment attachment = page.getAttachmentNamed(file);
        return attachment.getFileSize();
    }
    @Override
    public void setData(long pageId, String file, byte[] data) throws StorageServiceException {
        Object result = transactionTemplate.execute(() -> {
            try {
                Page page = pageManager.getPage(pageId);
                if (page == null){
                    throw new StorageServiceException("Page not found");
                }
                if (AuthenticatedUserThreadLocal.get() != null){
                    if (!permissionManager.hasPermission(AuthenticatedUserThreadLocal.get(), Permission.VIEW, page)){
                        throw new StorageServiceException("Not authorized");
                    }
                }
                //Attachment attachment = page.getAttachmentNamed(file);
                List<Attachment> attachment = page.getLatestVersionsOfAttachments().stream().filter((a) -> {
                    return a.getNameForComparison().equals(file);
                }).collect(Collectors.toList());

                if (attachment.size() > 0){
                    //return attachmentManager.getAttachmentData(attachment.get(0));
                    attachment.get(0).setFileSize(data.length);
                    attachmentManager.getAttachmentDao().replaceAttachmentData(attachment.get(0), new ByteArrayInputStream(data));
                    return Boolean.TRUE;
                } else {
                    return new StorageServiceException("Attachment not found");
                }


            } catch(Throwable ex) {
                return ex;   
            }
        });
        if (result instanceof Throwable) {
            throw new StorageServiceException("Failed to save file", (Throwable)result);
        }
    }

    @Autowired
    public AttachmentStorageService(
            final AttachmentManager attachmentManager,
            final TransactionTemplate transactionTemplate,
            final PageManager pageManager,
            final PermissionManager permissionManager
    ){
        this.attachmentManager = attachmentManager;
        this.transactionTemplate = transactionTemplate;
        this.pageManager = pageManager;
        this.permissionManager = permissionManager;
    }
}