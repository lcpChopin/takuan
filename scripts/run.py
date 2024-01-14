#!/usr/bin/python3

import os
import sys
import time
import subprocess as sub
import re
import json
import csv
import datetime
import shutil
import argparse
import collections

SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))  # Dir of this script
TOOL_JAR = '${HOME}/.m2/repository/org/reset-finder/reset-finder/1.0-SNAPSHOT/reset-finder-1.0-SNAPSHOT.jar'
_DOWNLOADS_DIR = SCRIPT_DIR + '/_downloads'
_RESULTS_DIR = SCRIPT_DIR + '/_results'
RESETTER_LIST_DIR = SCRIPT_DIR + '/resetters'
GEN_TESTS_DIR = SCRIPT_DIR + '/gen_tests'
EVO_GEN_TESTS_DIR = SCRIPT_DIR + '/evo_gen_tests'

EXP_CSV = SCRIPT_DIR + '/result.csv'
CP_JAR = SCRIPT_DIR + '/libs/commons-cli-1.3.1.jar:' + SCRIPT_DIR + '/libs/commons-io-2.4.jar:' + SCRIPT_DIR + '/libs/hamcrest-core-1.3.jar:' + SCRIPT_DIR + '/libs/commons-codec-1.6.jar:' + SCRIPT_DIR + '/libs/junit-4.12.jar:' + SCRIPT_DIR + '/libs/bcel-6.3.jar:' + '${HOME}/.m2/repository/org/reset-finder/reset-finder/1.0-SNAPSHOT/reset-finder-1.0-SNAPSHOT.jar'
RANDOOP_JAR = SCRIPT_DIR + '/libs/randoop-all-4.2.6.jar'
JUNIT_JAR = SCRIPT_DIR + '/libs/junit-4.13.2.jar'
HAMCREST_JAR = SCRIPT_DIR + '/libs/hamcrest-core-1.3.jar'
GUAVA_JAR = SCRIPT_DIR + '/libs/guava-15.0.jar'
EVOSUITE_JAR = SCRIPT_DIR + '/libs/evosuite-1.0.6.jar'


def cloneProject(project_name, project_url, project_sha,
                 downloads_dir=_DOWNLOADS_DIR):
    cwd = os.getcwd()
    client_dir = downloads_dir + '/' + project_name
    if os.path.isdir(client_dir):
        shutil.rmtree(client_dir)
    if os.path.isdir(client_dir + '-fake'):
        shutil.copytree(client_dir + '-fake', client_dir)
    else:
        os.chdir(downloads_dir)
        sub.run('git clone ' + project_url + ' ' + project_name, shell=True, \
                stdout=open(os.devnull, 'w'), stderr=sub.STDOUT)
        shutil.copytree(client_dir, client_dir + '-fake')
    os.chdir(client_dir)
    sub.run('git checkout .' + project_sha, shell=True, \
            stdout=open(os.devnull, 'w'), stderr=sub.STDOUT)
    sub.run('git checkout ' + project_sha, shell=True, \
            stdout=open(os.devnull, 'w'), stderr=sub.STDOUT)
    os.chdir(cwd)


def buildProject(project_name, project_sha, project_module, test_fqn,
                 downloads_dir=_DOWNLOADS_DIR, results_dir=_RESULTS_DIR):
    cwd = os.getcwd()
    client_result_dir = results_dir + '/' + project_name + '/' + \
                        project_sha + '/' + test_fqn
    if not os.path.isdir(client_result_dir):
        os.makedirs(client_result_dir)
    os.chdir(downloads_dir + '/' + project_name)
    build_log = client_result_dir + '/build.txt'
    start_time = time.time()
    ###
    # print ('Copy jars ... '  + str(datetime.datetime.now()))
    # if os.path.isdir(downloads_dir + '/' + project_name + '/jars'):
    #     shutil.rmtree(downloads_dir + '/' + project_name + '/jars')
    # os.makedirs(downloads_dir + '/' + project_name + '/jars')
    # sub.run('mvn dependency:copy-dependencies', shell=True,
    #         stdout=open(os.devnull, 'w'), stderr=sub.STDOUT)
    # for dir_path, subpaths, files in os.walk(downloads_dir + '/' + project_name):
    #     for sp in subpaths:
    #         if sp == 'dependency':
    #             for jar in os.listdir(dir_path + '/' + sp):
    #                 if not jar.endswith('.jar'):
    #                     continue
    #                 shutil.copy(dir_path + '/' + sp + '/' + jar,
    #                             downloads_dir + '/' + project_name + '/jars')
    # for dir_path, subpaths, files in os.walk(downloads_dir + '/' + project_name):
    #     for f in files:
    #         if f.endswith('.jar') and not dir_path.endswith('/jars'):
    #             os.remove(dir_path + '/' + f)
    ###
    print('Building client ... ' + str(datetime.datetime.now()))
    if project_name == 'skywalking':
        sub.run(
            'mvn install -pl apm-commons/apm-datacarrier -am -DskipTests -Ddetector.detector_type=random-class-method -Ddt.randomize.rounds=10 -Ddt.detector.original_order.all_must_pass=false -Ddependency-check.skip=true -Denforcer.skip=true -Drat.skip=true -Dmdep.analyze.skip=true -Dmaven.javadoc.skip=true -Dgpg.skip -Dlicense.skip=true',
            shell=True, stdout=open(build_log, 'w'), stderr=sub.STDOUT)
    else:
        sub.run(
            'mvn install -DskipTests -Ddetector.detector_type=random-class-method -Ddt.randomize.rounds=10 -Ddt.detector.original_order.all_must_pass=false -Ddependency-check.skip=true -Denforcer.skip=true -Drat.skip=true -Dmdep.analyze.skip=true -Dmaven.javadoc.skip=true -Dgpg.skip -Dlicense.skip=true -am -pl ' + project_module,
            shell=True, stdout=open(build_log, 'w'), stderr=sub.STDOUT)
    end_time = time.time()
    insertTimeInLog(start_time, end_time, build_log)
    os.chdir(cwd)


