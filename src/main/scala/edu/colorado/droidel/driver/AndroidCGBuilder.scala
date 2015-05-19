package edu.colorado.droidel.driver

import com.ibm.wala.classLoader.{IClass, IMethod}
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions
import com.ibm.wala.ipa.callgraph.impl.{ArgumentTypeEntrypoint, ClassHierarchyClassTargetSelector, ClassHierarchyMethodTargetSelector}
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys
import com.ibm.wala.ipa.callgraph.{AnalysisCache, AnalysisOptions, AnalysisScope, CallGraphBuilder, ClassTargetSelector, ContextSelector, Entrypoint, MethodTargetSelector}
import com.ibm.wala.ipa.cha.{ClassHierarchy, IClassHierarchy}
import edu.colorado.walautil.cg.MemoryFriendlyZeroXContainerCFABuilder
import edu.colorado.walautil.{ClassUtil, JavaUtil, WalaAnalysisResults}

import scala.collection.JavaConversions._

class AndroidCGBuilder(analysisScope : AnalysisScope, harnessClass : String = "Landroid/app/ActivityThread",
                       harnessMethod : String = "main") {

  private val _harnessClass =
    if (!harnessClass.startsWith("L") || harnessClass.contains(".")) ClassUtil.walaifyClassName(harnessClass)
    else harnessClass

  private val cha = ClassHierarchy.make(analysisScope)

  val cache = new AnalysisCache()
  
  def makeAndroidCallGraph() : WalaAnalysisResults = {    
    val entrypoints = makeEntrypoints
    assert(!entrypoints.isEmpty, s"Couldn't find entrypoint method $harnessMethod in class ${_harnessClass}")
    val options = makeOptions(entrypoints)
    
    // finally, build the call graph and extract the points-to analysis
    val cgBuilder = makeCallGraphBuilder(options, cache)
    new WalaAnalysisResults(cgBuilder.makeCallGraph(options, null),
			                      cgBuilder.getPointerAnalysis())
  }     
  
  def makeEntrypoints() : Iterable[Entrypoint] = {  
    def isEntrypointClass(c : IClass) : Boolean = c.getName().toString() == _harnessClass
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
    new MemoryFriendlyZeroXContainerCFABuilder(cha, options, cache, null, null, defaultInstancePolicy)
  }
  
  // override to specify custom context interpreters and selectors
  def makeContextInterpreter(options : AnalysisOptions, cache : AnalysisCache) : SSAContextInterpreter = null
  def makeContextSelector(options : AnalysisOptions, cha : IClassHierarchy) : ContextSelector = null
  def makeMethodTargetSelector(): MethodTargetSelector = new ClassHierarchyMethodTargetSelector(cha)    
  def makeClassTargetSelector() : ClassTargetSelector =new ClassHierarchyClassTargetSelector(cha)  
  
  def addBypassLogic(options : AnalysisOptions, analysisScope : AnalysisScope, cha : IClassHierarchy) : Unit = {
    val nativeSpec = JavaUtil.getResourceAsFile("natives.xml", getClass)
    assert(nativeSpec.exists(), s"Can't find native spec ${nativeSpec.getAbsolutePath}")
    com.ibm.wala.ipa.callgraph.impl.Util.setNativeSpec(nativeSpec.getAbsolutePath)
    com.ibm.wala.ipa.callgraph.impl.Util.addDefaultBypassLogic(options, analysisScope,
      classOf[com.ibm.wala.ipa.callgraph.impl.Util].getClassLoader(), cha)
  }
}
