package edu.colorado.droidel.codegen

import scala.collection.JavaConversions._
import java.io.FileWriter
import java.io.StringWriter
import edu.colorado.droidel.util.JavaUtil
import edu.colorado.droidel.util.ClassUtil
import com.ibm.wala.types.MethodReference
import com.ibm.wala.classLoader.IClass
import java.io.File
import com.squareup.javawriter.JavaWriter
import java.util.EnumSet
import com.ibm.wala.types.ClassLoaderReference
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC
import edu.colorado.droidel.constants.AndroidConstants._
import edu.colorado.droidel.constants.DroidelConstants._
import AndroidLayoutStubGenerator._
import com.ibm.wala.ipa.cha.IClassHierarchy
import com.ibm.wala.types.TypeReference
import edu.colorado.droidel.parser.LayoutElement
import edu.colorado.droidel.parser.LayoutView
import edu.colorado.droidel.parser.LayoutFragment
import edu.colorado.droidel.constants.DroidelConstants
import edu.colorado.droidel.util.Types._
import com.ibm.wala.shrikeBT.MethodEditor.Output
import com.ibm.wala.shrikeBT.PopInstruction
import com.ibm.wala.shrikeBT.InvokeInstruction
import com.ibm.wala.shrikeBT.IInvokeInstruction
import com.ibm.wala.ssa.SSAInvokeInstruction
import com.ibm.wala.ssa.SymbolTable
import edu.colorado.droidel.constants.AndroidConstants
import com.ibm.wala.classLoader.IMethod
import com.ibm.wala.ssa.IR


object AndroidLayoutStubGenerator {
  protected val DEBUG = false
}