def extractCasesInfo(csv_file):
    all_cases = []
    fp = open(csv_file, 'r')
    csv_reader = csv.reader(fp, delimiter=",", quotechar='"')
    for row in csv_reader:
        if row[0][0] == '#':
            continue
        case = collections.OrderedDict({})
        case['url'] = row[0]
        case['sha'] = row[1]
        case['module'] = row[2]
        case['test_fqn'] = row[3]
        case['method_fqn'] = row[4]
        if case not in all_cases:
            all_cases.append(case)

    return all_cases


def getAllClassPathsInDir(path):
    concat_class_path = ''
    # for dir_path, subpaths, files in os.walk(path):
    #     for sp in subpaths:
    #         if sp == 'classes':
    #             concat_class_path += dir_path + '/' + sp + ':'
    # concat_class_path = concat_class_path[:-1]
    concat_class_path += ':$(find ' + path + \
                         ' -name \"classes\" -type d | paste -sd :)'
    concat_class_path += ':$(find ' + path + \
                         ' -name \"test-classes\" -type d | paste -sd :)'
    concat_class_path += ':$(find /tmp/jars -name \"*.jar\" -type f | paste -sd :)'
    # concat_class_path += '$(for f in $(find -name dependency); do echo -n \":${f}/*.jar\"; done)'
    # concat_class_path = concat_class_path[:-1]
    return concat_class_path

def genRandoopPrimsSpecFile(project_name, project_sha, test_fqn,
                            results_dir=_RESULTS_DIR):
    client_result_dir = results_dir + '/' + project_name + '/' + \
                        project_sha + '/' + test_fqn
    mine_prims_log_file = client_result_dir + '/prims.txt'
    with open(mine_prims_log_file, 'r') as fr:
        lines = fr.readlines()
    prims = []
    for i in range(len(lines)):
        if not lines[i].startswith('===') and not lines[i].startswith('[TIME]'):
            prim = lines[i].strip()
            if prim not in prims:
                prims.append(prim)
    output_lines = ''
    output_lines += 'START CLASSLITERALS\n\n'
    output_lines += 'CLASSNAME\n'
    output_lines += 'java.lang.String\n\n'
    output_lines += 'LITERALS\n'
    for prim in prims:
        output_lines += 'String:\"' + prim + '\"\n'
    output_lines += '\nEND CLASSLITERALS\n'
    output_file = client_result_dir + '/literals.txt'
    with open(output_file, 'w') as fw:
        fw.write(output_lines)

def runFindRefClasses(project_name, project_sha, test_fqn, field_fqn,
                      downloads_dir=_DOWNLOADS_DIR, results_dir=_RESULTS_DIR,
                      tool_jar=TOOL_JAR):
    client_result_dir = results_dir + '/' + project_name + '/' + \
        project_sha + '/' + test_fqn
    if not os.path.isdir(client_result_dir):
        os.makedirs(client_result_dir)
    cwd = os.getcwd()
    os.chdir(downloads_dir + '/' + project_name)
    analysis_log = client_result_dir + '/ref-classes-trans.txt'
    start_time = time.time()
    classpath = downloads_dir + '/' + project_name
    if project_name == 'spring-ws':
        classpath += ':/tmp/jars/spring-security-core-5.0.9.RELEASE.jar'
    if project_name == 'incubator-dubbo':
        classpath += ':' + SCRIPT_DIR + '/libs/fst-2.48-jdk-6.jar'
    sub.run('java -cp \"' + CP_JAR + '\"' + \
            ' org.reseterfinder.Main' + \
            ' -field \"' + field_fqn.replace('$', '\\$') + '\"' + \
            ' -klasspath ' + classpath + \
            ' -mode ref-classes-transitive',
            shell=True, stdout=open(analysis_log, 'w'), stderr=sub.STDOUT)
    end_time = time.time()
    insertTimeInLog(start_time, end_time, analysis_log)
    os.chdir(cwd)

