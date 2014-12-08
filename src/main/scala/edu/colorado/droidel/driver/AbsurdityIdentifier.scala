package edu.colorado.droidel.driver

import java.io.{File, PrintWriter}

import com.ibm.wala.analysis.pointers.HeapGraph
import com.ibm.wala.classLoader.{IClass, IBytecodeMethod, IMethod}
import com.ibm.wala.ipa.callgraph.propagation._
import com.ibm.wala.ipa.callgraph.{CGNode, CallGraph}
import com.ibm.wala.ipa.cha.IClassHierarchy
import com.ibm.wala.ssa.{IR, SSAConditionalBranchInstruction, SSAGetInstruction, SSAInstruction, SSAInvokeInstruction}
import com.ibm.wala.types.MethodReference
import edu.colorado.droidel.constants.DroidelConstants
import edu.colorado.walautil.{ClassUtil, IRUtil, Util, WalaAnalysisResults}

import scala.collection.JavaConversions._

/** class for identifying "absurdities", or likely soundness issues in a callgraph/points-to analysis */
class AbsurdityIdentifier(harnessClassName : String) {
  
  def isGeneratedMethod(m : IMethod) : Boolean = { 
    val className = m.getDeclaringClass().getName().toString()
    /*className.startsWith(s"L${DroidelConstants.STUB_DIR}") ||
    className.startsWith(s"L${DroidelConstants.HARNESS_DIR}")*/ 
    className == harnessClassName
  } 

  // TODO: factor these out to a PtUtil class in WalaUtil project
  def makeLPK(valueNum : Int, n : CGNode, hm : HeapModel) : LocalPointerKey =
    hm.getPointerKeyForLocal(n, valueNum).asInstanceOf[LocalPointerKey]
  
  def getPt(k : PointerKey, hg : HeapGraph[InstanceKey]) : Set[InstanceKey] =
    hg.getSuccNodes(k).toSet.map((k : Object) => k.asInstanceOf[InstanceKey])
  
  def formatMethod(m : MethodReference) : String = 
    ClassUtil.pretty(m).stripPrefix("L").replace(File.separatorChar,'.').replace('<','(').replace('>',')')
  
  type Absurdity = String
  
  def getAbsurdities(walaRes : WalaAnalysisResults, reportLibraryAbsurdities : Boolean = false,
                     doXmlOutput : Boolean = false) : Iterable[Absurdity] = {
    import walaRes._

    val methodNodeMap =
      cg.filter(n => (reportLibraryAbsurdities || !ClassUtil.isLibrary(n)) &&
                     !isGeneratedMethod(n.getMethod()))
      .groupBy(n => n.getMethod().getReference())

    def getAbsurditiesInternal[T](absurdityName : String, 
                                  getAbsurditiesForNode : (CGNode, HeapModel, HeapGraph[InstanceKey]) => Iterable[T],
                                  xmlifyAbsurdity : (T, String, MethodReference) => String) : List[String] = 
      methodNodeMap.foldLeft (List.empty[Absurdity]) ((l, entry) => {
        val caller = entry._1
        val absurdities = entry._2.foldLeft (null : Set[T]) ((s, n) => {
          val absurditiesForNode = getAbsurditiesForNode(n, hm, hg).toSet
          if (s == null) absurditiesForNode else absurditiesForNode.intersect(s)
        })
        absurdities.foldLeft (l) ((l, t) => xmlifyAbsurdity(t, absurdityName, caller) :: l)
      })      
          
    val nullDispatches = getAbsurditiesInternal("nullDispatch", getNullDispatchMethods, xmlifyMethodAbsurdity)
    val nullRets = getAbsurditiesInternal("nullRet", getNullReturnValueMethods, xmlifyMethodAbsurdity)
    val bogusBranches = getAbsurditiesInternal("bogusBranch", getBogusBranches, xmlifyBranchAbsurdity)

    val haveAbsurdities = !nullDispatches.isEmpty || !nullRets.isEmpty || !bogusBranches.isEmpty
    if (!haveAbsurdities) println("Found no absurdities.")
    else {
      println("Found absurdities: ")
      println("<absurdities>")
      nullDispatches.foreach(println)
      nullRets.foreach(println)
      bogusBranches.foreach(println)
      println("</absurdities>")
    }  
        
    def outputAbsurditiesXML() : Unit = {
      val prefix = 
        if (harnessClassName == "LdummyMainClass") "flowdroid" 
        else if (harnessClassName.contains(DroidelConstants.HARNESS_CLASS)) "droidel" 
        else "default"
  
      val absurditiesName = s"${prefix}_absurdities.xml"
      //val absurditiesPath = s"$appPath$absurditiesName"
      val absurditiesPath = absurditiesName
      
      println("Writing out absurdities to " + absurditiesPath)
            
      val SPACE = "  "
      Some(new PrintWriter(absurditiesPath)).foreach { f => {
        f.write("<absurdities>\n")
        nullDispatches.foreach(a => f.write(SPACE + a + "\n"))
        nullRets.foreach(a => f.write(SPACE + a + "\n"))
        bogusBranches.foreach(a => f.write(SPACE + a + "\n"))
        f.write("</absurdities>\n")
        f.close
      }}
      
      /*val reachableMethodsName = s"${prefix}_reachableMethods.txt"
      val reachableMethodsPath = s"$appPath/$reachableMethodsName"
      // write out list of reachable methods
      Some(new PrintWriter(reachableMethodsPath)).foreach { f => {
        methodNodeMap.keys.foreach(m => f.write(formatMethod(m) + "\n"))
        f.close
      }}*/
    }
    
    if (doXmlOutput) outputAbsurditiesXML    
    Util.combine(nullDispatches, Util.combine(nullRets, bogusBranches))
  }  

