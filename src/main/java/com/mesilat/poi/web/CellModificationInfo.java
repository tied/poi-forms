package com.mesilat.poi.web;

import com.mesilat.poi.PoiServer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class CellModificationInfo {
    @XmlElement
    private String file;
    @XmlElement
    private Long version;
    @XmlElement
    private Integer sheetId;
    @XmlElement
    private Integer[] point;
    @XmlElement
    private Object value;

    public String getFile() {
        return file;
    }
    public Long getVersion() {
        return version;
    }
    public Integer getSheetId() {
        return sheetId;
    }
    public Integer[] getPoint() {
        return point;
    }
    public Object getValue() {
        return value;
    }

    public CellModificationInfo(){}

    public CellModificationInfo(Object obj) {
        Map<String, Object> map = (Map<String, Object>) obj;
        file = (String) map.get("file");
        version = (Long) map.get("version");
        sheetId = (Integer) map.get("sheet-id");
        point = ((List<Integer>) map.get("point")).toArray(new Integer[]{});
        if (map.containsKey("number-value")) {
            value = map.get("number-value");
        } else if (map.containsKey("date-value")) {
            value = new Date((Long) map.get("date-value"));
        } else if (map.containsKey("boolean-value")) {
            value = map.get("boolean-value");
        } else if (map.containsKey("string-value")) {
            value = map.get("string-value");
        } else if (map.containsKey("value")) {
            value = map.get("value");
        }
    }

    public static List<CellModificationInfo> parse(Object data){
        final List<CellModificationInfo> cmi = new ArrayList<>();
        if (data instanceof Map){
            cmi.add(new CellModificationInfo(data));
        } else if (data instanceof List){
            List list = (List)data;
            list.stream().forEach((o) -> {
                cmi.add(new CellModificationInfo(o));
            });
        }
        return cmi;
    }
    public static List<PoiServer.CellValueInfo> convert(List<CellModificationInfo> infos){
        return infos.stream().map((o) -> {
            return new PoiServer.CellValueInfo(o.getSheetId(), o.getPoint(), o.getValue());
        }).collect(Collectors.toList());
    }
}