def runRandoopTestGen(project_name, project_sha, test_fqn,
                      resetter_list_dir=RESETTER_LIST_DIR,
                      downloads_dir=_DOWNLOADS_DIR):
    print('Copy jars ... ' + str(datetime.datetime.now()))
    cwd = os.getcwd()
    os.chdir(downloads_dir + '/' + project_name)
    # collect dependency jars
    if os.path.isdir('/tmp/jars'):
        shutil.rmtree('/tmp/jars')
    os.makedirs('/tmp/jars')
    sub.run('mvn dependency:copy-dependencies', shell=True,
            stdout=open(os.devnull, 'w'), stderr=sub.STDOUT)
    for dir_path, subpaths, files in os.walk(downloads_dir + '/' + project_name):
        for sp in subpaths:
            if sp == 'dependency':
                for jar in os.listdir(dir_path + '/' + sp):
                    if not jar.endswith('.jar'):
                        continue
                    if jar.startswith('slf4j-log4j12'):
                        continue
                    shutil.copy(dir_path + '/' + sp + '/' + jar, '/tmp/jars')
    os.chdir(cwd)
    resetters_json_file = resetter_list_dir + '/' + project_name + '/' + \
                       project_sha + '/' + test_fqn + '/resetters.json'
    with open(resetters_json_file, 'r') as fr:
        resetters = json.load(fr, object_pairs_hook=collections.OrderedDict)
    # resetters = []
    # resetters.append("in.natelev.toyflakytests.ExternalODLogger()")
    # resetters.append("place_holder")
    # resetters.append("in.natelev.toyflakytests.ExternalODLogger.clearLogs()")
    # resetters.append("in.natelev.toyflakytests.ExternalODLogger.log(java.lang.String)")
    print('--- Generating Tests for Resetters ...')
    runRandoopTestGenOnOneResetter(resetters, project_name, project_sha,
                                    test_fqn)


