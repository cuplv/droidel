#!/bin/bash

# download and build JPhantom
git clone https://github.com/gbalats/jphantom
cd jphantom && ant jar
cd ..

# download apktool
wget http://android-apktool.googlecode.com/files/apktool1.5.2.tar.bz2
tar xvf apktool1.5.2.tar.bz2
mv apktool1.5.2/*.jar .
rm -r apktool1.5.2/
rm apktool1.5.2.tar.bz2

# download dex2jar
wget http://dex2jar.googlecode.com/files/dex2jar-0.0.9.15.zip
unzip dex2jar-0.0.9.15.zip
mv dex2jar-0.0.9.15/lib/*.jar .
rm -r dex2jar-0.0.9.15
rm dex2jar-0.0.9.15.zip

