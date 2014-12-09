package edu.colorado.droidel.codegen

import java.io.{File, FileWriter, StringWriter}

import com.squareup.javawriter.JavaWriter
import edu.colorado.walautil.JavaUtil

trait AndroidStubGenerator {
  // type aliases to make some type signatures more clear
  type VarName = String
  type Expression = String
  type Statement = String
  val DEBUG = false

  val inhabitor = new TypeInhabitor
  val strWriter = new StringWriter
  val writer = new JavaWriter(strWriter)

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
