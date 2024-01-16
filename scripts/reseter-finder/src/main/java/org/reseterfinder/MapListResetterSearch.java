package org.reseterfinder;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import javax.xml.validation.Schema;

public class MapListResetterSearch {

    private String targetProjetDir;
    private String targetJar;
    private String targetClassPath;

    private List<URL> urls = new ArrayList<>();

    private String xmlPath;

    private List<Resetter> resetters = new ArrayList<>();

    public MapListResetterSearch() {
    }

    public void setUpJarURLsList() throws IOException {
        String classpath = this.targetClassPath;
        for (String entry : classpath.split(":")) {
            if (entry.endsWith(".jar")) {
                URL url = new File(entry).toURI().toURL();
                if (! this.urls.contains(url)) {
                    this.urls.add(url);
                }
            } else {
                List<String> jars = this.getJarsListFromDir(entry);
                for (String jar : jars) {
                    URL url = new File(jar).toURI().toURL();
                    if (! this.urls.contains(url)) {
                        this.urls.add(url);
                    }
                }
            }
        }
    }

    public void searchInClassPath(String targetField) throws IOException, ClassNotFoundException {
        setUpJarURLsList();
        Map<String, List<String>> targetClassFiles = getTargetClassesListFromClassPath();
        for (String entry : targetClassFiles.keySet()) {
            System.out.println("=== SEARCH CLASSPATH ENTRY: " + entry);
            if (entry.endsWith(".jar")) {
                for (String f : targetClassFiles.get(entry)) {
                    //System.out.println(f);
                    ClassParser cp = new ClassParser(entry, f);
                    JavaClass clz = cp.parse();
                    boolean isMatched = searchInClass(targetField, clz);
                    if (isMatched) {
                        return;
                    }
                }
            } else {
                for (String f : targetClassFiles.get(entry)) {
                    //System.out.println(f);
                    ClassParser cp = new ClassParser(f);
                    JavaClass clz = cp.parse();
                    boolean isMatched = searchInClass(targetField, clz);
                    if (isMatched) {
                        return;
                    }
                }
            }
        }
    }

    public boolean searchInClass(String targetField, JavaClass clz) throws IOException, ClassNotFoundException {
        if (clz.isInterface()) {
            return false;
        }
        boolean isMatched = false;
        ConstantPoolGen constantPoolGen = new ConstantPoolGen(clz.getConstantPool());
        for (Field field : clz.getFields()) {
            String fieldFQN = clz.getClassName() + '.' + field.getName(); // include "$"
            //System.out.println(fieldFQN);
            if (fieldFQN.equals(targetField)) {
                System.out.println("[INFO]: Target field is defined in class " + clz.getClassName());
                isMatched = true;
                Type fieldType = field.getType();
                String fieldTypeFQN = fieldType.toString();
                if (null != identifyFieldTypeFQNFromXML(this.xmlPath)) {
                    fieldTypeFQN = identifyFieldTypeFQNFromXML(this.xmlPath);
                }
                System.out.println("[INFO]: Target field type: " + fieldTypeFQN);
                if (checkCollectionType(field) != null) {
                    System.out.println("[INFO]: Target field itself is a map/list!");
                    findPotentialResetterMethods(fieldFQN, checkCollectionType(field));
                    return isMatched;
                }
                Map<String, String> importantFields = new HashMap<>();
                String fieldTypeName = checkCollectionType(field);
                if (fieldTypeName != null) {
                    importantFields.put(fieldFQN, fieldTypeName);
                } else {
                    importantFields = searchImportantFieldsInClass(fieldTypeFQN);
                }
                for (String fqn : importantFields.keySet()) {
                    findPotentialResetterMethods(fqn, importantFields.get(fqn));
                }
                return isMatched;
            }
        }
        return isMatched;
    }

