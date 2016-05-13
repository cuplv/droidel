import sys
import os
import shutil

home = os.getenv("HOME")
LIBS_DIR= home + '/Documents/source/Scanr/supportJars'

class GradleToStd:
    """Transform a project from the gradle structure to the standard one."""
    def __init__(self, appdir, buildType, target):
        self.appdir = appdir
        self.target = target
        self.buildType = buildType


    def transform(self):
        self.createTargetStruct()
        if not self._copyClasses():
            sys.stderr.write("************ERRROR***************\n"
                             "classes dir not found in %s\n" % (self.appdir))
            return False

        if not self._copyLayout():
            sys.stderr.write("************ERRROR***************\n"
                             "layout dir not found %s\n" % (self.appdir))
            return False
            
        self._copyLibs()

        self._copyManifest()

        return True

    def createTargetStruct(self):
        # os.path.join(self.target,"bin","classes"),
        # os.path.join(self.target,"res","layout")
        # os.path.join(self.target,"libs")
        dst_paths = [os.path.join(self.target,"res")]
        for dst_dir in dst_paths:
            if not os.path.isdir(dst_dir):
                os.makedirs(dst_dir)

    def _copyPaths(self, src_paths, dst_path):
        for src_path in src_paths:
            if (os.path.exists(src_path) and os.path.isdir(src_path)):
                if (os.path.isdir(dst_path)):
                    shutil.rmtree(dst_path)
                # found element, copy
                shutil.copytree(src_path, dst_path)
                return True
            # else:
            #     print("Folder %s does not exists" % src_path)

        # failed to copy, dir not found
        return False
        
    def _copyClasses(self):

        src_paths = [os.path.join(self.appdir, "build", "intermediates", "classes", self.buildType),
                     os.path.join(self.appdir, "bin", "classes")]
        # if(not os.path.isdir(src_paths[0])):
        #     src_paths = [os.path.join(self.appdir, "app", "build", "intermediates", "classes", self.buildType),
        #                  os.path.join(self.appdir, "bin", "classes")]
        dst_path = os.path.join(self.target, "bin", "classes")

        if (not self._copyPaths(src_paths, dst_path)): return False
        else: return True
            
    def _copyLayout(self):
        src_paths = [os.path.join(self.appdir, "build", "intermediates", "res", self.buildType, "layout"),
                     os.path.join(self.appdir, "build", "intermediates", "res", "merged", self.buildType, "layout"),
                     os.path.join(self.appdir, "res", "layout")]    
        dst_path = os.path.join(self.target, "res", "layout")

        if (not self._copyPaths(src_paths, dst_path)): return False
        else: return True
    
    def _copyLibs(self):
        src_paths = [os.path.join(self.appdir, "libs")]    
        dst_path = os.path.join(self.target, "libs")
	###
	os.mkdir(dst_path)
        src_files = os.listdir(LIBS_DIR)
        for file_name in src_files:
            full_file_name = os.path.join(LIBS_DIR, file_name)
            if (os.path.isfile(full_file_name)):
		print "FFF: " + full_file_name
                shutil.copyfile(full_file_name, dst_path + "/" + full_file_name.split("/")[-1])

        if (not self._copyPaths(src_paths, dst_path)): return False
        else: return True
            
    def _copyManifest(self):
        src_files = [os.path.join(self.appdir, "build", "intermediates", "manifests", self.buildType, "AndroidManifest.xml"),
                     os.path.join(self.appdir, "build", "intermediates", "manifests", "full", self.buildType, "AndroidManifest.xml"),
                     os.path.join(self.appdir, "build", "intermediates", "manifests", "debug", "AndroidManifest.xml")]
        dst_path = self.target

        for src_file in src_files:
            if (os.path.isfile(src_file)):
                shutil.copy(src_file, dst_path)
                return True
        return False
        
        
