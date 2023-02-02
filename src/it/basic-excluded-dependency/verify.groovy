def buildLog = new File(basedir, "build.log")
assert buildLog.text.contains("[WARNING] Dependency com.google.guava:guava excludes commons-collections:commons-collections unnecessarily")