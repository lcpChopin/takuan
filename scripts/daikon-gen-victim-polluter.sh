#!/usr/bin/env bash

trap "exit" INT

scriptsDir="$(dirname $(readlink -f "$0"))"

DAIKONDIR=${scriptsDir}/../../daikon
# if [[ -z "${NO_DYNCOMP}" ]]; then
#     COMPARABILITY_FILE='--comparability-file=Runner.decls-DynComp'
# else
#     COMPARABILITY_FILE=''
# fi

if [[ -n "${IGNORED_CLASSES}" ]]; then
    BOOT_CLASSES="--boot-classes=$IGNORED_CLASSES"
fi

if [[ -n "${PPT_SELECT}" ]]; then
    PPT_SELECT_PATTERN="--ppt-select-pattern=$PPT_SELECT"
fi

if [[ -n "${INSTRUMENT_ONLY}" ]]; then
    INSTRUMENT_ONLY="--instrument-only=$INSTRUMENT_ONLY"
fi

run_daikon() {
    local TYPE=$1
    local SHOULD=$2
    local START_TIME=$(date +%s)
    shift; shift

    if [ "$SHOULD" = "pass" ]; then
        # if [[ -z "${NO_DYNCOMP}" ]]; then
        #     # TODO: we should retry if it fails, but only if it fails for an actual reason, not because of the various DynComp bugs
        #     java -cp "./target/dependency/*:./target/classes:./target/test-classes:$DAIKONDIR/daikon.jar:$scriptsDir/runner-1.0-SNAPSHOT.jar:$CLASSPATH" daikon.DynComp --ppt-omit-pattern='org.junit|junit.framework|junit.runner|com.sun.proxy|javax.servlet|org.hamcrest|in.natelev.runner|groovyjarjarasm.asm' $PPT_SELECT_PATTERN in.natelev.runner.Runner $@
        # fi

        if ! java -cp "./target/dependency/*:./target/classes:./target/test-classes:$DAIKONDIR/daikon.jar:$scriptsDir/runner-1.0-SNAPSHOT.jar:$CLASSPATH" daikon.Chicory --ppt-omit-pattern='org.junit|junit.framework|junit.runner|com.sun.proxy|javax.servlet|org.hamcrest|in.natelev.runner|groovyjarjarasm.asm' $PPT_SELECT_PATTERN $BOOT_CLASSES $COMPARABILITY_FILE $INSTRUMENT_ONLY --disable-classfile-version-mismatch-warning in.natelev.runner.Runner $@
        then
            echo "Getting invariants failed: daikon.Chicory returned a nonzero exit code when tests should've passed"
            exit 1
        fi
    else 
        # if [[ -z "${NO_DYNCOMP}" ]]; then
        #     while java -cp "./target/dependency/*:./target/classes:./target/test-classes:$DAIKONDIR/daikon.jar:$scriptsDir/runner-1.0-SNAPSHOT.jar:$CLASSPATH" daikon.DynComp --ppt-omit-pattern='org.junit|junit.framework|junit.runner|com.sun.proxy|javax.servlet|org.hamcrest|in.natelev.runner|groovyjarjarasm.asm' $PPT_SELECT_PATTERN in.natelev.runner.Runner $@
        #     do echo "Re-trying daikon.DynComp because the test(s) passed..."; sleep 1; done;
        # fi

        if java -cp "./target/dependency/*:./target/classes:./target/test-classes:$DAIKONDIR/daikon.jar:$scriptsDir/runner-1.0-SNAPSHOT.jar:$CLASSPATH" daikon.Chicory --ppt-omit-pattern='org.junit|junit.framework|junit.runner|com.sun.proxy|javax.servlet|org.hamcrest|in.natelev.runner|groovyjarjarasm.asm' $PPT_SELECT_PATTERN $BOOT_CLASSES $COMPARABILITY_FILE $INSTRUMENT_ONLY --disable-classfile-version-mismatch-warning in.natelev.runner.Runner $@
        then
            echo "Getting invariants failed: daikon.Chicory returned a zero exit code when tests should've failed"
            exit 1
        fi
    fi

    echo "Running Daikon..."

    java -cp $DAIKONDIR/daikon.jar daikon.Daikon Runner.dtrace.gz >"daikon-$TYPE.log"

    mv Runner.inv.gz "daikon-$TYPE.inv.gz"

    gunzip -f "daikon-$TYPE.inv.gz"

    rm Runner.dtrace.gz
    # [[ -z "${NO_DYNCOMP}" ]] && rm Runner.decls-DynComp

    echo "Completed '$TYPE'. Daikon text output is in daikon-$TYPE.log, binary output is in daikon-$TYPE.inv"
    echo "[!] [$TYPE] Took $(( $(date +%s) - $START_TIME ))s"
}

if [ "$1" = "" ] || [ "$2" = "" ]; then
    echo "Usage: ./daikon-victim-polluter.sh <com.example.VictimTest> <com.example.PolluterTest>"
elif [ "$1" = "test" ]; then
    shift
    VICTIM=$1
    POLLUTER=$2
    
    java -cp "./target/dependency/*:./target/classes:./target/test-classes:$DAIKONDIR/daikon.jar:$scriptsDir/runner-1.0-SNAPSHOT.jar" in.natelev.runner.Runner $POLLUTER $VICTIM
else
    VICTIM=$1
    POLLUTER=$2

    run_daikon "pv" "fail" "$POLLUTER" "$VICTIM"
    run_daikon "polluter" "pass" "$POLLUTER"
    run_daikon "victim" "pass" "$VICTIM"
fi;
