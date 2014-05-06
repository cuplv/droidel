package edu.colorado.droidel.driver

import scala.collection.JavaConversions._
import com.ibm.wala.ipa.callgraph.AnalysisCache
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXContainerCFABuilder
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys
import com.ibm.wala.ipa.callgraph.CallGraphBuilder
import com.ibm.wala.ipa.cha.IClassHierarchy
import com.ibm.wala.ipa.callgraph.AnalysisOptions
import com.ibm.wala.ipa.callgraph.AnalysisScope
import com.ibm.wala.ipa.callgraph.ContextSelector
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions
import com.ibm.wala.ssa.SSAOptions
import com.ibm.wala.ipa.callgraph.Entrypoint
import com.ibm.wala.ssa.InstanceOfPiPolicy
import com.ibm.wala.ipa.callgraph.impl.ArgumentTypeEntrypoint
import com.ibm.wala.classLoader.IMethod
import com.ibm.wala.classLoader.IClass
import com.ibm.wala.ipa.cha.ClassHierarchy
import com.ibm.wala.ipa.callgraph.impl.ClassHierarchyClassTargetSelector
import com.ibm.wala.ipa.callgraph.ClassTargetSelector
import com.ibm.wala.ipa.callgraph.impl.ClassHierarchyMethodTargetSelector
import com.ibm.wala.ipa.callgraph.MethodTargetSelector
import java.util.jar.JarFile
import java.io.File
import edu.colorado.droidel.util.Util
import edu.colorado.droidel.util.ClassUtil

class AndroidCGBuilder(analysisScope : AnalysisScope, harnessClass : String, harnessMethod : String) {       
  
  val cha = ClassHierarchy.make(analysisScope)    
  
  def makeAndroidCallGraph() : WalaAnalysisResults = {    
    val entrypoints = makeEntrypoints    
    val options = makeOptions(entrypoints)        
    val cache = new AnalysisCache
    
    // finally, build the call graph and extract the points-to analysis
    val cgBuilder = makeCallGraphBuilder(options, cache)
    // very important to do this *after* creating the call graph builder
    addBypassLogic(options, analysisScope, cha)   
    options.setSelector(makeClassTargetSelector)
    options.setSelector(makeMethodTargetSelector)    
    new WalaAnalysisResults(cgBuilder.makeCallGraph(options, null),
			                      cgBuilder.getPointerAnalysis())
  }     
  
  def makeEntrypoints() : Iterable[Entrypoint] = {  
    def isEntrypointClass(c : IClass) : Boolean = c.getName().toString() == harnessClass 
    def isEntrypointMethod(m : IMethod) : Boolean = m.getName().toString() == harnessMethod 
    def mkEntrypoint(m : IMethod, cha : IClassHierarchy) = new ArgumentTypeEntrypoint(m, cha)
    
    def addMethodsToEntrypoints(methods : Iterable[IMethod], entrypoints : List[Entrypoint]) : List[Entrypoint] = 
      methods.foldLeft (entrypoints) ((entrypoints, m) => 
        if (isEntrypointMethod(m)) mkEntrypoint(m, cha) :: entrypoints else entrypoints) 
    
    cha.foldLeft (List.empty[Entrypoint]) ((entrypoints, c) => 
      if (isEntrypointClass(c)) addMethodsToEntrypoints(c.getDeclaredMethods(), entrypoints)
      else entrypoints
    )
  }
  
  def makeOptions(entrypoints : Iterable[Entrypoint]) : AnalysisOptions = {
    val collectionEntrypoints : java.util.Collection[_ <: Entrypoint] = entrypoints
    val options = new AnalysisOptions(analysisScope, collectionEntrypoints)
    // turn off handling of Method.invoke(), which dramatically speeds up pts-to analysis
    options.setReflectionOptions(ReflectionOptions.NO_METHOD_INVOKE)   
    options
  }
  
  def makeCallGraphBuilder(options : AnalysisOptions, cache : AnalysisCache) : CallGraphBuilder = {
    assert(options.getMethodTargetSelector() == null, "Method target selector should not be set at this point.")
    assert(options.getClassTargetSelector() == null, "Class target selector should not be set at this point.")
    addBypassLogic(options, analysisScope, cha)
    val defaultInstancePolicy = ZeroXInstanceKeys.ALLOCATIONS | ZeroXInstanceKeys.SMUSH_MANY | 
                                ZeroXInstanceKeys.SMUSH_STRINGS | ZeroXInstanceKeys.SMUSH_THROWABLES
    new ZeroXContainerCFABuilder(cha, options, cache, makeContextSelector(options, cha), makeContextInterpreter(options, cache), defaultInstancePolicy)
  }
  
  // override to specify custom context interpreters and selectors
  def makeContextInterpreter(options : AnalysisOptions, cache : AnalysisCache) : SSAContextInterpreter = null
  def makeContextSelector(options : AnalysisOptions, cha : IClassHierarchy) : ContextSelector = null
  def makeMethodTargetSelector(): MethodTargetSelector = new ClassHierarchyMethodTargetSelector(cha)    
  def makeClassTargetSelector() : ClassTargetSelector =new ClassHierarchyClassTargetSelector(cha)  
  
  def addBypassLogic(options : AnalysisOptions, analysisScope : AnalysisScope, cha : IClassHierarchy) : Unit = 
    com.ibm.wala.ipa.callgraph.impl.Util.addDefaultBypassLogic(options, analysisScope, classOf[com.ibm.wala.ipa.callgraph.impl.Util].getClassLoader(), cha)
}
