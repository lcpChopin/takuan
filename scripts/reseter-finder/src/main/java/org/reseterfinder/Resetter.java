package org.reseterfinder;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

public class Resetter {

    private JavaClass clz;
    private Method method;
    private String fqnAndSig;

    public Resetter(JavaClass clz, Method method) {
        this.clz = clz;
        this.method = method;
        this.fqnAndSig = clz.getClassName() + "." + method.getName() + method.getSignature();
    }

    public JavaClass getClz() {
        return clz;
    }

    public void setClz(JavaClass clz) {
        this.clz = clz;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public String getFqnAndSig() {
        return fqnAndSig;
    }

    public void setFqnAndSig(String fqnAndSig) {
        this.fqnAndSig = fqnAndSig;
    }
}
