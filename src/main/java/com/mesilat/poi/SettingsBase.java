package com.mesilat.poi;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import org.apache.commons.codec.binary.Base64;

public abstract class SettingsBase {
    public void save(final PluginSettings settings) throws Exception {
        final Object configData = this;
        Object result = AccessController.doPrivileged(new PrivilegedAction() {
            @Override
            public Object run() {
                for (Field field : configData.getClass().getDeclaredFields()) {
                    try {
                        if (field.getAnnotation(XmlElement.class) == null && field.getAnnotation(XmlAttribute.class) == null) {
                            continue;
                        }
                        field.setAccessible(true);
                        String name = field.getName();
                        Object value = field.get(configData);
                        if (value == null) {
                            settings.remove(configData.getClass().getName() + "." + name);
                        } else if (value instanceof String
                                || value instanceof Boolean
                                || value instanceof Integer
                                || value instanceof Long
                                || value instanceof Float
                                || value instanceof Double) {
                            settings.put(configData.getClass().getName() + "." + name, value.toString());
                        } else {
                            settings.put(configData.getClass().getName() + "." + name, toBase64(serialize(value)));
                        }
                    } catch (IllegalArgumentException ex) {
                        return ex;
                    } catch (IllegalAccessException ex) {
                        return ex;
                    } catch (IOException ex) {
                        return ex;
                    }
                }
                return Boolean.TRUE;
            }
        });
        if (result instanceof Exception) {
            throw (Exception) result;
        }
    }
    public SettingsBase() {
    }
    public SettingsBase(final PluginSettings settings) throws Exception {
        final Object configData = this;
        Object result = AccessController.doPrivileged(new PrivilegedAction() {
            @Override
            public Object run() {
                for (Field field : configData.getClass().getDeclaredFields()) {
                    try {
                        if (field.getAnnotation(XmlElement.class) == null && field.getAnnotation(XmlAttribute.class) == null) {
                            continue;
                        }
                        field.setAccessible(true);
                        String name = field.getName();
                        String value = (String) settings.get(configData.getClass().getName() + "." + name);
                        if (field.getType().equals(String.class)) {
                            field.set(configData, value);
                        } else if (field.getType().equals(Boolean.class)) {
                            field.set(configData, Boolean.parseBoolean(value));
                        } else if (field.getType().equals(Integer.class)) {
                            field.set(configData, Integer.parseInt(value));
                        } else if (field.getType().equals(Long.class)) {
                            field.set(configData, Long.parseLong(value));
                        } else if (field.getType().equals(Float.class)) {
                            field.set(configData, Float.parseFloat(value));
                        } else if (field.getType().equals(Double.class)) {
                            field.set(configData, Double.parseDouble(value));
                        } else {
                            field.set(configData, deserialize(fromBase64(value)));
                        }
                    } catch (IllegalArgumentException ex) {
                        return ex;
                    } catch (IllegalAccessException ex) {
                        return ex;
                    } catch (IOException ex) {
                        return ex;
                    } catch (ClassNotFoundException ex) {
                        return ex;
                    }
                }
                return Boolean.TRUE;
            }
        });
        if (result instanceof Exception) {
            throw (Exception) result;
        }
    }
    private static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(buf);
        out.writeObject(object);
        out.flush();
        out.close();
        return buf.toByteArray();
    }
    private static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream buf = new ByteArrayInputStream(data);
        ObjectInput in = new ObjectInputStream(buf);
        Object o = in.readObject();
        in.close();
        return o;
    }
    private static String toBase64(byte[] data) {
        return Base64.encodeBase64String(data);
    }
    private static byte[] fromBase64(String data) {
        return Base64.decodeBase64(data);
    }
}
