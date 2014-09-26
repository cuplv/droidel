package com.ibm.mobile.droidertemplate

import java.io.StringWriter
import java.util.EnumSet
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

class AndroidHarnessWithLoopIfWriter(val w : StringWriter) extends AndroidHarnessWriter(w) {
  
  override def emitBegin() {
    super.emitBegin();
    writer.emitEmptyLine();
    writer.beginMethod("boolean", "nondet", EnumSet.of(PRIVATE, STATIC)) //Method to generate non-deterministic choices
    writer.emitStatement("return new Random().nextBoolean()")
    writer.endMethod();
  }
  
  
  override def beginCallToComponent() : Unit = {
    writer.beginControlFlow("while(nondet())");//begin while loop;
  }
  
  override protected def emitImports() {
    super.emitImports();
    writer.emitImports("java.util.Random");
  }
  

  override def emitEndHarness() {
    writer.endControlFlow() // end while loop
    super.emitEndHarness()
  }
  
  override def emitCallToComponent(call : String) =  {
    writer.beginControlFlow("if(nondet())");
    super.emitCallToComponent(call);
    writer.endControlFlow();
  }
  
}