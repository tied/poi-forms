package com.mesilat.poi;

import com.atlassian.confluence.event.events.content.attachment.AttachmentTrashedEvent;
import com.atlassian.confluence.event.events.content.attachment.GeneralAttachmentRemoveEvent;
import com.atlassian.confluence.event.events.content.attachment.GeneralAttachmentUpdateEvent;
import com.atlassian.confluence.event.events.content.page.PageUpdateEvent;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.mesilat.jmx.SimpleMBean;
import com.mesilat.poi.JavascriptCache.MacroInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Named;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

@Scanned
@Named("poiWorkbookCache")
public class WorkbookCacheImpl extends SimpleMBean implements WorkbookCache, InitializingBean, DisposableBean, Runnable, Constants, WorkbookCacheMBean {
    private final StorageService storageService;
    private final EventPublisher eventPublisher;
    private final JavascriptCache javascriptCache;

    private Thread thread = null;

    private int maxFiles = 64;
    private long maxSize = 256 * 1024 * 1024; // 256M by default

    // <editor-fold defaultstate="collapsed" desc="InitializingBean, DisposableBean, Runnable Implementation">
    @Override
    public void afterPropertiesSet() throws Exception {
        registerMBean();
        eventPublisher.register(this);
        thread = new Thread(this);
        thread.start();
    }
    @Override
    public void destroy() throws Exception {
        unregisterMBean();
        eventPublisher.unregister(this);
        if (thread != null) {
            thread.interrupt();
            thread.join();
            thread = null;
        }
    }
    @Override
    @SuppressWarnings("SleepWhileInLoop")
    public void run() {
        while (true) {
            try {
                Thread.sleep(60000); // Do not run too often
            } catch(InterruptedException ignore) {
                break;
            }

            saveModifiedFiles();
            removeOutdated();
            unloadIfCacheTargetExceeded();
            clearChangeHistory();
        }

        purge();
    }
    private Stream<CachedWorkbook> all(){
        return all(null);
    }
    private Stream<CachedWorkbook> all(Long pageId){
        try {
            return getMBeanServer().queryNames(new ObjectName(formatKey(pageId,null)), null).stream().map(name -> {
                return CachedWorkbook.getWorkbook(name);
            });
        } catch (MalformedObjectNameException ex) {
            throw new RuntimeException(ex); // Should not happen
        }
    }
    private void saveModifiedFiles(){
        List<CachedWorkbook> workbooks;
        synchronized(this){
            workbooks = all().filter((workbook) -> {
                return workbook.isModified();
            }).collect(Collectors.toList());
        }
        workbooks.stream().forEach((workbook) -> {
            try {
                workbook.save();
            } catch (IOException | StorageServiceException ex) {
                LOGGER.error(String.format("Failed to save workbook %s", workbook.toString()), ex);
            }
        });
    }
    private void removeOutdated(){
        List<CachedWorkbook> workbooks;
        Date treshold = new Date(System.currentTimeMillis() - 10 * 60 * 1000);
        synchronized(this){
            workbooks = all().filter((workbook) -> {
                return workbook.getTouchTime().before(treshold);
            }).collect(Collectors.toList());
        }
        workbooks.stream().forEach((workbook) -> {
            workbook.purge();
        });
    }
    private void unloadIfCacheTargetExceeded(){
        final List<CachedWorkbook> workbooks = new ArrayList<>();
        final AtomicInteger files = new AtomicInteger(0);
        final AtomicLong size = new AtomicLong(0);

        synchronized(this){
            all()
            .filter((workbook)->{
                return workbook.isLoaded();
            })
            .sorted((a,b)->{
                if (a.getTouchTime().before(b.getTouchTime()))
                    return -1;
                else if (a.getTouchTime().after(b.getTouchTime()))
                    return 1;
                else
                    return 0;
            })
            .forEach((workbook)->{
                files.incrementAndGet();
                size.addAndGet(workbook.getSize());

                if (files.get() > maxFiles || size.get() > maxSize){
                    workbooks.add(workbook);
                }
            });
        }

        workbooks.stream().forEach((workbook) -> {
            workbook.unload();
        });
    }
    private void clearChangeHistory(){
        long checkpoint = System.currentTimeMillis() - 10 * 60 * 1000;
        all().forEach((a) -> {
            a.clearChanges(checkpoint);
        });
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="WorkbookCacheMBean Implementation">
    @Override
    public int getMaxFiles(){
        return maxFiles;
    }
    @Override
    public void setMaxFiles(int maxFiles){
        this.maxFiles = maxFiles;
    }
    @Override
    public long getMaxBytes(){
        return maxSize;
    }
    @Override
    public void setMaxBytes(long size){
        this.maxSize = size;
    }
    @Override
    public void purge(){
        List<CachedWorkbook> workbooks;
        workbooks = all().collect(Collectors.toList());
        for (CachedWorkbook workbook : workbooks){
            workbook.purge();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="WorkbookCache Implementation">
    @Override
    public CachedWorkbook getWorkbook(long pageId, String file) throws WorkbookCacheException {
        try {
            ObjectName name = new ObjectName(formatKey(pageId, file));
            CachedWorkbook workbook = CachedWorkbook.getWorkbook(name);
            if (workbook != null){
                workbook.touch();
            } else {
                MacroInfo macroInfo = javascriptCache.getMacroInfo(pageId, file);
                workbook = new CachedWorkbook(storageService, pageId, file, macroInfo == null? null: macroInfo.getBody(), macroInfo == null? false: macroInfo.getParams().containsKey("locked"));
                workbook.registerMBean();
            }
            return workbook;
        } catch (NotCompliantMBeanException | InstanceAlreadyExistsException | MBeanRegistrationException | MalformedObjectNameException ex) {
            throw new WorkbookCacheException(ex);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Attachment Events">
    @EventListener
    public void onAttachment(AttachmentTrashedEvent event){
        onAttachment(formatKey(event.getContent().getId(), event.getAttachment().getFileName()));
    }
    @EventListener
    public void onAttachment(GeneralAttachmentRemoveEvent event){
        onAttachment(formatKey(event.getContent().getId(), event.getAttachment().getFileName()));
    }
    @EventListener
    public void onAttachment(GeneralAttachmentUpdateEvent event){
        onAttachment(formatKey(event.getContent().getId(), event.getAttachment().getFileName()));
    }
    @EventListener
    public void onPageUpdateEvent(PageUpdateEvent event){
        final List<CachedWorkbook> workbooks = all(event.getPage().getId()).collect(Collectors.toList());
        if (workbooks.size() > 0){
            Thread t = new Thread(() -> {
                workbooks.stream().forEach((w) -> {
                    w.purge();
                });
            });
            t.start();
        }
    }
    private void onAttachment(String key){
        try {
            final CachedWorkbook workbook = CachedWorkbook.getWorkbook(new ObjectName(key));
            if (workbook != null){
                Thread t = new Thread(() -> {
                    workbook.purge();
                });
                t.start();
            }
        } catch (MalformedObjectNameException ex) {
            throw new RuntimeException(ex); // Should not happen
        }
    }
    // </editor-fold>


    @Autowired
    public WorkbookCacheImpl(
        final StorageService storageService,
        final JavascriptCache javascriptCache,
        final @ComponentImport EventPublisher eventPublisher,
        final @ComponentImport PluginSettingsFactory pluginSettingsFactory
    ) throws NotCompliantMBeanException{
        super(WorkbookCacheMBean.class);

        this.storageService = storageService;
        this.javascriptCache = javascriptCache;
        this.eventPublisher = eventPublisher;

        try {
            WorkbookCacheSettings.fromSettings(WorkbookCacheSettings.getSettings(pluginSettingsFactory.createGlobalSettings())).apply(this);
        } catch (SettingsException ex) {
            LOGGER.error("Invalid workbook cache settings", ex);
        }
    }

    public static String formatKey(Long pageId, String file){
        return String.format("POI Forms Plugin:type=Cached Workbook,page=%s,file=%s",
            pageId == null? "*": Long.toString(pageId),
            file == null? "*": file);
    }
}