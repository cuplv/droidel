#!/bin/bash

[[ $@ ]] || { echo "Usage: ./compile_stubs.sh <android_jar>" ; exit 1; }

rm -rf out/*
mkdir -p out
cp $1 out
cd out && jar xf $1 && rm $1 && cd ..
find . -name *.java | xargs javac -cp .:out -d out
cd out && jar cf droidel_$1 *
