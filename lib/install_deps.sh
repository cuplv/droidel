#!/bin/bash

# download and build JPhantom
git clone https://github.com/gbalats/jphantom
cd jphantom && ant jar
cd ..

# download and publish WALAUtil
git clone https://github.com/cuplv/walautil.git
cd walautil && sbt publishLocal
cd ..

# download apktool
wget https://bitbucket.org/iBotPeaches/apktool/downloads/apktool_2.0.0rc2.jar
mkdir apktool
mv apktool_2.0.0rc2.jar apktool/apktool.jar

# download dex2jar
wget http://dex2jar.googlecode.com/files/dex2jar-0.0.9.15.zip
unzip dex2jar-0.0.9.15.zip
mv dex2jar-0.0.9.15/lib/*.jar .
rm -r dex2jar-0.0.9.15
rm dex2jar-0.0.9.15.zip

