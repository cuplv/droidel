package edu.colorado.droidel.driver

import java.io.File
import edu.colorado.droidel.preprocessor.ApkDecoder

object Main {

  // TODO: improve this by using a real cmd line args tool, adding exta opts
  def main(args: Array[String]) : Unit = {
    val APP = "-app"
    val ANDROID_JAR = "-android_jar"
    val NO_JPHANTOM = "-no_jphantom"
    val NO_INSTRUMENT = "-no_instrument"
    val FRAMEWORKLESS_HARNESS = "-frameworkless_harness"
    val NO_FRAGMENT_STUBS = "-no_fragment_stubs"
    val DROIDEL_HOME = "-droidel_home"
    val BUILD_CG = "-build_cg"
    val opts = Map(s"$APP" -> "Path to APK file or top-level directory of Android application",	
    	             s"$ANDROID_JAR" -> "Path to Android library JAR to use during analysis",
                   s"$DROIDEL_HOME" -> "Full path to droidel directory (default: .)")
		  
    val flags = Map(NO_JPHANTOM -> ("Don't preprocess app bytecodes with JPhantom. Less sound, but faster", false),
                    NO_INSTRUMENT ->
                      ("Don't perform bytecode instrumentation of libraries. Less sound, but much faster", false),
                    FRAMEWORKLESS_HARNESS ->
                      ("Generate harness usable outside of the Android framework rather than using ActivityThread.main",
                       false),
                    NO_FRAGMENT_STUBS -> ("Don't generated stubs for Fragment's (either app or support)", false),
                    BUILD_CG -> ("After transformation, build call graph and print reachable methods", false))
    
    def printUsage() : Unit = {
      println(s"Usage: ./droidel.sh $APP <path_to_app> $ANDROID_JAR <path_to_jar> [flags]")
      println("Options:")
      opts.foreach(entry => println(s"${entry._1}\t${entry._2}"))
      flags.foreach(entry => println(s"${entry._1}\t${entry._2._1}  default: ${entry._2._2}"))
    }
 
    if (args.length == 0) printUsage
    else {
      val DASH = "-"
      def parseArg(opt : String, arg : String, args : Map[String,String]) : Map[String,String] = {
        require(opt.startsWith(DASH))
        require(!arg.startsWith(DASH))
        args + (opt -> arg)
      }
      def parseFlag(flag : String, flags : Map[String,Boolean]) : Map[String,Boolean] = {
        require(flag.startsWith(DASH))
        flags + (flag -> true)
      }

      @annotation.tailrec
      def parseOpts(args : List[String], parsedArgs : Map[String,String],
                    parsedFlags : Map[String,Boolean]) : (Map[String,String], Map[String,Boolean]) = args match {
        case Nil => (parsedArgs, parsedFlags)
        case opt :: arg :: args if opts.contains(opt) => parseOpts(args, parseArg(opt, arg, parsedArgs), parsedFlags)
        case flag :: args if flags.contains(flag) => parseOpts(args, parsedArgs, parseFlag(flag, parsedFlags))
        case opt :: _ => 
          println(s"Unrecognized option $opt")
          printUsage
          sys.exit
      }

      def missingArgError(arg : String) = {
        printUsage
        sys.error(s"Couldn't find required arg $arg")
      }

      val (parsedArgs, parsedFlags) = parseOpts(args.toList, Map.empty[String,String], Map.empty[String,Boolean])
      def getFlagOrDefault(flag : String) : Boolean = parsedFlags.getOrElse(flag, flags(flag)._2)
      val app = parsedArgs.getOrElse(APP, missingArgError(APP))
      val androidJar = parsedArgs.getOrElse(ANDROID_JAR, missingArgError(ANDROID_JAR))
      val droidelHome = parsedArgs.getOrElse(DROIDEL_HOME, ".")
      val noJphantom = getFlagOrDefault(NO_JPHANTOM)
      val noInstrument = getFlagOrDefault(NO_INSTRUMENT)
      val noFragmentStubs = getFlagOrDefault(NO_FRAGMENT_STUBS)
      val frameworklessHarness = getFlagOrDefault(FRAMEWORKLESS_HARNESS)
      val buildCg = getFlagOrDefault(BUILD_CG)

      val appFile = new File(app)
      val jarFile = new File(androidJar)
      assert(appFile.exists(), s"Couldn't find input application $app")
      assert(jarFile.exists(), s"Couldn't find input JAR file $androidJar")
      assert(androidJar.contains("droidel"),
             s"Android JAR's used by Droidel must be preprocessed; see the Setting up an Android framework JAR section of the REAMDE")
      // convert the input from an apk into our desired format if necessary
      val droidelInput = if (appFile.isFile())            
        // decode the app resources and decompile the dex bytecodes to Java bytecodes
        new ApkDecoder(app, droidelHome).decodeApp.getAbsolutePath()
      else app   

      val transformer = new AndroidAppTransformer(droidelInput, new File(androidJar), droidelHome,
                                                  useJPhantom = !noJphantom,
                                                  instrumentLibs = !noInstrument,
                                                  generateFragmentStubs = !noFragmentStubs,
                                                  generateFrameworkIndependentHarness = frameworklessHarness,
                                                  buildCg = buildCg)

      transformer.transformApp()
    }
  }
}
