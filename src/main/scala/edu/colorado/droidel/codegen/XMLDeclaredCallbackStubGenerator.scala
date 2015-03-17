package edu.colorado.droidel.codegen

import java.io.File
import java.util.EnumSet
import javax.lang.model.element.Modifier.{FINAL, PUBLIC, STATIC}

import com.ibm.wala.classLoader.{IClass, IMethod}
import edu.colorado.droidel.constants.AndroidConstants._
import edu.colorado.droidel.constants.DroidelConstants._
import edu.colorado.walautil.ClassUtil

class XMLDeclaredCallbackStubGenerator extends AndroidStubGenerator {

  def generateStubs(manifestDeclaredCallbackMap : Map[IClass,Set[IMethod]], stubClassName : String,
                    stubMethodName : String, androidJarPath : String, appBinPath : String) : File = {
    writer.emitPackage(STUB_DIR)
    writer.beginType(stubClassName, "class", EnumSet.of(PUBLIC, FINAL)) // begin class
    val context = "context"
    val view = "view"
    writer.beginMethod("void", stubMethodName, EnumSet.of(PUBLIC, STATIC, FINAL), CONTEXT_TYPE, context, VIEW_TYPE, view)
    var firstPass = true
    manifestDeclaredCallbackMap.foreach(pair => {
      val (clazz, cbs) = pair
      val classString = ClassUtil.deWalaifyClassName(clazz)
      val classVarName = ClassUtil.partitionPackageAndName(classString)._2.toLowerCase
      val cond =
        if (firstPass) { firstPass = false; "if" }
        else "else if"
      writer.beginControlFlow(s"$cond ($context instanceof $classString)")
      writer.emitStatement(s"$classString $classVarName = ($classString) $context")
      // TODO: using "nondetBool" is sound, but not precise--the framework actually tests the View id
      val callPrefix = if (cbs.size > 1) "if (droidelhelpers.Nondet.nondetBool())" else ""
      cbs.foreach(cb => writer.emitStatement(s"$callPrefix $classVarName.${cb.getName.toString}($view)"))
      writer.endControlFlow()
    })

    writer.endMethod()
    writer.endType()

    val stubPath = s"$STUB_DIR${File.separator}$stubClassName"
    val compilerOptions = List("-cp", s"${androidJarPath}${File.pathSeparator}$appBinPath")
    writeAndCompileStub(stubPath, compilerOptions)
  }
}
