#!/bin/sh
# Run flupke client sample using Java modules

DEPENDENCIES_DIR=libs
if [ ! -d "$DEPENDENCIES_DIR" ] || [ -z "$(ls -A $DEPENDENCIES_DIR)" ]; then
    echo "Cannot find dependencies in 'libs' directory."
    echo "Download the dependencies and place them in the 'libs' directory."
    echo "You can view the runtime dependencies by running 'gradle -p core dependencies --configuration runtimeClasspath'"
    echo "Here is the output of this gradle command: `gradle -p core -q dependencies --configuration runtimeClasspath`"
    exit
fi

 flupkemodule=`ls core/build/libs/flupke*.jar | grep -v javadoc | grep -v sources 2> /dev/null`
if [ ! -f "$flupkemodule" ]; then
    echo "Cannot find flupke module"
    exit
fi
flupkesamplesmodule=`ls samples/build/libs/flupke-sample*.jar | grep -v javadoc | grep -v sources 2> /dev/null`
if [ ! -f "$flupkesamplesmodule" ]; then
    echo "Cannot find flupke samples module"
    exit
fi

qpackjar=`ls $DEPENDENCIES_DIR/qpack*.jar | grep -v javadoc | grep -v sources 2> /dev/null`
if [ ! -f "$qpackjar" ]; then
    echo "Cannot find qpack module"
    exit
fi
kwikcorejar=`ls $DEPENDENCIES_DIR/kwik*.jar | grep -v javadoc | grep -v sources 2> /dev/null`
if [ ! -f "$kwikcorejar" ]; then
    echo "Cannot find kwik module"
    exit
fi
siphashjar=`ls $DEPENDENCIES_DIR/io.whitfin.siphash*.jar | grep -v javadoc | grep -v sources 2> /dev/null`
if [ ! -f "$siphashjar" ]; then
    echo "Cannot find siphash module"
    exit
fi
hkdfjar=`ls $DEPENDENCIES_DIR/hkdf*.jar | grep -v javadoc | grep -v sources 2> /dev/null`
if [ ! -f "$hkdfjar" ]; then
    echo "Cannot find hkdf module"
    exit
fi
agent15jar=`ls $DEPENDENCIES_DIR/agent15*.jar | grep -v javadoc | grep -v sources 2> /dev/null`
if [ ! -f "$agent15jar" ]; then
    echo "Cannot find agent15 module"
    exit
fi

MODULEPATH=$flupkemodule:$flupkesamplesmodule:$DEPENDENCIES_DIR
java --module-path $MODULEPATH --module tech.kwik.flupke.samples/tech.kwik.flupke.sample.Sample $*


