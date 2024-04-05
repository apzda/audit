#!/usr/bin/env bash

export SERVICE_NAME=audit
export SERVICE_VER=1.0.2

if [ "$1" = "up" ]; then
    if [ "${2:0:2}" = "-D" ]; then
        mvn -pl ${SERVICE_NAME}-server -P+layer -am "${2}" clean package
    else
        mvn -pl ${SERVICE_NAME}-server -P+layer -am clean package
    fi
    docker rmi apzda/${SERVICE_NAME}-server:${SERVICE_VER}
fi

if [ -z "$1" ]; then
    exec docker compose up
else
    exec docker compose "${@:0:1}"
fi
