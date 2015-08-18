package edu.colorado.droidel.driver

import java.io.File
import java.util.jar.JarFile

import com.ibm.wala.classLoader.{IClass, IField, IMethod}
import com.ibm.wala.ipa.callgraph.AnalysisScope
import com.ibm.wala.ipa.cha.{ClassHierarchy, IClassHierarchy}
import com.ibm.wala.shrikeBT.MethodEditor.{Output, Patch}
import com.ibm.wala.shrikeBT.{DupInstruction, IInvokeInstruction, MethodEditor, NewInstruction, _}
import com.ibm.wala.ssa.{IR, SSAInvokeInstruction, SSANewInstruction, SymbolTable}
import com.ibm.wala.types.annotations.Annotation
import com.ibm.wala.types.{ClassLoaderReference, FieldReference, MethodReference, TypeReference}
import edu.colorado.droidel.codegen._
import edu.colorado.droidel.constants.DroidelConstants._
import edu.colorado.droidel.constants.{AndroidConstants, AndroidLifecycle}
import edu.colorado.droidel.driver.AndroidAppTransformer._
import edu.colorado.droidel.instrumenter.BytecodeInstrumenter
import edu.colorado.droidel.parser._
import edu.colorado.droidel.preprocessor.CHAComplementer
import edu.colorado.walautil.{CHAUtil, ClassUtil, IRUtil, JavaUtil, Timer, Util}

import scala.collection.JavaConversions._
import scala.io.Source
import scala.sys.process._

object AndroidAppTransformer {
  private val DEBUG = false
} 