class AndroidLayoutStubGenerator(resourceMap : Map[IClass,Set[LayoutElement]], 
                                 cha : IClassHierarchy, 
                                 androidJarPath : String, 
                                 appBinPath : String ) extends AndroidStubGenerator {
  // rather than keep track of layouts and view hierarchies, smush them all together into one giant hierarchy
  // this creates complications for things like duplicate id's
  val SMUSH_VIEWS = true  
    
  type LayoutId = Int
  type VarName = String
  type Expression = String
  type Statement = String
  
  /** @param appBinPath - path to app's binaries, needed to compile stubs */
  override def generateStubs(stubMap : StubMap, generatedStubs : List[File]) : (StubMap, List[File]) =
    if (SMUSH_VIEWS) {
      val (allViews, allFragments) = resourceMap.foldLeft (List.empty[LayoutView], List.empty[LayoutFragment]) ((pair, entry) => {
        entry._2.foldLeft (pair) ((pair, e) => e match {
          case e : LayoutView =>
            val oldList = pair._1
            // if we're smushing all the View stubs into a single file, we won't be able to handle layout elements with duplicate id's (will cause duplicate
            // case labels and duplicate variables). unsoundly drop one of the duplicates on the floor. one of the disadvantages of smushing.
            if (oldList.exists(v => v.id == e.id)) (oldList, pair._2) else (e :: oldList, pair._2)
          case e : LayoutFragment => 
            val oldList = pair._2
            // ditto with unsound dropping for Fragment's
            if (oldList.exists(v => v.id == e.id)) (pair._1, oldList) else (pair._1, e :: oldList)
        })
      })
      
      generateWalaStubs(allViews, allFragments, stubMap, generatedStubs, Map.empty[LayoutId, MethodReference], 
                        LAYOUT_STUB_CLASS, appBinPath)
    } else sys.error("Only smushed views implemented for now") 
      /*resourceMap.foldLeft (List.empty[File], Map.empty[Int,MethodReference]) ((m, entry) => {
      val (views, fragments) = entry._2.foldLeft (List.empty[LayoutView], List.empty[LayoutFragment]) ((pair, e) => e match {
        case e : LayoutView => (e :: pair._1, pair._2)
        case e : LayoutFragment => (pair._1, e :: pair._2)
      })
      generateWalaStubs(views, fragments, m._1, m._2, s"${ClassUtil.deWalaifyClassName(entry._1)}_generatedStubs", appBinPath)
    })*/ 
  
  val VIEW_PREFIX = "android.view"
  val WIDGET_PREFIX = "android.widget"
  val WEBKIT_PREFIX = "android.webkit"
  val layoutPrefixes = List(WIDGET_PREFIX, VIEW_PREFIX, WEBKIT_PREFIX)
  
  private def getTypeForAndroidClassName(name : String) : TypeReference = {        
    def isPackageExpandedName(name : String) : Boolean = name.contains('.')
    val packageExpandedNames = {
      if (isPackageExpandedName(name)) List(name) 
      else {       
        def capitalize(str : String) : String = s"${Character.toUpperCase(str.charAt(0))}${str.substring(1)}"
        
        // need this since XML allows lowercase v <view> elements
        val upperName = if (name.charAt(0).isUpper) name else capitalize(name)
        //assert(name.size > 0 && name.charAt(0).isUpper, s"$name does not look like a type")
        // speculatively guess that the name is either part of the view package or the widget package
        // this is a hack, but it's easier than manually specifying every kind of Android view and widget
        layoutPrefixes.map(prefix => s"$prefix.$upperName")
      }
    }.map(name => ClassUtil.walaifyClassName(name))
        
    // find a class in the class hierarchy corresponding to one of our guesses for the package expanded name
    cha.find(c => packageExpandedNames.contains(c.getName().toString())) match {
      case Some(c) => c.getReference()
      case None =>
        sys.error(s"Couldn't find class name corresponding to any of $packageExpandedNames in class hierarchy")
    }     
  }
  
  class InhabitedLayoutElement(val name : String, val id : Option[Int], val inhabitant : Expression, val typ : TypeReference)
  
  // generate a Java class with stubs for UI element lookups such as findViewById and findFragmentById
  private def generateWalaStubs(views : Iterable[LayoutView], fragments : Iterable[LayoutFragment], 
                                stubMap : StubMap, generatedStubs : List[File],
                                specializedGetterMap : Map[LayoutId,MethodReference], 
                                stubClassName : String, appBinPath : String) : (StubMap, List[File]) = {
    val inhabitor = new TypeInhabitor  
      
    def getFieldsAndAllocsForLayoutElems(elems : Iterable[LayoutElement], allocs : List[Statement]) : (List[InhabitedLayoutElement],List[Statement]) =
      elems.foldLeft (List.empty[InhabitedLayoutElement], allocs) ((pair, v) => {       
        val elemType = getTypeForAndroidClassName(v.typ)
        cha.lookupClass(elemType) match {
          case null => pair
          case clazz if ClassUtil.isInnerOrEnum(clazz) => pair
          case _ => 
            val (inhabitant, allocs) = inhabitor.inhabit(elemType, cha, pair._2, doAllocAndReturnVar = false)
            /*val id = v match {
              case v : LayoutView => v.id 
              case _ => None
            }*/
            (new InhabitedLayoutElement(v.name, v.id, inhabitant, elemType) :: pair._1, allocs)
        }        
      })
      
    val (viewFields, allocs1) = getFieldsAndAllocsForLayoutElems(views, List.empty[Statement])
    // not dealing with fragments for now
    val (fragmentFields, finalAllocs) = getFieldsAndAllocsForLayoutElems(fragments, allocs1)
        
    val stubDir = new File(STUB_DIR)
    if (!stubDir.exists()) stubDir.mkdir()
    //val stubClassName = s"${ClassUtil.deWalaifyClassName(clazz.getName()).replace('.', '_')}_layoutStubs"
    
    val strWriter = new StringWriter
    val writer = new JavaWriter(strWriter)        
    
    val ALL_WIDGETS = "android.widget.*"
    val ALL_VIEWS = "android.view.*"
    
    writer.emitPackage(STUB_DIR) 

    writer.emitImports(ALL_VIEWS, ALL_WIDGETS) 
    writer.emitEmptyLine()
    
    writer.beginType(stubClassName, "class", EnumSet.of(PUBLIC, FINAL)) // begin class

    // emit a field for each statically declared View and Fragment
    viewFields.foreach(v => writer.emitField(ClassUtil.deWalaifyClassName(v.typ), v.name, EnumSet.of(PRIVATE, STATIC)))
    fragmentFields.foreach(f => writer.emitField(ClassUtil.deWalaifyClassName(f.typ), f.name, EnumSet.of(PRIVATE, STATIC)))
    writer.emitEmptyLine()
    
    writer.beginInitializer(true) // begin static
    writer.beginControlFlow("try") // begin try
    // emit initialization of helper locals. need to reverse because allocs are prepended to list
    finalAllocs.reverse.foreach(a => writer.emitStatement(a))
    // emit initialization of View and Fragment fields
    viewFields.foreach(v => writer.emitStatement(s"${v.name} = ${v.inhabitant}"))
    fragmentFields.foreach(f => writer.emitStatement(s"${f.name} = ${f.inhabitant}"))
    
    // TODO: disabling for now, causing too many problems. in the future, look up the type and see if it has the setId/setText method
    // TODO: do this for Fragments too
    // emit writes to View id's if applicable        
    /*views.foreach(v => v.id match {
      case Some(id) => writer.emitStatement(v.name + ".setId(" + id + ")")
      case None => ()
    })
    val QUOTE = "\""
    // emit writes to View text if applicable
    views.foreach(v => v.text match {
      case Some(text) =>   
        // need to be careful in case text looks like a Java String literal
        // also escape format strings so JavaWriter doesn't get confused
        val escaped = StringEscapeUtils.escapeJava(text).replace("%", "%%")
        writer.emitStatement(v.name + s".setText($QUOTE${escaped}$QUOTE)")
      case None => ()
    })*/    
    
    writer.endControlFlow() // end try
    writer.beginControlFlow("catch (Exception e)") // begin catch   
    writer.endControlFlow() // end catch
    writer.endInitializer() // end static          
    writer.emitEmptyLine()
    
    //def makeIdSwitchForLayoutElements(elems : Iterable[LayoutElement]) : Unit = {
    def makeIdSwitchForLayoutElements(elems : Iterable[InhabitedLayoutElement]) : Unit = {
      writer.beginControlFlow("switch (id)") // begin switch on id's
      // one switch case per statically declared element
      elems.foreach(v => v.id match {
        case Some(id) =>  writer.emitStatement("case " + id + ": return " + v.name)
        case None => ()
      }) 
      // TODO: do something different here? like returning a phi of all other elements?
      writer.emitStatement("default: return null")
      writer.endControlFlow() // end switch    
    }
    
    // emit findViewById method that can return any of the child View's
    if (viewFields.size != 0) {
      writer.beginMethod(VIEW_TYPE, FIND_VIEW_BY_ID, EnumSet.of(PUBLIC, STATIC), "int", "id") // begin findViewById
      makeIdSwitchForLayoutElements(viewFields)
      writer.endMethod() // end findViewById
    }
    
    // emit findFragmentById() method than can return child Fragments
    if (fragmentFields.size != 0) {
      writer.beginMethod(FRAGMENT_TYPE, FIND_FRAGMENT_BY_ID, EnumSet.of(PUBLIC, STATIC), "int", "id") // begin findFragmentById
      makeIdSwitchForLayoutElements(fragmentFields)
      writer.endMethod() // end findFragmentById
    }
    
    def emitSpecializedGettersForLayoutElems(elems : Iterable[InhabitedLayoutElement], getterName : String, 
                                             specializedGetterMap : Map[Int,MethodReference]) : Map[Int,MethodReference] = 
      elems.foldLeft (specializedGetterMap) ((specializedGetterMap, v) => v.id match {
        case Some(id) => 
          val generatedName = s"${getterName}$id"
          val methodRetval = ClassUtil.deWalaifyClassName(v.typ)
          //if (getterName == "getFragment") writer.beginMethod(FRAGMENT_TYPE, generatedName, EnumSet.of(PUBLIC, STATIC))
          //else writer.beginMethod(methodRetval, generatedName, EnumSet.of(PUBLIC, STATIC))
          writer.beginMethod(methodRetval, generatedName, EnumSet.of(PUBLIC, STATIC))
          writer.emitStatement(s"return ${v.name}")
          writer.endMethod()

          val sig = s"()${v.typ.getName().toString()}"
          specializedGetterMap + 
            //(id -> MethodReference.findOrCreate(ClassLoaderReference.Application, 
          (id -> MethodReference.findOrCreate(ClassLoaderReference.Primordial,
                                                ClassUtil.walaifyClassName(s"$STUB_DIR.$stubClassName"), generatedName, sig))
        case None => specializedGetterMap
      })   
    
    // emit specialized getters for each View and Fragment with a statically known id
    val specializedGetters = {
      val viewMap = emitSpecializedGettersForLayoutElems(viewFields, "getView", specializedGetterMap)
      emitSpecializedGettersForLayoutElems(fragmentFields, "getFragment", viewMap)
    }
        
    writer.endType() // end class            
    
    // write out stub to file
    val stubPath = s"$STUB_DIR${File.separator}$stubClassName"
    val fileWriter = new FileWriter(s"${stubPath}.java")
    if (DEBUG) println(s"Generated stub: ${strWriter.toString()}")
    fileWriter.write(strWriter.toString())    
    // cleanup
    strWriter.close()
    writer.close()    
    fileWriter.close()
    
    // compile stub against Android library *and* app (since it may use types from the app)
    val compilerOptions = List("-cp", s"${androidJarPath}${File.pathSeparator}$appBinPath")
    if (DEBUG) println(s"Running javac ${compilerOptions(0)} ${compilerOptions(1)}")
    val compiled = JavaUtil.compile(List(stubPath), compilerOptions)
    assert(compiled, s"Couldn't compile stub file $stubPath")    
    // TODO: pass path of generated stubs out for easier cleanup later
    (makeStubMap(specializedGetters, stubMap), new File(stubPath) :: generatedStubs)
  }
  
  private def makeStubMap(specializedLayoutGettersMap : Map[LayoutId, MethodReference], stubMap : StubMap) : StubMap = {
    def isSpecializedId(id : LayoutId) : Boolean = specializedLayoutGettersMap.contains(id)
    def isFirstParamSpecializedId(i : SSAInvokeInstruction, tbl : SymbolTable) : Boolean =
      i.getNumberOfUses() > 1 && tbl.isIntegerConstant(i.getUse(1)) && isSpecializedId(tbl.getIntValue(i.getUse(1)))          
          
    val viewTypeRef = ClassUtil.makeTypeRef(AndroidConstants.VIEW_TYPE)
    val activityTypeRef = ClassUtil.makeTypeRef(AndroidConstants.ACTIVITY_TYPE)
    val fragmentManagerTypeRef = ClassUtil.makeTypeRef(AndroidConstants.FRAGMENT_MANAGER_TYPE)
    
    val findViewByIdDescriptor = s"(I)${ClassUtil.walaifyClassName(AndroidConstants.VIEW_TYPE)}"
    val findFragmentByIdDescriptor = s"(I)${ClassUtil.walaifyClassName(AndroidConstants.FRAGMENT_TYPE)}"
    
    // TODO: check the class hierarchy for overrides of findViewById? 
    val findViewById = cha.resolveMethod(MethodReference.findOrCreate(viewTypeRef, AndroidConstants.FIND_VIEW_BY_ID, findViewByIdDescriptor))
    val activityFindViewById = cha.resolveMethod(MethodReference.findOrCreate(activityTypeRef, AndroidConstants.FIND_VIEW_BY_ID, findViewByIdDescriptor))    
    val findFragmentById = cha.resolveMethod(MethodReference.findOrCreate(fragmentManagerTypeRef, AndroidConstants.FIND_FRAGMENT_BY_ID, findFragmentByIdDescriptor))    
        
    val findViewByIdMeths = List(findViewById, activityFindViewById, findFragmentById)    
    
    def tryCreatePatch(i : SSAInvokeInstruction, ir : IR) : Option[Patch] = {
      val tbl = ir.getSymbolTable
      if (!ClassUtil.isLibrary(ir.getMethod()) && // we only want to inject these stubs in application code
          isFirstParamSpecializedId(i, tbl)) Some(createShrikePatch(specializedLayoutGettersMap(tbl.getIntValue(i.getUse(1)))))
      else None // TODO: add call to Stubs.findViewById(param) for non-specialized cases heres
    }
      
    findViewByIdMeths.foldLeft (stubMap) ((map, method) => if (method != null) map + (method -> tryCreatePatch) else map)
  }
  
  private def createShrikePatch(m : MethodReference) : Patch = new Patch() {
    override def emitTo(o : Output) : Unit = {
      if (DEBUG) println("Instrumenting call to layout stub")
      o.emit(PopInstruction.make(1)) // pop the constant passed to findViewById/findFragmentById off the stack
      o.emit(PopInstruction.make(1)) // pop the receiver of findViewById/findFragmentById off the stack
      val methodClass = ClassUtil.typeRefToBytecodeType(m.getDeclaringClass())
      o.emit(InvokeInstruction.make(m.getDescriptor().toString(), 
             methodClass,
             m.getName().toString(), 
             IInvokeInstruction.Dispatch.STATIC)
      )
    }
  }
  
}