    public String identifyFieldTypeFQNFromXML(String xmlPath) {
        File xmlFile = new File(xmlPath);
        if (!xmlFile.exists()) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            StringBuilder xmlStringBuilder = new StringBuilder();
            Document doc = builder.parse(xmlFile);
            Element root = doc.getDocumentElement();
            //System.out.println(root.getTagName());
            if (root.getTagName().contains("_-")) { // inner class
                return root.getTagName().replace("_-", "$");
            }
            return root.getTagName();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Returns name of field type, otherwise null
    public String checkCollectionType(Field field) throws ClassNotFoundException {
        Type fieldType = field.getType();
        //System.out.println(fieldType);
        URLClassLoader childClassLoader = new URLClassLoader(this.urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader());
        //System.out.println(Class.forName(fieldType.toString(), true , childClassLoader));
        /*if (List.class.isAssignableFrom(Class.forName(fieldType.toString(), true, childClassLoader))) {
            //System.out.println("List!");
            importantFields.put(fieldFQN, "LIST");
        } else if (Map.class.isAssignableFrom(Class.forName(fieldType.toString(), true, childClassLoader))) {
            //System.out.println("Map!");
            importantFields.put(fieldFQN, "MAP");
        }*/
        Class fieldClass = null;
        try {
            fieldClass = Class.forName(fieldType.toString(), true, childClassLoader);
        } catch (Exception e) {
            return null;
        } catch (Error e) {
            return null;
        }
        if (Collection.class.isAssignableFrom(fieldClass) || Map.class.isAssignableFrom(fieldClass)) {
            return fieldType.toString();
        } else if (ThreadLocal.class.isAssignableFrom(fieldClass)) {
            //System.out.println(field.getGenericSignature().split("<")[1].split(">")[0]);
            return field.getGenericSignature().split("<")[1].split(">")[0];
        }
        return null;
    }

    public Map<String, String> searchImportantFieldsInClass(String classFQN) throws IOException, ClassNotFoundException {
        JavaClass clz = findJavaClass(classFQN);
        //System.out.println(clz);
        if (clz.isInterface()) {
            System.out.println("[ALERT] The field type is interface! ");
        }
        Map<String, String> importantFields = new HashMap<>();
        if (clz == null) {
            System.out.println("NULL " + classFQN);
            return importantFields;
        }
        System.out.println("[INFO] Important fields: ");
        for (Field field : clz.getFields()) {
            String fieldFQN = clz.getClassName() + "." + field.getName();
            String fieldTypeName = checkCollectionType(field);
            if (fieldTypeName != null) {
                importantFields.put(fieldFQN, fieldTypeName);
            }
        }
        return importantFields;
    }

    public void findPotentialResetterMethods(String fieldFQN, String fieldCategory) throws IOException {
        System.out.println("Important field: " + fieldFQN);
        Map<String, List<String>> targetClassFiles = getTargetClassesListFromClassPath();
        for (String entry : targetClassFiles.keySet()) {
            if (entry.endsWith(".jar")) {
                for (String f : targetClassFiles.get(entry)) {
                    ClassParser cp = new ClassParser(entry, f);
                    JavaClass clz = cp.parse();
                    findPotentialResetterMethodsInClass(fieldFQN, fieldCategory, clz);
                }
            } else {
                for (String f : targetClassFiles.get(entry)) {
                    ClassParser cp = new ClassParser(f);
                    JavaClass clz = cp.parse();
                    findPotentialResetterMethodsInClass(fieldFQN, fieldCategory, clz);
                }
            }
        }
    }

    public boolean findPotentialResetterMethodsInClass(String fieldFQN, String fieldCategory, JavaClass clz) {
        if (clz.isInterface()) {
            return false;
        }
        boolean isMatched = false;
        for (Method method : clz.getMethods()) {
            //System.out.println("----------------- " + clz.getClassName() + "." + method.getName());
            if (isResetterMethod(fieldFQN, fieldCategory, method, clz)) {
                String resetterFQNAndSig = clz.getClassName() + "." + method.getName() + method.getSignature();
                Resetter r = new Resetter(clz, method);
                if (!this.resetters.contains(r)) {
                    this.resetters.add(r);
                }
                System.out.println("[INFO] Potential Resetter: " + resetterFQNAndSig);
            }
        }
        return isMatched;
    }

    public boolean isResetterMethod(String fieldFQN, String fieldCategory, Method method, JavaClass clz) {
        if (method.isAbstract() || method.isNative()) {
            return false;
        }
        boolean isResetter = false;
        boolean isClearCalled = false;
        boolean isFieldAccessed = false;
        String className = clz.getClassName();
        ConstantPoolGen constantPoolGen = new ConstantPoolGen(clz.getConstantPool());
        MethodGen methodGen = new MethodGen(method, className, constantPoolGen);
        InstructionList instList = methodGen.getInstructionList();
        for (int i = 0; i < instList.getInstructions().length; i++) {
            Instruction inst = instList.getInstructions()[i];
            if (inst instanceof INVOKEVIRTUAL) {
                String methodCallFQN = ((INVOKEVIRTUAL) inst).getClassName(constantPoolGen) + "."
                        + ((INVOKEVIRTUAL) inst).getMethodName(constantPoolGen);
                if (methodCallFQN.endsWith(".clear") || methodCallFQN.endsWith(".remove") ||
                        methodCallFQN.endsWith(".add") || methodCallFQN.endsWith(".put") ||
                        methodCallFQN.endsWith(".putIfAbsent")) {
                    isClearCalled = true;
                    //System.out.println(methodCallFQN);
                }
            } else if (inst instanceof INVOKEINTERFACE) { // interface method call
                String methodCallFQN = ((INVOKEINTERFACE) inst).getClassName(constantPoolGen) + "."
                        + ((INVOKEINTERFACE) inst).getMethodName(constantPoolGen);
                if (methodCallFQN.endsWith(".clear") || methodCallFQN.endsWith(".remove") ||
                        methodCallFQN.endsWith(".add") || methodCallFQN.endsWith(".put") ||
                        methodCallFQN.endsWith(".putIfAbsent")) {
                    isClearCalled = true;
                    //System.out.println(methodCallFQN);
                }
            }
            else if (inst instanceof GETSTATIC) {
                String fqn = ((GETSTATIC) inst).getClassName(constantPoolGen) + "." + ((GETSTATIC) inst).getFieldName(constantPoolGen);
                if (fqn.equals(fieldFQN)) {
                    //System.out.println(fqn);
                    isFieldAccessed = true;
                }
            } else if (inst instanceof GETFIELD) {
                String fqn = ((GETFIELD) inst).getClassName(constantPoolGen) + "." + ((GETFIELD) inst).getFieldName(constantPoolGen);
                if (fqn.equals(fieldFQN)) {
                    //System.out.println(fqn);
                    isFieldAccessed = true;
                }
            }
        }
        if (isClearCalled && isFieldAccessed) {
            isResetter = true;
        }
        return isResetter;
    }

    public JavaClass findJavaClass(String targetClassFQN) throws IOException {
        Map<String, List<String>> targetClassFiles = getTargetClassesListFromClassPath();
        for (String entry : targetClassFiles.keySet()) {
            //System.out.println(entry);
            if (entry.endsWith(".jar")) {
                for (String f : targetClassFiles.get(entry)) {
                    //System.out.println(f);
                    ClassParser cp = new ClassParser(entry, f);
                    JavaClass clz = cp.parse();
                    if (clz.getClassName().equals(targetClassFQN)) {
                        return clz;
                    }
                }
            } else {
                for (String f : targetClassFiles.get(entry)) {
                    //System.out.println(f);
                    ClassParser cp = new ClassParser(f);
                    JavaClass clz = cp.parse();
                    if (clz.getClassName().equals(targetClassFQN)) {
                        return clz;
                    }
                }
            }
        }
        return null;
    }

    public List<String> getJarsListFromDir(String dir) throws IOException {
        ArrayList<String> jars = new ArrayList<>();
        Files.walk(Paths.get(dir))
        .filter(Files::isRegularFile)
        .forEach((f)->{
            if(f.toString().endsWith(".jar")) {
                jars.add(f.toString());
            }
        });
        return jars;
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
        Map<String, List<String>> classes = new TreeMap<>();
        String classpath = this.targetClassPath;
        List<String> jarList = getJarsListFromDir(classpath);
        for (String jarPath : jarList) {
            classpath += ":" + jarPath;
        }
        //System.out.println(classpath);
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

    public List<Resetter> getResetters() {
        return resetters;
    }

    public void setResetters(List<Resetter> resetters) {
        this.resetters = resetters;
    }

    public String getXmlPath() {
        return xmlPath;
    }

    public void setXmlPath(String xmlPath) {
        this.xmlPath = xmlPath;
    }
}

