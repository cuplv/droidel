package edu.colorado.droidel.constants

import com.ibm.wala.types.TypeReference
import com.ibm.wala.types.ClassLoaderReference

object DroidelConstants {
  // path to list of special callback interface classes
  val CALLBACK_LIST_PATH = "src/main/resources/AndroidCallbacks.txt"
      
  // path to libraries in canonical layout of Android project
  val LIB_SUFFIX = "libs"
  // path to binaries in canonical layout of Android project    
  val BIN_SUFFIX = "bin/classes"
  // path to binaries that have been complemented by JPhantom
  val JPHANTOMIZED_BIN_SUFFIX = "bin/jphantom_classes"
  // path to binaries that have been instrumented by Droidel
  val DROIDEL_BIN_SUFFIX = "bin/droidel_classes"
    
  // default locations for generated stubs and harnesses
  val STUB_DIR = "stubs"
  val HARNESS_DIR = "generatedHarness"
  val HARNESS_CLASS = "GeneratedAndroidHarness" 
  val HARNESS_TYPE = TypeReference.findOrCreate(ClassLoaderReference.Primordial, s"L$HARNESS_DIR/$HARNESS_CLASS")      
  val HARNESS_MAIN = "androidMain"

}
