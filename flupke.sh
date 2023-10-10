#!/bin/sh
flupkejar=`ls build/libs/flupke-uber*.jar | grep -v javadoc | grep -v sources 2> /dev/null`
if [ ! -f "$flupkejar" ]; then
    echo "Cannot find flupke-uber.jar. Did you forgot to build it? ('gradle uberJar')"
    exit
fi
java -cp $flupkejar net.luminis.http3.sample.Sample $*
