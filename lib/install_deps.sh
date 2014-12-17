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
wget http://downloads.sourceforge.net/project/dex2jar/dex2jar-2.0-20140818.061505-10.zip
unzip dex2jar-2.0-20140818.061505-10.zip
mv dex2jar-2.0-SNAPSHOT dex2jar
rm dex2jar-2.0-20140818.061505-10.zip

