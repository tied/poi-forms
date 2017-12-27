package com.mesilat.poi;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.apache.poi.ss.formula.OperationEvaluationContext;
import org.apache.poi.ss.formula.eval.BlankEval;
import org.apache.poi.ss.formula.eval.BoolEval;
import org.apache.poi.ss.formula.eval.ErrorEval;
import org.apache.poi.ss.formula.eval.RefEval;
import org.apache.poi.ss.formula.eval.NumberEval;
import org.apache.poi.ss.formula.eval.StringEval;
import org.apache.poi.ss.formula.eval.ValueEval;
import org.apache.poi.ss.formula.functions.FreeRefFunction;
import org.apache.poi.ss.formula.udf.AggregatingUDFFinder;
import org.apache.poi.ss.formula.udf.DefaultUDFFinder;
import org.apache.poi.ss.formula.udf.UDFFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface WorkbookCache {
    public static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.poi-forms");

    CachedWorkbook getWorkbook(long pageId, String file) throws WorkbookCacheException;

    public static class UserFunction implements FreeRefFunction {
        private final Method callMember;
        private final Object obj;
        private final String name;

        @Override
        public ValueEval evaluate(ValueEval[] args, OperationEvaluationContext ec) {
            try {
                List<Object> values = new ArrayList<>();
                Arrays.asList(args).stream().forEach((arg) -> {
                    try {
                        values.add(evaluate(arg, ec));
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                        values.add(new StringEval(ex.getMessage()));
                    }
                });

                Object value = callMember.invoke(obj, name, values.toArray(new Object[]{}));
                if (value instanceof Boolean){
                    return BoolEval.valueOf((Boolean)value);
                } else if (value instanceof Integer) {
                    return new NumberEval((Integer)value);
                } else if (value instanceof Long) {
                    return new NumberEval((Long)value);
                } else if (value instanceof Float) {
                    return new NumberEval((Float)value);
                } else if (value instanceof Double) {
                    return new NumberEval((Double)value);
                } else {
                    return new StringEval(value.toString());
                }
            } catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException ex){
                return new StringEval(ex.getMessage());
            }
        }
        private Object evaluate(ValueEval arg, OperationEvaluationContext ec) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            if (arg instanceof ErrorEval){
                ErrorEval v = (ErrorEval)arg;
                return callMember.invoke(obj, "__internal_error__", new Object[]{ v.getErrorString() });
            } else if (arg instanceof BoolEval){
                BoolEval v = (BoolEval)arg;
                return v.getBooleanValue();
            } else if (arg instanceof NumberEval){
                NumberEval v = (NumberEval)arg;
                return v.getNumberValue();
            } else if (arg instanceof StringEval){
                StringEval v = (StringEval)arg;
                return v.getStringValue();
            } else if (arg instanceof RefEval){
                RefEval v = (RefEval)arg;
                return evaluate(v.getInnerValueEval(ec.getSheetIndex()), ec);
            } else if (arg instanceof BlankEval){
                return "";
            } else {
                return callMember.invoke(obj, "__internal_error__", new Object[]{ String.format("Cannot evaluate %s", arg) });
            }
        }

        public UserFunction(Method callMember, Object obj, String name) {
            this.callMember = callMember;
            this.obj = obj;
            this.name = name;
        }

        public static UDFFinder getTookpack(String script) {
            UDFFinder udfs;
            String nashornUrl = null;
            if (script == null || script.trim().isEmpty()){
                udfs = new DefaultUDFFinder(new String[]{}, new FreeRefFunction[]{});
            } else {
                try {
                    /*
                    NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
                    ScriptEngine engine = factory.getScriptEngine();
                    */
                    
                    ScriptEngineManager engineManager = new ScriptEngineManager();
                    ScriptEngine engine = engineManager.getEngineByName("nashorn");
                    Class nashornClass, scriptObjectMirrorClass = null;
                    if (engine == null){
                        File nashornPath = new File( System.getProperty("java.home") + "/lib/ext/nashorn.jar" );
                        if (!nashornPath.exists()){
                            throw new ScriptException("Nashorn library could not be found");
                        }
                        nashornUrl = "jar:file://" + nashornPath.getAbsolutePath() + "!/";
                        URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{ new URL(nashornUrl) });
                        nashornClass = Class.forName("jdk.nashorn.api.scripting.NashornScriptEngineFactory", true, urlClassLoader);
                        scriptObjectMirrorClass = Class.forName("jdk.nashorn.api.scripting.ScriptObjectMirror", true, urlClassLoader);
                        engineManager.registerEngineName("nashorn", (ScriptEngineFactory)nashornClass.newInstance());
                        engine = engineManager.getEngineByName("nashorn");
                    }
                    StringBuilder sb = new StringBuilder();
                    sb
                        .append("function factory(){return {")
                        .append(script)
                        .append(",__internal_error__: function(msg){return new Error(msg);}")
                        .append("};}");
                    engine.eval(sb.toString());
                    Object obj = engine.eval("factory()");
                    List<String> functionNames = new ArrayList<>();
                    for (Entry<String,Object> e: (Set<Entry<String, Object>>)scriptObjectMirrorClass.getMethod("entrySet").invoke(obj)){
                        if (e.getKey().startsWith("__internal_")){
                            continue;
                        }
                        if (e.getValue().toString().startsWith("function")){
                            functionNames.add(e.getKey());
                        }
                    }
                    List<FreeRefFunction> functions = new ArrayList<>();
                    for (String name : functionNames){
                        functions.add(new UserFunction(scriptObjectMirrorClass.getMethod("callMember", String.class, Object[].class), obj, name));
                    }
                    udfs = new DefaultUDFFinder(functionNames.toArray(new String[]{}), functions.toArray(new FreeRefFunction[]{}));
                } catch (ScriptException ex) {
                    LOGGER.error(String.format("Error parcing javascript: %s", script), ex);
                    udfs = new DefaultUDFFinder(new String[]{}, new FreeRefFunction[]{});
                } catch (MalformedURLException ex) {
                    LOGGER.error(String.format("Invalid URL: %s", nashornUrl), ex);
                    udfs = new DefaultUDFFinder(new String[]{}, new FreeRefFunction[]{});
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | SecurityException | InvocationTargetException ex) {
                    LOGGER.error(String.format("Failed to load nashorn jar: %s", nashornUrl), ex);
                    udfs = new DefaultUDFFinder(new String[]{}, new FreeRefFunction[]{});
                }
            }
            return new AggregatingUDFFinder(udfs);
        }
        public static URL getJarUrl(String dbJar) throws MalformedURLException {
            String urlString;
            File e = new File(dbJar);
            if (e.isAbsolute()) {
                urlString = "jar:file://" + dbJar + "!/";
            } else {
                urlString = "jar:file:" + System.getProperty("catalina.base") + File.separator + dbJar + "!/";
            }
            return new URL(urlString);
        }
    }
}