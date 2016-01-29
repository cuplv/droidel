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

    val filt: Array[File] = getRecursiveListOfFilesFilt(appPathf, ".*\\.java".r)
    val pkgRegex = "^package.*".r
    val pkgs: Set[String] = filt.flatMap((file: File) => {
        val lines: Iterator[String] = Source.fromFile(file).getLines()
        lines.flatMap((line: String) => pkgRegex.findFirstIn(line))
    }).toSet.filter((a: String) => a.length > 2) //assumption that a package should be greater than 3 chars


    ???
  }
  def getBinPath(appPath: String): String = {
    val appPathf: File = new File(appPath)
    getRecursiveListOfFilesFilt(appPathf, ".*\\.class".r)
    getAllAppPackages(appPathf)




    ???

  }

}
