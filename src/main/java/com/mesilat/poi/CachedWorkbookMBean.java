package com.mesilat.poi;

import com.mesilat.jmx.Description;
import java.io.IOException;
import java.util.Date;

@Description("Cached POI Workbook")
public interface CachedWorkbookMBean {
    @Description("Page id")
    long getPageId();

    @Description("Workbook name")
    String getFile();

    @Description("Workbook size")
    long getSize();

    @Description("Workbook current version")
    long getCurrentVersion();

    @Description("TRUE if workbook is loaded into POI object")
    boolean isLoaded();

    @Description("TRUE if workbook is modified and needs to be saved to storage")
    boolean isModified();

    @Description("Workbook modification time")
    Date getModificationTime();

    @Description("Workbook last touch time (either GET or PUT)")
    Date getTouchTime();

    @Description("Save workbook and remove from cache")
    void purge();

    @Description("Save workbook")
    void save() throws IOException, StorageServiceException;

    @Description("TRUE if workbook is locked and could not be edited")
    boolean isLocked();
}