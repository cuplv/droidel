package edu.colorado.droidel.codegen

import java.io.File
import java.util.EnumSet
import javax.lang.model.element.Modifier.{FINAL, PUBLIC, STATIC}

import com.ibm.wala.classLoader.IClass
import com.ibm.wala.ipa.cha.IClassHierarchy
import edu.colorado.droidel.constants.DroidelConstants._
import edu.colorado.walautil.ClassUtil

class AndroidFrameworkCreatedTypesStubGenerator extends AndroidStubGenerator {

  /** @param classes - list of types to inhabit
    * @param defaultRet - type to return if the input string to the stub does not match any of @param classes */
  def generateStubs(classes: Iterable[IClass], stubClassName : String, stubMethodName : String,
                    stubMethodRetType : String, defaultRet : Expression, cha : IClassHierarchy, androidJarPath : String,
                    appBinPath : String) : File = {
    println(s"Generating $stubMethodRetType stubs")
    writer.emitPackage(STUB_DIR)
    writer.beginType(stubClassName, "class", EnumSet.of(PUBLIC, FINAL)) // begin class
    writer.beginMethod(stubMethodRetType, stubMethodName, EnumSet.of(PUBLIC, STATIC, FINAL), "String", "className")

    var firstPass = true
    classes.foreach(c => {
      inhabitor.inhabitantCache.clear()
      val cond =
        if (firstPass) { firstPass = false; "if" }
        else "else if"
      writer.beginControlFlow(s"$cond (className == " + '"' + ClassUtil.deWalaifyClassName(c) + '"' + ")")
      val (ret, allocs) = inhabitor.inhabit(c.getReference, cha, List.empty[Statement], doAllocAndReturnVar = false)
      allocs.foreach(alloc => writer.emitStatement(alloc))
      writer.emitStatement(s"return $ret")
      writer.endControlFlow()
    })
    if (firstPass) writer.emitStatement(s"return $defaultRet")
    else writer.emitStatement(s"else return $defaultRet")
    writer.endMethod()
    writer.endType()

    val stubPath = s"$STUB_DIR${File.separator}$stubClassName"
    val compilerOptions = Seq("-cp", s"${androidJarPath}${File.pathSeparator}$appBinPath")
    writeAndCompileStub(stubPath, compilerOptions)
  }
}
