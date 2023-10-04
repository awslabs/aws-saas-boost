#!/bin/sh

artifact="SaaSBoostApiClientHelper"
package=$artifact-lambda.zip

if [ -d build ]
then
	echo "cleaning existing build dir"
	rm -rf build
fi

mkdir build
cp -a python build

mvn -q -f java/pom.xml clean package
unzip -q java/target/$package -d build

cd build
if [ -f $package ]
then
	rm -f $package
fi
zip -r $package .
cd ..
