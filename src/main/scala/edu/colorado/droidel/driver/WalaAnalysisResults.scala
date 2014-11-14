package edu.colorado.droidel.driver

import com.ibm.wala.ipa.callgraph.CallGraph
import com.ibm.wala.ipa.callgraph.propagation.{InstanceKey, PointerAnalysis}


class WalaAnalysisResults(val cg : CallGraph, val pa : PointerAnalysis[InstanceKey]) {
  val cha = cg.getClassHierarchy()
  val hg = pa.getHeapGraph()
  val hm = pa.getHeapModel()
}
