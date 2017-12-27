package com.mesilat.poi;

import com.mesilat.jmx.SimpleMBean;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedWorkbook extends SimpleMBean implements Constants, ModifiableWorkbook, CachedWorkbookMBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(PLUGIN_KEY);
    private static final Map<String,CachedWorkbook> REGISTRY = new HashMap<>();

    public enum ChangeType {
        PutValue,
        AddRows
    }

    private final long pageId;
    private final String file;
    private final String script;
    private final boolean locked;
    private final StorageService storageService;
    private final List<Change> changes = new ArrayList<>();

    private Workbook workbook;
    private long size;
    private long checkpoint = System.currentTimeMillis();
    private long saveTimestamp;
    private long touchTimestamp;

    // <editor-fold defaultstate="collapsed" desc="ModifiableWorkbook implementation">
    @Override
    public Map<String,Object> getInfo() throws WorkbookException {
        synchronized(this){
            try {
                Map<String,Object> details = PoiServer.getWorkbookDetails(getWorkbook());
                Map<String,Object> result = new HashMap<>();
                result.put("page-id", getPageId());
                result.put("file", getFile());
                result.put("version", getCurrentVersion());
                result.put("details", details);
                return result;
            } catch (IOException | StorageServiceException ex) {
                throw new WorkbookException(ex);
            }
        }
    }
    @Override
    public Map<String,Object> getSheet(int sheetId) throws WorkbookException {
        synchronized(this){
            try {
                Map<String,Object> details = PoiServer.getSheetDetails(getWorkbook(), sheetId);
                Map<String,Object> result = new HashMap<>();
                result.put("page-id", getPageId());
                result.put("file", getFile());
                result.put("version", getCurrentVersion());
                result.put("details", details);
                return result;
            } catch (IOException | StorageServiceException ex) {
                throw new WorkbookException(ex);
            }
        }
    }
    @Override
    public Map<String,Object> putValue(int sheet, Integer[] point, Object value, long baseVersion) throws WorkbookException {
        synchronized(this){
            if (baseVersion < getCurrentVersion()){
                throw new WorkbookVersionException("Your workbook version is out of date. The view will be refreshed automatically.");
            }
            try {
                Map<String,Object> changeDetails = PoiServer.putValue(getWorkbook(), sheet, point, value);
                Change change = new Change(System.currentTimeMillis(), ChangeType.PutValue, changeDetails);
                changes.add(change);
                Map<String,Object> result = new HashMap<>();
                result.put("page-id", getPageId());
                result.put("file", getFile());
                result.put("version", getCurrentVersion());
                result.put("changes", changeDetails);
                return result;
            } catch (IOException | StorageServiceException | IllegalAccessException | InvocationTargetException ex) {
                throw new WorkbookException(ex);
            }
        }
    }
    @Override
    public Map<String,Object> putValues(List<PoiServer.CellValueInfo> values, long baseVersion) throws WorkbookException {
        synchronized(this){
            if (baseVersion < getCurrentVersion()){
                throw new WorkbookVersionException("Your workbook version is out of date. The view will be refreshed automatically.");
            }
            try {
                Map<String,Object> changeDetails = PoiServer.putValues(getWorkbook(), values);
                Change change = new Change(System.currentTimeMillis(), ChangeType.PutValue, changeDetails);
                changes.add(change);
                Map<String,Object> result = new HashMap<>();
                result.put("page-id", getPageId());
                result.put("file", getFile());
                result.put("version", getCurrentVersion());
                result.put("changes", changeDetails);
                return result;
            } catch (IOException | StorageServiceException | IllegalAccessException | InvocationTargetException ex) {
                throw new WorkbookException(ex);
            }
        }
    }
    @Override
    public Map<String,Object> addRows(int sheet, Integer[] point, String source, long baseVersion) throws WorkbookException {
        synchronized(this){
            if (baseVersion < getCurrentVersion()){
                throw new WorkbookVersionException("Your workbook version is out of date. The view will be refreshed automatically.");
            }
            try {
                Map<String,Object> changeDetails = PoiServer.addRows(getWorkbook(), sheet, point, source);
                Change change = new Change(System.currentTimeMillis(), ChangeType.AddRows, changeDetails);
                changes.add(change);
                Map<String,Object> result = new HashMap<>();
                result.put("page-id", getPageId());
                result.put("file", getFile());
                result.put("version", getCurrentVersion());
                result.put("changes", changeDetails);
                return result;
            } catch (Exception ex) {
                throw new WorkbookException(ex);
            }
        }
    }
    @Override
    public Map<String,Object> getChanges(final long since) throws WorkbookException {
        synchronized(this){
            Map<String,Object> result = new HashMap<>();
            List<Map> changeData = new ArrayList<>();
            result.put("page-id", getPageId());
            result.put("file", getFile());
            result.put("version", getCurrentVersion());
            result.put("changes", changeData);

            if (since < checkpoint){
                throw new WorkbookVersionException((String)null);
            } else if (since == checkpoint){
                changes.stream().forEach((a) -> {
                    changeData.add(a.toMapObject());
                });
            } else {
                changes.stream().filter((a) -> {
                    return a.getTimestamp() > since;
                }).forEach((a) -> {
                    changeData.add(a.toMapObject());
                });
            }

            return result;
        }
    }
    @Override
    public void clearChanges(final long before) {
        synchronized(this){
            if (changes.isEmpty()){
                return;
            }
            List<Change> changesToRemove = changes.stream().filter((a) -> {
                return a.getTimestamp() < before;
            }).collect(Collectors.toList());
            if (changesToRemove.isEmpty()){
                return;
            }
            checkpoint = changesToRemove.get(changesToRemove.size() - 1).getTimestamp();
            changes.removeAll(changesToRemove);
        }
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="CachedWorkbookMBean implementation">
    @Override
    public String getMBeanName() {
        return String.format("POI Forms Plugin:type=Cached Workbook,page=%d,file=%s", pageId, normalize(file));
    }
    @Override
    public long getPageId(){
        return pageId;
    }
    @Override
    public String getFile(){
        return file;
    }
    @Override
    public long getSize(){
        return size;
    }
    @Override
    public long getCurrentVersion() {
        synchronized(this){
            return changes.isEmpty()? checkpoint: changes.get(changes.size() - 1).getTimestamp();
        }
    }
    @Override
    public boolean isLoaded() {
        return workbook != null;
    }
    @Override
    public boolean isModified(){
        return saveTimestamp < getCurrentVersion();
    }
    @Override
    public Date getModificationTime(){
        return new Date(getCurrentVersion());
    }
    @Override
    public Date getTouchTime(){
        return touchTimestamp != 0? new Date(touchTimestamp): null;
    }
    @Override
    public void purge(){
        unload();
        unregisterMBean();
    }
    @Override
    public void save() throws IOException, StorageServiceException {
        synchronized (this) {
            if (workbook != null && isModified()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                workbook.write(buf);
                buf.close();
                storageService.setData(pageId, file, buf.toByteArray());
                saveTimestamp = System.currentTimeMillis();
                LOGGER.debug(String.format("Workbook saved: %s", toString()));
            }
        }
    }
    @Override
    public boolean isLocked(){
        return locked;
    }

    // </editor-fold>

    public Workbook getWorkbook() throws IOException, StorageServiceException {
        touchTimestamp = System.currentTimeMillis();
        synchronized (this) {
            if (workbook == null) {
                try (InputStream in = storageService.getData(pageId, file)){
                    workbook = new HSSFWorkbook(in);
                }
                size = storageService.getSize(pageId, file);
                try {
                    workbook.addToolPack(WorkbookCache.UserFunction.getTookpack(script));
                    HSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);
                } catch(Exception ex) {
                    LOGGER.warn(String.format("Error evaluating script in %d, %s", pageId, file), ex);
                }
                changes.clear();
                checkpoint = saveTimestamp = touchTimestamp;
            }
            return workbook;
        }
    }
    public void unload(){
        synchronized (this) {
            if (workbook != null && isModified()) {
                try {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    workbook.write(buf);
                    buf.close();
                    workbook = null;
                    storageService.setData(pageId, file, buf.toByteArray());
                } catch (IOException | StorageServiceException ex) {
                    LOGGER.error(String.format("Failed to save workbook %s", toString()), ex);
                }
            }
        }
        
    }
    @Override
    public String toString(){
        return WorkbookCacheImpl.formatKey(pageId, file);
    }
    @Override
    public void registerMBean() throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        synchronized(REGISTRY){
            super.registerMBean();
            REGISTRY.put(getMBeanName(), this);
        }
    }
    @Override
    public void unregisterMBean() {
        super.unregisterMBean();
        synchronized(REGISTRY){
            String name = getMBeanName();
            if (REGISTRY.containsKey(name)){
                REGISTRY.remove(name);
            }
        }
    }
    protected static CachedWorkbook getWorkbook(ObjectName name){
        return REGISTRY.get(name.toString());
    }
    protected void touch(){
        touchTimestamp = System.currentTimeMillis();
    }

    protected CachedWorkbook(StorageService storageService, long pageId, String file, String script, boolean locked) throws NotCompliantMBeanException{
        super(CachedWorkbookMBean.class);
        this.storageService = storageService;
        this.pageId = pageId;
        this.file = file;
        this.script = script;
        this.locked = locked;
    }

    public static String normalize(String file){
        return file.replaceAll("[:,=]", "");
    }

    public static class Change {
        private final long timestamp;
        private final ChangeType changeType;
        private final Map<String,Object> details;

        public long getTimestamp() {
            return timestamp;
        }
        public ChangeType getChangeType() {
            return changeType;
        }
        public Map<String,Object> getDetails() {
            return details;
        }

        public Map<String,Object> toMapObject(){
            Map<String,Object> map = new HashMap<>();
            map.put("changeType", changeType.toString());
            map.put("timestamp", timestamp);
            map.put("details", details);
            return map;
        }

        public Change(long timestamp, ChangeType changeType, Map<String,Object> details){
            this.timestamp = timestamp;
            this.changeType = changeType;
            this.details = details;
        }
    }
}