package org.reseterfinder;

import org.apache.bcel.classfile.Method;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Options options = new Options();
        Option field = new Option("field", "field", true, "field");
        Option fieldList = new Option("fieldlist", "fieldlist", true, "field list");
        Option dir = new Option("dir", "dir", true, "dir");
        Option classpath = new Option("klasspath", "klasspath", true, "klasspath");
        Option jar = new Option("lib", "lib", true, "lib");
        Option mode = new Option("mode", "mode", true, "mode");
        Option xml = new Option("xml", "xml", true, "xml");
        Option test = new Option("test", "test", true, "test");
        Option resetter = new Option("resetter", "resetter", true, "resetter");

        field.setRequired(false);
        dir.setRequired(false);
        jar.setRequired(false);
        classpath.setRequired(false);
        fieldList.setRequired(false);
        mode.setRequired(false);
        xml.setRequired(false);
        test.setRequired(false);
        resetter.setRequired(false);

        options.addOption(field);
        options.addOption(dir);
        options.addOption(jar);
        options.addOption(classpath);
        options.addOption(fieldList);
        options.addOption(mode);
        options.addOption(xml);
        options.addOption(test);
        options.addOption(resetter);

        try {
            CommandLine cl = new DefaultParser().parse(options, args, true);
            String fieldStr = cl.getOptionValue("field");
            String dirStr = cl.getOptionValue("dir");
            String jarStr = cl.getOptionValue("lib");
            String classPathStr = cl.getOptionValue("klasspath");
            String fieldListFilePathStr = cl.getOptionValue("fieldlist");
            String modeStr = cl.getOptionValue("mode");
            String xmlPath = cl.getOptionValue("xml");
            String victimTest = cl.getOptionValue("test");
            String resetterFQN = cl.getOptionValue("resetter");

            if (modeStr != null && modeStr.equals("map-list-resetters")) {
                // Search resetters for collections
                MapListResetterSearch m = new MapListResetterSearch();
                if (classPathStr != null) {
                    m.setTargetClassPath(classPathStr);
                    m.setXmlPath(xmlPath);
                    m.searchInClassPath(fieldStr);
                    List<Resetter> resetters = m.getResetters();
                    PatchGen pg = new PatchGen();
                    for (Resetter r : resetters) {
                        String patch = pg.genPatch(r);
                        System.out.println("[PATCH]: " + patch);
                    }
                }
            } else if (modeStr != null && modeStr.equals("mine-prims")) {
                MinePrims miner = new MinePrims(fieldStr, victimTest);
                if (dirStr != null) {
                    miner.setTargetProjetDir(dirStr);
                    miner.minePrims();
                } else if (jarStr != null) {
                    miner.setTargetJar(jarStr);
                    miner.minePrims();
                } else if (classPathStr != null) {
                    miner.setTargetClassPath(classPathStr);
                    miner.minePrims();
                }
            } else if (modeStr != null && modeStr.equals("mine-prims-all-classes")) {
                MinePrims miner = new MinePrims(fieldStr, victimTest, true);
                if (dirStr != null) {
                    miner.setTargetProjetDir(dirStr);
                    miner.minePrims();
                } else if (jarStr != null) {
                    miner.setTargetJar(jarStr);
                    miner.minePrims();
                } else if (classPathStr != null) {
                    miner.setTargetClassPath(classPathStr);
                    miner.minePrims();
                }
            } else if (modeStr != null && modeStr.equals("find-callee-getters")) {
                CalleeGetterFinder finder = new CalleeGetterFinder(resetterFQN);
                if (dirStr != null) {
                    finder.setTargetProjetDir(dirStr);
                    finder.findCalleeGetters();
                } else if (jarStr != null) {
                    finder.setTargetJar(jarStr);
                    finder.findCalleeGetters();
                } else if (classPathStr != null) {
                    finder.setTargetClassPath(classPathStr);
                    finder.findCalleeGetters();
                }
            } else if (modeStr != null && modeStr.equals("ref-classes-transitive")) {
                FieldRefClassesFinder finder = new FieldRefClassesFinder(fieldStr);
                if (dirStr != null) {
                    finder.setTargetProjetDir(dirStr);
                    finder.findRefClassesTransitiveClosure();
                } else if (jarStr != null) {
                    finder.setTargetJar(jarStr);
                    finder.findRefClassesTransitiveClosure();
                } else if (classPathStr != null) {
                    finder.setTargetClassPath(classPathStr);
                    finder.findRefClassesTransitiveClosure();
                }
            } else {
                // Search PUTSTATIC and GETSTATIC
                APISearch apiSearch = new APISearch();

                if (fieldListFilePathStr != null) {
                    Scanner s = new Scanner(new File(fieldListFilePathStr));
                    List<String> targetFields = new ArrayList<String>();
                    while (s.hasNextLine()) {
                        targetFields.add(s.nextLine().trim());
                    }
                    s.close();
                    for (String f : targetFields) {
                        processOneField(apiSearch, f, dirStr, jarStr, classPathStr);
                    }
                } else {
                    processOneField(apiSearch, fieldStr, dirStr, jarStr, classPathStr);
                }
            }

        } catch (ParseException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void processOneField(APISearch apiSearch, String fieldStr, String dirStr, String jarStr, String classPathStr) throws IOException {
        if (dirStr != null) {
            apiSearch.setTargetProjetDir(dirStr);
            apiSearch.searchInDir(fieldStr);
        } else if (jarStr != null) {
            apiSearch.setTargetJar(jarStr);
            apiSearch.searchInJar(fieldStr);
        } else if (classPathStr != null) {
            apiSearch.setTargetClassPath(classPathStr);
            apiSearch.searchInClassPath(fieldStr);
        }
    }
}
