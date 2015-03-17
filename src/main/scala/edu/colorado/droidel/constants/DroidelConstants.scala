package edu.colorado.droidel.constants

import com.ibm.wala.types.TypeReference
import com.ibm.wala.types.ClassLoaderReference
import AndroidConstants._
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
  val PREWRITTEN_STUB_DIR = "droidelhelpers"
  val STUB_DIR = "generatedstubs"
  val LAYOUT_STUB_CLASS = "GeneratedAndroidLayoutStubs"
  val INFLATE_VIEW_BY_ID = "inflateViewById"
  val GET_SUPPORT_FRAGMENT = "getSupportFragment"
  val GET_APP_FRAGMENT = "getAppFragment"

  val SYSTEM_SERVICE_STUB_CLASS = "GeneratedAndroidSystemServiceStubs"

  val XML_DECLARED_CALLBACKS_STUB_CLASS = "GeneratedAndroidXMLCallbackStubs"
  val XML_DECLARED_CALLBACKS_STUB_METHOD = "callXMLRegisteredCallback"

  val APPLICATION_STUB_CLASS = "GeneratedApplicationStubs"
  val APPLICATION_STUB_METHOD = "getApplication"
  val ACTIVITY_STUB_CLASS = "GeneratedActivityStubs"
  val ACTIVITY_STUB_METHOD = "getActivity"
  val SERVICE_STUB_CLASS = "GeneratedServiceStubs"
  val SERVICE_STUB_METHOD = "getService"
  val BROADCAST_RECEIVER_STUB_CLASS = "GeneratedBroadcastReceiverStubs"
  val BROADCAST_RECEIVER_STUB_METHOD = "getBroadcastReceiver"
  val CONTENT_PROVIDER_STUB_CLASS = "GeneratedContentProviderStubs"
  val CONTENT_PROVIDER_STUB_METHOD = "getContentProvider"

  private val NULL = "null"
  // map from a framework-created type to its stub class, stub method and a string representing its default value
  val TYPE_STUBS_MAP =
    Map(APPLICATION_TYPE -> (APPLICATION_STUB_CLASS, APPLICATION_STUB_METHOD, s"new $APPLICATION_TYPE()"),
        ACTIVITY_TYPE -> (ACTIVITY_STUB_CLASS, ACTIVITY_STUB_METHOD, s"new $ACTIVITY_TYPE()"),
        SERVICE_TYPE -> (SERVICE_STUB_CLASS, SERVICE_STUB_METHOD, NULL),
        BROADCAST_RECEIVER_TYPE -> (BROADCAST_RECEIVER_STUB_CLASS, BROADCAST_RECEIVER_STUB_METHOD, NULL),
        CONTENT_PROVIDER_TYPE -> (CONTENT_PROVIDER_STUB_CLASS, CONTENT_PROVIDER_STUB_METHOD, NULL)
       )

  val HARNESS_DIR = "generatedharness"
  val HARNESS_CLASS = "GeneratedAndroidHarness" 
  val HARNESS_TYPE =
    TypeReference.findOrCreate(ClassLoaderReference.Primordial, s"L$HARNESS_DIR${File.separator}$HARNESS_CLASS")
  val HARNESS_MAIN = "androidMain"

}
