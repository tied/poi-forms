package com.mesilat.jmx;

import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

public class SimpleMBean extends StandardMBean {
    private final Class mbeanInterface;

    @Override
    protected String getDescription(MBeanInfo info) {
        if (info == null) {
            return null;
        }

        try {
            Class c = Class.forName(info.getClassName());
            Description desc = (Description)c.getAnnotation(Description.class);
            if (desc != null) {
                return desc.value();
            }
            
            desc = (Description)mbeanInterface.getAnnotation(Description.class);
            if (desc != null) {
                return desc.value();
            }
        } catch(Exception ignore) {
        }
        return info.getDescription();
    }
    @Override
    protected String getDescription(MBeanAttributeInfo info) {
        if (info == null) {
            return null;
        }

        Method method = null;
        do {
            try {
                method = mbeanInterface.getMethod("get" + info.getName());
                if (method != null) {
                    break;
                }
            } catch (NoSuchMethodException ignore) {
            } catch (SecurityException ignore) {
            }

            try {
                method = mbeanInterface.getMethod("is" + info.getName());
                if (method != null) {
                    break;
                }
            } catch (NoSuchMethodException ignore) {
            } catch (SecurityException ignore) {
            }

            try {
                method = mbeanInterface.getMethod("set" + info.getName());
            } catch(NoSuchMethodException ignore) {
            } catch(SecurityException ignore) {
            }
        } while(false);

        try {
            return method.getAnnotation(Description.class).value();
        } catch(Exception ignore) {
            return super.getDescription(info);
        }
    }
    @Override
    protected String getDescription(MBeanOperationInfo op) {
        if (op == null) {
            return null;
        }

        Method method = null;
        Method[] methods = mbeanInterface.getMethods();
        for (Method m : methods) {
            if (!m.getName().equals(op.getName())) {
                continue;
            }
            Class[] params = m.getParameterTypes();
            if (params.length != op.getSignature().length) {
                continue;
            }
            boolean found = true;
            for (int i = 0; i < params.length; i ++) {
                if (!params[i].getName().equals(((MBeanParameterInfo)op.getSignature()[i]).getType())) {
                    found = false;
                    break;
                }
            }
            if (found) {
                method = m;
                break;
            }
        }

        if (method == null) {
            return super.getDescription(op);
        }

        try {
            return method.getAnnotation(Description.class).value();
        } catch(Exception ignore) {
            return super.getDescription(op);
        }
    }
    @Override
    protected String getDescription(MBeanOperationInfo op, MBeanParameterInfo param, int sequence) {
        if (op == null || param == null) {
            return null;
        }

        Method method = null;
        Method[] methods = mbeanInterface.getMethods();
        for (Method m : methods) {
            if (!m.getName().equals(op.getName())) {
                continue;
            }

            Class[] params = m.getParameterTypes();
            if (params.length != op.getSignature().length) {
                continue;
            }
            boolean found = true;
            for (int i = 0; i < params.length; i ++) {
                if (!params[i].getName().equals(((MBeanParameterInfo)op.getSignature()[i]).getType())) {
                    found = false;
                    break;
                }
            }
            if (found) {
                method = m;
                break;
            }
        }

        if (method == null) {
            return super.getDescription(op, param, sequence);
        }

        for (Annotation a : method.getParameterAnnotations()[sequence]) {
            if (a instanceof Description) {
                return ((Description)a).value();
            }
        }

        return super.getDescription(op, param, sequence);
    }

    public SimpleMBean(Class mbeanInterface) throws NotCompliantMBeanException {
        super(mbeanInterface);
        this.mbeanInterface = mbeanInterface;
    }

    protected static MBeanServer getMBeanServer() {
        return ManagementFactory.getPlatformMBeanServer();
    }
    public String getMBeanName() {
        MBeanName name = this.getClass().getAnnotation(MBeanName.class);
        if (name != null) {
            return name.value();
        }
        name = (MBeanName)mbeanInterface.getAnnotation(MBeanName.class);
        if (name != null) {
            return name.value();
        }
        return null;
    }
    public void registerMBean() throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        synchronized(this) {
            ObjectName objectName;
            try {
                String name = getMBeanName();
                if (name == null) {
                    throw new RuntimeException("Failed to get MBeanName for an object of type " + this.getClass().getName());
                } else {
                    objectName = new ObjectName(name);
                }
            } catch(MalformedObjectNameException ex) {
                throw new RuntimeException(ex);
            }

            getMBeanServer().registerMBean(this, objectName);
        }
    }
    public void unregisterMBean() {
        synchronized(this) {
            try {
                ObjectName objectName = new ObjectName(getMBeanName());
                getMBeanServer().unregisterMBean(objectName);
            } catch (InstanceNotFoundException ex) {
            } catch (MBeanRegistrationException ex) {
            } catch (MalformedObjectNameException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}