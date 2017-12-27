package com.mesilat.poi;

import com.mesilat.jmx.Description;
import com.mesilat.jmx.MBeanName;

@MBeanName("POI Forms Plugin:name=Workbook Cache")
@Description("")
public interface WorkbookCacheMBean {
    @Description("Max files in cache")
    int getMaxFiles();
    void setMaxFiles(int maxFiles);
    @Description("Max bytes in cache")
    long getMaxBytes();
    void setMaxBytes(long size);
    @Description("Clear cache")
    void purge();
}