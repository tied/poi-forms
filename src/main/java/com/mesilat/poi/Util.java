package com.mesilat.poi;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.util.HSSFColor;
import static org.apache.poi.ss.usermodel.CellStyle.*;

public class Util {

    public static String getCssFont(HSSFFont font) {
        StringBuilder sb = new StringBuilder();
        if (font.getBold()) {
            sb.append("bold");
        }
        if (font.getItalic()) {
            sb.append(sb.length() > 0 ? " " : "").append("italic");
        }
        sb.append(sb.length() > 0 ? " " : "").append(font.getFontHeightInPoints()).append("pt");
        sb.append(sb.length() > 0 ? " " : "").append(font.getFontName());
        return sb.toString();
    }

    public static String getColorHexString(HSSFColor color) {
        if (color == null) {
            return "";
        }
        short[] rgb = color.getTriplet();
        if (rgb[0] != 0 || rgb[1] != 0 || rgb[2] != 0) {
            return String.format("#%02X%02X%02X", (int) rgb[0], (int) rgb[1], (int) rgb[2]);
        } else {
            return "";
        }
    }

    public static String getAlignString(short align) {
        switch (align) {
            case ALIGN_LEFT:
                return "left";
            case ALIGN_CENTER:
            case ALIGN_CENTER_SELECTION:
                return "center";
            case ALIGN_RIGHT:
                return "right";
            case ALIGN_JUSTIFY:
            case ALIGN_FILL:
                return "justify";
            default:
                return "auto";
        }
    }

    public static String encodeURIComponent(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8")
                .replaceAll("\\+", "%20")
                .replaceAll("\\%21", "!")
                .replaceAll("\\%27", "'")
                .replaceAll("\\%28", "(")
                .replaceAll("\\%29", ")")
                .replaceAll("\\%7E", "~");
        } catch (UnsupportedEncodingException ignore) {
            return s;
        }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        BufferedInputStream bin = new BufferedInputStream(in);
        BufferedOutputStream bout = new BufferedOutputStream(out);
        try {
            int c;
            while ((c = bin.read()) != -1) {
                bout.write(c);
            }
        } finally {
            bout.flush();
        }
    }
}
