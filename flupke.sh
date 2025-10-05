#!/bin/sh
# A simple script to run flupke client sample.

flupkejar=`ls core/build/libs/flupke-*uber.jar | grep -v javadoc | grep -v sources 2> /dev/null`
if [ ! -f "$flupkejar" ]; then
    echo "Cannot find flupke-uber.jar. Did you forgot to build it? ('gradle -p core uberJar')"
    exit
fi
flupkesamplesjar=`ls samples/build/libs/flupke-samples*.jar | grep -v javadoc | grep -v sources 2> /dev/null`
if [ ! -f "$flupkesamplesjar" ]; then
    echo "Cannot find flupke-samples.jar. Did you forgot to build it? ('gradle build')"
    exit
fi
java $JAVA_OPTS -cp $flupkejar:$flupkesamplesjar tech.kwik.flupke.sample.Sample $*
