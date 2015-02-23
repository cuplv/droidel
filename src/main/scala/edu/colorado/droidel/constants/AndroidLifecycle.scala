package edu.colorado.droidel.constants

import com.ibm.wala.classLoader.{IClass, IMethod}
import com.ibm.wala.ipa.cha.IClassHierarchy
import com.ibm.wala.types._
import edu.colorado.droidel.constants.AndroidConstants._
import edu.colorado.walautil.{GraphImpl, ClassUtil}

import scala.collection.JavaConversions._

/** list taken from FlowDroid's AndroidEntryPointConstants.java */
object AndroidLifecycle {
  
  // lifecycle callbacks written in WALA style
  val ACTIVITY_ONCREATE = "onCreate(Landroid/os/Bundle;)V"
  val ACTIVITY_ONATTACHFRAGMENT = "onAttachFragment(Landroid/app/Fragment;)V"
  val ACTIVITY_ONCONTENTCHANGED = "onContentChanged()V"
  val ACTIVITY_ONSTART = "onStart()V"
  val ACTIVITY_ONRESTOREINSTANCESTATE = "onRestoreInstanceState(Landroid/os/Bundle;)V"
  val ACTIVITY_ONPOSTCREATE = "onPostCreate(Landroid/os/Bundle;)V"
  val ACTIVITY_ONRESUME = "onResume()V"
  val ACTIVITY_ONPOSTRESUME = "onPostResume()V"
  val ACTIVITY_ONATTACHEDTOWINDOW = "onAttachedToWindow()V"
  val ACTIVITY_ONCREATEOPTIONSMENU = "onCreateOptionsMenu(Landroid/view/Menu;)Z"
  val ACTIVITY_ONPREPAREOPTIONSMENU = "onPrepareOptionsMenu(Landroid/view/Menu;)Z"
  val ACTIVITY_ONCREATEDESCRIPTION = "onCreateDescription()Ljava/lang/CharSequence;"
  val ACTIVITY_ONSAVEINSTANCESTATE = "onSaveInstanceState(Landroid/os/Bundle;)V"
  val ACTIVITY_ONPAUSE = "onPause()V"
  val ACTIVITY_ONSTOP = "onStop()V"
  val ACTIVITY_ONRESTART = "onRestart()V"
  val ACTIVITY_ONDESTROY = "onDestroy()V"
  
  val SERVICE_ONCREATE = "onCreate()V"
  val SERVICE_ONSTARTCOMMAND = "onStartCommand(Landroid/content/Intent;II)I"
  val SERVICE_ONBIND = "onBind(Landroid/content/Intent;)Landroid/os/IBinder;"
  val SERVICE_ONREBIND = "onRebind(Landroid/content/Intent;)V"
  val SERVICE_ONUNBIND = "onUnbind(Landroid/content/Intent;)Z"
  val SERVICE_ONDESTROY = "onDestroy()V"
  
  val BROADCAST_ONRECEIVE = "onReceive(Landroid/content/Context;Landroid/content/Intent;)V"
 
  val CONTENTPROVIDER_ONCREATE = "onCreate()Z"  
       
  val APPLICATION_ONCREATE = "onCreate()V"
  val APPLICATION_ONTERMINATE = "onTerminate()V"
    
  val FRAGMENT_ONINFLATE = "onInflate(Landroid/app/Activity;Landroid/util/AttributeSet;Landroid/os/Bundle;)V"
  val FRAGMENT_ONATTACH = "onAttach(Landroid/app/Activity;)V"
  val FRAGMENT_ONCREATEVIEW = "onCreateView(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;"
  val FRAGMENT_ONVIEWCREATED = "onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V"
  val FRAGMENT_ONCREATE = "onCreate(Landroid/os/Bundle;)V"
  val FRAGMENT_ONSTART = "onStart()V"
  val FRAGMENT_ONRESUME = "onResume()V"
  val FRAGMENT_ONPAUSE = "onPause()V"
  val FRAGMENT_ONSTOP = "onStop()V"
  val FRAGMENT_ONDESTROYVIEW = "onDestroyView()V"
  val FRAGMENT_ONDETACH = "onDetach()V"

  // TODO: what are these?
  val APPLIFECYCLECALLBACK_ONACTIVITYSTARTED = "void onActivityStarted(android.app.Activity)";
  val APPLIFECYCLECALLBACK_ONACTIVITYSTOPPED = "void onActivityStopped(android.app.Activity)";
  val APPLIFECYCLECALLBACK_ONACTIVITYSAVEINSTANCESTATE = "void onActivitySaveInstanceState(android.app.Activity,android.os.Bundle)";
  val APPLIFECYCLECALLBACK_ONACTIVITYRESUMED = "void onActivityResumed(android.app.Activity)";
  val APPLIFECYCLECALLBACK_ONACTIVITYPAUSED = "void onActivityPaused(android.app.Activity)";
  val APPLIFECYCLECALLBACK_ONACTIVITYDESTROYED = "void onActivityDestroyed(android.app.Activity)";
  val APPLIFECYCLECALLBACK_ONACTIVITYCREATED = "void onActivityCreated(android.app.Activity,android.os.Bundle)";             

