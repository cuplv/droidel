#!/bin/bash

#[[ $# -ge 3 ]] || { echo "Usage: $0 [app] [build: eg: debug] [target]" ; exit 1; }
if [ $# -ne 3 ]
then
	echo "Usage: $0 [app] [build: eg: debug] [target]" 
	exit 1
fi

appdir=${1%/}

# if ./app exists append it to appdir

if [ -d $appdir/app/ ]
then
	appdir=$appdir/app/
fi



build=$2
appdirname=$(basename $1)
target=${3%/}

mkdir -p $target/bin/classes $target/res
if [ -d $appdir/build/intermediates/classes/ ]
then
	cp -R $appdir/build/intermediates/classes/$build/* $target/bin/classes
elif [ -d $appdir/bin/classes/ ]
then
	cp -R $appdir/bin/classes/* $target/bin/classes
else
	echo "************ERRROR***************"
	echo "classes dir not found"
fi

if [ -d $appdir/build/intermediates/res/ ]
then
	cp -R $appdir/build/intermediates/res/$build/layout $target/res/layout
elif [ -d $appdir/res/layout ]
then
	cp -R $appdir/res/layout $target/res/layout
else
	echo "************ERRROR***************"
	echo "layout dir not found"
fi

#TODO: copy .m2 and .ivy jars into lib otherwise jphantom will make fake versions
if [ -d "$appdir/libs" ]; then
	cp -R $appdir/libs $target/libs
else
	mkdir $target/libs
fi
if [ -f $appdir/build/intermediates/manifests/full/$build/AndroidManifest.xml ]
then
	cp $appdir/build/intermediates/manifests/full/$build/AndroidManifest.xml $target
elif [ -f $appdir/build/intermediates/manifests/debug/AndroidManifest.xml ]
then
	cp $appdir/build/intermediates/manifests/debug/AndroidManifest.xml $target
elif [ -d $appdir/Wordpress/build/intermediates/manifests ]
then
	cp $appdir/WordPress/src/main/AndroidManifest.xml $target
else
	echo "There was a problem copying the android manifest, please check location and try again"

fi

