package com.mesilat.poi;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorkbookCacheSettings implements WorkbookCacheMBean {
    private int maxFiles = 64;
    private long maxBytes = 256 * 1024 * 1024;

    @Override
    public int getMaxFiles() {
        return maxFiles;
    }

    @Override
    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    @Override
    public long getMaxBytes() {
        return maxBytes;
    }

    @Override
    public void setMaxBytes(long size) {
        this.maxBytes = size;
    }

    @Override
    public void purge() {
        throw new UnsupportedOperationException("Not supported");
    }   

    public static WorkbookCacheSettings fromSettings(Map<String,String> settings) throws SettingsException {
        WorkbookCacheSettings wcs = new WorkbookCacheSettings();
        if (settings.containsKey(MAX_FILES)){
            try {
                wcs.maxFiles = Integer.parseInt(settings.get(MAX_FILES));
            } catch(NumberFormatException ex){
                throw new SettingsException(String.format("Invalid %s value: %s", MAX_FILES, settings.get(MAX_FILES)));
            }
        }
        if (settings.containsKey(MAX_BYTES)){
            Matcher m = BYTES.matcher(settings.get(MAX_BYTES));
            if (m.matches()){
                wcs.maxBytes = Long.parseLong(m.group(1));
                if (m.group(2) != null){
                    switch(m.group(2)){
                        case "k":
                        case "K":
                            wcs.maxBytes *= 1024;
                            break;
                        case "m":
                        case "M":
                            wcs.maxBytes *= 1024 * 1024;
                            break;
                        case "g":
                        case "G":
                            wcs.maxBytes *= 1024 * 1024 * 1024;
                    }
                }
            } else {
                throw new SettingsException(String.format("Invalid %s value: %s", MAX_BYTES, settings.get(MAX_BYTES)));
            }
        }
        return wcs;
    }
    public void apply(WorkbookCache cache){
        WorkbookCacheMBean mbean = (WorkbookCacheMBean)cache;
        mbean.setMaxFiles(maxFiles);
        mbean.setMaxBytes(maxBytes);
    }
    public static Map<String,String> getSettings(PluginSettings pluginSettings){
        Map<String,String> settings = new HashMap<>();
        for (String key : SETTINGS){
            if (pluginSettings.get(key) != null){
                settings.put(key, pluginSettings.get(key).toString());
            }
        }
        return settings;
    }

    private static final String MAX_FILES = "max-files";
    private static final String MAX_BYTES = "max-bytes";
    protected static final String[] SETTINGS = new String[] { MAX_FILES, MAX_BYTES };
    private static final Pattern BYTES = Pattern.compile("^(\\d+)([k,K,m,M,g,G])?");
}