package org.reseterfinder;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class JarScanner {

    private String targetJar;
    private List<String> classes = new ArrayList<>();
    private List<String> methods = new ArrayList<>();
    private List<String> fields = new ArrayList<>();

    public void scanJar() throws IOException {
        List<String> targetClassFiles = getTargetClassesListFromJar();
        for (String f : targetClassFiles) {
            //System.out.println(f);
            ClassParser cp = new ClassParser(this.targetJar, f);
            JavaClass clz = cp.parse();
            classes.add(clz.getClassName());
            scanClass(clz);
        }
    }

    public void scanClass(JavaClass clz) {
        for (Method method : clz.getMethods()) {
            //System.out.println(method);
            methods.add(clz.getClassName() + "." + method.getName() + method.getSignature());
        }
        for (Field field : clz.getFields()) {
            fields.add(clz.getClassName() + "." + field.getName());
        }
    }

    public List<String> getTargetClassesListFromJar() {
        ArrayList<String> classes = new ArrayList<>();
        try {
            JarFile jar = new JarFile(this.targetJar);
            Stream<JarEntry> entries = APISearch.enumerationAsStream(jar.entries());
            entries.forEach(e -> {
                if (e.isDirectory() || !e.getName().endsWith(".class")) {
                    // do nothing
                } else {
                    classes.add(e.getName());
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
        return classes;
    }

    public String getTargetJar() {
        return targetJar;
    }

    public void setTargetJar(String targetJar) {
        this.targetJar = targetJar;
    }

    public List<String> getClasses() {
        return classes;
    }

    public void setClasses(List<String> classes) {
        this.classes = classes;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }
}
