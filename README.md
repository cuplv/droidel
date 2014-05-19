[![Build Status](https://travis-ci.org/cuplv/droidel.svg?branch=master)](https://travis-ci.org/cuplv/droidel)

Droidel
=======

Droidel is a model of the Android framework that simplifies static analysis of Android apps. Droidel takes an Android application as input and generates a Java bytecode harness for the analysis to use as an entrypoint. Droidel also generates application-specialized stubs and injects them directly into the application bytecode. The result is a transformed app ready to be analyzed by any Java program analysis framework ([WALA](http://wala.sourceforge.net/wiki/index.php/Main_Page), [Soot](http://www.sable.mcgill.ca/soot/), [Chord](http://pag.gatech.edu/chord), etc.).


What Android features does Droidel handle?
------------------------------------------
(1) Control flow via callbacks.  
Android is an event-driven system whose primary mechanism for control flow is the invocation of application-defined callback methods by the framework. The framework code that invokes callbacks is complicated and uses reflection heavily, which makes it opaque to most static analyzers. Droidel generates a Java bytecode harness that explicates this control flow. It identifies lifecycle callbacks (e.g., [`Activity.onCreate()`](http://developer.android.com/reference/android/app/Activity.html#onCreate(android.os.Bundle))), callbacks registered by extending special callback interfaces (e.g., [`OnClickListener`](http://developer.android.com/reference/android/view/View.OnClickListener.html)), and callbacks registered in the application manifest. Droidel generates a harness that invokes each of these callbacks on the appropriate object instance, yielding a single entrypoint for a static analysis to use.


(2) Layout inflation.  
Most Android applications define their GUI structure in specialized layout XML files. At runtime, the Android framework *inflates* the GUI by parsing the XML and instantiating each XML-declared element via reflection. Applications can then look up and manipulate the GUI elements via methods such as [`findViewById`](http://developer.android.com/reference/android/app/Activity.html#findViewById(int)). The inflation process and the correspond look up methods are also opaque to most static analyzers. Droidel parses the layout XML to determine the element types that may be allocated during inflation and their id's. Droidel then generates Java bytecodes stubs that explicate the allocations performed during layout inflation and generates stubs for `findViewById` and `findFragmentById` that return the appropriate layout element given its id. Finally, Droidel injects the stubs directly into the application code by using WALA's Shrike bytecode instrumentation utility. For the common case where `findViewById` is called with a constant identifier (e.g, `findViewById(R.id.MainActivity)`), Droidel injects a *specialized* stub that returns only the layout element whose identifier is `R.id.MainActivity`, thereby increasing precision.


(3) System services.
Utilities for performing many permission-sensitive operations (e.g., `NotificationManager`, `LocationManager`) are exposed via the [`getSystemService`](http://developer.android.com/reference/android/content/Context.html#getSystemService(java.lang.String)) method, which uses reflection. Failure to handle this reflection will cause many permission-sensitive methods to be absent from the call graph. Droidel handles this problem by generating stubs for `getSystemService` and injecting the stubs into both application and library code (including the Android library) using Shrike bytecode instrumentation.



Installation
------------
Installing Droidel requires sbt, maven, and ant. Droidel depends on [WALA](https://github.com/wala/WALA) and it also uses the [JPhantom](http://cgi.di.uoa.gr/~smaragd/jphantom-oopsla13.pdf) [tool](https://github.com/gbalats/jphantom). To install Droidel, run the following from Droidel's top-level directory:

(1) Download WALA and install it to your local Maven repository (it doesn't matter where the WALA project lives w.r.t Droidel):

    git clone https://github.com/wala/WALA.git
    cd WALA
    mvn clean install -DskipTests=true

(2) Download and build unmanaged dependencies using the provided script:
    
    cd lib
    ./install_deps.sh

(3) Build Droidel (from the droidel/ directory, do):

    sbt compile


Android JARs
------------
In order to run Droidel, you need a JAR file containing the bytecodes for the Android framework. Good sources for Android JARs are [GrepCode](http://grepcode.com/project/repository.grepcode.com/java/ext/com.google.android/android/) and Sable's [android-platforms](https://github.com/Sable/android-platforms) GitHub project. Do not use the JARs packaged with the Android SDK. These JARs only contain type-correct stubs for applications to compile against; they are not suitable for static analysis of the Android framework.


Running regression tests
------------------------

To be sure everything was installed correctly, it is probably a good idea to run Droidel's regression tests:
   
	sbt "test:run <path to an android JAR>"

The quotes are important. These should complete without failing any assertions. 


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

Droidel will generate a harness and stubs and produce instrumented copies of the app's bytecodes. The original bytecodes of the app will remain unchanged; Droidel's output is placed in APP/bin/droidel_classes. This directory will also contain the bytecodes for the Android JAR specified on the command line and the bytecodes of any libraries in the APP/libs directory.

The most important output is Droidel's harness class; this is placed in APP/bin/droidel_classes/generatedHarness/GeneratedAndroidHarness.class. To use Droidel in a static analysis framework such as WALA or Soot, simply load all of the code in APP/bin/droidel_classes into the analyzer and use `GeneratedAndroidHarness.androidMain()` as the entrypoint for analysis. Because droidel_classes contains all of the Android library bytecodes and bytecodes of all other libraries the app depends on, there should be no need to load any additional code other than the core Java libraries. For an example of how to build a call graph using Droidel's output in WALA, see how [`AndroidCGBuilder`](https://github.com/cuplv/droidel/blob/master/src/main/scala/edu/colorado/droidel/driver/AndroidCGBuilder.scala) is used in the regression [tests](https://github.com/cuplv/droidel/blob/master/src/test/scala/Regression.scala).

Droidel generates its harness and stubs at the Java source level and then compiles them against the application, its libraries, and the Android framework. For convenience, it preserves these artifacts so that they be be manually inspected and modified/recompiled if necessary. The harness source code is located at APP/bin/droidel_classes/generatedharness/GeneratedAndroidHarness.java, and the stub source code is located in APP/bin/droidel_classes/generatedstubs.


Known limitations
-----------------
Creating a reasonable framework model for Android is difficult, and Droidel is not perfect. A few known limitations of Droidel are:

(1) Currently, Droidel's harness is designed for flow-insensitive analysis. It does not faithfully model the invocation order of callbacks in the Android framework, and thus would not be suitable for direct use by a flow-sensitive analysis. We hope to add support for flow-sensitive harness generation in the future.

(2) Droidel does not generate stubs for lookup of preferences parsed from XML via the Activity.getPreferences() method (and similar). Fixing this is also high on our priority list.

(3) There are many other Android framework methods that use reflection under the hood that we also do not (yet) handle.


Troubleshooting
---------------
Problem: Droidel fails with "Couldn't compile stub file" or "Couldn't compile harness" message.  
Solution: Make sure that you are using the appropriate version of the Android JAR for your target application. Check the android:minSdkVersion and/or android:targetSdkVersion in AndroidManifest.xml to see what version of the framework is expected. If all else fails, try manually fixing the compiler errors and re-compiling the stubs and/or harness.

Problem: Droidel fails with "com.ibm.wala.shrikeCT.InvalidClassFileException" error message.  
Solution: Droidel uses WALA and Shrike, which cannot currently parse bytecodes produced by the Java 8 compiler. Try switching your default Java version to Java 7 or earlier.


Contributions
-------------
We welcome contributions to Droidel -- please do not hesitate to report an issue, send a pull request, or ask to be added as a contributor. Send questions and comments about Droidel to samuel.blackshear@colorado.edu.   


Eclipse
-------
To build Droidel in Eclipse, make sure you have installed the SBT Eclipse [plugin](https://github.com/typesafehub/sbteclipse/wiki/Installing-sbteclipse) and add the following two lines to Droidel's build.sbt:

   EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource  

   EclipseKeys.withSource := true

Finally, run 
    
    sbt eclipse

to generate an Eclipse project.


Acknowledgments
----------------
We are indebted to the creators of [FlowDroid](http://sseblog.ec-spride.de/tools/flowdroid/) for both their list of callback classes in the Android framework and their list of lifecycle methods for the core Android lifecycle types (Activity, Service, etc.).

This material is based on research sponsored by DARPA under agreement number FA8750-14-2-0039. The U.S.Government is authorized to reproduce and distribute reprints for Governmental purposes notwithstanding any copyright notation thereon.










