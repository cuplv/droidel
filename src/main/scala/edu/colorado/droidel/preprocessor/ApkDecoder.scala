package edu.colorado.droidel.preprocessor

import java.io.File
import edu.colorado.droidel.util.JavaUtil

/** Decode resources from an APK using apktool and decompile the APK using dex2jar or Dare*/
class ApkDecoder(apkPath : String) {
  val apkName = apkPath.stripSuffix(".apk")
  val apk = new File(apkPath)
  assert(apk.isFile() && apk.getName().endsWith(".apk"))
  
  def decodeApp() : File = {
    val outputDir = new File(apkName)
    if (outputDir.exists()) {
      println("APK already decoded, using previous results")
      outputDir
    } else {
      // TODO: check if app has already been processed by Droidel
      decodeResources(outputDir)
      val decompiledJar = decompile
   
      // extract the decompiled jar in outputResDir/bin/classes
      JavaUtil.extractJar(decompiledJar, s"${outputDir.getAbsolutePath()}/bin/classes")
      decompiledJar.delete() // delete the jar produced by dex2jar
      outputDir
    }
  }
  
  def decompile() : File = {
    // TODO: app support for using Dare instead    
    try {
      println("Running dex2jar")
      //com.googlecode.dex2jar.v3.Main.doFile(apk)
    } catch {
      case e : Throwable =>
        println(s"Error running dex2jar: $e. Exiting")
        sys.exit
    }
    
    val DEX2JAR_SUFFIX = "_dex2jar.jar"
    val dex2jarOutput = new File(s"$apkName$DEX2JAR_SUFFIX")
    assert(dex2jarOutput.exists(), s"Dex2jar failed to produce expected output file ${dex2jarOutput.getAbsolutePath()}")
    dex2jarOutput
  }
  
  def decodeResources(apkToolOutputDir : File) : File = {
    val args = Array("d", "-f", apkPath, apkToolOutputDir.getAbsolutePath())
    try {
      println("Running apktool")
      brut.apktool.Main.main(args)
    } catch {
      case e : Throwable =>
        println(s"Error running apktool: $e. Exiting")
        sys.exit
    }
    
    assert(apkToolOutputDir.exists(), s"Apktool failed to produce expected output file ${apkToolOutputDir.getAbsolutePath()}")
    apkToolOutputDir
  }
}