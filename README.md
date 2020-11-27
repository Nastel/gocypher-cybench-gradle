# GoCypher CyBench Gradle plugin

CyBench Gradle plugin allows running of CyBench benchmark tests, generating reports and sending it to CyBench website during software build process. Plugin is easy to attach to the project build.gradle file and can be used in continuous integration (CI) systems. 

CyBench Gradle plugin executes all classes that use JMH framework for benchmark implementation and creates a report to specified location at the end of benchmarking process. 

As CyBench report contains total score, it is possible to configure build failure if score does not pass the pre-defined threshold.

<b>Notice:</b> Benchmarks are running on the server where software build is preformed, so the builds machine must have enough HW resources for a successful and stable benchmarking of software items, and the benchmark results may wary on different machines.  


## CyBench Gradle plugin integration and usage

The simplest configuration for the plugin requires:
* adding the dependency to the plugin and applying the plugin task 'cybench-launcher'

```build.gradle
buildscript {
    repositories {
        mavenLocal()
        dependencies {
            classpath 'com.gocypher.cybench:gocypher-cybench-gradle:0.0.1'
        }
    }
}
apply plugin: 'cybench-launcher'
```

**Prerequisites**
* A project must have dependencies to JMH framework and contain classes which implements benchmarks using JMH framework.
* Until CyBench Gradle plugin and its dependencies are not released to Central Maven repository, must build `GoCypher CyBench Launch Gradle Plugin` locally and install it to local Maven repository. See section [CyBench Gradle Plugin Build](##CyBench Gradle Plugin Build) for details.

**Configuration**

Plugin is configurable inside plugin configuration tags. Properties available for plugin behaviour configuration:

| Property name        | Description           | Default value  |
| ------------- |-------------| -----:|
| **forks**      | Number of forks for benchmark execution. |1 |
| **threads**      | Number of threads for each benchmark test.      |  1 |
| **measurementIterations**| Number of iterations for each benchmark.      |    5 |
| **measurementSeconds**| Time (in seconds) used for measurement execution (applies only for benchmarks where mode is throughput).     |    10 |
| **warmUpIterations**| Number of iterations for each benchmark warm-up.      |    3 |
| **warmUpSeconds**| Time (in seconds) used for warm-up execution (applies only for benchmarks where mode is throughput).     |    5 |
| **expectedScore**| Threshold for a total score. If report total score is lower then build fails. If -1 the property does not affect build.  |    -1 |
| **shouldSendReportToCyBench**| A boolean flag which indicates if the benchmark report should be sent to CyBench.  |    false |
| **shouldStoreReportToFileSystem** | A boolean flag which indicates if the benchmark report should be saved to file system | true |
| **reportsFolder**| Location in a local file system where reports shall be stored.  |    Current execution directory. |
| **reportUploadStatus**| Parameter which indicates if the report is public or private. Possible values: `public`, `private`  |   public  |
| **reportName**| Name of the benchmark report. |   CyBench Report  |
| **userProperties**| User defined properties which will be added to benchmarks report section `environmentSettings->userDefinedProperties` as key/value strings. Configuration pattern:`<key1>:<value1>;<key2>:<value2>`. Example which adds a project name:`project=My Test Project;` |   -  |
| **skip**| A flag which allows to skip benchmarks execution during build process. Benchmarks execution also can be skipped via JVM system property `-DskipCybench`. |   false  |
| **shouldFailBuildOnReportDeliveryFailure**| A flag which triggers build failure if the benchmark report was configured to be sent to CyBench but its delivery failed. |   false 

## Example of Full CyBench Gradle plugin configuration

```build.gradle
buildscript {
    repositories {
        mavenLocal()
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
    measurementSeconds = 5
    warmUpIterations = 0
    warmUpSeconds = 10
    expectedScore = 1000
    shouldSendReportToCyBench = true
    shouldStoreReportToFileSystem = true
    reportUploadStatus = 'public'
    reportsFolder = '/report'
    reportName = 'My First Benchmark'
    userProperties ='project=My Benchmarks Project;'
    skip = false
}
```

## CyBench Gradle Plugin Build

This step is required in order to use CyBench Gradle plugin during build process until it and its dependencies are not released to Central Maven repository.

#### Build gocypher-cybench-runner project

* Clone [GitHub repository](https://github.com/K2NIO/gocypher-cybench-java) to local machine.
* Navigate to directory `gocypher-cybench-client`.
* Run command from the command line 
```sh
     mvn clean install
```

#### Build  gocypher-cybench-gradle project

* Clone [GitHub repository](https://github.com/K2NIO/gocypher-cybench-gradle) to local machine.
* Navigate to directory `Cybench-Launch-Gradle-Plugin`.
* Run command from the command line 
```sh
     gradle clean build install
```
<b>Notice:</b> After successful run project JAR's are installed to local Maven repository.