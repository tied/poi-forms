package com.mesilat.poi.web;

import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class AddRowsInfo {
    @XmlElement
    private String file;
    @XmlElement
    private Long version;
    @XmlElement
    private Integer sheetId;
    @XmlElement
    private Integer[] before;
    @XmlElement
    private String name;

    public String getFile() {
        return file;
    }
    public Long getVersion() {
        return version;
    }
    public Integer getSheetId() {
        return sheetId;
    }
    public Integer[] getBefore() {
        return before;
    }
    public String getName() {
        return name;
    }
    public AddRowsInfo() {
    }

    public AddRowsInfo(Object obj) {
        Map<String, Object> map = (Map<String, Object>) obj;
        file = (String) map.get("file");
        version = (Long) map.get("version");
        sheetId = (Integer) map.get("sheet-id");
        before = ((List<Integer>) map.get("before")).toArray(new Integer[]{});
        name = map.get("add-rows").toString();
    }
}