package edu.colorado.droidel.constants

import com.ibm.wala.types.TypeReference
import com.ibm.wala.types.ClassLoaderReference
import java.io.File

object DroidelConstants {
  var DROIDEL_HOME = "."
  // path to list of special callback interface classes
  val CALLBACK_LIST = "AndroidCallbacks.txt"
  // path to libraries in canonical layout of Android project
  val LIB_SUFFIX = "libs"
  // path to binaries in canonical layout of Android project    
  val BIN_SUFFIX = s"bin${File.separator}classes"
  // path to binaries that have been complemented by JPhantom
  val JPHANTOMIZED_BIN_SUFFIX = s"bin${File.separator}jphantom_classes"
  // path to binaries that have been instrumented by Droidel
  val DROIDEL_BIN_SUFFIX = s"bin${File.separator}droidel_classes"
  
  // default locations for generated stubs and harnesses
  val STUB_DIR = "generatedstubs"
  val LAYOUT_STUB_CLASS = "GeneratedAndroidLayoutStubs"
  val SYSTEM_SERVICE_STUB_CLASS = "GeneratedAndroidSystemServiceStubs"

  val APPLICATION_STUB_CLASS = "GeneratedApplicationStubs"
  val APPLICATION_STUB_METHOD = "getApplication"
  val ACTIVITY_STUB_CLASS = "GeneratedActivityStubs"
  val ACTIVITY_STUB_METHOD = "getActivity"

  val HARNESS_DIR = "generatedharness"
  val HARNESS_CLASS = "GeneratedAndroidHarness" 
  val HARNESS_TYPE = TypeReference.findOrCreate(ClassLoaderReference.Primordial, s"L$HARNESS_DIR${File.separator}$HARNESS_CLASS")      
  val HARNESS_MAIN = "androidMain"

}
