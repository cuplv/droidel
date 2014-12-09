package com.ibm.mobile.droidertemplate

import com.squareup.javawriter.JavaWriter

object WriterFactory {
  object Writers extends Enumeration {
    val Flat, LoopIf = Value;
  }
  import com.ibm.mobile.droidertemplate.WriterFactory.Writers._
  
  val writer = LoopIf;

  def factory(strwriter : JavaWriter) : AndroidHarnessWriter = writer match {
    case Flat => return new AndroidHarnessWriter(strwriter);
    case LoopIf => return new AndroidHarnessWithLoopIfWriter(strwriter);
  }
  
}