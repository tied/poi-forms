package com.mesilat.poi;

import java.util.List;
import java.util.Map;

public interface ModifiableWorkbook {
    Map<String,Object> getInfo() throws WorkbookException;
    Map<String,Object> getSheet(int sheetId) throws WorkbookException;
    Map<String,Object> putValue(int sheet, Integer[] point, Object value, long baseVersion) throws WorkbookException;
    Map<String,Object> putValues(List<PoiServer.CellValueInfo> values, long baseVersion) throws WorkbookException;
    Map<String,Object> addRows(int sheet, Integer[] point, String source, long baseVersion) throws WorkbookException;
    Map<String,Object> getChanges(final long since) throws WorkbookException;
    void clearChanges(final long before);
}