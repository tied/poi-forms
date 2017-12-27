package com.mesilat.poi;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFName;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PoiServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.poi-forms");

    final WorkbookCache workbookCache;

    public CachedWorkbook getWorkbook(long pageId, String file) throws WorkbookCacheException {
        return workbookCache.getWorkbook(pageId, file);
    }
    public static Map<String, Object> getWorkbookDetails(Workbook workbook) throws IOException, StorageServiceException {
        HSSFWorkbook hw = (HSSFWorkbook)workbook;

        Map<Integer,String> sheets = new HashMap<>();
        for (int i = 0; i < hw.getNumberOfSheets(); i++) {
            if (!hw.isSheetHidden(i) && !hw.isSheetVeryHidden(i)) {
                Sheet sheet = hw.getSheetAt(i);
                sheets.put(i, sheet.getSheetName());
            }
        }

        List<Map<String, Object>> styles = new ArrayList<>();
        for (int i = 0; i < hw.getNumCellStyles(); i++) {
            HSSFCellStyle hssfCellStyle = (HSSFCellStyle) hw.getCellStyleAt(i);
            Map<String,Object> style = new HashMap<>();
            style.put("index", hssfCellStyle.getIndex());
            style.put("border", new short[]{
                hssfCellStyle.getBorderLeft().getCode(),
                hssfCellStyle.getBorderRight().getCode(),
                hssfCellStyle.getBorderTop().getCode(),
                hssfCellStyle.getBorderBottom().getCode()
            });
            style.put("font", Util.getCssFont(hssfCellStyle.getFont(hw)));
            style.put("color", Util.getColorHexString(hssfCellStyle.getFont(hw).getHSSFColor(hw)));
            style.put("bgcolor", Util.getColorHexString(hssfCellStyle.getFillForegroundColorColor()));
            if (!"General".equals(hssfCellStyle.getDataFormatString())){
                style.put("format", hssfCellStyle.getDataFormatString());
            }
            style.put("align", Util.getAlignString(hssfCellStyle.getAlignment()));
            style.put("locked", hssfCellStyle.getLocked());
            styles.add(style);
        }

        Map<String,Object> data = new HashMap<>();
        data.put("sheets", sheets);
        data.put("styles", styles);
        return data;
    }
    public static Map<String, Object> getSheetDetails(Workbook workbook, int sheetId) throws IOException, StorageServiceException {
        HSSFWorkbook hw = (HSSFWorkbook)workbook;
        Sheet sheet = hw.getSheetAt(sheetId);

        Map<String,int[]> spans = new HashMap<>();
        for (CellRangeAddress addr : sheet.getMergedRegions()) {
            if (addr.isFullColumnRange() || addr.isFullRowRange()) {
                continue;
            }
            spans.put(String.format("R%dC%d", addr.getFirstRow(), addr.getFirstColumn()),
                    new int[]{
                        addr.getLastRow() - addr.getFirstRow() + 1,
                        addr.getLastColumn() - addr.getFirstColumn() + 1
                    });
        }

        List<Object> _rows = new ArrayList<>();
        Iterator<Row> itr = sheet.iterator();
        while (itr.hasNext()) {
            HSSFRow row = (HSSFRow) itr.next();
            Map<String,Object> _row = new HashMap<>();
            _row.put("index", row.getRowNum());
            _row.put("height", row.getHeightInPoints());
            List<CellInfo> cells = new ArrayList<>();
            Iterator<Cell> it = row.cellIterator();
            while (it.hasNext()) {
                HSSFCell c = (HSSFCell) it.next();
                boolean hidden = false;
                CellInfo ci = new CellInfo(c);
                String rc = String.format("R%dC%d", c.getRowIndex(), c.getColumnIndex());
                if (spans.containsKey(rc)) {
                    ci.put("span", spans.get(rc));
                } else if (c.getCellType() == 3 /*HSSFCell.CELL_TYPE_BLANK*/) {
                    for (CellRangeAddress addr : sheet.getMergedRegions()) {
                        if (addr.isInRange(c.getRowIndex(), c.getColumnIndex())) {
                            hidden = true;
                            break;
                        }
                    }
                }
                hidden |= c.getSheet().isColumnHidden(c.getColumnIndex());
                if (!hidden) {
                    cells.add(ci);
                }
                // Check if cell has a hyperlink
                Hyperlink link = c.getHyperlink();
                if (link != null) {
                    switch (link.getType()) {
                        case Hyperlink.LINK_URL:
                        case Hyperlink.LINK_DOCUMENT:
                            ci.put("link", link.getAddress());
                            ci.put("linkLabel", link.getLabel());
                    }
                }
            }
            _row.put("cells", cells);
            _row.put("hidden", row.getZeroHeight());
            _rows.add(_row);
        }

        Map<String,Object> data = new HashMap<>();
        data.put("sheet-id", sheetId);
        data.put("name", sheet.getSheetName());
        data.put("rows", _rows);
        return data;
    }
    public static Map<String, Object> addRows(Workbook workbook, int sheetId, Integer[] point, String source) throws Exception {
        HSSFWorkbook hw = (HSSFWorkbook)workbook;
        HSSFSheet sheet = hw.getSheetAt(sheetId);
        HSSFName name = hw.getName(source);
        if (name == null) {
            throw new Exception("Invalid MS Excel name: " + source);
        }
        AreaReference[] arefs = AreaReference.generateContiguous(name.getRefersToFormula());
        CellRangeAddress rowsToCopy = null;
        for (AreaReference aref : arefs) {
            if (aref.getFirstCell().getSheetName().equals(sheet.getSheetName())) {
                rowsToCopy = new CellRangeAddress(
                    aref.getFirstCell().getRow(),
                    aref.getLastCell().getRow(),
                    aref.getFirstCell().getCol(),
                    aref.getLastCell().getCol()
                );
                break;
            }
        }
        if (rowsToCopy == null) {
            throw new Exception("MS Excel name " + source + " does not reference the target sheet");
        }
        List<String> preserveNames = new ArrayList<>();
        preserveNames.add(source);
        sheet.insertRowCopy(rowsToCopy, new CellRangeAddress(point[0], point[0], point[1], point[1]), preserveNames, LOGGER);

        PoiEvaluator eval = new PoiEvaluator(hw);
        eval.setIgnoreMissingWorkbooks(true);
        List<HSSFCell> modifiedCells = new ArrayList<>();
        eval.evaluateAll(modifiedCells);

        List<CellInfo> cells = new ArrayList<>();
        for (HSSFCell c : modifiedCells) {
            if (c.getSheet().equals(sheet)) {
                CellInfo ci = new CellInfo((HSSFCell) c);
                ci.put("sheet-id", sheetId);
                cells.add(ci);
            }
        }

        Map<String,Object> data = new HashMap<>();
        data.put("sheet-id", sheetId);
        data.put("cells", cells);
        return data;
    }
    public static Map<String, Object> putValue(Workbook workbook, int sheetId, Integer[] point, Object value)
            throws IOException, IllegalAccessException, InvocationTargetException, StorageServiceException {
        HSSFWorkbook hw = (HSSFWorkbook)workbook;
        HSSFSheet sheet = hw.getSheetAt(sheetId);
        HSSFCell cell = sheet.getRow(point[0]).getCell(point[1]);
        if (value instanceof Integer) {
            cell.setCellValue(((Integer)value).doubleValue());
        } else if (value instanceof Float) {
            cell.setCellValue(((Float)value).doubleValue());
        } else if (value instanceof Double) {
            cell.setCellValue((Double)value);
        } else if (value instanceof Date) {
            cell.setCellValue((Date)value);
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean)value);
        } else if (value instanceof String) {
            cell.setCellValue(value.toString());
        } else if (value == null) {
            cell.setCellType(Cell.CELL_TYPE_BLANK);
        } else {
            throw new IllegalArgumentException("Bad value type: " + value.getClass().getName());
        }

        PoiEvaluator eval = new PoiEvaluator(hw);
        eval.setIgnoreMissingWorkbooks(true);
        List<HSSFCell> modifiedCells = new ArrayList<>();
        modifiedCells.add(cell);
        eval.evaluateAll(modifiedCells);

        List<CellInfo> cells = new ArrayList<>();
        for (HSSFCell c : modifiedCells) {
            if (!c.getSheet().isColumnHidden(c.getColumnIndex()) && !hw.isSheetHidden(sheetId) && !hw.isSheetVeryHidden(sheetId)) {
                CellInfo ci = new CellInfo((HSSFCell)c);
                ci.put("sheet-id", hw.getSheetIndex(c.getSheet()));
                cells.add(ci);
            }
        }

        Map<String,Object> data = new HashMap<>();
        data.put("cells", cells);
        return data;
    }
    public static Map<String, Object> putValues(Workbook workbook, List<CellValueInfo> values)
            throws IOException, IllegalAccessException, InvocationTargetException, StorageServiceException {
        List<HSSFCell> modifiedCells = new ArrayList<>();

        HSSFWorkbook hw = (HSSFWorkbook)workbook;
        values.stream().forEach((v) -> {
            HSSFSheet sheet = hw.getSheetAt(v.getSheetId());
            HSSFCell cell = sheet.getRow(v.getPoint()[0]).getCell(v.getPoint()[1]);
            if (v.getValue() instanceof Integer) {
                cell.setCellValue(((Integer)v.getValue()).doubleValue());
            } else if (v.getValue() instanceof Float) {
                cell.setCellValue(((Float)v.getValue()).doubleValue());
            } else if (v.getValue() instanceof Double) {
                cell.setCellValue((Double)v.getValue());
            } else if (v.getValue() instanceof Date) {
                cell.setCellValue((Date)v.getValue());
            } else if (v.getValue() instanceof Boolean) {
                cell.setCellValue((Boolean)v.getValue());
            } else if (v.getValue() instanceof String) {
                cell.setCellValue(v.getValue().toString());
            } else if (v.getValue() == null) {
                cell.setCellType(Cell.CELL_TYPE_BLANK);
            } else {
                throw new IllegalArgumentException("Bad value type: " + v.getValue().getClass().getName());
            }
            modifiedCells.add(cell);
        });

        PoiEvaluator eval = new PoiEvaluator(hw);
        eval.setIgnoreMissingWorkbooks(true);
        eval.evaluateAll(modifiedCells);

        List<CellInfo> cells = new ArrayList<>();
        for (HSSFCell c : modifiedCells) {
            if (!c.getSheet().isColumnHidden(c.getColumnIndex())
                && !hw.isSheetHidden(hw.getSheetIndex(c.getSheet()))
                && !hw.isSheetVeryHidden(hw.getSheetIndex(c.getSheet()))
            ){
                CellInfo ci = new CellInfo((HSSFCell)c);
                ci.put("sheet-id", hw.getSheetIndex(c.getSheet()));
                cells.add(ci);
            }
        }

        Map<String,Object> data = new HashMap<>();
        data.put("cells", cells);
        return data;
    }
    public static List<String> nameData(CachedWorkbook cachedWorkbook, String name) throws IOException, StorageServiceException{
        HSSFWorkbook workbook = (HSSFWorkbook)cachedWorkbook.getWorkbook();
        HSSFName _name = workbook.getName(name);
        AreaReference[] arefs = AreaReference.generateContiguous(_name.getRefersToFormula());
        List<String> values = new ArrayList<>();
        for (AreaReference aref : arefs){
            for (CellReference ref : aref.getAllReferencedCells()){
                String val = workbook.getSheet(ref.getSheetName()).getRow(ref.getRow()).getCell(ref.getCol()).getStringCellValue();
                if (val != null){
                    values.add(val);
                }
            }
        }
        return values;
    }

    public PoiServer(final WorkbookCache workbookCache) {
        this.workbookCache = workbookCache;
    }

    public static class CellInfo extends HashMap {
        public CellInfo() {
        }
        public CellInfo(HSSFCell c) {
            this.put("point", new int[] { c.getRowIndex(), c.getColumnIndex() });
            switch(c.getCellType() == 2/*HSSFCell.CELL_TYPE_FORMULA*/? c.getCachedFormulaResultType(): c.getCellType()) {
                case 0: //HSSFCell.CELL_TYPE_NUMERIC:
                    if (DateUtil.isCellDateFormatted(c)) {
                        this.put("date-value", DateUtil.getJavaDate(c.getNumericCellValue()));
                    } else {
                        this.put("number-value", c.getNumericCellValue());
                    }
                    break;
                case 4: //HSSFCell.CELL_TYPE_BOOLEAN:
                    this.put("boolean-value", c.getBooleanCellValue());
                    break;
                case 1: //HSSFCell.CELL_TYPE_STRING:
                    this.put("string-value", c.getStringCellValue());
                    break;
                case 5: //HSSFCell.CELL_TYPE_ERROR:
                    this.put("error-value", (int)c.getErrorCellValue());
                    break;
                //case HSSFCell.CELL_TYPE_BLANK:
                //    break;
                //case HSSFCell.CELL_TYPE_FORMULA:
                //    break;
            }
            this.put("style", c.getCellStyle().getIndex());
        }
    }
    public static class CellValueInfo {
        private final int sheetId;
        private final Integer[] point;
        private final Object value;

        public int getSheetId() {
            return sheetId;
        }
        public Integer[] getPoint() {
            return point;
        }
        public Object getValue() {
            return value;
        }

        public CellValueInfo(int sheetId, Integer[] point, Object value){
            this.sheetId = sheetId;
            this.point = point;
            this.value = value;
        }
    }
}