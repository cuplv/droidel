package edu.colorado.droidel.driver

import java.io.File

import com.ibm.wala.classLoader.{IClass, IMethod}
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions
import com.ibm.wala.ipa.callgraph.impl.{ArgumentTypeEntrypoint, ClassHierarchyClassTargetSelector, ClassHierarchyMethodTargetSelector}
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter
import com.ibm.wala.ipa.callgraph.propagation.cfa.{ZeroXContainerCFABuilder, ZeroXInstanceKeys}
import com.ibm.wala.ipa.callgraph.{AnalysisCache, AnalysisOptions, AnalysisScope, CallGraphBuilder, ClassTargetSelector, ContextSelector, Entrypoint, MethodTargetSelector}
import com.ibm.wala.ipa.cha.{ClassHierarchy, IClassHierarchy}
import edu.colorado.droidel.constants.DroidelConstants
import edu.colorado.walautil.WalaAnalysisResults

import scala.collection.JavaConversions._

class AndroidCGBuilder(analysisScope : AnalysisScope, harnessClass : String = "Landroid/app/ActivityThread",
                       harnessMethod : String = "main") {
  
  val cha = ClassHierarchy.make(analysisScope)
  
  def makeAndroidCallGraph() : WalaAnalysisResults = {    
    val entrypoints = makeEntrypoints    
    val options = makeOptions(entrypoints)        
    val cache = new AnalysisCache
    
    // finally, build the call graph and extract the points-to analysis
    val cgBuilder = makeCallGraphBuilder(options, cache)
    // very important to do this *after* creating the call graph builder
    //addBypassLogic(options, analysisScope, cha)
    //options.setSelector(makeClassTargetSelector)
    //options.setSelector(makeMethodTargetSelector)
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
    com.ibm.wala.ipa.callgraph.impl.Util.addDefaultSelectors(options, cha)
    addBypassLogic(options, analysisScope, cha)
    val defaultInstancePolicy = ZeroXInstanceKeys.ALLOCATIONS | ZeroXInstanceKeys.SMUSH_MANY | 
                                ZeroXInstanceKeys.SMUSH_STRINGS | ZeroXInstanceKeys.SMUSH_THROWABLES
    new ZeroXContainerCFABuilder(cha, options, cache, makeContextSelector(options, cha),
                                 makeContextInterpreter(options, cache), defaultInstancePolicy)
  }
  
  // override to specify custom context interpreters and selectors
  def makeContextInterpreter(options : AnalysisOptions, cache : AnalysisCache) : SSAContextInterpreter = null
  def makeContextSelector(options : AnalysisOptions, cha : IClassHierarchy) : ContextSelector = null
  def makeMethodTargetSelector(): MethodTargetSelector = new ClassHierarchyMethodTargetSelector(cha)    
  def makeClassTargetSelector() : ClassTargetSelector =new ClassHierarchyClassTargetSelector(cha)  
  
  def addBypassLogic(options : AnalysisOptions, analysisScope : AnalysisScope, cha : IClassHierarchy) : Unit = {
    val nativeSpec = new File(s"${DroidelConstants.DROIDEL_HOME}/config/natives.xml")
    assert(nativeSpec.exists(), s"Can't find native spec ${nativeSpec.getAbsolutePath}")
    com.ibm.wala.ipa.callgraph.impl.Util.setNativeSpec(nativeSpec.getAbsolutePath)
    //com.ibm.wala.ipa.callgraph.impl.Util.setNativeSpec("config/natives.xml")
    com.ibm.wala.ipa.callgraph.impl.Util.addDefaultBypassLogic(options, analysisScope,
      classOf[com.ibm.wala.ipa.callgraph.impl.Util].getClassLoader(), cha)
  }
}
