package edu.colorado.droidel.codegen

import java.io.File
import java.util.EnumSet
import javax.lang.model.element.Modifier.{PUBLIC, STATIC}

import com.ibm.mobile.droidertemplate.WriterFactory
import com.ibm.wala.classLoader.{CallSiteReference, IClass, IMethod, NewSiteReference}
import com.ibm.wala.ipa.cha.IClassHierarchy
import com.ibm.wala.shrikeBT.IInvokeInstruction
import com.ibm.wala.ssa.SSAInstruction
import com.ibm.wala.types.{ClassLoaderReference, FieldReference, TypeReference}
import edu.colorado.droidel.constants.{AndroidConstants, DroidelConstants}
import edu.colorado.walautil._

import scala.collection.JavaConversions._

object AndroidHarnessGenerator {
  private val DEBUG = false
}

class AndroidHarnessGenerator(cha : IClassHierarchy, instrumentationVars : Iterable[FieldReference])
  extends AndroidStubGenerator {
  // turning this off until we understand what to do better
  val HANDLE_EVENT_DISPATCH = false // TODO: fix and turn on
  val inhabitantCache = inhabitor.inhabitantCache
  
  // TODO: there can be multiple instrumentation vars of the same type. only one will be in the map. this may be undesirable  
  instrumentationVars.foreach(f => inhabitantCache.put(cha.lookupClass(f.getFieldType()), f.getName().toString()))
  
  var initAllocs = List.empty[Statement]  
  
  def makeSpecializedViewInhabitantCache(stubPaths : Iterable[File]) = {
    def makeClass(className : String) : IClass = 
      cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Primordial, ClassUtil.walaifyClassName(className)))
    val layoutStubClass = s"${DroidelConstants.STUB_DIR}.${DroidelConstants.LAYOUT_STUB_CLASS}"
    val viewClass = makeClass(AndroidConstants.VIEW_TYPE)
    val (alloc, freshVar) = inhabitor.mkAssign(viewClass, s"$layoutStubClass.findViewById(-1)")
    initAllocs = alloc :: initAllocs
    inhabitantCache.put(viewClass, freshVar)
  }

  // generate a harness at the WALA IR level rather than at the source level
  def generateWalaIRHarness(frameworkCreatedTypesCallbackMap : Map[IClass,Set[IMethod]],
    manifestDeclaredCallbackMap : Map[IClass,Set[IMethod]],
    instrumentedBinDir : String,
    androidJarPath : String) : String = {

    // (instrs, typeCache, currentlyInhabiting, pc, valueNumCounter) tuple
    type HarnessState = (List[SSAInstruction], Map[IClass, Int], Set[IClass], Int, Int)
    val emptyHarnessState : HarnessState = (List.empty[SSAInstruction], Map.empty[IClass,Int], Set.empty[IClass], 0, 1)
    val DUMMY_EXCEPTION = -1

    def inhabitType(t : TypeReference, harnessState : HarnessState) : HarnessState =
      if (t.isPrimitiveType) sys.error("Unimp: inhabiting primitive type " + t)
      else if (t.isArrayType) sys.error("Unimp: inhabiting array type " + t)
      else {
        val c = cha.lookupClass(t)
        val postState =
          inhabitClass(c, (harnessState._1, harnessState._2, harnessState._3 + c, harnessState._4, harnessState._5))
        // use currentlyInhabiting set from pre-state, everything else from post-state
        (postState._1, postState._2, harnessState._3, postState._4, postState._5)
      }

    def inhabitClass(c : IClass, harnessState : HarnessState) : HarnessState = {
      assert(!c.isAbstract && !c.isInterface, "Trying to inhabit abstract or interface type " + c)
      assert(!harnessState._2.contains(c), "Already inhabited " + c) // TODO: use type cache value here
      assert(!harnessState._3.contains(c), "Already inhabiting " + c) // TODO: add workaround for this case
      val constructors = c.getDeclaredMethods.filter(m => m.isInit)
      assert(!constructors.isEmpty, "Empty constructors") // TODO: in this case, just do allocation
      val (newHarnessState, allocNums) =
      constructors.foldLeft (harnessState, List.empty[Int]) ((pair, constructor) => {
        val ((instrs, typeCache, currentlyInhabiting, pc, valueNumCounter), allocNums) = pair
        val allocValueNum = valueNumCounter + 1
        val allocPc = pc + 1
        val allocSite = new NewSiteReference(allocPc, c.getReference)
        // allocate the type and add it to the instructions
        val newInstrs = IRUtil.factory.NewInstruction(IRUtil.getDummyIndex(), allocValueNum, allocSite) :: instrs
        val postAllocState = (newInstrs, typeCache, currentlyInhabiting + c, allocPc, allocValueNum)
        // inhabit all the parameters of the constructor
        val params = ClassUtil.getNonReceiverParameterTypes(constructor)
        // TODO: handle primitive types here
        val postParamsState = params.foldLeft (postAllocState) ((harnessState, t) =>
          inhabitType(t, (instrs, typeCache, currentlyInhabiting, pc, valueNumCounter)))
        val retState = {
          // inhabit the constructor call
          val (instrs, typeCache, _, pc, valueNumCounter) = postParamsState
          // call <allocValueNum>.init(params)
          val paramBindings =
            (allocValueNum :: params.map(t => if (t.isPrimitiveType) sys.error("Handle primitive types") else typeCache(c))
                              .toList
            ).toArray
          val callPc = pc + 1
          val callSite = CallSiteReference.make(callPc, constructor.getReference, IInvokeInstruction.Dispatch.SPECIAL)
          val callInstr = IRUtil.factory.InvokeInstruction(callPc, paramBindings, DUMMY_EXCEPTION, callSite)
          (callInstr :: instrs, typeCache, currentlyInhabiting, callPc, valueNumCounter)
        }
        (retState, allocValueNum :: allocNums)
      })
      // write val fresh : c = phi(subs)
      val (instrs, typeCache, currentlyInhabiting, pc, valueNumCounter) = newHarnessState
      val phiValueNum = valueNumCounter + 1
      val phiPc = pc + 1
      // create phiValueNum := phi(allocNums) instruction
      val finalInstrs = IRUtil.factory.PhiInstruction(phiPc, phiValueNum, allocNums.toArray) :: instrs
      (finalInstrs, typeCache + (c -> phiValueNum), currentlyInhabiting, phiPc, phiValueNum)
    }

    // allocate each of the framework-created types
    val harnessState =
      frameworkCreatedTypesCallbackMap.keys.foldLeft (emptyHarnessState) ((harnessState, c) =>inhabitClass(c, harnessState))

    sys.error("Unimplemented")
  }

  // take framework-allocated types and FieldReference's corresponding to instrumentation variables as input
  def generateHarness(frameworkCreatedTypesCallbackMap : Map[IClass,Set[IMethod]],
                      layoutElems : Iterable[InhabitedLayoutElement],
                      manifestDeclaredCallbackMap : Map[IClass,Set[IMethod]],
                      instrumentedBinDir : String,
                      androidJarPath : String) : File = {

    val harnessDir = new File(s"${instrumentedBinDir}/${DroidelConstants.HARNESS_DIR}")
    if (!harnessDir.exists()) harnessDir.mkdir()        
    
    // TODO: don't allocate new Fragment instances here -- get them by calling the layout stubs findFragmentById or specialized getters
    
    // create an instance of each framework-allocated type. return a list of allocations to emit 
    val allocStatements = frameworkCreatedTypesCallbackMap.keys.foldLeft (initAllocs) ((allocs, appType) => {
      // this will fill up the inhabitant cache with mappings for each framework created type
      val (inhabitant, newAllocs) = inhabitor.inhabit(appType.getReference(), cha, allocs)
      // if we inhabit a subtype of appType rather than appType itself, there will be no entry
      // for appType in the cache (the cache will contain the mapping subtype -> inhabitant). 
      // fix this oddity by adding a mapping from appType -> to inhabitant
      if (!inhabitantCache.contains(appType)) inhabitantCache.put(appType, inhabitant)
      newAllocs
    })        
        
    // TODO: do something smarter here too -- use our hardcoded list of callback classes and callback methods within those classes
    def getCallbacksOnType(typ : IClass) : Iterable[IMethod] = typ.getDeclaredMethods().filter(m => !m.isInit && !m.isClinit && m.isPublic())
    
    // create statements invoking all lifecycle and manifest-defined callbacks on each of our framework-created types
    val (frameworkCreatedCbCalls, allocStatements1) = frameworkCreatedTypesCallbackMap.foldLeft (List.empty[Statement],allocStatements) ((l, entry) => {
      val frameworkCreatedClass = entry._1
      val varName = inhabitantCache(frameworkCreatedClass) // this lookup will never fail because we've already allocated an instance of each framework-created type
      entry._2.foldLeft (l) ((l, m) => {      
        val (call, newAllocs) = inhabitor.inhabitFunctionCall(m, Some(varName), cha, l._2)
        (call :: l._1, newAllocs)
      })
    })    
        
    // invoke callbacks on framework-created types that extend callback interfaces
    val (frameworkCreatedInterfaceCbCalls, allocStatements2) = 
      frameworkCreatedTypesCallbackMap.keys.foldLeft (List.empty[Statement],allocStatements1) ((l, frameworkCreatedClass) => {
        val varName = inhabitantCache(frameworkCreatedClass) 
        
        def getAllParameterTypes(m : IMethod) : Iterable[TypeReference] = {
          (0 to m.getNumberOfParameters() - 1).map(i => m.getParameterType(i))
        }
        
        frameworkCreatedClass.getAllImplementedInterfaces().foldLeft (l) ((l, interfaceType) => {          
          // TODO: somewhat of a hack -- only invoke callback methods that our class directly overrides. 
          // this can miss callbacks that an application-scope superclass overrides
          val frameworkMethodsByName = frameworkCreatedClass.getDeclaredMethods().groupBy(m => m.getName())
          interfaceType.getDeclaredMethods().filter(m => m.isPublic() && !m.isStatic && CHAUtil.mayOverride(m, frameworkCreatedClass, cha)).foldLeft (l) ((l, m) => {
            val possibleOverrides = frameworkMethodsByName(m.getName())
                                    .filter(possibleOverride => !possibleOverride.isStatic() &&
                                            possibleOverride.getNumberOfParameters() == m.getNumberOfParameters())
            val toInhabit = if (possibleOverrides.size > 1) {
              // special case to handle generic methods, when we'll have one method with a parameter that is Object and one with a more specific type
              // TODO: this is a big hack. do better
              val objName = TypeReference.JavaLangObject.getName()
              val lessGeneralMethods = possibleOverrides.filter(overrideMethod => overrideMethod.getReturnType().getName() != objName && 
                                                                getAllParameterTypes(overrideMethod).forall(typ => typ.getName() != objName) && {
                (0 to m.getNumberOfParameters() - 1).forall(i => // check for covariance in parameter types  
                  CHAUtil.isAssignableFrom(m.getParameterType(i), overrideMethod.getParameterType(i), cha)                 
                )
              })
              if (lessGeneralMethods.isEmpty) None
              else Some(lessGeneralMethods.head) // pick one arbitrarily               
            } else Some(m)
            
            toInhabit match {
              case Some(toInhabit) =>
                 val (call, allocs) = inhabitor.inhabitFunctionCall(toInhabit, Some(varName), cha, l._2)
                 (call :: l._1, allocs)
              case None =>
                 if (DEBUG) sys.error(s"Couldn't find override for $m in $frameworkCreatedClass")
                 l
            }         
          })
        })
    })    

    // create statements invoking callbacks on each instrumentation variable. note that although an
    // application-allocated object can extend multiple callback interfaces, we create an instrumentation variable for
    // each CB interface it extends. thus, here it is sufficient to invoke the callback methods defined on the type of
    // each instrumentation var. To be concrete, here's an example of how we instrument an application-created type that
    // extends multiple callback interfaces:
    // 
    // class CBObj implements CallbackA, CallbackB
    // ...
    // x = new CBObj();
    // Harness.instrumented_CallbackA_1 = x; // added via instrumentation
    // Harness.instrumented_CallbackB_1 = x; // added via instrumentation
    // ...
    // class Harness
    // static CallbackA instrumented_CallbackA_1;
    // static CallbackB instrumented_CallbackB_1;
    // main() { instrumented_CallbackA_1.cbA(); instrumented_CallbackB_1.cbB(); }
    val (instrumentationVarCbCalls, allocStatements3) =
      instrumentationVars.foldLeft (List.empty[Statement], allocStatements2) ((l, v) =>
        getCallbacksOnType(CHAUtil.lookupClass(v.getFieldType(), cha)).foldLeft (l) ((l, m) => {
          val (call, finalAllocStatements) = inhabitor.inhabitFunctionCall(m, Some(v.getName().toString()), cha, l._2)
          (call :: l._1, finalAllocStatements)
        })
      )

    // TODO: improve this or make a dedicated list!
    def isEventDisaptch(m : IMethod) : Boolean = {
      val name = m.getName.toString
      name.startsWith("dispatch") || name.startsWith("perform")
    }

    // allocate calls to event dispatch callbacks
    val (eventDispatchCbCalls, finalAllocStatements) =
      if (!HANDLE_EVENT_DISPATCH) (List.empty[Statement], allocStatements3)
      else layoutElems.foldLeft (List.empty[Statement], allocStatements3) ((l, e) => cha.lookupClass(e.typ) match {
        case null => l
        case clazz =>
          clazz.getAllMethods.foldLeft (l) ((l, m) =>
            if (m.isPublic && isEventDisaptch(m)) {
              val (call, allocs) =
                inhabitor.inhabitFunctionCall(m, Some(s"${DroidelConstants.STUB_DIR}.${DroidelConstants.LAYOUT_STUB_CLASS}.${e.name}"),
                                              cha, l._2)
              (call :: l._1, allocs)
            } else l
          )
      })

    val harnessWriter = WriterFactory.factory(writer);

    harnessWriter.emitBegin();

    // emit static fields for each of our instrumentation variables
    instrumentationVars.foreach(field => harnessWriter.emitField(ClassUtil.deWalaifyClassName(field.getFieldType()),
                                                          field.getName().toString(),
                                                          EnumSet.of(PUBLIC, STATIC)))   
                                                          
    harnessWriter.emitBeginHarness();
    
    harnessWriter.beginAllocationComponent;
    
    // emit allocations. need to reverse because the list of allocations was populated by prepending the most recent allocation
    // (which may in turn depend on other allocations), so we want to go last to first
    finalAllocStatements.reverse.foreach(alloc => harnessWriter.emitAllocationComponent(alloc))
    
    harnessWriter.endAllocationComponent;
    
    harnessWriter.beginCallToComponent;
    
    // emit lifecycle callbacks on framework-created types
    frameworkCreatedCbCalls.foreach(invoke => harnessWriter.emitCallToComponent(invoke))
    // emit implemented interface callbacks on framework-created types
    frameworkCreatedInterfaceCbCalls.foreach(invoke => harnessWriter.emitCallToComponent(invoke))
    // emit instrumentation var callback invocations
    instrumentationVarCbCalls.foreach(invoke => harnessWriter.emitCallToComponent(invoke))
    // emit event dispatch callbacks on View's and Fragment's
    eventDispatchCbCalls.foreach(invoke => harnessWriter.emitCallToComponent(invoke))

    harnessWriter.endCallToComponent;
    
    harnessWriter.emitEndHarness();
    harnessWriter.emitEnd();
    
    
    val harnessPath = s"${harnessDir.getAbsolutePath()}/${DroidelConstants.HARNESS_CLASS}"
    // compile harness against Android library and the *instrumented* app (since the harness may use types from the app, and our instrumentation
    // may have made callbacks public that were previously private/protected)
    // place resulting .class file in the top-level directory for the instrumented app
    val compilerOptions =
      List("-cp", s".${File.pathSeparator}${androidJarPath}${File.pathSeparator}$instrumentedBinDir",
           "-d", instrumentedBinDir)
    writeAndCompileStub(harnessPath, compilerOptions)
  }
}
