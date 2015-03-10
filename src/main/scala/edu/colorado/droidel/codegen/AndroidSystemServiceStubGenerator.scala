package edu.colorado.droidel.codegen

import java.io.File
import java.util.EnumSet
import javax.lang.model.element.Modifier.{FINAL, PRIVATE, PUBLIC, STATIC}

import com.ibm.wala.ipa.cha.IClassHierarchy
import com.ibm.wala.shrikeBT.MethodEditor.{Output, Patch}
import com.ibm.wala.shrikeBT.{IInvokeInstruction, InvokeInstruction, PopInstruction, SwapInstruction}
import com.ibm.wala.ssa.{IR, SSAInvokeInstruction}
import com.ibm.wala.types.{ClassLoaderReference, MethodReference, TypeReference}
import edu.colorado.droidel.constants.AndroidConstants
import edu.colorado.droidel.constants.DroidelConstants._
import edu.colorado.walautil.ClassUtil

import scala.collection.JavaConversions._

object AndroidSystemServiceStubGenerator {
  val DEBUG = false
}

class AndroidSystemServiceStubGenerator(cha : IClassHierarchy, androidJarPath : String, appBinPath : String)
  extends AndroidStubGeneratorWithInstrumentation {
  
  val SYSTEM_SERVICES_MAP = Map(
    "accessibility" -> "android.view.accessibility.AccessibilityManager",
    "account" -> "android.accounts.AccountManager",
    "activity" -> "android.app.ActivityManager",
    "alarm" -> "android.app.AlarmManager",
    //"appops" -> "android.app.AppOpsManager", // added in Android 19 -- tends to cause compilation issues because it's so new
    "audio" -> "android.media.AudioManager",
    "connection" -> "android.net.ConnectivityManager",
    "download" -> "android.app.DownloadManager",
    "input_method" -> "android.view.inputmethod.InputMethodManager",
    "keyguard" -> "android.app.KeyguardManager",    
    "layout_inflater" -> "android.view.LayoutInflater",
    "location" -> "android.location.LocationManager",
    "notification" -> "android.app.NotificationManager",
    "power" -> "android.os.PowerManager",
    "search" -> "android.app.SearchManager",
    "uimode" -> "android.app.UiModeManager", 
    "vibrator" -> "android.os.Vibrator",
    "wifi" -> "android.net.wifi.WifiManager",
    "window" -> "android.view.WindowManager"
  )  

  override def generateStubs(stubMap : StubMap, generatedStubs : List[File]) : (StubMap, List[File]) = {
    val GET_SYSTEM_SERVICE = "getSystemService"
      
    val stubDir = new File(STUB_DIR)
    if (!stubDir.exists()) stubDir.mkdir()

    val (inhabitantMap, allocs) = SYSTEM_SERVICES_MAP.foldLeft (Map.empty[String,Expression], List.empty[Statement]) ((pair, entry) => {
      val typ = TypeReference.findOrCreate(ClassLoaderReference.Primordial, ClassUtil.walaifyClassName(entry._2))
      try {
        val (inhabitant, allocs) = inhabitor.inhabit(typ, cha, pair._2, doAllocAndReturnVar = false)
        (pair._1 + (entry._1 -> inhabitant), allocs)
      } catch { 
        // we do this in order to be robust in the face of system services that are added in later versions of Android
        // if we use an earlier JAR, lookups of these classes in the class hierarchy will fail and cause an exception
        // catching the exception allows us to pick up the pieces and move on
        case e : Throwable => pair
      }
    })
     
    writer.emitPackage(STUB_DIR) 

    val allServices = SYSTEM_SERVICES_MAP.values
    allServices.foreach(typ => writer.emitImports(typ))      
    writer.emitEmptyLine()
    
    writer.beginType(SYSTEM_SERVICE_STUB_CLASS, "class", EnumSet.of(PUBLIC, FINAL)) // begin class
    SYSTEM_SERVICES_MAP.foreach(entry => writer.emitField(entry._2, entry._1, EnumSet.of(PRIVATE, STATIC)))
    writer.emitEmptyLine()
    
    writer.beginInitializer(true) // begin static
    writer.beginControlFlow("try") // begin try    
    // emit initialization of static fields
    // first, emit allocs that we built up in inhabiting the values
    allocs.reverse.foreach(alloc => writer.emitStatement(alloc))
    // next emit the initialization of each field
    inhabitantMap.foreach(entry => writer.emitStatement(s"${entry._1} = ${entry._2}"))
    
    writer.endControlFlow() // end try
    writer.beginControlFlow("catch (Exception e)") // begin catch   
    writer.endControlFlow() // end catch
    writer.endInitializer() // end static          
    writer.emitEmptyLine()
    
    // emit stub for Context.getSystemService(String)
    val paramName = "name"
    writer.beginMethod("Object", GET_SYSTEM_SERVICE, EnumSet.of(PUBLIC, STATIC), "String", paramName)
    /*writer.beginControlFlow(s"switch ($paramName)") // begin switch
    inhabitantMap.keys.foreach(key => writer.emitStatement("case \"" + key + "\": return " + key))
    writer.emitStatement("default: return null")
    writer.endControlFlow() // end switch*/
    
    //To avoid switch statements on strings not compatible with Java 6
    inhabitantMap.keys.foreach(key => writer.emitStatement("if("+paramName+".equals(\"" + key + "\")) return " + key))
    writer.emitStatement("return null")
    
    writer.endMethod()
    writer.endType() // end class

    val stubPath = s"$STUB_DIR${File.separator}$SYSTEM_SERVICE_STUB_CLASS"
    val compiledStub = writeAndCompileStub(stubPath, List("-cp", s"${androidJarPath}${File.pathSeparator}$appBinPath"))
    
    val getSystemServiceDescriptor = "(Ljava/lang/String;)Ljava/lang/Object;"
    val contextTypeRef = ClassUtil.makeTypeRef(AndroidConstants.CONTEXT_TYPE)
    val getSystemServiceRef = MethodReference.findOrCreate(contextTypeRef, GET_SYSTEM_SERVICE, getSystemServiceDescriptor)
    val getSystemService = cha.resolveMethod(getSystemServiceRef)
    assert(getSystemService != null, "Couldn't find getSystemService() method")
    
    // get all the library methods that a call to getSystemService() might dispatch to (ignoring covariance for simplicity's sake)
    val possibleOverrides = cha.computeSubClasses(contextTypeRef).foldLeft (List(getSystemService)) ((l, c) =>
      if (!ClassUtil.isLibrary(c)) l
      else c.getDeclaredMethods().foldLeft (l) ((l, m) =>
        if (m.getName().toString() == GET_SYSTEM_SERVICE && m.getDescriptor().toString() == getSystemServiceDescriptor) m :: l
        else l
      )
    )     
    
    val stubTypeRef = TypeReference.findOrCreate(ClassLoaderReference.Application, s"L$STUB_DIR/$SYSTEM_SERVICE_STUB_CLASS")
    val shrikePatch = new Patch() {
      override def emitTo(o : Output) : Unit = {
        if (DEBUG) println("Instrumenting call to system service stub")
        // the stack is [String argument to getSystemService, receiver of getSystemService]. we want to get rid of the receiver,
        // but keep the string argument. we do this by performing a swap, then a pop to get rid of the receiver
        o.emit(SwapInstruction.make()) // swap the String argument and receiver on the stack     
        o.emit(PopInstruction.make(1)) // pop the receiver of getSystemService off the stack
        val methodClass = ClassUtil.typeRefToBytecodeType(stubTypeRef)
        o.emit(InvokeInstruction.make(getSystemServiceDescriptor, 
               methodClass,
               GET_SYSTEM_SERVICE, 
               IInvokeInstruction.Dispatch.STATIC)
        )
      }
    }
    
    def tryCreatePatch(i : SSAInvokeInstruction, ir : IR) : Option[Patch] = Some(shrikePatch)
    
    (possibleOverrides.foldLeft (stubMap) ((map, method) => map + (method -> tryCreatePatch)),
     compiledStub :: generatedStubs)
  }   
  
}