def runRandoopTestGenOnOneResetter(resetters, project_name, project_sha,
                                   test_fqn, downloads_dir=_DOWNLOADS_DIR,
                                   results_dir=_RESULTS_DIR,
                                   gen_tests_dir=GEN_TESTS_DIR):
    cwd = os.getcwd()
    os.chdir(downloads_dir + '/' + project_name)
    start_time = time.time()
    concat_project_class_path = \
        getAllClassPathsInDir(downloads_dir + '/' + project_name)
    if project_name == 'incubator-dubbo':
        concat_project_class_path = SCRIPT_DIR + '/libs/fst-2.48-jdk-6.jar:' + \
                                    concat_project_class_path
    # print (concat_project_class_path)
    test_method_num_limit = 500
    test_method_max_size = 100
    method_list_file = '/tmp/methods.txt'
    target_methods = []
    for resetter_fqn in resetters:
        # set up randoop
        output_dir = gen_tests_dir + '/' + project_name + '/' + project_sha + '/' + \
                 test_fqn + '/' + resetter_fqn.split('(')[0]
        if not os.path.isdir(output_dir):
            os.makedirs(output_dir)
        package_name = '.'.join(test_fqn.split('.')[:-2])
        # --- Target Methods
        randoop_fmt_resetter_fqn = convertJVMSigToDotSig(resetter_fqn)
        print('Randoop Format Resetter FQN: ' + randoop_fmt_resetter_fqn)
        resetter_getter_fqns = extractResetterGetters(resetter_fqn, project_name,
                                                    project_sha, test_fqn)
        # field_getter_fqns = extractFieldGetters(resetter_fqn, project_name,
        #                                        project_sha, test_fqn, field_fqn)
        target_methods.append(randoop_fmt_resetter_fqn)
        for m in resetter_getter_fqns: # + field_getter_fqns:
            fmt_m = convertJVMSigToDotSig(m)
            if fmt_m == '':
                continue
            if '<init>' in fmt_m or '<clinit>' in fmt_m:
                continue
            if '$' in fmt_m:
                continue
            if fmt_m not in target_methods:
                target_methods.append(fmt_m)
    with open(method_list_file, 'w') as fw:
        for tm in target_methods:
            fw.write(tm + '\n')
    output_dir = gen_tests_dir + '/' + project_name + '/' + project_sha + '/' + \
                 test_fqn + '/MTHD_LV'
    if not os.path.isdir(output_dir):
        os.makedirs(output_dir)
    client_result_dir = results_dir + '/' + project_name + '/' + \
                        project_sha + '/' + test_fqn
    literals_file = client_result_dir + '/literals.txt'
    # randoop_cmd = 'java -ea -classpath ' + RANDOOP_JAR + ':' + \
    #     JUNIT_JAR + ':' + HAMCREST_JAR + ':' + GUAVA_JAR + ':' + \
    #     concat_project_class_path + \
    #     ' randoop.main.Main gentests' \
    #               + ' --methodlist=' + method_list_file \
    #               + ' --junit-package-name=' + package_name \
    #               + ' --output-limit=' + str(test_method_num_limit) \
    #               + ' --time-limit=10' \
    #               + ' --maxsize=' + str(test_method_max_size) \
    #               + ' --junit-output-dir=' + output_dir \
    #               + ' --regression-test-basename=TestGroup' \
    #               + str(test_method_max_size) + 'Case' \
    #               + ' --testsperfile=1'
    randoop_cmd = 'java -ea -classpath ' + RANDOOP_JAR + ':' + \
                  JUNIT_JAR + ':' + HAMCREST_JAR + ':' + GUAVA_JAR + ':' + \
                  concat_project_class_path + \
                  ' randoop.main.Main gentests' \
                  + ' --methodlist=' + method_list_file \
                  + ' --output-limit=' + str(test_method_num_limit) \
                  + ' --time-limit=60' \
                  + ' --junit-package-name=' + package_name \
                  + ' --junit-output-dir=' + output_dir \
                  + ' --regression-test-basename=TestGroup' \
                  + str(test_method_max_size) + 'Case' \
                  + ' --literals-file=' + literals_file \
                  + ' --literals-level=ALL'
                  # + ' --literals-file=' + literals_file \
                  # + ' --literals-level=ALL'
    print(randoop_cmd)
    test_gen_log = output_dir + '/testgen.txt'
    sub.run(randoop_cmd, shell=True, stdout=open(test_gen_log, 'w'), stderr=sub.STDOUT)
    end_time = time.time()
    insertTimeInLog(start_time, end_time, test_gen_log)
    os.chdir(cwd)


