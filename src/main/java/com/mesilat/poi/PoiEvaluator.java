package com.mesilat.poi;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.formula.IStabilityClassifier;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

public class PoiEvaluator extends HSSFFormulaEvaluator {

    private final HSSFWorkbook _book;

    public PoiEvaluator(HSSFWorkbook workbook) {
        super(workbook);
        _book = workbook;
    }

    public PoiEvaluator(HSSFWorkbook workbook, IStabilityClassifier stabilityClassifier) {
        super(workbook, stabilityClassifier);
        _book = workbook;
    }

    public void evaluateAll(List<HSSFCell> modifiedCells) throws IllegalAccessException, InvocationTargetException {
        evaluateAllFormulaCells(modifiedCells);
    }
    private void evaluateAllFormulaCells(List<HSSFCell> modifiedCells) throws IllegalAccessException, InvocationTargetException {
        for (int i = 0; i < _book.getNumberOfSheets(); i++) {
            Sheet sheet = _book.getSheetAt(i);

            final Method method = getEvaluateFormulaCellValueMethod();
            AccessController.doPrivileged((PrivilegedAction) () -> {
                method.setAccessible(true);
                return null;
            });
            
            for (Row r : sheet) {
                for (Cell c : r) {
                    if (c.getCellType() == 2 /*CellType.FORMULA*/) {
                        //CellValue cv = evaluateFormulaCellValue(c); <- private access Gr-r-r!!!
                        CellValue cv = (CellValue)method.invoke(this, c);
                        setCellValue((HSSFCell)c, cv, modifiedCells);
                    }
                }
            }
        }
    }
    private Method getEvaluateFormulaCellValueMethod() {
        try {
            return HSSFFormulaEvaluator.class.getDeclaredMethod("evaluateFormulaCellValue", Cell.class);
        } catch(NoSuchMethodException | SecurityException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void setCellValue(HSSFCell cell, CellValue cv, List<HSSFCell> modifiedCells) {
        switch (cv.getCellType()) {
            case BOOLEAN:
                if (cell.getCachedFormulaResultType() != 4 /* BOOLEAN */ || cell.getBooleanCellValue() != cv.getBooleanValue()) {
                    modifiedCells.add(cell);
                }
                cell.setCellValue(cv.getBooleanValue());
                break;
            case ERROR:
                if (cell.getCachedFormulaResultType() != 5 /* ERROR */ || cell.getErrorCellValue() != cv.getErrorValue()) {                    
                    modifiedCells.add(cell);
                }
                cell.setCellErrorValue(cv.getErrorValue());
                break;
            case NUMERIC:
                if (cell.getCachedFormulaResultType() != 0 /* NUMERIC */ || cell.getNumericCellValue() != cv.getNumberValue()) {
                    modifiedCells.add(cell);
                }
                cell.setCellValue(cv.getNumberValue());
                break;
            case STRING:
                if (cell.getCachedFormulaResultType() != 1 /* STRING */
                        || cell.getStringCellValue() == null && cv.getStringValue() != null
                        || cell.getStringCellValue() != null && !cell.getStringCellValue().equals(cv.getStringValue())
                ) {
                    modifiedCells.add(cell);
                }
                cell.setCellValue(new HSSFRichTextString(cv.getStringValue()));
                break;
            //case BLANK:
            // never happens - blanks eventually get translated to zero
            //case FORMULA:
            // this will never happen, we have already evaluated the formula
            default:
                throw new IllegalStateException("Unexpected cell value type (" + cv.getCellType() + ")");
        }
    }
}
