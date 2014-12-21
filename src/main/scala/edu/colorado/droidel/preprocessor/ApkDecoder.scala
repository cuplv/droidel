package edu.colorado.droidel.preprocessor

import java.io.File

import edu.colorado.walautil.JavaUtil

import scala.sys.process._

/** Decode resources from an APK using apktool and decompile the APK using dex2jar or Dare*/
class ApkDecoder(apkPath : String, droidelHome : String) {
  val APKTOOL_JAR = s"$droidelHome/lib/apktool/apktool.jar"
  val DEX2JAR = s"$droidelHome/lib/dex2jar/d2j-dex2jar.sh"
  val apkName = apkPath.stripSuffix(".apk")
  val apk = new File(apkPath)
  assert(apk.isFile() && apk.getName().endsWith(".apk"))
  
  def decodeApp() : File = {
    val outputDir = new File(apkName)
    val decodedClassesDir = new File(s"${outputDir.getAbsolutePath()}/bin/classes")
    if (decodedClassesDir.exists()) {
      println("APK already decoded, using previous results")
      outputDir
    } else {
      decodeResources(apk, outputDir)
      val decompiledJar = decompile
   
      // extract the decompiled jar in outputResDir/bin/classes
      JavaUtil.extractJar(decompiledJar, decodedClassesDir.getAbsolutePath)
      decompiledJar.delete() // delete the jar produced by dex2jar
      outputDir
    }
  }
  
  def decompile() : File = {
    // TODO: support for using Dare instead
    val DEX2JAR_SUFFIX = "_dex2jar.jar"
    val dex2jarOut = new File(s"$apkName$DEX2JAR_SUFFIX")
    try {
      println("Running dex2jar")
      val cmd = s"$DEX2JAR $apk -o ${dex2jarOut.getAbsolutePath}"
      val output = cmd.!!
      println(output)
    } catch {
      case e : Throwable =>
        println(s"Error running dex2jar: $e. Exiting")
        sys.exit
    }

    assert(dex2jarOut.exists(), s"Dex2jar failed to produce expected output file ${dex2jarOut.getAbsolutePath()}")
    dex2jarOut
  }
  
  def decodeResources(apkFile : File, apkToolOutputDir : File) : File = {
    // run apktool from the command line to avoid conflicts due to different versions of Apache commons used in
    // apktool vs dex2jar
    val cmd = s"java -jar $APKTOOL_JAR d $apkFile -o ${apkToolOutputDir.getAbsolutePath()}"
    val output = cmd.!! // run apktool
    println(output)
    
    assert(apkToolOutputDir.exists(), s"Apktool failed to produce expected output file ${apkToolOutputDir.getAbsolutePath()}")
    apkToolOutputDir
  }
}