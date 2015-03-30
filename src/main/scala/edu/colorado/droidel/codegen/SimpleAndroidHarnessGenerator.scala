package edu.colorado.droidel.codegen

import java.io.File
import java.util.EnumSet
import javax.lang.model.element.Modifier.{FINAL, PUBLIC, STATIC}

import edu.colorado.droidel.constants.AndroidConstants._
import edu.colorado.droidel.constants.DroidelConstants._

/** generate a harness that calls the Android main (ActivityThread.main) and injects our stubs */
class SimpleAndroidHarnessGenerator extends AndroidStubGenerator {

  def generateHarness(instrumentedBinDir : String, androidJarPath : String, generateFragmentStubs : Boolean) : Unit = {
    writer.emitPackage(HARNESS_DIR)

    val STUBS_CLASS = "AppStubs"
    val STUBS_DELEGATE = s"$PREWRITTEN_STUB_DIR.DroidelStubs"
    writer.beginType(HARNESS_CLASS, "class", EnumSet.of(PUBLIC, FINAL)) // begin harness class

    writer.emitEmptyLine()
    writer.beginType(STUBS_CLASS, "class", EnumSet.of(STATIC, FINAL), null, STUBS_DELEGATE) // begin stubs class
    val OVERRIDE = "Override"
    // emit override methods for framework-created types methods
    TYPE_STUBS_MAP.foreach(pair => {
      val (typ, (stubClass, stubName, _)) = pair
      val argName = "className"
      writer.emitAnnotation(OVERRIDE)
      writer.beginMethod(typ, stubName, EnumSet.of(PUBLIC), "String", argName)
      writer.emitStatement(s"return $STUB_DIR.$stubClass.$stubName($argName)")
      writer.endMethod()
    })

    // emit override methods for layout stubs
    def emitInflateLayoutComponentById(typ : String, methodName : String) : Unit = {
      val id = "id"
      val ctx = "ctx"
      writer.emitAnnotation(OVERRIDE)
      writer.beginMethod(typ, methodName, EnumSet.of(PUBLIC), "int", id, CONTEXT_TYPE, ctx)
      writer.emitStatement(s"return $STUB_DIR.$LAYOUT_STUB_CLASS.$methodName($id, $ctx)")
      writer.endMethod()
    }

    def emitGetFragment(typ : String, methodName : String) = {
      val argName = "className"
      writer.emitAnnotation(OVERRIDE)
      writer.beginMethod(typ, methodName, EnumSet.of(PUBLIC), "String", argName)
      writer.emitStatement(s"return $STUB_DIR.$LAYOUT_STUB_CLASS.$methodName($argName)")
      writer.endMethod()
    }

    emitInflateLayoutComponentById(VIEW_TYPE, INFLATE_VIEW_BY_ID)
    emitGetFragment(FRAGMENT_TYPE, GET_SUPPORT_FRAGMENT)
    emitGetFragment(APP_FRAGMENT_TYPE, GET_APP_FRAGMENT)

    // emit override method for manifest-declared callbacks
    writer.emitAnnotation(OVERRIDE)
    val context = "context"
    val view = "view"
    writer.beginMethod("void", XML_DECLARED_CALLBACKS_STUB_METHOD, EnumSet.of(PUBLIC), CONTEXT_TYPE, context,
                       VIEW_TYPE, view)
    val stubClass = s"$STUB_DIR.$XML_DECLARED_CALLBACKS_STUB_CLASS"
    writer.emitStatement(s"$stubClass.$XML_DECLARED_CALLBACKS_STUB_METHOD($context, $view)")
    writer.endMethod()
    writer.endType() // end stubs class


    writer.emitEmptyLine()
    writer.beginMethod("void", HARNESS_MAIN, EnumSet.of(PUBLIC, STATIC)) // begin harness method
    //writer.emitStatement(s"com.android.server.SystemServer.main(null)")
    writer.emitStatement(s"android.app.ActivityThread.main(new $STUBS_CLASS())")
    writer.endMethod() // end harness method
    writer.endType() // end harness class

    val harnessDir = new File(s"${instrumentedBinDir}/$HARNESS_DIR")
    if (!harnessDir.exists()) harnessDir.mkdir()
    val harnessPath = s"${harnessDir.getAbsolutePath()}/$HARNESS_CLASS"

    val compilerOptions =
      List("-cp", Seq(".", androidJarPath, instrumentedBinDir).mkString(File.pathSeparator),
           "-d", instrumentedBinDir)
    writeAndCompileStub(harnessPath, compilerOptions)
  }

}