def runRandoopTestGenClassLevel(project_name, project_sha, test_fqn, field_fqn, project_module,
                                downloads_dir=_DOWNLOADS_DIR,
                                results_dir=_RESULTS_DIR,
                                gen_tests_dir=GEN_TESTS_DIR):
    print('Copy jars ... ' + str(datetime.datetime.now()))
    cwd = os.getcwd()
    os.chdir(downloads_dir + '/' + project_name)
    # print(cwd)
    # collect dependency jars
    if os.path.isdir('/tmp/jars'):
        shutil.rmtree('/tmp/jars')
    os.makedirs('/tmp/jars')
    os.chdir(downloads_dir + '/' + project_name + '/' + project_module)
    sub.run('mvn dependency:copy-dependencies', shell=True,
            stdout=open(os.devnull, 'w'), stderr=sub.STDOUT)
    os.chdir(downloads_dir + '/' + project_name)
    for dir_path, subpaths, files in os.walk(downloads_dir + '/' + project_name):
        for sp in subpaths:
            if sp == 'dependency':
                for jar in os.listdir(dir_path + '/' + sp):
                    if not jar.endswith('.jar'):
                        continue
                    if jar.startswith('slf4j-log4j12'):
                        continue
                    shutil.copy(dir_path + '/' + sp + '/' + jar, '/tmp/jars')
    os.chdir(downloads_dir + '/' + project_name)
    start_time = time.time()
    concat_project_class_path = \
        getAllClassPathsInDir(downloads_dir + '/' + project_name)
    if project_name == 'incubator-dubbo':
        concat_project_class_path = SCRIPT_DIR + '/libs/fst-2.48-jdk-6.jar:' + \
                                    concat_project_class_path
    if project_name == 'Universal-G-Code-Sender':
        concat_project_class_path = SCRIPT_DIR + '/libs/minio-6.0.11.jar:' + \
                                    concat_project_class_path
    # print (concat_project_class_path)
    # set up randoop
    output_dir = gen_tests_dir + '/' + project_name + '/' + project_sha + '/' + \
                 test_fqn + '/CLZ_LV'
    if not os.path.isdir(output_dir):
        os.makedirs(output_dir)
    package_name = '.'.join(test_fqn.split('.')[:-2])
    test_method_num_limit = 500
    test_method_max_size = 100
    class_list_file = '/tmp/classes.txt'
    method_list_file = '/tmp/methods.txt'
    '''
    ref_classes = []
    ref_classes_log_file = results_dir + '/' + project_name + '/' + project_sha + \
                           '/' + test_fqn + '/ref-classes-trans.txt'
    with open(ref_classes_log_file, 'r') as fr:
        lines = fr.readlines()
    for i in range(len(lines)):
        if lines[i].startswith('Ref Class: '):
            ref_class = lines[i].strip().split()[-1]
            if ref_class == 'org.apache.hadoop.mapreduce.MRJobConfig':
                continue
            if '.' not in ref_class:
                continue
            if ref_class not in ref_classes:
                ref_classes.append(ref_class)
    ref_classes = ref_classes[:10]
    # ref_classes.append('org.mockito.Mockito')
    with open(class_list_file, 'w') as fw:
        for rc in ref_classes:
            if '$' in rc:
                rc = rc.split('$')[0]
            fw.write(rc + '\n')
    '''
    client_result_dir = results_dir + '/' + project_name + '/' + \
                        project_sha + '/' + test_fqn
    # literals_file = client_result_dir + '/literals0.txt'
    randoop_cmd = 'java -ea -classpath ' + RANDOOP_JAR + ':' + \
                  JUNIT_JAR + ':' + HAMCREST_JAR + ':' + GUAVA_JAR + ':' + \
                  concat_project_class_path + \
                  ' randoop.main.Main gentests' \
                  + ' --output-limit=' + str(test_method_num_limit) \
                  + ' --time-limit=120' \
                  + ' --junit-package-name=' + package_name \
                  + ' --junit-output-dir=' + output_dir \
                  + ' --regression-test-basename=TestGroup' \
                  + str(test_method_max_size) + 'Case' \
                  + ' --classlist=' + class_list_file \
                  + ' --literals-level=ALL'
                  # + ' --methodlist=' + method_list_file \
                  # + ' --literals-file=' + literals_file \
                  # + ' --literals-level=ALL'
    print(randoop_cmd)
    test_gen_log = output_dir + '/testgen.txt'
    sub.run(randoop_cmd, shell=True, stdout=open(test_gen_log, 'w'), stderr=sub.STDOUT)
    end_time = time.time()
    insertTimeInLog(start_time, end_time, test_gen_log)
    os.chdir(cwd)

def extractResetterGetters(resetter_fqn, project_name, project_sha,
                           test_fqn, downloads_dir=_DOWNLOADS_DIR,
                           results_dir=_RESULTS_DIR, tool_jar=TOOL_JAR,
                           gen_tests_dir=GEN_TESTS_DIR):
    output_dir = gen_tests_dir + '/' + project_name + '/' + project_sha + '/' + \
        test_fqn + '/' + resetter_fqn.split('(')[0]
    if not os.path.isdir(output_dir):
        os.makedirs(output_dir)
    cwd = os.getcwd()
    os.chdir(downloads_dir + '/' + project_name)
    analysis_log = output_dir + '/resetter-getters.txt'
    start_time = time.time()
    # map list resetters
    sub.run('java -cp \"' + CP_JAR + '\"' + \
            ' org.reseterfinder.Main' + \
            ' -klasspath ' + downloads_dir + '/' + project_name + \
            ' -mode find-callee-getters' + \
            ' -resetter \"' + resetter_fqn.replace('$', '\\$') + '\"',
            shell=True, stdout=open(analysis_log, 'w'), stderr=sub.STDOUT)
    end_time = time.time()
    insertTimeInLog(start_time, end_time, analysis_log)
    os.chdir(cwd)
    resetter_getters = []
    with open(analysis_log, 'r') as fr:
        lines = fr.readlines()
    for i in range(len(lines)):
        if lines[i].startswith('Resetter\'s Callee Getter: '):
            resetter_getter_fqn = lines[i].strip().split()[-1]
            if resetter_getter_fqn not in resetter_getters:
                resetter_getters.append(resetter_getter_fqn)
    return resetter_getters

