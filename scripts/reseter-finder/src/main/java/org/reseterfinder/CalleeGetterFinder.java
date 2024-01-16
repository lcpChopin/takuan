package org.reseterfinder;

import org.apache.bcel.classfile.*;
import org.apache.bcel.generic.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class CalleeGetterFinder {
    private String targetProjetDir;
    private String targetJar;
    private String targetClassPath;
    private String resetterFQN;

    public CalleeGetterFinder (String resetterFQN) {
        this.resetterFQN = resetterFQN;
    }

    public void findCalleeGetters() throws IOException {
        this.searchInClassPath();
    }

    public void searchInDir() throws IOException {
        List<String> targetClassFiles = getTargetClassesListFromDir();
        for (String f : targetClassFiles) {
            //System.out.println("Processing class " + f);
            ClassParser cp = new ClassParser(f);
            try {
                JavaClass clz = cp.parse();
                if (searchInClass(clz)) {
                    System.out.println("[INFO] Target API called in class " + clz.getClassName());
                }
            } catch (ClassFormatException e) {
                continue;
            }
        }
    }

    public void searchInJar() throws IOException {
        List<String> targetClassFiles = getTargetClassesListFromJar();
        for (String f : targetClassFiles) {
            //System.out.println(f);
            ClassParser cp = new ClassParser(this.targetJar, f);
            JavaClass clz = cp.parse();
            if (searchInClass(clz)) {
                //System.out.println("[INFO] Target API called in class " + clz.getClassName());
            }
        }
    }

    public void searchInClassPath() throws IOException {
        Map<String, List<String>> targetClassFiles = getTargetClassesListFromClassPath();
        for (String entry : targetClassFiles.keySet()) {
            System.out.println("=== SEARCH CLASSPATH ENTRY: " + entry);
            if (entry.endsWith(".jar")) {
                for (String f : targetClassFiles.get(entry)) {
                    //System.out.println(f);
                    ClassParser cp = new ClassParser(entry, f);
                    JavaClass clz = cp.parse();
                    if (searchInClass(clz)) {
                        //System.out.println("[INFO] Target API called in class " + clz.getClassName());
                    }
                }
            } else {
                for (String f : targetClassFiles.get(entry)) {
                    //System.out.println(f);
                    ClassParser cp = new ClassParser(f);
                    JavaClass clz = cp.parse();
                    if (searchInClass(clz)) {
                        //System.out.println("[INFO] Target API called in class " + clz.getClassName());
                    }
                }
            }
        }
    }

    public boolean searchInClass(JavaClass clz) {
        if (clz.isInterface()) {
            return false;
        }
        StringBuilder builder = new StringBuilder();
        for (int i=0; i<resetterFQN.lastIndexOf("."); i++) {
            builder.append(resetterFQN.charAt(i));
        }
        String resetterCalleeClassFQN = builder.toString();
        for (Method m: clz.getMethods()) {
            Type returnType = m.getReturnType();
            if (returnType.toString().equals(resetterCalleeClassFQN)) {
                System.out.println("Resetter's Callee Getter: " + clz.getClassName() + "." + m.getName() + m.getSignature());
            }
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
