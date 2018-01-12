package com.mesilat.poi;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.util.velocity.VelocityUtils;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.webresource.api.assembler.PageBuilderService;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Scanned
public class PoiFormMacro implements Macro {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.poi-forms");
    
    private final I18nResolver resolver;
    private final PageBuilderService pageBuilderService;

    @Override
    public Macro.BodyType getBodyType() {
        return Macro.BodyType.PLAIN_TEXT;
    }
    @Override
    public Macro.OutputType getOutputType() {
        return Macro.OutputType.BLOCK;
    }
    @Override
    public String execute(Map<String,String> params, String body, ConversionContext conversionContext) throws MacroExecutionException {
        try {
            Map context = new HashMap();
            context.putAll(params);
            context.put("i18n", resolver);
            context.put("protection", params.containsKey("protection") && params.get("protection").equals("true"));
            context.put("body", body);
            context.put("pageId", conversionContext.getEntity().getIdAsString());
            if ("preview".equals(conversionContext.getOutputType())) {
                return VelocityUtils.getRenderedTemplate("templates/preview.vm", context);
            } else {
                pageBuilderService.assembler().resources()
                    .requireContext("poi-macro")
                    .requireWebResource("confluence.web.resources:moment");
                return VelocityUtils.getRenderedTemplate("templates/workbook.vm", context);
            }
        } catch(Exception ex) {
            LOGGER.error("Error in PoiFormMacro.execute", ex);
            throw new MacroExecutionException(ex);
        }        
    }

    @Inject
    public PoiFormMacro(
        final I18nResolver resolver,
        final PageBuilderService pageBuilderService
    ){
        this.resolver = resolver;
        this.pageBuilderService = pageBuilderService;
    }
}