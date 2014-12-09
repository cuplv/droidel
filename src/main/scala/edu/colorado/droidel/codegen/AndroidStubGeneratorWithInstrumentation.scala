package edu.colorado.droidel.codegen

import java.io.File

import com.ibm.wala.classLoader.IMethod
import com.ibm.wala.shrikeBT.MethodEditor.Patch
import com.ibm.wala.ssa.{IR, SSAInvokeInstruction}

trait AndroidStubGeneratorWithInstrumentation extends AndroidStubGenerator {
  type TryCreatePatch = (SSAInvokeInstruction, IR) => Option[Patch]
  type StubMap = Map[IMethod, TryCreatePatch]  
  
  /** mapping from MethodReference to stub -> function deciding whether (and how) a given instruction should be stubbed,
   *  and alist of files created during stub generation 
   */          
  def generateStubs(stubMap : StubMap = Map.empty[IMethod, TryCreatePatch], generatedStubs : List[File] = Nil) : (StubMap, List[File])
                         
  // TODO: factor out some of the functionality for generating Java code for stubs/compiling into this trait or another one                                                  
}