  type BytecodeIndex = Int
  type SourceLine = Int
  type SrcVarName = String
    
  def xmlifyMethodAbsurdity(quad : (MethodReference,BytecodeIndex,SourceLine,SrcVarName), absurdityName : String, caller : MethodReference) : String = 
    "<" + absurdityName + " callee=\"" + formatMethod(quad._1) + "\" bcIndex=\"" + quad._2 + "\" srcLine=\"" + quad._3 + 
    "\" var=\"" + quad._4 + "\" caller=\"" + formatMethod(caller) + "\" />"
    
  def xmlifyBranchAbsurdity(indices : (Int,Int,SrcVarName,SrcVarName), absurdityName : String, caller : MethodReference) : String = {
    "<" + absurdityName + " bcIndex=\"" + indices._1 + "\" srcLine=\"" + indices._2 + "\" lhsVar=\"" + indices._3 + 
    "\" rhsVar=\"" + indices._4 + "\" caller=\"" + formatMethod(caller) + "\" />"
  }    
    
  def getBytecodeIndexAndSourceLine(i : SSAInstruction, n : CGNode, index : Int) : (BytecodeIndex,SourceLine) = {
    val bcIndex =
      n.getMethod() match {
        case m :IBytecodeMethod => m.getBytecodeIndex(index)
        case _ => -1
    }
    val srcLine = IRUtil.getSourceLine(i, n.getIR())
    (bcIndex, srcLine)
  }
  
  val DUMMY_NAME = "0_NONE" // dummy name that's not a valid java identifier
  def getLocalName(instrIndex : Int, useNum : Int, ir : IR) : String = ir.getLocalNames(instrIndex, useNum) match {
    case names if names != null && names.length == 1 => names(0)
    case _ => if (ir == null) DUMMY_NAME else
      // try a bit harder--if this is a field read rather than a local, look up a field read
      // using a field read is fine in our instrumentation because it won't have and side effects IF it is a read of a 
      // this field or a static field read
      ir.getInstructions().find(i => i != null && i.hasDef() && i.getDef() == useNum) match {
        case Some(i : SSAGetInstruction) => 
          if (i.getUse(0) == 1) i.getDeclaredField().getName().toString() // ClassName.f or this.f, which can't throw exceptions on read
          else {
            if (i.isStatic) {
              val fld = i.getDeclaredField()
              val declClass = ClassUtil.deWalaifyClassName(fld.getDeclaringClass())
              s"$declClass.${fld.getName().toString()}"
            } else DUMMY_NAME            
          }
        case _ => DUMMY_NAME
      }
  }

