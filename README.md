Droidel
=======

Droidel is a model of the Android framework that simplifies static analysis of Android apps. Droidel takes an Android application as input and generates a Java bytecode harness for the analysis to use as an entrypoint. Droidel also generates application-specialized stubs and injects them directly into the application bytecode. The result is a transformed app ready to be analyzed by any Java program analysis framework (WALA, Soot, Chord, etc.).

Installation
------------
Installing Droidel requires sbt and ant. Droidel also depends on the [JPhantom](http://cgi.di.uoa.gr/~smaragd/jphantom-oopsla13.pdf) [tool](https://github.com/gbalats/jphantom). To install Droidel, run the following from Droidel's top-level directory:

(1) Download and build JPhantom into the lib/ directory: 

    mkdir -p lib
    cd lib
    git clone https://github.com/gbalats/jphantom.git
    ant
    cd ..

(2) Build Droidel:

    sbt compile


Running regression tests
------------------------

To be sure everything was installed correctly, it is probably a good idea to run Droidel's regression tests:
   
	sbt test:run

These should complete without failing any assertions.


Running Droidel
--------------

Droidel takes two inputs: APP, a path to a top level directory of an Android application, and ANDROID_JAR, a path to a JAR containing the desired version of the Android library. Droidel expects the directory for the application to be organized as follows:

<pre>
APP
+-- AndroidManifest.XML
+-- bin/
|   +-- classes/
|       +-- bytecodes
|-- res/
|   +-- layout/
|       +-- layout XML files
+-- libs/
|   +-- library JARs, if any
</pre>

Do not use the JARS packaged with the Android SDK as ANDROID_JAR. These JARs only contain type-correct stubs for applications to compile against. Good sources for Android JARs are [GrepCode](http://grepcode.com/project/repository.grepcode.com/java/ext/com.google.android/android/) and Sable's [android-platforms](https://github.com/Sable/android-platforms) GitHub project.

Run Droidel using:

	./droidel.sh -app <APP> -android_jar <JAR>

Droidel will generate a harness and stubs and produce instrumented copies of the app's bytecodes. The original bytecodes of the app will remain unchanged; Droidel's output is placed in APP/bin/droidel_classes. This directory will also contain the bytecodes for the Android JAR specified on the command line and the bytecodes of any libraries in the APP/libs directory.

The most important output is Droidel's harness class; this is placed in APP/bin/droidel_classes/generatedHarness/GeneratedAndroidHarness.class. To use Droidel in a static analysis framework such as WALA or Soot, simply load all of the code in APP/bin/droidel_classes into the analyzer and use GeneratedAndroidHarness.main as the entrypoint for analysis. Because droidel_classes contains all of the Android library bytecodes, there should be no need to load any additional code other than the core Java libraries. For an example of how to build a call graph using Droidel's output in WALA, see the AndroidCGBuilder [class](https://github.com/cuplv/droidel/blob/master/src/main/scala/edu/colorado/droidel/driver/AndroidCGBuilder.scala).

Droidel generates its harness and stubs at the Java source level and then compiles them against the application. For convenience, it preserves these artifacts so that they be be manually inspected and modified/recompiled if necessary. The harness source code is located in APP/bin/droidel_classes/generatedHarness/GeneratedAndroidHarness.java, and the stub source code is located in APP/bin/droidel_classes/stubs/GeneratedAndroidStubs.java.

Contributions
-------------
We welcome contributions to Droidel -- please do not hesitate to send a pull request or ask to be added as a contributor. Send questions and comments about Droidel to samuel.blackshear@colorado.edu.

What Android features does Droidel handle?
------------------------------------------
Coming soon.

Known limitations
-----------------
Creating a reasonable framework model for Android is tedious and difficult, and Droidel is not perfect. A few known limitations of Droidel are:

(1) Currently, Droidel's harness is designed for flow-insensitive analysis. It does not faithfully model the invocation order of callbacks in the Android framework, and thus would not be suitable for direct use by a flow-sensitive analysis. We hope to add support for flow-sensitive harness generation in the future.

(2) Droidel does not model the invocation of lifecycle methods of the Fragment type, which is heavily used by many Android apps. We are working on adding support for Fragments.

(3) Droidel does not generate stubs for lookup of preferences parsed from XML via the Activity.getPreferences() method (and similar). This is also high on our priority list.

(4) There are many other Android framework methods that use reflection under the hood that we also do not (yet) handle.

Acknowledgements
----------------
We are indebted to the creators of [FlowDroid](http://sseblog.ec-spride.de/tools/flowdroid/) for both their list of callback classes in the Android framework and their list of lifecycle methods for the core Android lifecycle types (Activity, Service, etc.).










