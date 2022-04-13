# GoCypher CyBench Gradle plugin

[CyBench](https://cybench.io) Gradle plugin allows running of CyBench benchmark tests, generating reports and sending it
to CyBench website during software build process. Plugin is easy to attach to the project build.gradle file and can be
used in continuous integration (CI) systems.

CyBench Gradle plugin executes all classes that use JMH framework for benchmark implementation and creates a report to
specified location at the end of benchmarking process.

As CyBench report contains total score, it is possible to configure build failure if score does not pass the pre-defined
threshold.

**Notice:** Benchmarks are running on the server where software build is preformed, so the builds machine must have
enough HW resources for a successful and stable benchmarking of software items, and the benchmark results may wary on
different machines.

### Start using CyBench Gradle plugin

Include dependency to CyBench Gradle plugin in your project `build.gradle` file as described
in [the chapters below](#cybench-gradle-plugin-integration-and-usage) and start using it.

## CyBench Gradle plugin integration and usage

The simplest configuration for the plugin requires:

```groovy
/* At the top of the build.gradle file */
buildscript {
    repositories {
        maven {
            url "https://repo1.maven.org/maven2"
        }
    }
    dependencies {
        classpath "com.gocypher.cybench.launcher.plugin:cybench-launcher-gradle-plugin:1.0.3"
    }
}
/* below the dependencies tag inside the build.gradle */
apply plugin: 'cybench-launcher-gradle-plugin'
```

**Prerequisites**

* A project must have `dependencies` to JMH framework and contain classes which implements benchmarks using JMH
  framework.

  ```groovy
  implementation 'org.openjdk.jmh:jmh-core:1.34'
  annotationProcessor 'org.openjdk.jmh:jmh-generator-annprocess:1.34'
  testAnnotationProcessor 'org.openjdk.jmh:jmh-generator-annprocess:1.34'
  ```

* Project must also have a project.properties file in order for CyBench runner to correctly generate metadata associated
  with your benchmarks, adding this task will be enough

  * Groovy:

    ```groovy
    ant.mkdir(dir: "${projectDir}/config/")
    ant.propertyfile(file: "${projectDir}/config/project.properties") {
        entry(key: "PROJECT_ARTIFACT", value: project.name)
        entry(key: "PROJECT_ROOT", value: project.rootProject)
        entry(key: "PROJECT_VERSION", value: project.version)
        entry(key: "PROJECT_PARENT", value: project.parent)
        entry(key: "PROJECT_BUILD_DATE", value: new Date())
        entry(key: "PROJECT_GROUP", value: project.group)
    }
    ```

  * Kotlin:

    ```kotlin
    ant.withGroovyBuilder {
        "mkdir"("dir" to "${projectDir}/config/")
        "propertyfile"("file" to "$projectDir/config/project.properties") {
            "entry"("key" to "PROJECT_ARTIFACT", "value" to project.name)
            "entry"("key" to "PROJECT_ROOT", "value" to project.rootProject)
            "entry"("key" to "PROJECT_VERSION", "value" to project.version)
            "entry"("key" to "PROJECT_PARENT", "value" to project.parent)
            "entry"("key" to "PROJECT_GROUP", "value" to project.group)
        }
    }
    ```

## Configuration

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
| **benchQueryToken** | By providing the "query" token that you get after creating a workspace in CyBench UI, you can run and send automated comparisons within your project to your private directory, which will be visible only to the users that you authorize. | - |
| **useCyBenchBenchmarkSettings**| Set if the CyBench provided or JMH default benchmarks settings should be used | true |
| **email** | Email property is used to identify report sender while sending reports to both private and public repositories | - |
| **shouldFailBuildOnReportDeliveryFailure**| A flag which triggers build failure if the benchmark report was configured to be sent to CyBench but its delivery failed. |   false  |

You can also add a configuration for automated performance regression testing, which will run with every single
benchmark report. This is configurable inside the `cybenchAutomation{}` tag. 
  
**NOTE** In order to run automated comparisons, you must add the **benchQueryToken** to the `cybenchJMH{}` configuration.

| Property name        | Description           | Options  |
| ------------- |-------------| -----:|
| **scope** | Choose between comparing within current version, or between previous versions. When using `BETWEEN`, a specific version must be specified with the property `compareVersion`. | `WITHIN` or `BETWEEN` |
| **compareVersion** | Used for `BETWEEN` version comparisons. | Any project version you have previously tested |
| **numLatestReports** | How many reports do you want to compare against? 1 will compare this report against the most recent report in the version you are comparing against. # > 1 will compare this report against the average of the scores of the most recent # reports in the version you are comparing against. | Number >= 1 |
| **anomaliesAllowed** | How many anomalies do you want to allow? If the number of benchmark anomalies surpasses your specified number, CyBench benchmark runner will fail... triggering your CI/CD pipeline to halt. | Number >= 0 |
| **method** | Decide which method of comparison to use. `DELTA` will compare difference in score, and requires an additional property, `threshold`. `SD` will do comparisons regarding standard deviation. `SD` requires an additional property as well, `deviationsAllowed`. | `DELTA` or `SD` |
| **threshold** | Only used with the `DELTA` method. `GREATER` will compare raw scores, `PERCENT_CHANGE` is used to measure the percent change of the score in comparison to previous scores. `PERCENT_CHANGE` requires an additional property: `percentChangeAllowed`. | `GREATER` or `PERCENT_CHANGE` |
| **percentChangeAllowed** | This argument is used when running assertions, makes sure your new score is within X percent of the previous scores you're comparing to. | Any Double value. |
| **deviationsAllowed** | Used with assertions to check that the new score is within the given amount of deviations from the mean. (mean being calculated from the scores being compared to). | Any Double value. |

### Example of Full CyBench Gradle plugin configuration

```groovy
/* At the top of the build.gradle file */
buildscript {
    repositories {
        maven {
            url "https://repo1.maven.org/maven2"
        }
    }
    dependencies {
        classpath "com.gocypher.cybench.launcher.plugin:cybench-launcher-gradle-plugin:1.0.3"
    }
}

plugins {
    id 'java'
}

group 'org.example'
version '1.0-SNAPSHOT'

repositories {
    mavenCentral()
}

dependencies {
    testImplementation 'junit:junit:4.13.2'
    implementation 'com.gocypher.cybench.client:gocypher-cybench-annotations:1.3.3'
    annotationProcessor 'com.gocypher.cybench.client:gocypher-cybench-annotations:1.3.3'
    // if benchmarks are in test directory
    testAnnotationProcessor 'com.gocypher.cybench.client:gocypher-cybench-annotations:1.3.3'

    implementation 'org.openjdk.jmh:jmh-core:1.34'
    annotationProcessor 'org.openjdk.jmh:jmh-generator-annprocess:1.34'
    // if benchmarks are in test directory
    testAnnotationProcessor 'org.openjdk.jmh:jmh-generator-annprocess:1.34'
}

/* below the dependencies tag inside the build.gradle */
apply plugin: 'cybench-launcher-gradle-plugin'

/* Configuration properties that could be provided to the plugin */
cybenchJMH {
    forks = 1
    threads = 1
    measurementIterations = 5
    measurementSeconds = 5
    warmUpIterations = 1
    warmUpSeconds = 5
    shouldSendReportToCyBench = false
    reportsFolder = './reports/'
    reportName = 'My Report'
    userProperties = 'library=My Library;'
}

cybenchAutomation {
    scope = "BETWEEN"
    compareVersion = "2.0"
    numLatestReports = 1
    anomaliesAllowed = 1
    method = "SD"
    deviationsAllowed = 2
}

ant.mkdir(dir: "${projectDir}/config/")
ant.propertyfile(file: "${projectDir}/config/project.properties") {
    entry(key: "PROJECT_ARTIFACT", value: project.name)
    entry(key: "PROJECT_ROOT", value: project.rootProject)
    entry(key: "PROJECT_VERSION", value: project.version)
    entry(key: "PROJECT_PARENT", value: project.parent)
    entry(key: "PROJECT_BUILD_DATE", value: new Date())
    entry(key: "PROJECT_GROUP", value: project.group)
}
```

#### Optional: gocypher-cybench-annotation For adding custom benchmark annotations @BenchmarkTag

Install annotation binaries into your local Maven repository

```sh
mvn install:install-file -Dfile=gocypher-cybench-annotations-1.3.3.jar -DgroupId=com.gocypher.cybench.client -DartifactId=gocypher-cybench-annotations -Dversion=1.3.3 -Dpackaging=jar
```

Include dependency to annotation processor in your project `build.gradle` file.

```groovy
implementation 'com.gocypher.cybench.client:gocypher-cybench-annotations:1.3.3'
```

**Notice:** if you want for the tags to be auto generated on your projects you should add annotationProcessor or
testAnnotationProcessor. First launch will generate the annotations for benchmarks and fail build.

```groovy
annotationProcessor 'com.gocypher.cybench.client:gocypher-cybench-annotations:1.3.3'
testAnnotationProcessor 'com.gocypher.cybench.client:gocypher-cybench-annotations:1.3.3'
```

## More information on benchmarking your code

* [CyBench Benchmark samples](https://github.com/K2NIO/cybench-java-benchmarks)
* [Avoiding Benchmarking Pitfalls on the JVM](https://www.oracle.com/technical-resources/articles/java/architect-benchmarking.html#:~:text=JMH%20is%20a%20Java%20harness,to%20unwanted%20virtual%20machine%20optimizations)
* [JMH - Java Microbenchmark Harness](http://tutorials.jenkov.com/java-performance/jmh.html)
* [Java Benchmarks with JMH](https://medium.com/swlh/java-benchmarks-with-jmh-a-preamble-285510a77dd2)
* [Microbenchmarking with Java](https://www.baeldung.com/java-microbenchmark-harness)

## CyBench Gradle Plugin Building

This step is required in order to use CyBench Gradle plugin if you want to build it from the source code to include the
latest changes.

#### Build gocypher-cybench-runner project

* Clone [GitHub repository](https://github.com/K2NIO/gocypher-cybench-java) to local machine.
* Navigate to directory `gocypher-cybench-client`.
* Run command from the command line

```sh
     mvn clean install
```

#### Build  gocypher-cybench-gradle project

* Clone [GitHub repository](https://github.com/K2NIO/gocypher-cybench-gradle) to local machine.
* Navigate to directory `cybench-launch-gradle-plugin`.
* Run command from the command line

```sh
     gradle publishToMavenLocal
```

**Notice:** After successful run project JAR's are installed to local Maven repository.
