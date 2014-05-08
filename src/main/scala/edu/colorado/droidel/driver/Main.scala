package edu.colorado.droidel.driver

import java.io.File
import edu.colorado.droidel.preprocessor.ApkDecoder

object Main {

  // TODO: improve this by using a real cmd line args tool, adding exta opts
  def main(args: Array[String]) : Unit = {
    val APP = "-app"
    val ANDROID_JAR = "-android_jar"
    val NO_JPHANTOM = "-no-jphantom"
    val opts = Map(s"$APP" -> "Path to top-level directory of Android application",	
    	           s"$ANDROID_JAR" -> "Path to Android library JAR to use during analysis")
		  
    val flags = Map(s"$NO_JPHANTOM" -> ("Don't preprocess app bytecodes with JPhantom", false))
    
    def printUsage() : Unit = {
      println(s"Usage: ./droidel.sh -$APP <path_to_app> -$ANDROID_JAR <path_to_jar> [flags]")
      println("Options:")
      opts.foreach(entry => println(s"${entry._1}\t${entry._2}"))
      flags.foreach(entry => println(s"${entry._1}\t${entry._1}  default: ${entry._2}"))
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
      def parseOpts(args : List[String], parsedArgs : Map[String,String], parsedFlags : Map[String,Boolean]) : (Map[String,String], Map[String,Boolean]) = args match {
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
      val app = parsedArgs.getOrElse(APP, missingArgError(APP))
      val androidJar = parsedArgs.getOrElse(ANDROID_JAR, missingArgError(ANDROID_JAR))
      val noJphantom = parsedFlags.getOrElse(NO_JPHANTOM, flags(NO_JPHANTOM)._2)
      
      val appFile = new File(app) 
      assert(appFile.exists(), s"Couldn't find input application $app")
      // convert the input from an apk into our desired format if necessary
      val droidelInput = if (appFile.isFile())            
        // decode the app resources and decompile the dex bytecodes to Java bytecodes
        new ApkDecoder(app).decodeApp.getAbsolutePath()
      else app   

      val transformer = new AndroidAppTransformer(droidelInput, new File(androidJar), useJPhantom = !noJphantom)
      transformer.transformApp()
    }
  }
}