class AndroidAppTransformer(_appPath : String, androidJar : File, droidelHome : String,
                            useJPhantom : Boolean = true,
                            instrumentLibs : Boolean = true,
                            cleanupGeneratedFiles : Boolean = true,
                            generateFrameworkIndependentHarness : Boolean = false,
                            generateFragmentStubs : Boolean = true,
                            buildCg : Boolean = false) {

  require(androidJar.exists(), s"Couldn't find specified Android JAR file ${androidJar.getAbsolutePath()}")

  type TryCreatePatch = (SSAInvokeInstruction, SymbolTable) => Option[Patch]
  type StubMap = Map[IMethod, TryCreatePatch]

  DROIDEL_HOME = droidelHome
  val harnessClassName = s"L${HARNESS_DIR}${File.separator}${HARNESS_CLASS}"
  val harnessMethodName = HARNESS_MAIN  
  
  private val appPath = if (_appPath.endsWith(File.separator)) _appPath else s"${_appPath}${File.separator}" 

  private val libJars = {     
    // load libraries in "the libs" directory if they exist
    val libsDir = new File(s"${appPath}${LIB_SUFFIX}")
    if (libsDir.exists) libsDir.listFiles().toList.filter(f =>
      // TODO: only expecting JAR files here -- be more robust
      if (!f.getName().endsWith(".jar")) {
        println(s"Warning: Unexpected kind of input lib file $f; expected JAR. Excluding.")
        false
      } else true
    ) else List.empty[File]
  }
  
  val unprocessedBinPath = s"${appPath}${BIN_SUFFIX}"
  val appBinPath = { // path to the bytecodes for the app
    if (useJPhantom) {
      // check for bytecodes that have been processed with JPhantom and use them
      // if they exist. otherwise, create them
      val jPhantomizedBinPath = s"${appPath}${JPHANTOMIZED_BIN_SUFFIX}"
      val jPhantomizedBinDir = new File(jPhantomizedBinPath)
      if (jPhantomizedBinDir.exists() && jPhantomizedBinDir.list().length != 0) {
        println("Found JPhantom-processed bytecodes--using them")
        jPhantomizedBinPath
      } else {
        // pre-process the app bytecodes with JPhantom
        val appBinFile = new File(unprocessedBinPath)        
        val originalJarName = "original.jar"
        // create JAR containing original classes
        val originalJar = JavaUtil.createJar(appBinFile, originalJarName, "", startInsideDir = true) 
        val jPhantomTimer = new Timer
        jPhantomTimer.start
        val success = new CHAComplementer(originalJar, droidelHome, androidJar :: libJars, jPhantomizedBinDir).complement
        jPhantomTimer.printTimeTaken("Running JPhantom")
        // remove the JAR we made
        originalJar.delete()
        if (success) jPhantomizedBinPath else unprocessedBinPath
      }
    } else unprocessedBinPath
  }

  // parse list of Android framework classes / interfaces whose methods are used as callbacks. This list comes from
  // FlowDroid (Arzt et al. PLDI 2014)
  private val callbackClasses =
    Source.fromURL(getClass.getResource(s"${File.separator}${CALLBACK_LIST}"))
    .getLines.foldLeft (Set.empty[TypeReference]) ((set, line) => 
      set + TypeReference.findOrCreate(ClassLoaderReference.Primordial, ClassUtil.walaifyClassName(line)))

  assert(!callbackClasses.isEmpty, "Couldn't find list of callback classes")

  val manifest = new ManifestParser().parseAndroidManifest(new File(appPath))

  val INSTRUMENTED_JAR_NAME = "instrumented.jar"

  /** @return true if the app has already been processed by Droidel */
  def isAppDroidelProcessed() : Boolean =
    // assume the app has been processed if the droidel_classes directory exists
    new File(s"${appPath}${DROIDEL_BIN_SUFFIX}").exists()

  /** return the list of manually specified callback classes */
  def getCallbackClasses() : Set[TypeReference] = callbackClasses

  /** @return true if @param c is a callback class */
  def isCallbackClass(c : IClass) : Boolean = callbackClasses.contains(c.getReference)

  def getApplicationCodeDir(applicationCodePath : String) : Option[File] = new File(applicationCodePath) match {    
    case f if f.exists() && f.isDirectory() => Some(f)
    case f =>
      // couldn't find the application code dir where it was supposed to be. this happens when the package specified in
      // the manifest doesn't correspond to an actual directory structure. try moving back up to the
      // parent directory and seeing if that directory structure exists
      val parent = f.getParent()
      if (parent != null) {
        println(s"Warning: couldn't find application code path specified in manifest, trying $parent")
        getApplicationCodeDir(f.getParent()) 
      } else None
  }
  
  // load Android libraries/our stubs in addition to the normal analysis scope loading 
  def makeAnalysisScope(useHarness : Boolean) : AnalysisScope = {
    val packagePath = manifest.packageName.replace('.', File.separatorChar)

    val binPath =
      if (useHarness) s"${appPath}${DROIDEL_BIN_SUFFIX}"
		  else appBinPath
    val applicationCodePath = s"$binPath${File.separator}$packagePath"
    val analysisScope = AnalysisScope.createJavaAnalysisScope()

    val harnessFile = if (useHarness) {
      val f = new File(s"${binPath}${File.separator}${ClassUtil.stripWalaLeadingL(harnessClassName)}.class")
      analysisScope.addClassFileToScope(analysisScope.getApplicationLoader(), f)
      Some(f)
    } else None
   
    val manifestUsedActivitiesAndApplications = manifest.entries.foldLeft (Set.empty[String]) ((s, a) => 
      s + s"${binPath}${File.separator}${a.getPackageQualifiedName.replace('.', File.separatorChar)}.class")


    val droidelDirs = Iterable(STUB_DIR, PREWRITTEN_STUB_DIR, HARNESS_DIR).map(dir => s"$binPath${File.separator}$dir")

    // load application code using Application class loader and all library code using Primordial class loader
    // we decide which code is application code using the package path from the application manifest
    val allFiles = Util.getAllFiles(new File(binPath)).filter(f => !f.isDirectory())
    allFiles.foreach(f => assert(!f.getName().endsWith(".jar"), 
                                 s"Not expecting JAR ${f.getAbsolutePath()} in app bin directory"))
    allFiles.foreach(f => if (f.getName().endsWith(".class")) {
      val path = f.getAbsolutePath
      // make sure code in the manifest-declared app package is loaded as application
      if (path.contains(applicationCodePath) ||
          // if we have library code that is declared as an application Activity in the manifest, load as application
          manifestUsedActivitiesAndApplications.contains(path) ||
          droidelDirs.exists(dir => path.contains(dir)))
        analysisScope.addClassFileToScope(analysisScope.getApplicationLoader(), f)
      else if (!useHarness || f.getAbsolutePath() != harnessFile.get.getAbsolutePath())
        analysisScope.addClassFileToScope(analysisScope.getPrimordialLoader(), f)
  })

    // if we're using JPhantom, all of the application code and all non-core Java library code (including the Android library)
    // has been deposited into the app bin directory, which has already been loaded. otherwise, we need to load library code
    if (!useJPhantom || binPath == unprocessedBinPath) {
      // load JAR libraries in libs directory as library code
      libJars.foreach(f => analysisScope.addToScope(analysisScope.getPrimordialLoader(), new JarFile(f)))
      // load Android JAR file as library code
      analysisScope.addToScope(analysisScope.getPrimordialLoader(), new JarFile(androidJar))
    }
    
    // load core Java libraries as library code
    // TODO: use or check for Android reimplementation of core libraries?
    getJVMLibFile match {
      case Some(javaLibJar) => analysisScope.addToScope(analysisScope.getPrimordialLoader(), new JarFile(javaLibJar))
      case None => sys.error("Can't find path to Java libraries. Exiting.")
    }

    // add WALA stubs
    getWALAStubs match {
      case Some(stubFile) => analysisScope.addToScope(analysisScope.getPrimordialLoader, new JarFile(stubFile))
      case None => sys.error("Can't find WALA stubs. Exiting.")
    }

    analysisScope.addToScope(analysisScope.getPrimordialLoader(), new JarFile(androidJar))
    analysisScope
  }

  type LayoutId = Int
  // make mapping from layout ID -> classes that use the corresponding layout
  private def makeLayoutIdToClassMapping(cha : IClassHierarchy) : Map[LayoutId,Set[IClass]] = {
    // TODO: improve these
    val SET_CONTENT_VIEW = "setContentView"
    val INFLATE = "inflate"
    def isSetContentView(m : MethodReference) : Boolean = 
      m.getName().toString() == SET_CONTENT_VIEW 
    def isInflate(m : MethodReference) : Boolean = 
      m.getName().toString() == INFLATE && m.getNumberOfParameters() >= 2 
    
    cha.foldLeft (Map.empty[Int,Set[IClass]]) ((map, c) => 
      if (!ClassUtil.isLibrary(c)) c.getDeclaredMethods.foldLeft (map) ((map, m) => {
        val ir = IRUtil.makeIR(m)
        if (ir != null) {
          val tbl = ir.getSymbolTable()
          ir.iterateNormalInstructions().foldLeft (map) ((map, i) => i match {
            case i : SSAInvokeInstruction if isSetContentView(i.getDeclaredTarget()) || isInflate(i.getDeclaredTarget()) =>
              // TODO: THIS IS A HACK! specify list of inflate methods and parameter nums for layouts
              (0 to i.getNumberOfUses() - 1).find(use => tbl.isIntegerConstant(i.getUse(use))) match {
                case Some(use) =>
                  val viewId = tbl.getIntValue(i.getUse(use))
                  // note that layouts can be reused across multiple classes, and a single class can be associated with multiple layouts
                  Util.updateSMap(map, viewId, c)
                case None => 
                  // setting layout, but not using a constant. conservatively assume that class can be associated with any layout
                  // the layout parser will handle this by mapping each of these classes to all layouts that it finds
                  // TODO: layout can also be dynamically declared. this hack doesn't handle that case
                  Util.updateSMap(map, LayoutParser.UNKNOWN_LAYOUT_ID, c)
              }
            case _ => map        
          })
        } else {
          if (DEBUG) println(s"Null IR for $m")
          map
        }
      }) else map
    )
  }   
  
   // collect callbacks registered in the manifest
  private def collectManifestDeclaredCallbacks(layoutMap : Map[IClass,Set[LayoutElement]]) : Map[IClass,Set[IMethod]] = {    
    def getEventHandlerMethod(eventHandlerName : String, parentClass : IClass) : Option[IMethod] =
      parentClass.getAllMethods().collect({ case m if m.getName().toString() == eventHandlerName => m }) match {
        case eventHandlers if eventHandlers.isEmpty =>
          // TODO: no sense in having this warning now. bring it back once our association of Activity's to layout is less dumb
          //println(s"Warning: couldn't find manifest-declared event handler method $eventHandlerName as a method on ${ClassUtil.pretty(parentClass)}")
          None          
        case eventHandlers =>
          if (eventHandlers.size > 1) println(s"Warning: expected to find exactly one method with name $eventHandlerName; found $eventHandlers")
          Some(eventHandlers.head)
      }

    layoutMap.foldLeft (Map.empty[IClass,Set[IMethod]]) ((m, entry) => entry._2.foldLeft (m) ((m, v) => v match {
      case v : LayoutView =>
        v.onClick match {
          case Some(onClick) =>
            val callbackClass = entry._1
            // this event handler method was explicitly declared in the manifest (rather than using the default onClick) 
            // look up its parent class, create a MethodReference, and add it to our set of manifest-declared entrypoints
            getEventHandlerMethod(onClick, callbackClass) match {
              case Some(callback) =>
                if (DEBUG) println(s"Adding manifest-declared entrypoint $callback")
                Util.updateSMap(m, callbackClass, callback)
              case None => m
            }
          case None => m // no event handler declared for this layout view
        }
      case v : LayoutFragment =>
        // fragments are odd in that (like View components) they can be created by the frameowrk or the application,
        // but (unlike View components, but like Activity's) they have lifecycle methods
        //println(s"Warning: Fragment ${v.typ} detected. We do not currently support the Fragment lifecycle")
        // TODO: add Fragment lifecycle methods here? or support Fragment as a top-level lifecycle type like Activity's?
        //sys.error("unsupported: fragments")
        m
      case v : LayoutInclude => m // happens when an include goes unresolved
    }))
  }

  def makeClassfileName(clazz : IClass) : String = s"${ClassUtil.stripWalaLeadingL(clazz.getName().toString())}.class"

  private def instrumentForApplicationAllocatedCallbackTypes(cha : IClassHierarchy,
    appCreatedCbMap : Map[IClass,Set[IMethod]], patchMap : Map[IMethod, (SSAInvokeInstruction, IR) => Option[Patch]],
    instrumentCbAllocs : Boolean = true) : (File, Iterable[FieldReference]) = {

    var dummyID = 0
    def getFreshDummyFieldName : String = { dummyID += 1; s"extracted_$dummyID" }
            
    val harnessClassName = s"L${HARNESS_DIR}${File.separator}${HARNESS_CLASS}"

    // map from classes to sets of callback classes they extend
    val cbImplMap =
      if (!instrumentCbAllocs) Map.empty[IClass, List[IClass]]
      else callbackClasses.foldLeft (Map.empty[IClass,List[IClass]]) ((m, t) => cha.lookupClass(t) match {
        case null => m
        case cbClass =>
          (cha.computeSubClasses(t) ++ cha.getImplementors(t)).foldLeft (m) ((m, c) =>
            m + (c -> (cbClass :: m.getOrElse(c, List.empty[IClass]))))
      })

    // look for application-created callback types by iterating through the class hierarchy instead of the methods in the callgraph.
    // this has pros and cons:
    // pro: iterating over the class hierarchy in a single pass is sound, whereas if we were iterating over the callgraph we would
    // have to iterate, instrument, build harness, rinse and repeat until we reach a fixed point
    // con: if some methods in the class hierarchy aren't reachable, we will extract their application-created callback types anyway.
    // this is sound, but not precise.
    val (instrFlds, allocMap, stubMap) = cha.foldLeft (List.empty[FieldReference], 
                                                       Map.empty[String,Map[IMethod,Iterable[(Int, List[FieldReference])]]],
                                                       Map.empty[String,Map[IMethod,Iterable[(Int, Patch)]]]) ((trio, clazz) =>
      if (instrumentLibs || !ClassUtil.isLibrary(clazz)) {
        val (flds, allocMap, stubMap) = clazz.getDeclaredMethods()
                                        .foldLeft (trio._1, 
                                                   Map.empty[IMethod,List[(Int, List[FieldReference])]],
                                                   Map.empty[IMethod,Iterable[(Int, Patch)]]) ((trio, m) => {
          val ir = IRUtil.makeIR(m)
          if (ir != null) {
            val (allocs, calls) = ir.getInstructions().zipWithIndex.foldLeft (List.empty[(Int, List[IClass])],List.empty[(Int, Patch)]) ((l, pair) =>
              pair._1 match {
                case i : SSANewInstruction if instrumentCbAllocs && !ClassUtil.isLibrary(m) =>
                  cha.lookupClass(i.getConcreteType()) match {
                    case null => l
                    case clazz =>
                      cbImplMap.get(clazz) match {
                        case None => l
                        case Some(cbImpls) =>
                          if (DEBUG)
                            println(s"Instrumenting allocation of ${ClassUtil.pretty(i.getConcreteType())} in method${ClassUtil.pretty(m)} at source line ${IRUtil.getSourceLine(i, ir)}")
                          ((pair._2, cbImpls) :: l._1, l._2)
                      }
                  }                
                case i : SSAInvokeInstruction if patchMap.contains(cha.resolveMethod(i.getDeclaredTarget())) => 
                  patchMap(cha.resolveMethod(i.getDeclaredTarget()))(i, ir) match {
                    case Some(patch) => 
                      if (DEBUG) 
                        println(s"Stubbing out call of ${ClassUtil.pretty(i.getDeclaredTarget())} in method " +
                                s"${ClassUtil.pretty(m)} at source line ${IRUtil.getSourceLine(i, ir)}")
                      (l._1, (pair._2, patch) :: l._2)
                    case None => l
                  }                               
                case _ => l
              }
            )      
            val newCalls = if (calls.isEmpty) trio._3 else trio._3 + (m -> calls)
            val (newFlds, newAllocs) = 
              if (allocs.isEmpty) (trio._1, trio._2) 
              else {
                // create instrumentation vars for each allocation of a callbacky type (one var per callback interface implemented
                val instrumentation = 
                  allocs.map(pair => (pair._1, pair._2.map(c => FieldReference.findOrCreate(ClassLoaderReference.Application,
                                                                                            harnessClassName.toString(),
                                                                                            getFreshDummyFieldName,
                                                                                            c.getName().toString())))
                  )
                // update list of instrFields                                                                                                
                val instrFields = instrumentation.foldLeft (trio._1) ((l, pair) => pair._2.foldLeft (l) ((l, f) => f :: l))                                                                                               
                (instrFields, trio._2 + (m -> instrumentation))
              }
            (newFlds, newAllocs, newCalls)
          } else trio
        })
        val className = makeClassfileName(clazz)
        val newCalls  = if (stubMap.isEmpty) trio._3 else trio._3 + (className -> stubMap)
        val (newFlds, newAllocs) = if (allocMap.isEmpty) (trio._1, trio._2) else (flds, trio._2 + (className -> allocMap))
        (newFlds, newAllocs, newCalls)
      } else trio
    )
    timer.printTimeTaken("Computing instrumentation to do")
    
    // create JAR containing original classes
    val originalJarName = "original.jar"
    val appBinFile = new File(appBinPath)
    val originalJar = JavaUtil.createJar(appBinFile, originalJarName, "", startInsideDir = true)
    
    val cbsToMakePublic =
      if (!instrumentCbAllocs) Map.empty[String,Set[IMethod]]
      else appCreatedCbMap.foldLeft (Map.empty[String,Set[IMethod]]) ((m, entry) => entry._2.filter(m => !m.isPublic()) match {
        case needToMakePublic if needToMakePublic.isEmpty => m
        case needToMakePublic => needToMakePublic.foldLeft (m) ((m, method) =>
          Util.updateSMap(m, makeClassfileName(method.getDeclaringClass()), method)
        )
      })
    if (!cbsToMakePublic.isEmpty || !allocMap.isEmpty || !stubMap.isEmpty) { // if there is instrumentation to do
      println("Performing bytecode instrumentation")
      val toInstrumentJarName = "toInstrument.jar"
      // create JAR containing classes to instrument only
      val toInstrument =
        JavaUtil.createJar(appBinFile, toInstrumentJarName, "", startInsideDir = true, j => j.isDirectory() ||
                           cbsToMakePublic.contains(j.getName()) || allocMap.contains(j.getName()) || stubMap.contains(j.getName()))
      // perform instrumentation
      val instrumentedJar =
        new BytecodeInstrumenter().doIt(toInstrument, allocMap, stubMap, Map.empty, cbsToMakePublic, INSTRUMENTED_JAR_NAME)
      toInstrument.delete() // cleanup JAR containing classes to instrument
      mergeInstrumentedAndOriginalJars(instrumentedJar , originalJar)
    }
    
    (originalJar, instrFlds)
  }

  private def mergeInstrumentedAndOriginalJars(instrumentedJar : File, originalJar : File) : File = {
    assert(instrumentedJar.exists(), s"Instrumentation did not create JAR file $instrumentedJar")
    // merge JAR containing instrumented classes on top of JAR containing original app. very important that
    // instrumented JAR comes first in the sequence passed to mergeJars, since we want to overwrite some entries in
    // the original JAR
    val mergedJarName = "merged.jar"
    JavaUtil.mergeJars(Seq(instrumentedJar, originalJar), mergedJarName, duplicateWarning = false)
    val newJar = new File(mergedJarName)
    // rename merged JAR to original JAR name
    if (originalJar.exists()) originalJar.delete()
    if (instrumentedJar.exists()) instrumentedJar.delete()
    newJar.renameTo(instrumentedJar)
    newJar
  }

  // make a map from framework class -> set of application classes implementing framework class)
  // TODO: use manifest to curate this list. right now we are (soundly, but imprecisely) including too much
  // TODO: curate by reasoning about callback registration. only need to include registered classes
  private def makeFrameworkCreatedTypesMap(cha : IClassHierarchy) : Map[IClass,Set[IClass]] = {
    val m = AndroidLifecycle.getOrCreateFrameworkCreatedClasses(cha).foldLeft(Map.empty[IClass, Set[IClass]])((m, c) =>
      cha.computeSubClasses(c.getReference()).filter(c => !ClassUtil.isLibrary(c)) match {
        //case appSubclasses if appSubclasses.isEmpty => m
        case appSubclasses =>
          // we only handle public classes because we need to be able to instantiate them and call their callbakcs
          // callback extraction should handle most of the other cases
          // abstract classes cannot be registered for callbacks because they can't be instantiated
          appSubclasses.filter(c => c.isPublic() && !c.isAbstract() && !ClassUtil.isInnerOrEnum(c)) match {
            //case concreteSubclasses if concreteSubclasses.isEmpty => m
            case concreteSubclasses =>
              m + (c -> concreteSubclasses.toSet)
          }
      }
    )

    // TODO: parse and check more than just Activity's. also, use the manifest to curate what we include above so we do
    // not include too much
    // sanity check our list of framework created types against the manifest
    val allApplicationActs = m.values.flatten.toSet
    manifest.activities.foreach(a => {
      val typeRef = TypeReference.findOrCreate(ClassLoaderReference.Application, ClassUtil.walaifyClassName(a.getPackageQualifiedName))
      val clazz = cha.lookupClass(typeRef)
      if (clazz == null || !allApplicationActs.contains(clazz)) {
        println(s"Warning: Activity ${a.getPackageQualifiedName} Typeref $typeRef IClass $clazz declared in manifest, but is not in framework-created types map")
        if (!useJPhantom) println(s"Recommended: use JPhantom! It is likely that $typeRef is being discarded due to a missing superclass that JPhantom can generate")
        if (DEBUG) sys.error("Likely unsoundness, exiting")
      }
    })

    m
  }

  private def hasDependencyInjectionAnnotation(f : IField) : Boolean = {
    // TODO: hack, replace with list of known dependency injection annotations
    def isInjectionAnnotation(a : Annotation) : Boolean = {
      val annotName = a.getType().getName().toString()
      annotName.toLowerCase.contains("inject")
    }

    f.getAnnotations match {
      case null => false
      case annots => annots.exists(a => isInjectionAnnotation(a))
    }
  }

  private def getDependencyInjectedFields(cha : IClassHierarchy) =
    cha.foldLeft (Map.empty[String,Map[IMethod,Iterable[(Int,Patch)]]]) ((m, c) =>
      if (ClassUtil.isLibrary(c)) m
      else {
        // TODO: handle dependecy injection on static fields as well? don't think this is common in practice
        val dependencyInjectedFields = c.getDeclaredInstanceFields().filter(f => hasDependencyInjectionAnnotation(f))
        if (dependencyInjectedFields.isEmpty) m
        else {
          val constructors = c.getDeclaredMethods.filter(method => method.isInit)
          assert(!constructors.isEmpty)
          val patch =
            new MethodEditor.Patch() {
            override def emitTo(o : Output) : Unit = {
              dependencyInjectedFields.foreach(f => {
                val fieldTypeClass = cha.lookupClass(f.getFieldTypeReference)
                if (fieldTypeClass != null) {
                  val chosenFieldTypeClass =
                    if (fieldTypeClass.isAbstract || fieldTypeClass.isInterface)
                    // we can't allocate an abstract type or an interface--choose a subclass instead
                      cha.computeSubClasses(fieldTypeClass.getReference).find(c => !c.isAbstract && !c.isInterface)
                    else Some(fieldTypeClass)
                  chosenFieldTypeClass match {
                    case Some(chosenFieldTypeClass) =>
                      val fieldTypeRef = chosenFieldTypeClass.getReference
                      val fieldName = f.getName.toString
                      val fieldDeclaringClass = ClassUtil.typeRefToBytecodeType(c.getReference)
                      val fieldType = ClassUtil.typeRefToBytecodeType(fieldTypeRef)
                      val isStatic = false
                      // load "this"
                      o.emit(LoadInstruction.make(ClassUtil.typeRefToBytecodeType(TypeReference.JavaLangObject), 0))
                      o.emit(NewInstruction.make(fieldType, fieldTypeRef.getDimensionality)) // allocate field type
                      o.emit(DupInstruction.make(0)) // copy instruction on top of stack
                      // call empty constructor on allocated cell
                      o.emit(InvokeInstruction.make("()V", fieldType, "<init>", IInvokeInstruction.Dispatch.SPECIAL))
                      // write allocated cell into this.field
                      o.emit(PutInstruction.make(fieldType, fieldDeclaringClass, fieldName, isStatic))
                    case None => ()
                  }
                }
              })
            }
          }
          val innerMap =
            constructors.foldLeft (Map.empty[IMethod,Iterable[(Int,Patch)]]) ((m, constructor) =>
              m + (constructor -> Iterable((1, patch)))
            )
          m + (makeClassfileName(c) -> innerMap)
        }
      }
    )

  private def instrumentForDependencyInjection(cha : IClassHierarchy) : Unit = {
    val depInjectedFields = getDependencyInjectedFields(cha)
    if (depInjectedFields.nonEmpty) {
      println("Performing bytecode instrumentation to explicate dependency injection")
      // create JAR containing original classes
      val originalJarName = "original.jar"
      val appBinFile = new File(appBinPath)
      val originalJar = JavaUtil.createJar(appBinFile, originalJarName, "", startInsideDir = true)
      val toInstrumentJarName = "toInstrument.jar"
      // create JAR containing classes to instrument only
      val toInstrument =
        JavaUtil.createJar(appBinFile, toInstrumentJarName, "", startInsideDir = true,
                           j => j.isDirectory() || depInjectedFields.contains(j.getName))
      val instrumentedJar =
        new BytecodeInstrumenter()
        .doIt(toInstrument, Map.empty, Map.empty, insertMap = depInjectedFields, Map.empty, INSTRUMENTED_JAR_NAME)
      toInstrument.delete() // cleanup JAR containing classes to instrument
      mergeInstrumentedAndOriginalJars(instrumentedJar, originalJar)
    }
  }

  private def doInstrumentationAndGenerateHarness(frameworkCreatedTypesMap : Map[IClass,Set[IClass]],
                                                  manifestDeclaredCallbackMap : Map[IClass,Set[IMethod]],
                                                  layoutElems : Iterable[InhabitedLayoutElement],
                                                  stubMap : Map[IMethod, (SSAInvokeInstruction, IR) => Option[Patch]],
                                                  stubPaths : Iterable[File],
                                                  cha : IClassHierarchy) : File = {

    // make a map fron application class -> set of lifecyle and manifest-declared callbacks on application class (+ all
    // on* methods). note that this map does *not* contain callbacks from implementing additional callback interfaces --
    // these are discovered in the harness generator itself.
    val frameworkCreatedTypesCallbackMap =
      frameworkCreatedTypesMap.foldLeft (Map.empty[IClass,Set[IMethod]]) ((m, entry) => {
      val possibleCallbacks = AndroidLifecycle.getCallbacksOnFrameworkCreatedType(entry._1, cha)
      entry._2.foldLeft (m) ((m, appClass) => {
        val appOverrides = {
          possibleCallbacks.filter(method => CHAUtil.mayOverride(method, appClass, cha))
          .map(m => cha.resolveMethod(appClass, m.getSelector())) // TODO: this can fail due to covariance in parameter types/return types. handle
        }          
        // add all methods that start with on for good measure
        // TODO: this is a hack. make an exhaustive list of these methods instead
        val onMethodsAndOverrides = appClass.getAllMethods().foldLeft (appOverrides.toSet) ((s, m) => 
          if (!m.isPrivate() && !ClassUtil.isLibrary(m.getDeclaringClass()) && m.getName().toString().startsWith("on") &&
            // hack to avoid difficulties with generic methods, whose parameter types are often Object
            (0 to m.getNumberOfParameters() - 1)
            .forall(i => m.getParameterType(i).getName() != TypeReference.JavaLangObject.getName())) s + m else s)
                
          val allCbs =
            manifestDeclaredCallbackMap.getOrElse(appClass, Set.empty[IMethod])
            .foldLeft (onMethodsAndOverrides) ((s, m) => s + m)
          assert(!m.contains(appClass), s"Callback map already has entry for app class $appClass")
          m + (appClass ->  allCbs)
        })
      })
      
    // perform two kinds of instrumentations on the bytecode of the app:
    // (1) find all types allocated in the application and instrument the allocating method to extract the allocation
    // via an instrumentation field
    // (2) make all callback methods in the appClassCbMap public so they can be called from the harness
    val (instrumentedJar, instrumentationFields) =
      instrumentForApplicationAllocatedCallbackTypes(cha, frameworkCreatedTypesCallbackMap, stubMap)
    timer.printTimeTaken("Performing bytecode instrumentation")

    println("Generating harness")
    generateAndroidHarnessAndPackageWithApp(frameworkCreatedTypesCallbackMap, layoutElems, manifestDeclaredCallbackMap,
                                            instrumentationFields, stubPaths, instrumentedJar, cha)
    timer.printTimeTaken("Generating and compiling harness")
    // no need to keep the JAR; we have an output directory containing these files
    if (instrumentedJar.exists()) instrumentedJar.delete()

    instrumentedJar
  }
  
  private def generateAndroidHarnessAndPackageWithApp(frameworkCreatedTypesCallbackMap : Map[IClass,Set[IMethod]],
                                                      layoutElems : Iterable[InhabitedLayoutElement],
                                                      manifestDeclaredCallbackMap : Map[IClass,Set[IMethod]], 
                                                      instrumentationFields : Iterable[FieldReference],   
                                                      stubPaths : Iterable[File], instrumentedJar : File,
                                                      cha : IClassHierarchy) : File = {

    // create fresh directory for instrumented bytecodes
    val instrumentedBinDirPath = s"${appPath}${DROIDEL_BIN_SUFFIX}"
    val instrumentedBinDir = new File(instrumentedBinDirPath)
    if (instrumentedBinDir.exists()) Util.deleteAllFiles(instrumentedBinDir)
    instrumentedBinDir.mkdir()

    // extract the JAR containing the instrumented class files
    Process(Seq("jar", "xvf", instrumentedJar.getAbsolutePath()), instrumentedBinDir).!!

    val harnessGen = new AndroidHarnessGenerator(cha, instrumentationFields)
    harnessGen.makeSpecializedViewInhabitantCache(stubPaths)
    harnessGen.generateHarness(frameworkCreatedTypesCallbackMap, layoutElems, manifestDeclaredCallbackMap,
                               instrumentedBinDirPath, androidJar.getAbsolutePath())

    // move stubs in with the apps
    Process(Seq("mv", STUB_DIR, s"$instrumentedBinDir${File.separator}${STUB_DIR}")).!!

    instrumentedBinDir
  }

  private def generateFrameworkDependentHarness() : File = {
    val instrumentedBinDirPath = s"${appPath}${DROIDEL_BIN_SUFFIX}"
    val instrumentedBinDirFile = new File(instrumentedBinDirPath)
    // delete instrumented bytecode directory if it exists
    if (instrumentedBinDirFile.exists()) Process(Seq("rm", "-r", instrumentedBinDirPath)).!!

    val instrumentedJar = new File(INSTRUMENTED_JAR_NAME)
    if (instrumentedJar.exists()) {
      instrumentedBinDirFile.mkdir()
      // extract the JAR containing the instrumented class files into the new bin dir
      Process(Seq("jar", "xvf", instrumentedJar.getAbsolutePath()), instrumentedBinDirFile).!!
      instrumentedJar.delete() // cleanup instrumented JAR
    } else Process(Seq("cp", "-r", appBinPath, instrumentedBinDirPath)).!! // just make a copy of app bytecodes

    // move stubs in with the instrumented bytecodes
    Process(Seq("mv", STUB_DIR, s"$instrumentedBinDirPath${File.separator}${STUB_DIR}")).!!

    // note that this automatically moves the compiled harness file into the bin directory for the instrumented app
    val harnessGen = new SimpleAndroidHarnessGenerator()
    harnessGen.generateHarness(instrumentedBinDirPath, androidJar.getAbsolutePath, generateFragmentStubs)
    instrumentedBinDirFile
  }

  def generateStubs(layoutMap : Map[IClass,Set[LayoutElement]], cha : IClassHierarchy) :
    (Map[IMethod, (SSAInvokeInstruction, IR) => Option[Patch]], Iterable[InhabitedLayoutElement], List[File]) = {
    println("Generating stubs")
    val layoutStubGenerator =
      new AndroidLayoutStubGenerator(layoutMap, cha, androidJar.getAbsolutePath, appBinPath, generateFragmentStubs)
    val (stubMap, generatedStubs) = layoutStubGenerator.generateStubs()
    val (finalStubMap, stubPaths) =
      if (generateFrameworkIndependentHarness) (stubMap, generatedStubs)
      else
        new AndroidSystemServiceStubGenerator(cha, androidJar.getAbsolutePath(), appBinPath)
        .generateStubs(stubMap, generatedStubs)
    timer.printTimeTaken("Generating and compiling stubs")
    (finalStubMap, layoutStubGenerator.getInhabitedElems, stubPaths)
  }

  def parseLayout(cha : IClassHierarchy) : (Map[IClass,Set[LayoutElement]], Map[IClass,Set[IMethod]]) = {
    println("Parsing layout")
    val layoutIdClassMap = makeLayoutIdToClassMapping(cha)
    val layoutMap =
      new LayoutParser().parseAndroidLayout(new File(appPath), new File(appBinPath), manifest, layoutIdClassMap)
    val manifestDeclaredCallbackMap = collectManifestDeclaredCallbacks(layoutMap)
    timer.printTimeTaken("Parsing layout")
    (layoutMap, manifestDeclaredCallbackMap)
  }

  def generateFrameworkCreatedTypesStubs(frameworkCreatedTypesMap : Map[IClass,Set[IClass]],
                                         cha : IClassHierarchy) : Unit =
    frameworkCreatedTypesMap.foreach(pair => {
      val className = ClassUtil.deWalaifyClassName(pair._1)
      val appImpls = pair._2
      val gen = new AndroidFrameworkCreatedTypesStubGenerator()
      TYPE_STUBS_MAP.get(className) match {
        case Some((stubClass, stubMethod, defaultVal)) =>
          gen.generateStubs(appImpls, stubClass, stubMethod, className, defaultVal, cha, androidJar.getAbsolutePath,
                            appBinPath)
        case None => ()
      }
    })

  def generateManifestDeclaredCallbackStubs(manifestDeclaredCallbackMap : Map[IClass,Set[IMethod]],
                                            cha : IClassHierarchy) : Unit = {
    val activityType =
      cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Primordial,
                                                 ClassUtil.walaifyClassName(AndroidConstants.ACTIVITY_TYPE)))
    val contextType =
      cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Primordial,
                                                 ClassUtil.walaifyClassName(AndroidConstants.CONTEXT_TYPE)))
    val viewType =
      cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Primordial,
                                                 ClassUtil.walaifyClassName(AndroidConstants.VIEW_TYPE)))

    // sanity-check that set of registered cb's
    val filteredMap =
      manifestDeclaredCallbackMap.filter(pair => {
        val (clazz, cbSet) = pair
        if (!CHAUtil.isAssignableFrom(activityType, clazz, cha)) {
          // TODO: I think this comes up due to problems in computing the manifest-declared callback map
          println(s"Warning: expecting $clazz to be Activity subclass.")
          if (DEBUG) sys.error("exiting")
          false
        } else
          cbSet.forall(cb =>
            if (cb.isStatic) {
              println(s"Warning: expected non-static method as manifest-registered cb, but got $cb")
              false
            } else if (cb.getNumberOfParameters != 2) {
              println(s"Warning: expected exactly two parameters for manifest-registered cb $cb")
              false
            } else if (!CHAUtil.isAssignableFrom(contextType, cha.lookupClass(cb.getParameterType(0)), cha)) {
              println(s"Warning: expected first argument of $cb to be a subtype of Context")
              false
            } else if (!CHAUtil.isAssignableFrom(viewType, cha.lookupClass(cb.getParameterType(1)), cha)) {
              println(s"Warning: expected second argument of $cb to be a subtype of View")
              false
            } else true
          )
      })
    new XMLDeclaredCallbackStubGenerator()
    .generateStubs(filteredMap, XML_DECLARED_CALLBACKS_STUB_CLASS,
                   XML_DECLARED_CALLBACKS_STUB_METHOD, androidJar.getAbsolutePath, appBinPath)
  }
  
  val timer = new Timer
  timer.start() 

  def transformApp() : Unit = {
    println(s"Transforming $appPath")
    // create class hierarchy from app
    val analysisScope = makeAnalysisScope(useHarness = false)
    val cha = ClassHierarchy.make(analysisScope)
    // parse app layout
    val (layoutMap, manifestDeclaredCallbackMap) = parseLayout(cha)
    // generate app-specialized stubs for layout
    val (stubMap, inhabitedLayoutElems, stubPaths) = generateStubs(layoutMap, cha)
    val frameworkCreatedTypesMap = makeFrameworkCreatedTypesMap(cha)

    if (generateFrameworkIndependentHarness) {
      // inject the stubs via bytecode instrumentation and generate app-specialized harness
      doInstrumentationAndGenerateHarness(frameworkCreatedTypesMap, manifestDeclaredCallbackMap,
                                          inhabitedLayoutElems, stubMap, stubPaths, cha)
    } else {
      // instrument the bytecode to add allocation instructions for dependency injection
      instrumentForDependencyInjection(cha)
      // generated app-specialized stubs for lifecycle types (Activity's, Service's, etc.)
      generateFrameworkCreatedTypesStubs(frameworkCreatedTypesMap, cha)
      generateManifestDeclaredCallbackStubs(manifestDeclaredCallbackMap, cha)
      // generate a harness by using ActivityThread.main and fixing reflection problems via stub generation
      generateFrameworkDependentHarness()
    }

    if (buildCg) {
      println("Building call graph")
      val cgBuilder =
        new AndroidCGBuilder(makeAnalysisScope(useHarness = true), harnessClass = harnessClassName,
                             harnessMethod = harnessMethodName)
      val walaRes = cgBuilder.makeAndroidCallGraph()
      println("Reachable methods:")
      walaRes.cg.foreach(n => println(ClassUtil.pretty(n)))
    }

    // cleanup generated stub and harness source files
    if (cleanupGeneratedFiles) {
      val stubDir = new File(STUB_DIR)
      if (stubDir.exists()) Util.deleteAllFiles(stubDir) 
      val harnessDir = new File(HARNESS_DIR)
      if (harnessDir.exists()) Util.deleteAllFiles(harnessDir)         
    }
  }

  private def getJVMLibFile : Option[File] = {
    val PATH = System.getProperty("java.home")
    List(new File(Seq(PATH, "lib", "rt.jar").mkString(File.separator)),
         new File(Seq(PATH, "..", "Classes", "classes.jar").mkString(File.separator))).find(f => f.exists())
  }

  def getWALAStubs : Option[File] = {
    val f = JavaUtil.getResourceAsFile("primordial.jar.model", getClass)
    if (f.exists()) Some(f) else None
  }
    
}
