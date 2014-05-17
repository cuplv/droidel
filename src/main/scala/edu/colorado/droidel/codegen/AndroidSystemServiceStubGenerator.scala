package edu.colorado.droidel.codegen

import java.io.StringWriter
import com.squareup.javawriter.JavaWriter
import java.io.File
import edu.colorado.droidel.constants.DroidelConstants._
import java.util.EnumSet
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC
import edu.colorado.droidel.util.ClassUtil
import com.ibm.wala.types.TypeReference
import com.ibm.wala.types.ClassLoaderReference
import com.ibm.wala.ipa.cha.IClassHierarchy
import java.io.FileWriter
import AndroidSystemServiceStubGenerator._
import edu.colorado.droidel.util.JavaUtil

object AndroidSystemServiceStubGenerator {
  val DEBUG = false
}

class AndroidSystemServiceStubGenerator(cha : IClassHierarchy, androidJarPath : String) {  
  
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
  
  type Expression = String
  type Statement = String
  
  def generateStubs() : Unit = {
    val stubClassName = "GeneratedSystemServiceStubs"
      
    val stubDir = new File(STUB_DIR)
    if (!stubDir.exists()) stubDir.mkdir()
    
    val inhabitor = new TypeInhabitor  
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
        

    val strWriter = new StringWriter
    val writer = new JavaWriter(strWriter)
     
    writer.emitPackage(STUB_DIR) 

    val allServices = SYSTEM_SERVICES_MAP.values
    allServices.foreach(typ => writer.emitImports(typ))      
    writer.emitEmptyLine()
    
    writer.beginType(stubClassName, "class", EnumSet.of(PUBLIC, FINAL)) // begin class
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
    writer.beginMethod("Object", "getSystemService", EnumSet.of(PUBLIC, STATIC), "String", paramName)
    writer.beginControlFlow(s"switch ($paramName)") // begin switch
    inhabitantMap.keys.foreach(key => writer.emitStatement("case \"" + key + "\": return " + key))
    writer.emitStatement("default: return null")
    writer.endControlFlow() // end switch
    writer.endMethod()
    writer.endType() // end class
    
    // write out stub to file
    val stubPath = s"$STUB_DIR${File.separator}$stubClassName"
    val fileWriter = new FileWriter(s"${stubPath}.java")
    println(s"Generated stub: ${strWriter.toString()}")
    fileWriter.write(strWriter.toString())    
    // cleanup
    strWriter.close()
    writer.close()    
    fileWriter.close()

    
     // compile stub against Android library 
    val compilerOptions = List("-cp", s"${androidJarPath}")
    if (DEBUG) println(s"Running javac ${compilerOptions(0)} ${compilerOptions(1)}")
    val compiled = JavaUtil.compile(List(stubPath), compilerOptions)
    assert(compiled, s"Couldn't compile stub file $stubPath")   
  }
  
}