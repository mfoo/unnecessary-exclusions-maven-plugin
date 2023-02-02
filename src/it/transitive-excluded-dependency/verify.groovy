def buildLog = new File(basedir, "build.log")

// This one shouldn't be excluded because it's not a dependency or a transitive dependency
assert buildLog.text.contains("[WARNING] Dependency localhost:child1 excludes commons-collections:commons-collections unnecessarily")

// This one is a transitive dependency and shouldn't be suggested
//assert !buildLog.text.contains("[WARNING] Dependency localhost:child1 excludes com.google.guava:failureaccess unnecessarily")