package org.reseterfinder;

import org.apache.bcel.classfile.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class FieldRefClassesFinder {

    private String targetProjetDir;
    private String targetJar;
    private String targetClassPath;
    private String targetFieldStr;
    private List<String> visitedClasses;
    private List<String> toVisitClasses;
    private List<String> allRefClasses;

    public FieldRefClassesFinder (String fieldStr) {
        this.targetFieldStr = fieldStr;
        this.visitedClasses = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (int i=0; i<targetFieldStr.lastIndexOf("."); i++) {
            builder.append(targetFieldStr.charAt(i));
        }
        String targetClassFQN = builder.toString();
        System.out.println("Ref Class: " + targetClassFQN);
        this.toVisitClasses = new ArrayList<>();
        this.toVisitClasses.add(targetClassFQN);
        this.allRefClasses = new ArrayList<>();
    }

    public void findRefClassesTransitiveClosure() throws IOException {
        this.searchInClassPath(this.targetFieldStr);
    }

    public void searchInDir(String targetField) throws IOException {
        List<String> targetClassFiles = getTargetClassesListFromDir();
        for (String f : targetClassFiles) {
            //System.out.println("Processing class " + f);
            ClassParser cp = new ClassParser(f);
            try {
                JavaClass clz = cp.parse();
                if (searchInClass(targetField, clz)) {
                    System.out.println("[INFO] Target API called in class " + clz.getClassName());
                }
            } catch (ClassFormatException e) {
                continue;
            }
        }
    }

    public void searchInJar(String targetField) throws IOException {
        List<String> targetClassFiles = getTargetClassesListFromJar();
        for (String f : targetClassFiles) {
            //System.out.println(f);
            ClassParser cp = new ClassParser(this.targetJar, f);
            JavaClass clz = cp.parse();
            if (searchInClass(targetField, clz)) {
                //System.out.println("[INFO] Target API called in class " + clz.getClassName());
            }
        }
    }

    public void searchInClassPath(String targetField) throws IOException {
        Map<String, List<String>> targetClassFiles = getTargetClassesListFromClassPath();
        for (String entry : targetClassFiles.keySet()) {
            System.out.println("=== SEARCH CLASSPATH ENTRY: " + entry);
            if (entry.endsWith(".jar")) {
                for (String f : targetClassFiles.get(entry)) {
                    //System.out.println(f);
                    ClassParser cp = new ClassParser(entry, f);
                    JavaClass clz = cp.parse();
                    if (searchInClass(targetField, clz)) {
                        //System.out.println("[INFO] Target API called in class " + clz.getClassName());
                    }
                }
            } else {
                for (String f : targetClassFiles.get(entry)) {
                    //System.out.println(f);
                    ClassParser cp = new ClassParser(f);
                    JavaClass clz = cp.parse();
                    if (searchInClass(targetField, clz)) {
                        //System.out.println("[INFO] Target API called in class " + clz.getClassName());
                    }
                }
            }
        }
    }

    public boolean searchInClass(String targetFieldStr, JavaClass clz) throws IOException {
        if (clz.isInterface()) {
            return false;
        }
        if (this.visitedClasses.contains(clz.getClassName())) {
            return false;
        }
        if (!this.toVisitClasses.contains(clz.getClassName())) {
            return false;
        }
        System.out.println("Visit Class: " + clz.getClassName());
        if (!this.allRefClasses.contains(clz.getClassName())) {
            this.allRefClasses.add(clz.getClassName());
        }
        ConstantPool pool = clz.getConstantPool();
        for (int i = 0; i < pool.getLength(); i++) {
            Constant c = pool.getConstant(i);
            //System.out.println(c);
            if (c instanceof ConstantClass) {
                int classNameIndex = ((ConstantClass) c).getNameIndex();
                String classNameValue = ((ConstantUtf8)(pool.getConstant(classNameIndex))).getBytes();
                classNameValue = classNameValue.replace("/", ".");
                if (classNameValue.startsWith("java.")) {
                    continue;
                }
                if (!this.allRefClasses.contains(classNameValue)) {
                    System.out.println("Ref Class: " + classNameValue);
                    this.allRefClasses.add(classNameValue);
                }
                if (!this.toVisitClasses.contains(classNameValue)) {
                    this.toVisitClasses.add(classNameValue);
                }
            }
        }
        System.out.println("==================================");
        if (!this.visitedClasses.contains(clz.getClassName())) {
            this.visitedClasses.add(clz.getClassName());
        }
        this.toVisitClasses.remove(clz.getClassName());
        if (!this.toVisitClasses.isEmpty()) {
            this.searchInClassPath(this.targetFieldStr);
        }
        return false;
    }

    public List<String> getTargetClassesListFromDir() throws IOException {
        ArrayList<String> classes = new ArrayList<>();
        Files.walk(Paths.get(this.targetProjetDir))
        .filter(Files::isRegularFile)
        .forEach((f)->{
            if(f.toString().endsWith(".class")) {
                classes.add(f.toString());
            }
        });
        return classes;
    }

    public List<String> getTargetClassesListFromJar() {
        ArrayList<String> classes = new ArrayList<>();
        try {
            JarFile jar = new JarFile(this.targetJar);
            Stream<JarEntry> entries = enumerationAsStream(jar.entries());
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

    public Map<String, List<String>> getTargetClassesListFromClassPath() throws IOException {
        Map<String, List<String>> classes = new HashMap<>();
        String classpath = this.targetClassPath;
        for (String entry : classpath.split(":")) {
            if (entry.endsWith(".jar")) {
                this.targetJar = entry;
                List<String> jarClasses = this.getTargetClassesListFromJar();
                classes.put(entry, jarClasses);
            } else {
                this.targetProjetDir = entry;
                List<String> dirClasses = this.getTargetClassesListFromDir();
                classes.put(entry, dirClasses);
            }
        }
        return classes;
    }

    public static <T> Stream<T> enumerationAsStream(Enumeration<T> e) {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        new Iterator<T>() {
                            public T next() {
                                return e.nextElement();
                            }

                            public boolean hasNext() {
                                return e.hasMoreElements();
                            }
                        },
                        Spliterator.ORDERED), false);
    }

    public String getTargetProjetDir() {
        return targetProjetDir;
    }

    public void setTargetProjetDir(String targetProjetDir) {
        this.targetProjetDir = targetProjetDir;
    }

    public String getTargetJar() {
        return targetJar;
    }

    public void setTargetJar(String targetJar) {
        this.targetJar = targetJar;
    }

    public String getTargetClassPath() {
        return targetClassPath;
    }

    public void setTargetClassPath(String targetClassPath) {
        this.targetClassPath = targetClassPath;
    }
}
