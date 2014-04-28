package edu.colorado.droidel.util

import scala.collection.JavaConversions._
import com.ibm.wala.types.FieldReference
import com.ibm.wala.ssa.SSAPutInstruction
import com.ibm.wala.ssa.IR
import com.ibm.wala.ipa.callgraph.CGNode
import com.ibm.wala.ssa.SSAArrayStoreInstruction
import com.ibm.wala.ssa.SSAInstruction
import com.ibm.wala.ssa.SymbolTable
import com.ibm.wala.classLoader.IField
import com.ibm.wala.ipa.callgraph.propagation.StaticFieldKey
import com.ibm.wala.classLoader.JavaLanguage
import com.ibm.wala.types.TypeReference
import com.ibm.wala.classLoader.IMethod
import com.ibm.wala.ipa.callgraph.AnalysisCache
import com.ibm.wala.ipa.callgraph.impl.Everywhere
import com.ibm.wala.ssa.SSAOptions
import com.ibm.wala.ipa.cha.IClassHierarchy
import com.ibm.wala.types.MethodReference
import com.ibm.wala.classLoader.IBytecodeMethod

object IRUtil {
  def makeIR(m : MethodReference, cha : IClassHierarchy) : IR = makeIR(cha.resolveMethod(m), None)
  
  // utility for creating IR for a method without constructing a call graph
  def makeIR(m : IMethod, analysisCache : Option[AnalysisCache] = None) : IR = {
    val cache = analysisCache match {
      case Some(analysisCache) => analysisCache
      case None => new AnalysisCache
    }
    cache.getSSACache().findOrCreateIR(m, Everywhere.EVERYWHERE, SSAOptions.defaultOptions()) 
  } 

  def getSourceLine(i : SSAInstruction, ir : IR) : Int = ir.getMethod() match {
    case m : IBytecodeMethod => 
      val bcIndex = ir.getInstructions().indexOf(i)        
      m.getLineNumber(bcIndex)
    case _ => -1
  }
}  
