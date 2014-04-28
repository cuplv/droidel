package edu.colorado.droidel.driver

import com.ibm.wala.ipa.callgraph.CallGraph
import com.ibm.wala.ipa.callgraph.propagation.HeapModel
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis
import com.ibm.wala.analysis.pointers.HeapGraph
import com.ibm.wala.ipa.cha.IClassHierarchy


class WalaAnalysisResults(val cg : CallGraph, val pa : PointerAnalysis) {
  val cha : IClassHierarchy = cg.getClassHierarchy()
  val hg : HeapGraph = pa.getHeapGraph()
  val hm : HeapModel = pa.getHeapModel()
}
