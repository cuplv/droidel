[![Build Status](https://travis-ci.org/cuplv/droidel.svg?branch=master)](https://travis-ci.org/cuplv/droidel)

Droidel
=======

Droidel is a model of the Android framework that simplifies static analysis of Android apps. It works by examining app code and using it to explicate tricky uses of reflection in the Android framework. The result is a transformed app with a single entrypoint ready to be analyzed by any Java program analysis framework ([WALA](http://wala.sourceforge.net/wiki/index.php/Main_Page), [Soot](http://www.sable.mcgill.ca/soot/), [Chord](http://pag.gatech.edu/chord), etc.). See our SOAP 2015 [paper](http://www.cs.colorado.edu/~bec/papers/droidel-soap15.pdf) for a full explanation of the Droidel approach.


What Android features does Droidel handle?
------------------------------------------
(1) Framework-created types.  
Android is an event-driven system whose primary mechanism for control flow is the invocation of application-overridden callback methods by the framework. The framework code that invokes callbacks is complicated and uses reflection heavily. Droidel understands what application types the framework will instantiate reflectively. It uses this information to inject stubs into the framework that explicate what types are allocated via reflection. 

(2) Harness generation.  
Droidel generates a harness that can be used as a single entrypoint for an Android app. The harness is a slightly modified version of the framework's `ActivityThread.main` method. The important changes are that the modified `main` calls Droidel's stubs for framework-created types to de-obfuscate reflection, and it adds all possible externally generated events to the main `Looper` of the app.

(3) Layout inflation.  
Most Android applications define their GUI structure in specialized layout XML files. At runtime, the Android framework *inflates* the GUI by parsing the XML and instantiating each XML-declared element via reflection. Applications can then look up and manipulate the GUI elements via methods such as [`findViewById`](http://developer.android.com/reference/android/app/Activity.html#findViewById(int)). The inflation process and the correspond look up methods are also opaque to most static analyzers. Droidel parses the layout XML to determine the element types that may be allocated during inflation and their id's. Droidel then generates Java bytecodes stubs that explicate the allocations performed during layout inflation and injects them into the framework code.

(4) XML-registered callback invocation.  
When callbacks are registered in the layout XML via the `onClick` attribute, Droidel sees this and will generate stubs to make sure the method referred to by the `onClick` attribute gets invoked.


Installation
------------
Droidel has been tested on JDK 7. There are known issues with JDK 8.

Installing Droidel requires Scala 2.10.2 or later, sbt, maven, and ant. Droidel depends on [WALA](https://github.com/wala/WALA) and it also uses the [JPhantom](http://cgi.di.uoa.gr/~smaragd/jphantom-oopsla13.pdf) [tool](https://github.com/gbalats/jphantom). To install Droidel, run the following from Droidel's top-level directory:

(1) Download and build unmanaged dependencies using the provided script:
    
    cd lib
    ./install_deps.sh

(2) Build Droidel (from the droidel/ directory, do):

    sbt compile


Setting up an Android framework JAR
-----------------------------------
In order to run Droidel, you first need to generate a JAR file for the Android framework that has been injected with Droidel's stub interfaces. Do this by moving the Android JAR you want to use into the `stubs` directory and running `compile_stubs.sh <android_jar>` in the `stubs` directory. The resulting injected JAR will be places in `stubs/out/droidel_<android_jar>`.

Droidel has been tested using the [Android 4.4.2](http://repository.grepcode.com/java/ext/com/google/android/android/4.4.2_r1/android-4.4.2_r1.jar) JAR. Other versions of Android may require slight adjustments to the stubs in order to compile.

    cd stubs
    wget http://repository.grepcode.com/java/ext/com/google/android/android/4.4.2_r1/android-4.4.2_r1.jar
    ./compile_stubs.sh android-4.4.2_r1.jar

Converting Directory Structure of Android Application
---------------------------------------------
The script lib/gradle-to-droidel.sh is used to convert the directory structure from an Android Gradle project to what Droidel expects.  After the script is built with a command such as:

    ./gradlew assembleDebug 
   
The script can be run as 

    sh lib/gradle-to-droidel.sh [path to project] [build (eg debug)] [output dir]
    
The output directory can then be used as a normal project would in droidel.

Running Droidel
---------------

Droidel takes two inputs: APP, a path to an APK file or the top level directory of an Android application, and ANDROID_JAR, a path to a JAR containing the desired version of the Android library. Given an application directory as input, Droidel expects the directory for the application to be organized as follows:

<pre>
APP
+-- AndroidManifest.XML
+-- bin/
|   +-- classes/
|       +-- bytecodes
+-- res/
|   +-- layout/
|       +-- layout XML files
+-- libs/
|   +-- library JARs, if any
</pre>


If Droidel is given an APK, it decodes the application manifest and resources using [apktool](http://code.google.com/p/android-apktool/) and decompiles the DEX bytecodes to Java bytecodes using [dex2jar](http://code.google.com/p/dex2jar/). We plan to add support for decompiling using [Dare](http://siis.cse.psu.edu/dare/) in the near future. Droidel also organizes the decoded APK into the directory format outlined above.

Run Droidel using:

	./droidel.sh -app <APP> -android_jar <JAR> [OPTIONS]

or

	sbt "run -app <APP> -android_jar <JAR>"

The Android JAR passed here should be the one you generated using the process described in the "Setting up an Android framework JAR" section. Droidel will run JPhantom on the app and generate a harness and specialized stubs. The original bytecodes of the app will remain unchanged; Droidel's output is placed in `<APP>/bin/droidel_classes`. This directory will also contain the bytecodes for the Android JAR specified on the command line and the bytecodes of any libraries in the `<APP>/libs` directory.

The most important output is Droidel's harness class; this is placed in `<APP>/bin/droidel_classes/generatedHarness/GeneratedAndroidHarness.class`. To use Droidel in a static analysis framework such as WALA or Soot, simply load all of the code in `<APP>/bin/droidel_classes` into the analyzer and use `GeneratedAndroidHarness.androidMain()` as the entrypoint for analysis. Because `droidel_classes` contains all of the Android library bytecodes and bytecodes of all other libraries the app depends on, there should be no need to load any additional code other than the core Java libraries. For an example of how to build a call graph using Droidel's output in WALA, see how [`AndroidCGBuilder`](https://github.com/cuplv/droidel/blob/master/src/main/scala/edu/colorado/droidel/driver/AndroidCGBuilder.scala) is used in the regression [tests](https://github.com/cuplv/droidel/blob/master/src/test/scala/Regression.scala).

Droidel generates its harness and stubs at the Java source level and then compiles them against the application, its libraries, and the Android framework. For convenience, it preserves these artifacts so that they be be manually inspected and modified/recompiled if necessary. The harness source code is located at `<APP>/bin/droidel_classes/generatedharness/GeneratedAndroidHarness.java`, and the stub source code is located in `<APP>/bin/droidel_classes/generatedstubs`.


Known limitations
-----------------
Creating a reasonable framework model for Android is difficult, and Droidel is not perfect. A few known limitations of Droidel are:

(1) Currently, Droidel's harness is designed for flow-insensitive analysis. It does not faithfully model the invocation order of callbacks in the Android framework, and thus would not be suitable for direct use by a flow-sensitive analysis. We hope to add support for flow-sensitive harness generation in the future.

(2) There are other Android framework methods that use reflection under the hood that we also do not (yet) handle.


Troubleshooting
---------------
Problem: Droidel fails with "Couldn't compile stub file" or "Couldn't compile harness" message.  
Solution: Make sure that you are using the appropriate version of the Android JAR for your target application. Check the android:minSdkVersion and/or android:targetSdkVersion in AndroidManifest.xml to see what version of the framework is expected. Also, make sure that you have followed the instructions in the "Setting up an Android framework JAR" section. If all else fails, try manually fixing the compiler errors and re-compiling the stubs and/or harness.

Problem: Droidel fails with "com.ibm.wala.shrikeCT.InvalidClassFileException" error message.  
Solution: Droidel uses WALA and Shrike, which cannot currently parse bytecodes produced by the Java 8 compiler. Try switching your default Java version to Java 7 or earlier.


Contributions
-------------
We welcome contributions to Droidel -- please do not hesitate to report an issue, send a pull request, or ask to be added as a contributor. Send questions and comments about Droidel to samuel.blackshear@colorado.edu.   


Acknowledgments
----------------
We are indebted to the creators of [FlowDroid](http://sseblog.ec-spride.de/tools/flowdroid/) for both their list of callback classes in the Android framework and their list of lifecycle methods for the core Android lifecycle types (Activity, Service, etc.).

This material is based on research sponsored in part by DARPA under agreement number FA8750-14-2-0039, the National Science Foundation under CAREER grant number CCF-1055066, and a Facebook Graduate Fellowship. The U.S. Government is authorized to reproduce and distribute reprints for Governmental purposes notwithstanding any copyright notation thereon.