  def getUncalledMethods(cg : CallGraph, cha : IClassHierarchy) : Set[IMethod] = {
    val cgMethods = cg.foldLeft (Set.empty[IMethod]) ((s, n) => s + n.getMethod)
    cha.foldLeft (Set.empty[IMethod]) ((s, c) =>
      if (!ClassUtil.isLibrary(c)) c.getAllMethods.foldLeft (s) ((s, m) => if (cgMethods.contains(m)) s else s + m)
      else s
    )
  }

  /** return the set of methods for @param c that are not syntactically called by other methods of @param c */
  def getNonSyntacticallyCalledMethods(c : IClass, cg : CallGraph, cha : IClassHierarchy) = {
    val allMethods = c.getAllMethods.toSet
    // get all methods syntactically called in the IR for m, remove them from s
    allMethods.foldLeft (allMethods) ((s, m) =>
      cg.getNodes(m.getReference).foldLeft (allMethods) ((s, n) =>
        if (n.getIR == null) s
        else
          n.getIR.getInstructions.foldLeft(allMethods)((s, i) => i match {
            case i: SSAInvokeInstruction => s - cha.resolveMethod(i.getCallSite.getDeclaredTarget)
            case _ => s
          })
      )
    )
  }

  def getBogusBranches(n : CGNode, hm : HeapModel, hg : HeapGraph[InstanceKey]) : Iterable[(BytecodeIndex,SourceLine,SrcVarName,SrcVarName)] = n.getIR match {
    case null => Nil
    case ir => ir.getInstructions().toIterable.zipWithIndex.collect({
      case (i : SSAConditionalBranchInstruction, index : Int) if i.isObjectComparison() && { 
        val tbl = ir.getSymbolTable()
        val (use0, use1) = (i.getUse(0), i.getUse(1))
        if (!tbl.isNullConstant(use0) && !tbl.isNullConstant(use1)) {
          val (pt0, pt1) = (getPt(makeLPK(use0, n, hm), hg), getPt(makeLPK(use1, n, hm), hg))
          pt0.intersect(pt1).isEmpty
          //pt0.isEmpty || pt1.isEmpty
        } else false 
      } =>
        val (lhs, rhs) = (getLocalName(index, i.getUse(0), ir), getLocalName(index, i.getUse(1), ir))
        val (bcIndex, srcLine) = getBytecodeIndexAndSourceLine(i, n, index)
        (bcIndex, srcLine, lhs, rhs)
    })
  }
      
  def getNullDispatchMethods(n : CGNode, hm : HeapModel, hg : HeapGraph[InstanceKey]) : Iterable[(MethodReference,BytecodeIndex,SourceLine,SrcVarName)] = n.getIR match {
    case null => Nil
      // collect all non-static invokes in the IR for n whose receiver has an empty points-to set
    case ir => ir.getInstructions().toIterable.zipWithIndex.collect({ 
      case (i : SSAInvokeInstruction, index : Int) if !i.isStatic() && getPt(makeLPK(i.getReceiver(), n, hm), hg).size == 0 =>
        val (bcIndex, srcLine) = getBytecodeIndexAndSourceLine(i, n, index)
        val receiverName = getLocalName(index, i.getReceiver(), ir)
        (i.getDeclaredTarget(), bcIndex, srcLine, receiverName)
    })
  }
  
  def getNullReturnValueMethods(n : CGNode, hm : HeapModel, hg : HeapGraph[InstanceKey]) : Iterable[(MethodReference,BytecodeIndex,SourceLine,SrcVarName)] = n.getIR match {
    case null => Nil
      // collect all invokes with non-primitive return values such that the pts-to set of the return value is empty
    case ir => ir.getInstructions().toIterable.zipWithIndex.collect({ 
      case (i : SSAInvokeInstruction, index : Int) if i.hasDef() && i.getDeclaredResultType().isReferenceType() && 
        getPt(makeLPK(i.getDef(), n, hm), hg).size == 0 =>
          val (bcIndex, srcLine) = getBytecodeIndexAndSourceLine(i, n, index)
          val retvalName = getLocalName(index, i.getDef(), ir)
          (i.getDeclaredTarget(), bcIndex, srcLine, retvalName)
    })
  }
}
