#!/bin/bash

PACKAGE=${PWD##*/}
echo Building $PACKAGE

mvn clean package && docker build -t rdomloge/$PACKAGE-armv7 . && docker build -t rdomloge/$PACKAGE-i386 . -f Dockerfile-i386 \
&& docker tag rdomloge/$PACKAGE-armv7 docker.io/rdomloge/$PACKAGE-armv7 \
&& docker tag rdomloge/$PACKAGE-i386 docker.io/rdomloge/$PACKAGE-i386 \
&& docker push docker.io/rdomloge/$PACKAGE-armv7 \
&& docker push docker.io/rdomloge/$PACKAGE-i386 \
