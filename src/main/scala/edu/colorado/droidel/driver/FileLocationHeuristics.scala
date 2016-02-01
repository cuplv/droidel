package edu.colorado.droidel.driver

import java.io.File

import scala.util.matching.Regex
import scala.io.Source

/**
 * Created by s on 1/29/16.
 * This is a set of methods for figuring out how a project is organized, it finds the files necessary for conversion
 */
object FileLocationHeuristics {
  def getRecursiveListOfFiles(dir: File): Array[File] = {
    val these = dir.listFiles
    these ++ these.filter(_.isDirectory).flatMap(getRecursiveListOfFiles)
  }
  def getRecursiveListOfFilesFilt(dir: File, pattern: Regex): Array[File] = {
    val filt: Array[File] = getRecursiveListOfFiles(dir)
    getRecursiveListOfFiles(dir).filter((a: File) => {
      val name: String = a.getName
      pattern.findFirstIn(name).isInstanceOf[Some[_]]
    })
  }

  /**
   *
   * @param appPathf root file of project directory
   * @return all package strings from a project
   */
  def getAllAppPackages(appPathf: File): Set[String] = {

    val filt: Array[File] = getRecursiveListOfFilesFilt(appPathf, ".*\\.java$".r)
    val pkgRegex = "^package.*".r
    //assumption that a package should be greater than 3 chars
    val pkgs: Set[String] = filt.flatMap((file: File) => {
        if(file.exists()) {
          val lines: Iterator[String] = Source.fromFile(file).getLines()
          lines.flatMap((line: String) => pkgRegex.findFirstIn(line))
        }else None
    }).toSet.filter((a: String) => a.length > 2)

    pkgs
  }
  def getBinPath(appPath: String): String = {
    val appPathf: File = new File(appPath)
    val classFiles: Array[File] = getRecursiveListOfFilesFilt(appPathf, ".*\\.class".r)
    val packages: Set[String] = getAllAppPackages(appPathf)
    val substrdr: Set[String] = packages.map((a:String) => a.split("\\.")
      .mkString(File.separator)
      .substring(8, a.length -1))
    val classFilesFullPaths = classFiles.map((a: File) => a.getAbsolutePath)

    //find directory which contains all class files compiled
    val map: Set[Array[String]] = substrdr.map((a:String)=> classFilesFullPaths.flatMap((b: String) => {
      val contains: Boolean = b.contains(a)
      if(contains){
        val str: String = b.substring(0, b.indexOfSlice(a))
        Some(str)
      } else None
    }))
    val containingDirs: Set[String] = map.flatMap((a: Array[String]) =>{
      val toSet: Set[String] = a.toSet
      toSet
    })
    val countsOfClassFileLocations: Map[String, Int] = map.foldLeft(Map[String,Int]())(
      (acc: Map[String, Int], v: Array[String]) => {
        val clist: Map[String, Int] = v.foldLeft(acc)((acc: Map[String, Int], v: String) => {
          val cnt = acc.getOrElse(v, 0)
          acc.updated(v, cnt + 1)
        })
        clist
    })
    val locListCount: List[(String, Int)] = countsOfClassFileLocations.toList.sortBy(_._2)



    if(locListCount.length < 1){
      throw new IllegalStateException("could not find class file directory")
    }else {
      locListCount(0)._1
    }

  }

}