def extractFieldGetters(resetter_fqn, project_name, project_sha,
                        test_fqn, downloads_dir=_DOWNLOADS_DIR,
                        results_dir=_RESULTS_DIR,
                        gen_tests_dir=GEN_TESTS_DIR):
    output_dir = gen_tests_dir + '/' + project_name + '/' + project_sha + '/' + \
        test_fqn + '/' + resetter_fqn.split('(')[0]
    if not os.path.isdir(output_dir):
        os.makedirs(output_dir)
    cwd = os.getcwd()
    os.chdir(downloads_dir + '/' + project_name)
    analysis_log = output_dir + '/field-getters.txt'
    with open(results_dir + '/' + project_name + '/' + project_sha + '/' + \
              test_fqn + '/analysis.txt') as fr:
        lines = fr.readlines()
    field_getters = []
    for i, l in enumerate(lines):
        if ', GETSTATIC method: ' in l:
            field_getter_fqn = l.strip().split()[-1]
            if field_getter_fqn not in field_getters:
                field_getters.append(field_getter_fqn)
    with open(analysis_log, 'w') as fw:
        for field_getter in field_getters:
            fw.write('Field Getter: ' + field_getter + '\n')
    return field_getters

def runRandoopTestGenAllClasses(project_name, project_sha, test_fqn, field_fqn,
                                downloads_dir=_DOWNLOADS_DIR,
                                results_dir=_RESULTS_DIR,
                                gen_tests_dir=GEN_TESTS_DIR):
    print('Copy jars ... ' + str(datetime.datetime.now()))
    cwd = os.getcwd()
    os.chdir(downloads_dir + '/' + project_name)
    # collect dependency jars
    if os.path.isdir('/tmp/jars'):
        shutil.rmtree('/tmp/jars')
    os.makedirs('/tmp/jars')
    sub.run('mvn dependency:copy-dependencies', shell=True,
            stdout=open(os.devnull, 'w'), stderr=sub.STDOUT)
    for dir_path, subpaths, files in os.walk(downloads_dir + '/' + project_name):
        for sp in subpaths:
            if sp == 'dependency':
                for jar in os.listdir(dir_path + '/' + sp):
                    if not jar.endswith('.jar'):
                        continue
                    if jar.startswith('slf4j-log4j12'):
                        continue
                    shutil.copy(dir_path + '/' + sp + '/' + jar, '/tmp/jars')
    os.chdir(downloads_dir + '/' + project_name)
    start_time = time.time()
    concat_project_class_path = \
        getAllClassPathsInDir(downloads_dir + '/' + project_name)
    if project_name == 'incubator-dubbo':
        concat_project_class_path = SCRIPT_DIR + '/libs/fst-2.48-jdk-6.jar:' + \
                                    concat_project_class_path
    # print (concat_project_class_path)
    # set up randoop
    output_dir = gen_tests_dir + '/' + project_name + '/' + project_sha + '/' + \
                 test_fqn + '/ALL_CLZS'
    if not os.path.isdir(output_dir):
        os.makedirs(output_dir)
    package_name = '.'.join(test_fqn.split('.')[:-2])
    test_method_num_limit = 500
    test_method_max_size = 100
    class_list_file = '/tmp/classes.txt'
    all_classes = []
    for dir_path, subpaths, files in os.walk(downloads_dir + '/' + project_name):
        for f in files:
            if f.endswith('.class') and '/classes/' in dir_path:
                clz = (dir_path + '/' + f.split('.')[0]). \
                    split('/classes/')[-1].replace('/', '.')
                if clz not in all_classes:
                    all_classes.append(clz)
    # ref_classes.append('org.mockito.Mockito')
    with open(class_list_file, 'w') as fw:
        for rc in all_classes:
            if '$' in rc:
                rc = rc.split('$')[0]
            fw.write(rc + '\n')
    client_result_dir = results_dir + '/' + project_name + '/' + \
                        project_sha + '/' + test_fqn
    literals_file = client_result_dir + '/literals.txt'
    randoop_cmd = 'java -ea -classpath ' + RANDOOP_JAR + ':' + \
                  JUNIT_JAR + ':' + HAMCREST_JAR + ':' + GUAVA_JAR + ':' + \
                  concat_project_class_path + \
                  ' randoop.main.Main gentests' \
                  + ' --classlist=' + class_list_file \
                  + ' --output-limit=' + str(test_method_num_limit) \
                  + ' --time-limit=120' \
                  + ' --junit-package-name=' + package_name \
                  + ' --junit-output-dir=' + output_dir \
                  + ' --regression-test-basename=TestGroup' \
                  + str(test_method_max_size) + 'Case' \
                  + ' --literals-file=' + literals_file \
                  + ' --literals-level=ALL'
    print(randoop_cmd)
    test_gen_log = output_dir + '/testgen.txt'
    sub.run(randoop_cmd, shell=True, stdout=open(test_gen_log, 'w'), stderr=sub.STDOUT)
    end_time = time.time()
    insertTimeInLog(start_time, end_time, test_gen_log)
    os.chdir(cwd)

