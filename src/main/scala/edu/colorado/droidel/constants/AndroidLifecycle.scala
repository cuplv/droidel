package edu.colorado.droidel.constants

import com.ibm.wala.classLoader.{IClass, IMethod}
import com.ibm.wala.ipa.cha.IClassHierarchy
import com.ibm.wala.types.{ClassLoaderReference, MethodReference, Selector, TypeReference}
import edu.colorado.droidel.constants.AndroidConstants._
import edu.colorado.walautil.ClassUtil

import scala.collection.JavaConversions._

/** list taken from FlowDroid's AndroidEntryPointConstants.java */
object AndroidLifecycle {
  
  // lifecycle callbacks written in WALA style
  val ACTIVITY_ONCREATE = "onCreate(Landroid/os/Bundle;)V"
  val ACTIVITY_ONSTART = "onStart()V"
  val ACTIVITY_ONRESTOREINSTANCESTATE = "onRestoreInstanceState(Landroid/os/Bundle;)V"
  val ACTIVITY_ONPOSTCREATE = "onPostCreate(Landroid/os/Bundle;)V"
  val ACTIVITY_ONRESUME = "onResume()V"
  val ACTIVITY_ONPOSTRESUME = "onPostResume()V"
  val ACTIVITY_ONCREATEDESCRIPTION = "onCreateDescription()Ljava/lang/CharSequence;"
  val ACTIVITY_ONSAVEINSTANCESTATE = "onSaveInstanceState(Landroid/os/Bundle;)V"
  val ACTIVITY_ONPAUSE = "onPause()V"
  val ACTIVITY_ONSTOP = "onStop()V"
  val ACTIVITY_ONRESTART = "onRestart()V"
  val ACTIVITY_ONDESTROY = "onDestroy()V"
  
  val SERVICE_ONCREATE = "onCreate()V"
  val SERVICE_ONSTART1 = "onStart(Landroid/content/Intent;I)V"
  val SERVICE_ONSTART2 = "onStartCommand(Landroid/content/Intent;II)I"
  val SERVICE_ONBIND = "onBind(Landroid/content/Intent;)Landroid/os/IBinder;"
  val SERVICE_ONREBIND = "onRebind(Landroid/content/Intent;)V"
  val SERVICE_ONUNBIND = "onUnbind(Landroid/content/Intent;)Z"
  val SERVICE_ONDESTROY = "onDestroy()V"
  
  val BROADCAST_ONRECEIVE = "onReceive(Landroid/content/Context;Landroid/content/Intent;)V"
 
  val CONTENTPROVIDER_ONCREATE = "onCreate()Z"  
       
  val APPLICATION_ONCREATE = "onCreate()V"
  val APPLICATION_ONTERMINATE = "onTerminate()V"
    
  // TODO: this is not complete
  val FRAGMENT_ONCREATE = "onCreate(Landroid/os/Bundle;)V"
  val FRAGMENT_ONSTART = "onStart()V"

  // TODO: what are these?
  val APPLIFECYCLECALLBACK_ONACTIVITYSTARTED = "void onActivityStarted(android.app.Activity)";
  val APPLIFECYCLECALLBACK_ONACTIVITYSTOPPED = "void onActivityStopped(android.app.Activity)";
  val APPLIFECYCLECALLBACK_ONACTIVITYSAVEINSTANCESTATE = "void onActivitySaveInstanceState(android.app.Activity,android.os.Bundle)";
  val APPLIFECYCLECALLBACK_ONACTIVITYRESUMED = "void onActivityResumed(android.app.Activity)";
  val APPLIFECYCLECALLBACK_ONACTIVITYPAUSED = "void onActivityPaused(android.app.Activity)";
  val APPLIFECYCLECALLBACK_ONACTIVITYDESTROYED = "void onActivityDestroyed(android.app.Activity)";
  val APPLIFECYCLECALLBACK_ONACTIVITYCREATED = "void onActivityCreated(android.app.Activity,android.os.Bundle)";             
  
  // mapping of framework-created lifecycle types to callbacks defined on those types
  val frameworkCbMap = Map(
    ACTIVITY_TYPE -> List(ACTIVITY_ONCREATE, 
                          ACTIVITY_ONSTART, 
                          ACTIVITY_ONRESTOREINSTANCESTATE,
                          ACTIVITY_ONPOSTCREATE,
                          ACTIVITY_ONRESUME,
                          ACTIVITY_ONPOSTRESUME,
                          ACTIVITY_ONCREATEDESCRIPTION,
                          ACTIVITY_ONSAVEINSTANCESTATE,
                          ACTIVITY_ONPAUSE,
                          ACTIVITY_ONSTOP,
                          ACTIVITY_ONRESTART,
                          ACTIVITY_ONDESTROY
                     ),
    SERVICE_TYPE -> List(SERVICE_ONCREATE,
                         SERVICE_ONSTART1,
                         SERVICE_ONSTART2,
                         SERVICE_ONBIND,
                         SERVICE_ONREBIND,
                         SERVICE_ONUNBIND,
                         SERVICE_ONDESTROY
                    ),
    BROADCAST_RECEIVER_TYPE -> List(BROADCAST_ONRECEIVE),
    CONTENT_PROVIDER_TYPE -> List(CONTENTPROVIDER_ONCREATE),
    APPLICATION_TYPE -> List(APPLICATION_ONCREATE,
                             APPLICATION_ONTERMINATE
                        ),
    FRAGMENT_TYPE -> List(FRAGMENT_ONCREATE, 
                          FRAGMENT_ONSTART
                     ),
    APP_FRAGMENT_TYPE -> List(FRAGMENT_ONCREATE, 
                              FRAGMENT_ONSTART)
  )
    
  def getFrameworkCreatedClasses(cha : IClassHierarchy) : Iterable[IClass] =
    frameworkCbMap.keys.foldLeft (List.empty[IClass]) ((l, c) => {
      val clazz = cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Primordial, ClassUtil.walaifyClassName(c)))
      if (clazz == null) {
        println(s"Warning: couldn't find $c in class hierarchy")
        l
      } else clazz :: l
  })
  
  def isFrameworkCreatedType(c : IClass, cha : IClassHierarchy) : Boolean = getFrameworkCreatedClasses(cha).contains(c)
  
  def getCallbacksOnFrameworkCreatedType(frameworkType : IClass, cha : IClassHierarchy) : Iterable[IMethod] = {
    val className = ClassUtil.deWalaifyClassName(frameworkType.getName())
    
    frameworkCbMap.get(className) match {
      case Some(cbMethods) =>
        val frameworkTypeRef = frameworkType.getReference()
        cbMethods.map(m => {
          val methodRef = MethodReference.findOrCreate(frameworkTypeRef, Selector.make(m))
          cha.resolveMethod(methodRef) match {
            case null => sys.error(s"Couldn't find method $methodRef on class $frameworkType. Methods: ${frameworkType.getAllMethods().toList}")
            case m => m
          }
        })
      case None => sys.error(s"$className is not a framework-created type")
    }
  }

}
