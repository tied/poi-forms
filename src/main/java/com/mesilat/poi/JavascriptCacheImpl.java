package com.mesilat.poi;

import com.atlassian.confluence.core.BodyContent;
import com.atlassian.confluence.event.events.content.page.PageCreateEvent;
import com.atlassian.confluence.event.events.content.page.PageRemoveEvent;
import com.atlassian.confluence.event.events.content.page.PageRestoreEvent;
import com.atlassian.confluence.event.events.content.page.PageTrashedEvent;
import com.atlassian.confluence.event.events.content.page.PageUpdateEvent;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Named;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

@Scanned
@Named("javascriptCache")
public class JavascriptCacheImpl implements JavascriptCache, InitializingBean, DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.poi-forms");

    private final PageManager pageManager;
    private final EventPublisher eventPublisher;
    private final Map<Long,Map<String, MacroInfo>> cache = new HashMap<>();
    
    @Override
    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
    }
    @Override
    public void destroy() throws Exception {
        eventPublisher.unregister(this);
    }

    @EventListener
    public void onPageCreateEvent(PageCreateEvent event) {
        synchronized(cache){
            cache.remove(event.getPage().getId());
        }
    }
    @EventListener
    public void onPageUpdateEvent(PageUpdateEvent event) {
        synchronized(cache){
            cache.remove(event.getPage().getId());
        }
    }
    @EventListener
    public void pageTrashedEvent(PageTrashedEvent event) {
        synchronized(cache){
            cache.remove(event.getPage().getId());
        }
    }
    @EventListener
    public void pageRemoveEvent(PageRemoveEvent event) {
        synchronized(cache){
            cache.remove(event.getPage().getId());
        }
    }
    @EventListener
    public void pageRestoreEvent(PageRestoreEvent event) {
        synchronized(cache){
            cache.remove(event.getPage().getId());
        }
    }

    private void parse(Page page){
        Map<String, MacroInfo> macros = parsePage(page);
        synchronized(cache){
            cache.put(page.getId(), macros);
        }
    }
    private Map<String, MacroInfo> parsePage(Page page){
        Map<String, MacroInfo> macros = new HashMap<>();
        for (BodyContent content : page.getBodyContents()) {
            Document doc = Jsoup.parse(new StringBuilder()
                .append("<body>")
                .append(content.getBody())
                .append("</body>")
                .toString(), "");

            for (Element elt : doc.getElementsByTag("ac:structured-macro")) {
                if (!"poi-form".equals(elt.attr("ac:name"))){
                    continue;
                }

                MacroInfo macro = parseMacro(page, elt);
                if (macro != null){
                    macros.put(macro.getId(), macro);
                }
            }
        }
        return macros;
    }
    private MacroInfo parseMacro(Page page, Element macro) {
        String file = null;
        StringBuilder body = new StringBuilder();
        Map<String,String> params = new HashMap<>();

        for (Element bodyElt : macro.getElementsByTag("ac:plain-text-body")) {
            String text = getText(bodyElt);
            body.append(text);
        }
        for (Element paramElt : macro.getElementsByTag("ac:parameter")){
            String text = getText(paramElt);
            params.put(paramElt.attr("ac:name"), text);
            if ("file".equals(paramElt.attr("ac:name"))){
                file = text;
            }
        }
        if (file == null || file.isEmpty()){
            return null;
        } else {
            return new MacroInfo(createMacroKey(page.getId(), file), body.toString(), params);
        }
    }
    private static String getText(Element elt) {
        StringBuilder sb = new StringBuilder();
        for (Node node : elt.childNodes()) {
            if (node instanceof TextNode) {
                TextNode txt = (TextNode)node;
                sb.append(txt.getWholeText());
            }
        }
        return sb.toString();
    }
    private static String createMacroKey(long pageId, String file){
        return String.format("Script for %d: %s", pageId, file);
    }

    @Override
    public MacroInfo getMacroInfo(long pageId, String file) {
        synchronized(cache){
            if (cache.containsKey(pageId)){
                Map<String, MacroInfo> map = cache.get(pageId);
                String key = createMacroKey(pageId, file);
                return map.get(key);
            }
        }

        Page page = pageManager.getPage(pageId);
        if (page == null){
            return null;
        } else {
            parse(page);
        }

        synchronized(cache){
            if (cache.containsKey(pageId)){
                Map<String, MacroInfo> map = cache.get(pageId);
                String key = createMacroKey(pageId, file);
                return map.get(key);
            }
        }

        return null;
    }

    @Autowired
    public JavascriptCacheImpl(
        final PageManager pageManager,
        final EventPublisher eventPublisher)
    {
        this.pageManager = pageManager;
        this.eventPublisher = eventPublisher;
    }
}