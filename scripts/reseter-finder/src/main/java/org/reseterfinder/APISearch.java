package org.reseterfinder;

import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class APISearch {

    private String targetProjetDir;
    private String targetJar;
    private String targetClassPath;

    public APISearch() {

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

    public boolean searchInClass(String targetField, JavaClass clz) {
        if (clz.isInterface()) {
            return false;
        }
        boolean isMatched = false;
        for (Method method : clz.getMethods()) {
            //System.out.println(method);
            if (searchInMethod(targetField, method, clz)) {
                System.out.println("[INFO]: Target API called in class " + clz.getClassName() +
                        ", method " + method.getName() + method.getSignature());
                isMatched = true;
            }
        }
        return isMatched;
    }

    public boolean searchInMethod(String targetField, Method method, JavaClass clz) {
        if (method.isAbstract() || method.isNative()) {
            return false;
        }
        boolean isMatch = false;
        String className = clz.getClassName();
        ConstantPoolGen constantPoolGen = new ConstantPoolGen(clz.getConstantPool());
        MethodGen methodGen = new MethodGen(method, className, constantPoolGen);
        InstructionList instList = methodGen.getInstructionList();
        for (int i = 0; i < instList.getInstructions().length; i++) {
            Instruction inst = instList.getInstructions()[i];
            // setting static field
            if (inst instanceof PUTSTATIC) {
                String fieldName = clz.getClassName() + "." + ((PUTSTATIC) inst).getFieldName(constantPoolGen);
                Type fieldType = ((PUTSTATIC) inst).getFieldType(constantPoolGen);
                if (fieldName.equals(targetField)) {
                    String resetter_method_fqn = clz.getClassName() + "." + method.getName() + method.getSignature();
                    System.out.println("Field Name: " + fieldName + ", Field Type: " + fieldType + ", PUTSTATIC method: " + resetter_method_fqn);
                }
            }
            // getting static field
            else if (inst instanceof GETSTATIC) {
                String fieldName = clz.getClassName() + "." + ((GETSTATIC) inst).getFieldName(constantPoolGen);
                Type fieldType = ((GETSTATIC) inst).getFieldType(constantPoolGen);
                if (fieldName.equals(targetField)) {
                    String resetter_method_fqn = clz.getClassName() + "." + method.getName() + method.getSignature();
                    System.out.println("Field Name: " + fieldName + ", Field Type: " + fieldType + ", GETSTATIC method: " + resetter_method_fqn);
                }
            }
        }
        return isMatch;
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
