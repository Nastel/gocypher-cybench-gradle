# gocypher-cybench-gradle

## CyBench launcher plugin integration into any gradle project

```build.gradle
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        dependencies {
            classpath 'com.gocypher.cybench:gocypher-cybench-gradle:0.0.1'
        }
    }
}
apply plugin: 'cybench-launcher'

/* Configuration properties that could be provided to the plugin */
cybenchJMH{
    forks = 1
    threads = 1
    measurementIterations = 1
    warmUpIterations = 0
    warmUpSeconds = 10
    expectedScore = 10
    shouldSendReportToCyBench = true
    shouldStoreReportToFileSystem = true
    reportUploadStatus = 'public'
    reportsFolder = '/report'
    reportName = 'Custom name'
    userProperties ='Anything'
    skip = false
}
```			