  val FRAGMENT_LIFECYCLE = List(FRAGMENT_ONINFLATE,
                                FRAGMENT_ONATTACH,
                                FRAGMENT_ONCREATE,
                                FRAGMENT_ONCREATEVIEW,
                                FRAGMENT_ONSTART,
                                FRAGMENT_ONRESUME,
                                FRAGMENT_ONPAUSE,
                                FRAGMENT_ONSTOP,
                                FRAGMENT_ONDETACH
                           )

  // mapping of framework-created lifecycle types to callbacks defined on those types
  val frameworkCbMap = Map(
    ACTIVITY_TYPE -> List(ACTIVITY_ONCREATE,
                          ACTIVITY_ONATTACHFRAGMENT,
                          ACTIVITY_ONCONTENTCHANGED,
                          ACTIVITY_ONSTART, 
                          ACTIVITY_ONRESTOREINSTANCESTATE,
                          ACTIVITY_ONPOSTCREATE,
                          ACTIVITY_ONRESUME,
                          ACTIVITY_ONPOSTRESUME,
                          ACTIVITY_ONATTACHEDTOWINDOW,
                          ACTIVITY_ONCREATEOPTIONSMENU,
                          ACTIVITY_ONCREATEDESCRIPTION,
                          ACTIVITY_ONSAVEINSTANCESTATE,
                          ACTIVITY_ONPAUSE,
                          ACTIVITY_ONSTOP,
                          ACTIVITY_ONRESTART,
                          ACTIVITY_ONDESTROY
                     ),
    SERVICE_TYPE -> List(SERVICE_ONCREATE,
                         SERVICE_ONSTARTCOMMAND,
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
    FRAGMENT_TYPE -> FRAGMENT_LIFECYCLE,
    APP_FRAGMENT_TYPE -> FRAGMENT_LIFECYCLE
  )

  // can't use a lazy val because we need the class hierarchy to construct
  private var frameworkCreatedClasses : Set[IClass] = null

  def getOrCreateFrameworkCreatedClasses(cha : IClassHierarchy) : Set[IClass] = {
    if (frameworkCreatedClasses == null) {
      frameworkCreatedClasses = frameworkCbMap.keys.foldLeft(Set.empty[IClass])((s, c) => {
        val clazz = cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Primordial, ClassUtil.walaifyClassName(c)))
        if (clazz == null) {
          println(s"Warning: couldn't find $c in class hierarchy")
          s
        } else s + clazz
      })
    }
    frameworkCreatedClasses
  }
  
  def isFrameworkCreatedType(c : IClass, cha : IClassHierarchy) : Boolean =
    getOrCreateFrameworkCreatedClasses(cha).contains(c)
  
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


  private def resolve(typeRef : TypeReference, selectorStr : String, cha : IClassHierarchy) : IMethod = {
    val m = cha.resolveMethod(MethodReference.findOrCreate(typeRef, Selector.make(selectorStr)))
    assert(m != null, s"Couldn't resolve method ${typeRef}.$selectorStr")
    m
  }

  // sources for Activity lifecycle info:
  // http://developer.android.com/training/basics/activity-lifecycle/starting.html
  // https://githuon.com/xxv/android-lifecycle
  def makeActivityLifecycleGraph(cha : IClassHierarchy) : GraphImpl[IMethod] = {
    val activityTypeName = TypeName.findOrCreate(ClassUtil.walaifyClassName(AndroidConstants.ACTIVITY_TYPE))
    val activityTypeRef = TypeReference.findOrCreate(ClassLoaderReference.Primordial, activityTypeName)
    val onCreate = resolve(activityTypeRef, ACTIVITY_ONCREATE, cha)
    val onAttachFragment = resolve(activityTypeRef, ACTIVITY_ONATTACHFRAGMENT, cha)
    val onContentChanged = resolve(activityTypeRef, ACTIVITY_ONCONTENTCHANGED, cha)
    val onStart = resolve(activityTypeRef, ACTIVITY_ONSTART, cha)
    val onPostCreate = resolve(activityTypeRef, ACTIVITY_ONPOSTCREATE, cha)
    val onResume = resolve(activityTypeRef, ACTIVITY_ONRESUME, cha)
    val onAttachedToWindow = resolve(activityTypeRef, ACTIVITY_ONATTACHEDTOWINDOW, cha)
    val onCreateOptionsMenu = resolve(activityTypeRef, ACTIVITY_ONCREATEOPTIONSMENU, cha)
    val onPrepareOptionsMenu = resolve(activityTypeRef, ACTIVITY_ONPREPAREOPTIONSMENU, cha)
    val onPause = resolve(activityTypeRef, ACTIVITY_ONPAUSE, cha)
    val onStop = resolve(activityTypeRef, ACTIVITY_ONSTOP, cha)
    val onRestart = resolve(activityTypeRef, ACTIVITY_ONRESTART, cha)
    val onDestroy = resolve(activityTypeRef, ACTIVITY_ONDESTROY, cha)
    val g = new GraphImpl[IMethod](root = Some(onCreate))
    g.addEdge(onCreate, onAttachFragment)
    g.addEdge(onAttachFragment, onContentChanged)
    g.addEdge(onContentChanged, onStart)
    g.addEdge(onStart, onPostCreate)
    g.addEdge(onPostCreate, onResume)
    g.addEdge(onResume, onAttachedToWindow)
    g.addEdge(onAttachedToWindow, onCreateOptionsMenu)
    g.addEdge(onCreateOptionsMenu, onPrepareOptionsMenu)
    g.addEdge(onPrepareOptionsMenu, onPause)
    g.addEdge(onPause, onStop)
    g.addEdge(onStop, onRestart)
    g.addEdge(onRestart, onStart)
    g.addEdge(onStop, onDestroy)
    g
  }

  // source: https://github.com/xxv/android-lifecycle
  def makeFragmentLifecycleGraph(fragmentTypeName : TypeName, cha : IClassHierarchy) : GraphImpl[IMethod] = {
    val fragmentTypeRef = TypeReference.findOrCreate(ClassLoaderReference.Primordial, fragmentTypeName)
    val onInflate = resolve(fragmentTypeRef, FRAGMENT_ONINFLATE, cha)
    val onAttach = resolve(fragmentTypeRef, FRAGMENT_ONATTACH, cha)
    val onCreateView = resolve(fragmentTypeRef, FRAGMENT_ONCREATEVIEW, cha)
    val onViewCreated = resolve(fragmentTypeRef, FRAGMENT_ONVIEWCREATED, cha)
    val onStart = resolve(fragmentTypeRef, FRAGMENT_ONSTART, cha)
    val onResume = resolve(fragmentTypeRef, FRAGMENT_ONRESUME, cha)
    val onPause = resolve(fragmentTypeRef, FRAGMENT_ONPAUSE, cha)
    val onStop = resolve(fragmentTypeRef, FRAGMENT_ONSTOP, cha)
    val onDestroyView = resolve(fragmentTypeRef, FRAGMENT_ONDESTROYVIEW, cha)
    val onDetach = resolve(fragmentTypeRef, FRAGMENT_ONDETACH, cha)
    val g = new GraphImpl[IMethod](root = Some(onAttach))
    g.addEdge(onInflate, onAttach)
    g.addEdge(onAttach, onCreateView)
    g.addEdge(onCreateView, onViewCreated)
    g.addEdge(onViewCreated, onStart)
    g.addEdge(onStart, onResume)
    g.addEdge(onResume, onPause)
    g.addEdge(onPause, onStop)
    g.addEdge(onPause, onResume)
    g.addEdge(onStop, onStart)
    g.addEdge(onStop, onCreateView)
    g.addEdge(onStop, onDestroyView)
    g.addEdge(onDestroyView, onCreateView)
    g.addEdge(onDestroyView, onDetach)
    g
  }

  // source: http://developer.android.com/guide/components/services.html#Lifecycle
  def makeServiceLifecycleGraph(cha : IClassHierarchy) : GraphImpl[IMethod] = {
    val serviceTypeName = TypeName.findOrCreate(ClassUtil.walaifyClassName(AndroidConstants.SERVICE_TYPE))
    val serviceTypeRef = TypeReference.findOrCreate(ClassLoaderReference.Primordial, serviceTypeName)
    val onCreate = resolve(serviceTypeRef, SERVICE_ONCREATE, cha)
    val onStartCommand = resolve(serviceTypeRef, SERVICE_ONSTARTCOMMAND, cha)
    val onBind = resolve(serviceTypeRef, SERVICE_ONBIND, cha)
    val onUnbind = resolve(serviceTypeRef, SERVICE_ONUNBIND, cha)
    val onDestroy = resolve(serviceTypeRef, SERVICE_ONDESTROY, cha)
    val g = new GraphImpl[IMethod](root = Some(onCreate))
    g.addEdge(onCreate, onStartCommand)
    g.addEdge(onCreate, onBind)
    g.addEdge(onBind, onUnbind)
    g.addEdge(onUnbind, onDestroy)
    g.addEdge(onStartCommand, onDestroy)
    g
  }

  // source: http://developer.android.com/reference/android/app/Application.html
  def makeApplicationLifecycleGraph(cha : IClassHierarchy) : GraphImpl[IMethod] = {
    val applicationTypeName = TypeName.findOrCreate(ClassUtil.walaifyClassName(AndroidConstants.APPLICATION_TYPE))
    val applicationTypeRef = TypeReference.findOrCreate(ClassLoaderReference.Primordial, applicationTypeName)
    val onCreate = resolve(applicationTypeRef, APPLICATION_ONCREATE, cha)
    val onTerminate = resolve(applicationTypeRef, APPLICATION_ONTERMINATE, cha)
    val g = new GraphImpl[IMethod](root = Some(onCreate))
    g.addEdge(onCreate, onTerminate)
    g
  }

}
