package com.ibm.mobile.droidertemplate

import java.io.StringWriter

object WriterFactory {
  object Writers extends Enumeration {
    val Flat, LoopIf = Value;
  }
  import Writers._
  
  val writer = LoopIf;

  def factory(strwriter : StringWriter) : AndroidHarnessWriter = writer match {
    case Flat => return new AndroidHarnessWriter(strwriter);
    case LoopIf => return new AndroidHarnessWithLoopIfWriter(strwriter);
  }
  
}