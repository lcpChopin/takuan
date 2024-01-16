package org.reseterfinder;

import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class MinePrims {

    private String targetProjetDir;
    private String targetJar;
    private String targetClassPath;
    private String targetFieldStr;
    private String victimTestStr;
    private boolean mineAllClasses;

    public MinePrims (String fieldStr, String victimTestStr) {
        this.targetFieldStr = fieldStr;
        this.victimTestStr = victimTestStr;
        this.mineAllClasses = false;
    }

    public MinePrims (String fieldStr, String victimTestStr, boolean mineAllClasses) {
        this.targetFieldStr = fieldStr;
        this.victimTestStr = victimTestStr;
        this.mineAllClasses = mineAllClasses;
    }

    public void minePrims() throws IOException {
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

    public boolean searchInClass(String targetFieldStr, JavaClass clz) {
        if (clz.isInterface()) {
            return false;
        }
        if (!this.mineAllClasses) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < targetFieldStr.lastIndexOf("."); i++) {
                builder.append(targetFieldStr.charAt(i));
            }
            String targetClassFQN = builder.toString();
            StringBuilder builder2 = new StringBuilder();
            for (int i = 0; i < this.victimTestStr.lastIndexOf("."); i++) {
                builder2.append(this.victimTestStr.charAt(i));
            }
            String victimTestFQN = builder2.toString();
            if (!clz.getClassName().equals(targetClassFQN) && !clz.getClassName().equals(victimTestFQN)) {
                return false;
            }
        }
        List<String> methodShortNames = new ArrayList<>();
        List<String> fieldShortNames = new ArrayList<>();
        for (Method m : clz.getMethods()) {
            if (!methodShortNames.contains(m.getName())) {
                //System.out.println(m.getName());
                methodShortNames.add(m.getName());
            }
        }
        for (Field f : clz.getFields()) {
            if (!fieldShortNames.contains(f.getName())) {
                //System.out.println(f.getName());
                fieldShortNames.add(f.getName());
            }
        }
        System.out.println("=== " + clz.getClassName() + " String Literals: =============");
        ConstantPool pool = clz.getConstantPool();
        for (int i = 0; i < pool.getLength(); i++) {
            Constant c = pool.getConstant(i);
            if (c instanceof ConstantString) {
                int stringUTFIndex = ((ConstantString) c).getStringIndex();
                String UTFValue = ((ConstantUtf8)(pool.getConstant(stringUTFIndex))).getBytes();
                if (UTFValue.trim().equals("")) {
                    continue;
                }
                if (UTFValue.contains("/")) {
                    continue;
                }
                if (methodShortNames.contains(UTFValue) || fieldShortNames.contains(UTFValue)) {
                    continue;
                }
                System.out.println(UTFValue);
            }
        }
        System.out.println("==================================");
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
