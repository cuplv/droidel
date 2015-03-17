package edu.colorado.droidel.codegen

import java.io.{File, FileWriter, StringWriter}

import com.ibm.wala.classLoader.IClass
import com.ibm.wala.ipa.cha.IClassHierarchy
import com.squareup.javawriter.JavaWriter
import edu.colorado.walautil.{ClassUtil, JavaUtil}
import scala.collection.JavaConversions._

trait AndroidStubGenerator {
  // type aliases to make some type signatures more clear
  type VarName = String
  type Expression = String
  type Statement = String
  val DEBUG = false

  val inhabitor = new TypeInhabitor
  val strWriter = new StringWriter
  val writer = new JavaWriter(strWriter)

  /** emit a if (className == C) return new C() ... else @param defaultRet case split for each C in @param classes */
  def emitCaseSplitOnClassNameAlloc(classes : Iterable[IClass], defaultRet : Expression, writer : JavaWriter,
                                    cha : IClassHierarchy) : Unit = {
    def hasDefaultConstructor(c : IClass) : Boolean =
      c.getDeclaredMethods.exists(m => m.isInit && m.getNumberOfParameters == 1)

    var firstPass = true
    classes.foreach(c =>
      if (hasDefaultConstructor(c)) {
        inhabitor.inhabitantCache.clear()
        val cond =
          if (firstPass) {
            firstPass = false; "if"
          }
          else "else if"
        writer.beginControlFlow(s"$cond (className == " + '"' + ClassUtil.deWalaifyClassName(c) + '"' + ")")
        val (ret, allocs) = inhabitor.inhabit(c.getReference, cha, List.empty[Statement], doAllocAndReturnVar = false)
        allocs.reverse.foreach(alloc => writer.emitStatement(alloc))
        writer.emitStatement(s"return $ret")
        writer.endControlFlow()
      } else
        println(s"Warning: not allocating $c in stubs since it does not have a default constructor")
    )
    if (firstPass) writer.emitStatement(s"return $defaultRet")
    else writer.emitStatement(s"else return $defaultRet")
  }

  def writeAndCompileStub(stubPath : String, compilerOptions : Iterable[String]) : File = {
    // write out stub to file
    val fileWriter = new FileWriter(s"${stubPath}.java")
    if (DEBUG) println(s"Generated stub: ${strWriter.toString()}")
    fileWriter.write(strWriter.toString())
    // cleanup
    strWriter.close()
    writer.close()
    fileWriter.close()
    val compiled = JavaUtil.compile(Iterable(stubPath), compilerOptions)
    assert(compiled, s"Couldn't compile stub file $stubPath")
    new File(stubPath)
  }
}
