#!/bin/sh
if [ ! -f build/libs/flupke-uber.jar ]; then
    echo "Cannot find flupke-uber.jar. Did you forgot to build it? ('gradle uberJar')"
    exit
fi

java -cp build/libs/flupke-uber.jar net.luminis.http3.sample.Sample $*
