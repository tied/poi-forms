package com.mesilat.poi;

import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

@Scanned
@Named("poiReferenceDataService")
public class ReferenceDataServiceImpl implements ReferenceDataService, InitializingBean {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.poi-forms");

    private final PluginSettingsFactory pluginSettingsFactory;
    private final TransactionTemplate transactionTemplate;
    private final Map<String,List<String>> map = new HashMap<String,List<String>>();

    @Override
    public void afterPropertiesSet() throws Exception {
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
        List<String> allRefData = (List<String>)settings.get(ReferenceDataServiceImpl.class.getName());
        if (allRefData != null) {
            for (String refData : allRefData) {
                map.put(refData, null);
            }
        }
    }
    public List<String> list() throws IOException {
        List<String> list = new ArrayList<String>();
        list.addAll(map.keySet());
        return list;
    }
    public List<String> find(String group, String text) throws IOException {
        if (!map.containsKey(group)) {
            throw new IOException("Invalid refdata group: " + group);
        }
        synchronized(map) {
            if (map.get(group) == null) {
                load(group);
            }
        }
        List<String> result = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            result.addAll(map.get(group));
        } else {
            for (String string : map.get(group)) {
                if (StringUtils.containsIgnoreCase(string, text)) {
                    result.add(string);
                }
            }
        }
        return result;
    }
    public void store(final String group, final List<String> strings) {
        Object result = transactionTemplate.execute(new TransactionCallback(){
            @Override
            public Object doInTransaction()
            {
                try
                {
                    PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
                    settings.put(ReferenceDataServiceImpl.class.getName() + "." + group, strings);
                    map.put(group, strings);
                    List<String> all = new ArrayList<String>();
                    for (String key: map.keySet()) {
                        all.add(key);
                    }
                    settings.put(ReferenceDataServiceImpl.class.getName(), all);
                    return Boolean.TRUE;
                }
                catch(Throwable ex) {
                    return ex;
                }   
            }
        });
        if (result instanceof Throwable) {
            LOGGER.error("Failed to save reference data " + group, (Throwable)result);
        }
        
    }

    private void load(String group) throws IOException {
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
        List<String> strings = (List<String>)settings.get(ReferenceDataServiceImpl.class.getName() + "." + group);
        map.put(group, strings);
    }

    @Autowired
    public ReferenceDataServiceImpl(final @ComponentImport PluginSettingsFactory pluginSettingsFactory,
            final @ComponentImport TransactionTemplate transactionTemplate) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public List<String> find(long pageId, String file, String name) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
