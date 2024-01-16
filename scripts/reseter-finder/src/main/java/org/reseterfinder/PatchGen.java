package org.reseterfinder;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

public class PatchGen {

    public String genPatch(Resetter resetter) {
        JavaClass clz = resetter.getClz();
        Method method = resetter.getMethod();
        String patch = "";
        if (method.isStatic()) {
            patch = clz.getClassName() + "." + method.getName() + "();"; // For now assume no arg
        } else {
            patch = clz.getClassName() + ".getInstance()." + method.getName() + "();"; // For now assume no arg
        }
        return patch;
    }
}
