package edu.colorado.droidel.parser

class AndroidManifest(val packageName : String, val targetAPIVersion : String, 
                      val activities : Iterable[ManifestActivity], val applications : Iterable[ManifestApplication]) {
  
  def entries : Iterable[ManifestEntry] = activities ++ applications  
}
