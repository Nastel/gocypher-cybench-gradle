# GoCypher CyBench Gradle plugin

[CyBench](https://www.gocypher.com/cybench/) Gradle plugin allows running of CyBench benchmark tests, generating reports and sending it to CyBench website during software build process. Plugin is easy to attach to the project build.gradle file and can be used in continuous integration (CI) systems. 

CyBench Gradle plugin executes all classes that use JMH framework for benchmark implementation and creates a report to specified location at the end of benchmarking process. 

As CyBench report contains total score, it is possible to configure build failure if score does not pass the pre-defined threshold.

<b>Notice:</b> Benchmarks are running on the server where software build is preformed, so the builds machine must have enough HW resources for a successful and stable benchmarking of software items, and the benchmark results may wary on different machines.  

## CyBench Gradle Plugin install

[Releases page](https://github.com/K2NIO/gocypher-cybench-gradle/releases) contains CyBench Gradle plugin shadowed with its dependencies (in a jar shape), which are possible to install to local Maven repository and start using it from any Gradle project immediately.

### Install CyBench Gradle plugin binaries
**Prerequisites:**  Maven command line tools on a local machine.
* Extract the content of the zip to your local filesystem. 
* Run the command using CMD from the folder where you extracted the binaries to:
```sh
mvn install:install-file -Dfile=gocypher-cybench-launch-gradle-plugin-1.0.0.jar -DgroupId=com.gocypher.cybench -DartifactId=gocypher-cybench-launch-gradle-plugin -Dversion=1.0.0 -Dpackaging=jar
```

### Start using CyBench Gradle plugin
Include dependency to CyBench Gradle plugin in your project `build.gradle` file as described in [the chapters below](#cybench-gradle-plugin-integration-and-usage) and start using it.

#### Optional: gocypher-cybench-annotation For adding custom benchmark annotations @BenchmarkTag
Install annotation binaries into your local maven repository
```sh
mvn install:install-file -Dfile=gocypher-cybench-annotations-1.0.0.jar -DgroupId=com.gocypher.cybench.client -DartifactId=gocypher-cybench-annotations -Dversion=1.0.0 -Dpackaging=jar
```
Include dependency to annotation processor in your project `build.gradle` file. 
```build.gradle
 compile 'com.gocypher.cybench.client:gocypher-cybench-annotations:1.0.0'
```
**Notice:** if you want for the tags to be auto generated on your projects you should add annotationProcessor or testAnnotationProcessor. First launch will generate the annotations for benchmarks and fail build.
```build.gradle
 annotationProcessor 'com.gocypher.cybench.client:gocypher-cybench-annotations:1.0.0'
 testAnnotationProcessor 'com.gocypher.cybench.client:gocypher-cybench-annotations:1.0.0'
```

## CyBench Gradle plugin integration and usage

The simplest configuration for the plugin requires: 

```build.gradle
/* At the top of the build.gradle file */
buildscript {
    repositories {
        mavenLocal()
        dependencies {
            classpath 'com.gocypher.cybench:gocypher-cybench-launch-gradle-plugin:1.0.0'
        }
    }
}
/* below the dependencies tag inside the build.gradle */
apply plugin: 'gocypher-cybench-launch-gradle-plugin'
```

**Prerequisites**
* A project must have `dependencies` to JMH framework and contain classes which implements benchmarks using JMH framework.
```build.gradle
    compile  group: 'org.openjdk.jmh', name: 'jmh-core', version: '1.26'
    annotationProcessor  group: 'org.openjdk.jmh', name:'jmh-generator-annprocess', version:'1.26'
    testAnnotationProcessor  group: 'org.openjdk.jmh', name:'jmh-generator-annprocess', version:'1.26'
```
*Note*: Until CyBench Gradle plugin and its dependencies are not released to Central Maven repository, must build `GoCypher CyBench Launch Gradle Plugin` locally or download the binaries from release and install it to local Maven repository. See sections [CyBench Gradle Plugin Build](#cybench-gradle-plugin-building) or [CyBench Gradle Plugin install](#cybench-gradle-plugin-install) for details.

##Configuration

Plugin is configurable inside cybenchJMH{} tag. Properties available for plugin behaviour configuration:

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
| **benchAccessToken** | By providing the "bench" token that you get after creating a workspace in CyBench UI, you can send reports to your private directory, which will be visible only to the users that you authorize. | - |
| **useCyBenchBenchmarkSettings**| Set if the CyBench provided or JMH default benchmarks settings should be used | true |
| **shouldFailBuildOnReportDeliveryFailure**| A flag which triggers build failure if the benchmark report was configured to be sent to CyBench but its delivery failed. |   false 

### Example of Full CyBench Gradle plugin configuration

```build.gradle
/* At the top of the build.gradle file */
buildscript {
    repositories {
        mavenLocal()
        dependencies {
            classpath 'com.gocypher.cybench:gocypher-cybench-launch-gradle-plugin:1.0.0'
        }
    }
}
/* below the dependencies tag inside the build.gradle */
apply plugin: 'gocypher-cybench-launch-gradle-plugin'

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
    reportsFolder = './reports/'
    reportName = 'My First Benchmark'
    userProperties ='project=My Benchmarks Project;'
    useCyBenchBenchmarkSettings = true
    skip = false
}
```
## CyBench Gradle Plugin Building

This step is required in order to use CyBench Gradle plugin if you want to build it from the source code to include latest changes.

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

## More information on benchmarking your code
* [CyBench Benchmark samples](https://github.com/K2NIO/gocypher-cybench-java-core/tree/main/gocypher-cybench-jvm/src/main/java/com/gocypher/cybench/jmh/jvm/client/tests)
* [Avoiding Benchmarking Pitfalls on the JVM](https://www.oracle.com/technical-resources/articles/java/architect-benchmarking.html#:~:text=JMH%20is%20a%20Java%20harness,to%20unwanted%20virtual%20machine%20optimizations)
* [JMH - Java Microbenchmark Harness](http://tutorials.jenkov.com/java-performance/jmh.html)
* [Java Benchmarks with JMH](https://medium.com/swlh/java-benchmarks-with-jmh-a-preamble-285510a77dd2)
* [Microbenchmarking with Java](https://www.baeldung.com/java-microbenchmark-harness)