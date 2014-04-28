package edu.colorado.droidel.driver

import java.io.File

object Main {

  // TODO: improve this by using a real cmd line args tool, adding exta opts
  def main(args: Array[String]) : Unit = {
    val APP = "app"
    val ANDROID_JAR = "android_jar"
    val opts = Map(s"-$APP" -> "Path to top-level directory of Android application",	
    	           s"-$ANDROID_JAR" -> "Path to Android library JAR to use during analysis")
		  
    //val flags = Map("-no-jphantom" -> "Don't preprocess app bytecodes with JPhantom")
    
    def printUsage() : Unit = {
      println(s"Usage: ./droidel.sh -$APP <path_to_app> -$ANDROID_JAR <path_to_jar>")
      println("Options:")
      opts.foreach(entry => println(s"${entry._1}\t${entry._2}"))
    }
 
    if (args.length == 0) printUsage
    else {
      val DASH = "-"
      def parseArg(opt : String, arg : String, parsed : Map[String,String]) : Map[String,String] = {
        require(opt.startsWith(DASH))
        require(!arg.startsWith(DASH))
        parsed + (opt.stripPrefix(DASH) -> arg)
      }

      def parseArgs(args : List[String], parsed : Map[String,String]) : Map[String,String] = args match {
        case Nil => parsed
        case opt :: arg :: args if opts.contains(opt) => parseArgs(args, parseArg(opt, arg, parsed))
        case opt :: _ => 
          println(s"Unrecognized option $opt")
	  printUsage
	  sys.exit
      }

      def missingArgError(arg : String) = {
	printUsage
	sys.error(s"Couldn't find required arg $arg")
      }
      
      val parsedOpts = parseArgs(args.toList, Map.empty[String,String])
      val app = parsedOpts.getOrElse(APP, missingArgError(APP))
      val androidJar = parsedOpts.getOrElse(ANDROID_JAR, missingArgError(ANDROID_JAR))

      val transformer = new AndroidAppTransformer(app, new File(androidJar))
      transformer.transformApp()
    }
  }
}
