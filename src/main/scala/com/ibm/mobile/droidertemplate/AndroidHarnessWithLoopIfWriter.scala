package com.ibm.mobile.droidertemplate

import java.util.EnumSet
import javax.lang.model.element.Modifier.{PRIVATE, STATIC}

import com.squareup.javawriter.JavaWriter

class AndroidHarnessWithLoopIfWriter(w : JavaWriter) extends AndroidHarnessWriter(w) {
  
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