def convertJVMSigToClassFQN(jvm_sig):
    class_fqn = '.'.join(jvm_sig.split('.')[:-1])
    if '$' in class_fqn:
        class_fqn = class_fqn.split('$')[0]
    return class_fqn


def convertJVMSigToDotSig(jvm_sig):
    args_part = jvm_sig.split('(')[-1].split(')')[0]
    converted_args = ''
    i = 0
    while i < len(args_part):
        if args_part[i] == 'Z':
            converted_args += 'boolean,'
        elif args_part[i] == 'B':
            converted_args += 'byte,'
        elif args_part[i] == 'C':
            converted_args += 'char,'
        elif args_part[i] == 'S':
            converted_args += 'short,'
        elif args_part[i] == 'I':
            converted_args += 'int,'
        elif args_part[i] == 'J':
            converted_args += 'long,'
        elif args_part[i] == 'F':
            converted_args += 'float,'
        elif args_part[i] == 'D':
            converted_args += 'double,'
        elif args_part[i] == 'L':
            for j in range(i + 1, len(args_part)):
                if args_part[j] == '/':
                    converted_args += '.'
                elif args_part[j] == ';':
                    converted_args += ','
                    i = j
                    break
                else:
                    converted_args += args_part[j]
        elif args_part[i] == '[':
            # do not handle array for now
            return ''
        i += 1
    dot_sig = jvm_sig.replace(args_part, converted_args).split(')')[0] + ')'
    if dot_sig.endswith(',)'):
        dot_sig = dot_sig.replace(',)', ')')
    return dot_sig


def runEvoSuiteTestGen(project_name, project_sha, test_fqn, field_fqn,
                       resetter_list_dir=RESETTER_LIST_DIR,
                       downloads_dir=_DOWNLOADS_DIR):
    print('Copy jars ... ' + str(datetime.datetime.now()))
    cwd = os.getcwd()
    os.chdir(downloads_dir + '/' + project_name)
    # collect dependency jars
    if os.path.isdir('/tmp/jars'):
        shutil.rmtree('/tmp/jars')
    os.makedirs('/tmp/jars')
    sub.run('mvn dependency:copy-dependencies', shell=True,
            stdout=open(os.devnull, 'w'), stderr=sub.STDOUT)
    for dir_path, subpaths, files in os.walk(downloads_dir + '/' + project_name):
        for sp in subpaths:
            if sp == 'dependency':
                for jar in os.listdir(dir_path + '/' + sp):
                    if not jar.endswith('.jar'):
                        continue
                    shutil.copy(dir_path + '/' + sp + '/' + jar, '/tmp/jars')
    os.chdir(cwd)
    resetters_json_file = resetter_list_dir + '/' + project_name + '/' + \
                          project_sha + '/' + test_fqn + '/resetters.json'
    with open(resetters_json_file, 'r') as fr:
        resetters = json.load(fr, object_pairs_hook=collections.OrderedDict)
    for rst in resetters:
        print('--- Generating Tests for Resetter ' + rst + ' ...')
        runEvoSuiteTestGenOnOneResetter(rst, project_name, project_sha,
                                        test_fqn, field_fqn)


def runEvoSuiteTestGenOnOneResetter(resetter_fqn, project_name, project_sha,
                                    test_fqn, field_fqn, downloads_dir=_DOWNLOADS_DIR,
                                    evosuite_jar=EVOSUITE_JAR,
                                    gen_tests_dir=EVO_GEN_TESTS_DIR):
    cwd = os.getcwd()
    os.chdir(downloads_dir + '/' + project_name)
    start_time = time.time()
    concat_project_class_path = \
        getAllClassPathsInDir(downloads_dir + '/' + project_name)
    # concat_project_class_path += \
    #    '$(for i in $(ls /tmp/jars); do printf \':/tmp/jars/\'$i;done)'
    # print (concat_project_class_path)
    # set up evosuite
    output_dir = gen_tests_dir + '/' + project_name + '/' + project_sha + '/' + \
                 test_fqn + '/' + resetter_fqn.split('(')[0]
    if os.path.isdir(output_dir):
        shutil.rmtree(output_dir)
    if not os.path.isdir(output_dir):
        os.makedirs(output_dir)
    resetter_class_fqn = '.'.join(resetter_fqn.split('.')[:-1])
    resetter_method_shortname = resetter_fqn.split('.')[-1].split('(')[0]
    search_budget = 60
    test_gen_cmd = 'java -Xmx4g -jar ' + evosuite_jar + \
                   ' -projectCP ' + EVOSUITE_JAR + concat_project_class_path + \
                   ' -class \"' + resetter_class_fqn + '\"' + \
                   ' -Dtarget_method=\"' + resetter_method_shortname + '\"' + \
                   ' -Dsearch_budget=' + str(search_budget)
    print(test_gen_cmd)
    test_gen_log = output_dir + '/testgen.txt'
    sub.run(test_gen_cmd, shell=True, stdout=open(test_gen_log, 'w'), stderr=sub.STDOUT)
    if os.path.isdir(downloads_dir + '/' + project_name + '/evosuite-tests'):
        shutil.move(downloads_dir + '/' + project_name + '/evosuite-tests', output_dir)
    if os.path.isdir(downloads_dir + '/' + project_name + '/evosuite-report'):
        shutil.move(downloads_dir + '/' + project_name + '/evosuite-report', output_dir)
    end_time = time.time()
    insertTimeInLog(start_time, end_time, test_gen_log)
    os.chdir(cwd)


