package com.ibm.mobile.droidertemplate

import com.squareup.javawriter.JavaWriter
import edu.colorado.droidel.constants.DroidelConstants
import java.util.EnumSet
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC
import javax.lang.model.element.Modifier
import java.io.StringWriter
import java.io.FileWriter
import java.io.File

class AndroidHarnessWriter(val strWriter : StringWriter) {
  
  val writer : JavaWriter = new JavaWriter(strWriter);

  def emitBegin() : Unit = { 
    writer.emitPackage(DroidelConstants.HARNESS_DIR)
    this.emitImports();
      
    writer.emitEmptyLine()
    writer.beginType(DroidelConstants.HARNESS_CLASS, "class", EnumSet.of(PUBLIC, FINAL)) // begin class
    writer.emitEmptyLine()
  }
  
  protected def emitImports() {
    writer.emitImports(s"${DroidelConstants.STUB_DIR}.*")
  }
  
  
  def emitField(className : String, fieldName : String, modifiers : java.util.Set[Modifier]) {
    writer.emitField(className, fieldName, modifiers)
  }
  
  def emitBeginHarness() {
    writer.emitEmptyLine()                                                          
    writer.beginMethod("void", DroidelConstants.HARNESS_MAIN, EnumSet.of(PUBLIC, STATIC)) // begin main harness method    
    // wrap everything in a try/catch block so the Java compiler doesn't complain
    writer.beginControlFlow("try") // begin try    
  }
  def beginAllocationComponent() : Unit = Unit;
  
  def emitAllocationComponent(alloc : String) =  writer.emitStatement(alloc);

  def endAllocationComponent() : Unit = Unit;
  
  def beginCallToComponent() : Unit = Unit;
  
  def emitCallToComponent(call : String) =  writer.emitStatement(call);

  def endCallToComponent() : Unit = Unit;
  
  def emitEndHarness() {
    writer.endControlFlow() // end try
    writer.beginControlFlow("catch (Exception e)") // begin catch
    writer.endControlFlow() // end catch    
    writer.endMethod() // end main harness method
  }
  
  def emitEnd() {
    writer.endType() // end class
  }
  
  def write(file : String, DEBUG : Boolean) {
    val fileWriter = new FileWriter(s"${file}.java")
    if (DEBUG) println("HARNESS: " + strWriter.toString())
    fileWriter.write(strWriter.toString())    
    // cleanup
    strWriter.close()
    writer.close()    
    fileWriter.close()
  }
  
}