package edu.colorado.droidel.codegen

import java.io.File
import java.util.EnumSet
import javax.lang.model.element.Modifier.{FINAL, PUBLIC, STATIC}

import edu.colorado.droidel.constants.DroidelConstants._
import edu.colorado.droidel.constants.{AndroidConstants, DroidelConstants}

/** generate a harness that calls the Android main (ActivityThread.main) and injects our stubs */
class SimpleAndroidHarnessGenerator extends AndroidStubGenerator {

  def generateHarness(instrumentedBinDir : String, androidJarPath : String) : Unit = {
    writer.emitPackage(DroidelConstants.HARNESS_DIR)

    val STUBS_CLASS = "AllStubs"
    val STUBS_DELEGATE = s"$PREWRITTEN_STUB_DIR.DroidelStubs"
    writer.beginType(HARNESS_CLASS, "class", EnumSet.of(PUBLIC, FINAL)) // begin harness class

    writer.emitEmptyLine()
    writer.beginType(STUBS_CLASS, "class", EnumSet.of(STATIC, FINAL), null, STUBS_DELEGATE) // begin stubs class
    TYPE_STUBS_MAP.foreach(pair => {
      val (typ, (stubClass, stubName, _)) = pair
      val argName = "className"
      writer.emitAnnotation("Override")
      writer.beginMethod(typ, stubName, EnumSet.of(PUBLIC), "String", argName)
      writer.emitStatement(s"return $STUB_DIR.$stubClass.$stubName($argName)")
      writer.endMethod()
    })
    val findViewById = "findViewById"
    val id = "id"
    writer.emitAnnotation("Override")
    writer.beginMethod(AndroidConstants.VIEW_TYPE, findViewById, EnumSet.of(PUBLIC), "int", id)
    writer.emitStatement(s"return $STUB_DIR.$LAYOUT_STUB_CLASS.$findViewById($id)")
    writer.endMethod()
    writer.endType() // end stubs class

    writer.emitEmptyLine()
    writer.beginMethod("void", DroidelConstants.HARNESS_MAIN, EnumSet.of(PUBLIC, STATIC)) // begin harness method
    writer.emitStatement(s"android.app.ActivityThread.main(new $STUBS_CLASS())")
    writer.endMethod() // end harness method
    writer.endType() // end harness class

    val harnessDir = new File(s"${instrumentedBinDir}/${DroidelConstants.HARNESS_DIR}")
    if (!harnessDir.exists()) harnessDir.mkdir()
    val harnessPath = s"${harnessDir.getAbsolutePath()}/${DroidelConstants.HARNESS_CLASS}"

    val compilerOptions =
      List("-cp", Seq(".", androidJarPath, instrumentedBinDir).mkString(File.pathSeparator),
           "-d", instrumentedBinDir)
    writeAndCompileStub(harnessPath, compilerOptions)
  }

}