def insertTimeInLog(start_time, end_time, log):
    duration = (end_time - start_time)
    fr = open(log, 'r')
    lines = fr.readlines()
    fr.close()
    lines.insert(0, '[TIME]: ' + str(datetime.timedelta(seconds=duration)) + '\n')
    fw = open(log, 'w')
    fw.write(''.join(lines))
    fw.close()


def runExp():
    all_cases = extractCasesInfo(EXP_CSV)
    finished_shas_fields = collections.OrderedDict({})
    for case in all_cases:
        project_name = case['url'].split('/')[-1]
        project_url = 'https://github.com/' + case['url']
        project_sha = case['sha']
        project_module = case['module']
        test_fqn = case['test_fqn']
        method_fqn = case['method_fqn']
        print(str(datetime.datetime.now()) + ' ===> Running ' + project_name + ' ' + \
              project_sha + ' ' + test_fqn)
        if project_sha in finished_shas_fields and \
                method_fqn in finished_shas_fields[project_sha]:
            if os.path.isdir(_RESULTS_DIR + '/' + project_name + '/' + project_sha + \
                             '/' + test_fqn):
                shutil.rmtree(_RESULTS_DIR + '/' + project_name + '/' + project_sha + \
                              '/' + test_fqn)
            shutil.copytree(_RESULTS_DIR + '/' + project_name + '/' + project_sha + \
                            '/' + finished_shas_fields[project_sha][method_fqn],
                            _RESULTS_DIR + '/' + project_name + '/' + project_sha + \
                            '/' + test_fqn)
            if os.path.isdir(RESETTER_LIST_DIR + '/' + project_name + '/' + \
                             project_sha + '/' + test_fqn):
                shutil.rmtree(RESETTER_LIST_DIR + '/' + project_name + '/' + \
                              project_sha + '/' + test_fqn)
            if os.path.isdir(RESETTER_LIST_DIR + '/' + project_name + '/' + \
                             project_sha + '/' + \
                             finished_shas_fields[project_sha][method_fqn]):
                shutil.copytree(RESETTER_LIST_DIR + '/' + project_name + '/' + \
                                project_sha + '/' + \
                                finished_shas_fields[project_sha][method_fqn],
                                RESETTER_LIST_DIR + '/' + project_name + '/' + \
                                project_sha + '/' + test_fqn)
            if os.path.isdir(GEN_TESTS_DIR + '/' + project_name + '/' + project_sha + \
                             '/' + test_fqn):
                shutil.rmtree(GEN_TESTS_DIR + '/' + project_name + '/' + project_sha + \
                              '/' + test_fqn)
            if os.path.isdir(GEN_TESTS_DIR + '/' + project_name + '/' + project_sha + \
                             '/' + finished_shas_fields[project_sha][method_fqn]):
                shutil.copytree(GEN_TESTS_DIR + '/' + project_name + '/' + \
                                project_sha + '/' + \
                                finished_shas_fields[project_sha][method_fqn],
                                GEN_TESTS_DIR + '/' + project_name + '/' + \
                                project_sha + '/' + test_fqn)
                continue
        cloneProject(project_name, project_url, project_sha)
        buildProject(project_name, project_sha, project_module, test_fqn)

        runRandoopTestGen(project_name, project_sha, test_fqn)
        # runRandoopTestGenClassLevel(project_name, project_sha, test_fqn, method_fqn, project_module)
        # runRandoopTestGenAllClasses(project_name, project_sha, test_fqn, field_fqn)
        # runEvoSuiteTestGen(project_name, project_sha, test_fqn, field_fqn)

        if project_sha not in finished_shas_fields:
            finished_shas_fields[project_sha] = collections.OrderedDict({})
        if method_fqn not in finished_shas_fields[project_sha]:
            finished_shas_fields[project_sha][method_fqn] = test_fqn


if __name__ == '__main__':
    runExp()

