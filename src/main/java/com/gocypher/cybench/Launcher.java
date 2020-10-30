package com.gocypher.cybench;

import com.gocypher.cybench.core.utils.IOUtils;
import com.gocypher.cybench.core.utils.JSONUtils;
import com.gocypher.cybench.launcher.environment.model.HardwareProperties;
import com.gocypher.cybench.launcher.environment.model.JVMProperties;
import com.gocypher.cybench.launcher.environment.services.CollectSystemInformation;
import com.gocypher.cybench.launcher.model.BenchmarkOverviewReport;
import com.gocypher.cybench.launcher.report.DeliveryService;
import com.gocypher.cybench.launcher.report.ReportingService;
import com.gocypher.cybench.launcher.utils.ComputationUtils;
import com.gocypher.cybench.launcher.utils.Constants;
import com.gocypher.cybench.launcher.utils.LauncherConfiguration;
import com.gocypher.cybench.launcher.utils.SecurityBuilder;
import com.jcabi.manifests.Manifests;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.HotspotRuntimeProfiler;
import org.openjdk.jmh.profile.HotspotThreadProfiler;
import org.openjdk.jmh.profile.SafepointsProfiler;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.CompilerHints;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class Launcher implements Plugin<Project> {
    private static final String JMH_RUNNER = "org.openjdk.jmh.Main";
    private static final String BENCHMARK_LIST_FILE = "/classes/java/main/META-INF/BenchmarkList";
    private static final String COMPILER_HINT_FILE = "/classes/java/main/META-INF/CompilerHints";

    @Override
    public void apply(Project project) {
        LauncherConfiguration configuration = project.getExtensions().create("cybenchJMH", LauncherConfiguration.class);
        SourceSet sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getAt("main");
        try {
            cybenchJMHReflectiveTask(project,sourceSets, configuration);
            project.getTasks().getByName("classes").finalizedBy("cybenchRun");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
//        project.task("testPlugin").doLast(task -> {
//            project.getLogger().lifecycle("Cybench test exec");
//        });
    }

    public void execute(String buildPath,  LauncherConfiguration configuration, Project project) throws MojoExecutionException {
        long start = System.currentTimeMillis();
       project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
       project.getLogger().lifecycle("                                 Starting CyBench benchmarks                             ");
       project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
        try {
            project.getLogger().lifecycle("Collecting hardware, software information...");
            HardwareProperties hwProperties = CollectSystemInformation.getEnvironmentProperties(project);
            project.getLogger().lifecycle("Collecting JVM properties...");
            JVMProperties jvmProperties = CollectSystemInformation.getJavaVirtualMachineProperties(project);

            //FIXME generate security hashes for report classes found on the classpath
            SecurityBuilder securityBuilder = new SecurityBuilder();
            Map<String, Object> benchmarkSettings = new HashMap<>();
            Map<String, Map<String, String>> customBenchmarksMetadata = ComputationUtils.parseCustomBenchmarkMetadata(configuration.getUserProperties());

            this.checkAndConfigureCustomProperties(securityBuilder,benchmarkSettings,customBenchmarksMetadata, project) ;
            benchmarkSettings.put("benchThreadCount", configuration.getThreads());
            benchmarkSettings.put("benchReportName", configuration.getReportName());

           project.getLogger().lifecycle("Executing benchmarks...");

            OptionsBuilder optBuild = new OptionsBuilder();
            Options opt = optBuild.forks(configuration.getForks())
                    .measurementIterations(configuration.getMeasurementIterations())
                    .warmupIterations(configuration.getWarmUpIterations())
                    .warmupTime(TimeValue.seconds(configuration.getWarmUpSeconds()))
                    .threads(configuration.getThreads())
                    .shouldDoGC(true)
                    .addProfiler(GCProfiler.class)
                    .addProfiler(HotspotThreadProfiler.class)
                    .addProfiler(HotspotRuntimeProfiler.class)
                    .addProfiler(SafepointsProfiler.class)
                    .detectJvmArgs()
                    .build();

            Runner runner = new Runner(opt);

            BenchmarkList benchmarkList = BenchmarkList.fromFile(buildPath+BENCHMARK_LIST_FILE);
            CompilerHints compilerHints = CompilerHints.fromFile(buildPath+COMPILER_HINT_FILE);
            UpdateFieldViaReflection(runner.getClass(), runner, "list", benchmarkList);
            UpdateFieldViaReflection(CompilerHints.class, compilerHints, "defaultList", compilerHints);

            Collection<RunResult> results = runner.run();

            BenchmarkOverviewReport report = ReportingService.getInstance().createBenchmarkReport(results, customBenchmarksMetadata, project);
            report.updateUploadStatus(configuration.getReportUploadStatus());

            report.getEnvironmentSettings().put("environment", hwProperties);
            report.getEnvironmentSettings().put("jvmEnvironment", jvmProperties);
            report.getEnvironmentSettings().put("unclassifiedProperties", CollectSystemInformation.getUnclassifiedProperties(project));
            report.getEnvironmentSettings().put("userDefinedProperties", customUserDefinedProperties(configuration.getUserProperties()));
            report.setBenchmarkSettings(benchmarkSettings);

            //FIXME add all missing custom properties including public/private flag

           project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
           project.getLogger().lifecycle("      Report score - "+report.getTotalScore());
           project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
            String reportJSON = JSONUtils.marshalToPrettyJson(report);
           project.getLogger().lifecycle(reportJSON) ;
            if (configuration.getExpectedScore() > 0 ) {
                if (report.getTotalScore().doubleValue() < configuration.getExpectedScore()) {
                    throw new MojoFailureException("CyBench score is less than expected:" + report.getTotalScore().doubleValue() + " < "+configuration.getExpectedScore());
                }
            }

            String reportEncrypted = ReportingService.getInstance().prepareReportForDelivery(securityBuilder, report);

            String responseWithUrl = null;
            if (report.isEligibleForStoringExternally() && configuration.isShouldSendReportToCyBench()) {
                responseWithUrl = DeliveryService.getInstance().sendReportForStoring(reportEncrypted);
                report.setReportURL(responseWithUrl);
            } else {
               project.getLogger().lifecycle("You may submit your report '"+ IOUtils.getReportsPath(configuration.getReportsFolder(), Constants.CYB_REPORT_CYB_FILE)+"' manually at "+ Constants.CYB_UPLOAD_URL);
            }
            if (configuration.isShouldStoreReportToFileSystem()) {
               project.getLogger().lifecycle("Saving test results to '" + IOUtils.getReportsPath(configuration.getReportsFolder(), Constants.CYB_REPORT_JSON_FILE) + "'");
                IOUtils.storeResultsToFile(IOUtils.getReportsPath(configuration.getReportsFolder(), Constants.CYB_REPORT_JSON_FILE), reportJSON);
               project.getLogger().lifecycle("Saving encrypted test results to '" + IOUtils.getReportsPath(configuration.getReportsFolder(), Constants.CYB_REPORT_CYB_FILE) + "'");
                IOUtils.storeResultsToFile(IOUtils.getReportsPath(configuration.getReportsFolder(), Constants.CYB_REPORT_CYB_FILE), reportEncrypted);
            }
           project.getLogger().lifecycle("Removing all temporary auto-generated files....");
            IOUtils.removeTestDataFiles();
           project.getLogger().lifecycle("Removed all temporary auto-generated files!!!");



        }catch (Throwable t){
            if (t.getMessage() != null && t.getMessage().contains("/META-INF/BenchmarkList")) {
               project.getLogger().warn("-------------------No benchmark tests found-------------------");
            }
            else {
                throw new MojoExecutionException("Error during benchmarks run", t);
            }
        }
       project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
       project.getLogger().lifecycle("         Finished CyBench benchmarking ("+ ComputationUtils.formatInterval(System.currentTimeMillis() - start) +")");
       project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
    }
    public static void addToClasspath(File file) {
        try {
            URL url = file.toURI().toURL();
            URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(classLoader, url);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }
    private void checkAndConfigureCustomProperties (SecurityBuilder securityBuilder
            ,Map<String,Object>benchmarkSettings
            ,Map<String,Map<String,String>>customBenchmarksMetadata, Project project){

        Reflections reflections = new Reflections("com.gocypher.cybench.", new SubTypesScanner(false));
        Set<Class<? extends Object>> allDefaultClasses = reflections.getSubTypesOf(Object.class);
        String tempBenchmark = null;
        for (Class<? extends Object> classObj : allDefaultClasses) {
            if (!classObj.getName().isEmpty() && classObj.getSimpleName().contains("Benchmarks")
                    && !classObj.getSimpleName().contains("_")) {
                // optBuild.include(classObj.getName());
                tempBenchmark = classObj.getName();
                securityBuilder.generateSecurityHashForClasses(classObj);
            }
        }
        if (tempBenchmark != null) {
           project.getLogger().lifecycle("tempBenchmark != null");
            String manifestData = null;
            if (Manifests.exists("customBenchmarkMetadata")) {
                manifestData = Manifests.read("customBenchmarkMetadata");
            }
            Map<String, Map<String, String>> benchmarksMetadata = ComputationUtils.parseCustomBenchmarkMetadata(manifestData);
            Map<String, String> benchProps;
            if (manifestData != null) {
                benchProps = ReportingService.getInstance().prepareBenchmarkSettings(tempBenchmark, benchmarksMetadata);
            } else {
               project.getLogger().lifecycle("manifestData != null");
                benchProps = ReportingService.getInstance().prepareBenchmarkSettings(tempBenchmark, customBenchmarksMetadata);
            }
            benchmarkSettings.putAll(benchProps);
        } else{
            benchmarkSettings.putIfAbsent("benchContext", "Custom");
            benchmarkSettings.putIfAbsent("benchVersion", "Other");
        }

    }
    public void cybenchJMHReflectiveTask(Project project, SourceSet sourceSets, LauncherConfiguration configuration) throws MalformedURLException {
        StringBuilder classpath = new StringBuilder();
        String buildPath = String.valueOf(project.getBuildDir());
        FileCollection test = sourceSets.getRuntimeClasspath();
        classpath.append(test.getAsPath()).append(";").append(buildPath);
        System.setProperty("java.class.path",classpath.toString());
        project.task("cybenchRun").doLast( task -> {
//            System.getProperty("skipCybench")  use -DskipCybench
//            project.getLogger().lifecycle("System.getProperty(\"skipCybench\") " + System.getProperty("skipCybench") );
            if(!configuration.isSkip() && System.getProperty("skipCybench") == null ) {
                try {
                    execute(buildPath, configuration, project);
                } catch (MojoExecutionException e) {
                    e.printStackTrace();
                }
            }else {
                project.getLogger().lifecycle("Skipping CyBench execution");
            }
        });

    }
    public void cybenchTaskWithDirectJMHTest(Project project, SourceSet sourceSets) throws MalformedURLException {
        StringBuilder classpath = new StringBuilder();
        String buildPath = String.valueOf(project.getBuildDir());
        FileCollection test = sourceSets.getRuntimeClasspath();
        classpath.append(test.getAsPath());
        classpath.append(";"+buildPath);
        System.setProperty("java.class.path",classpath.toString());
        project.task("testTask").doLast( task -> {
            int forks = 1;
            int measurementIterations = 1;
            int warmUpIterations = 1;
            int warmUpSeconds = 5;
            int threads = 1;
            OptionsBuilder optBuild = new OptionsBuilder();
            Options opt = optBuild
                    .forks(forks)
                    .jvmArgs("-Xms4096m -Xmx4096m")
                    .measurementIterations(measurementIterations)
                    .warmupIterations(warmUpIterations)
                    .warmupTime(TimeValue.seconds(warmUpSeconds))
                    .threads(threads)
                    .shouldDoGC(true)
                    .detectJvmArgs()
                    .build();
            Runner runner = new Runner(opt);
            BenchmarkList benchmarkList = BenchmarkList.fromFile(buildPath+"/classes/java/main/META-INF/BenchmarkList");
            CompilerHints compilerHints = CompilerHints.fromFile(buildPath+"/classes/java/main/META-INF/CompilerHints");
            UpdateFieldViaReflection(runner.getClass(), runner, "list", benchmarkList);
            UpdateFieldViaReflection(CompilerHints.class, compilerHints, "defaultList", compilerHints);

            Collection<RunResult> results = null;
            try {
                results = runner.run();
               project.getLogger().lifecycle("benchmarkList: " +   benchmarkList.toString());
            } catch (RunnerException e) {
                e.printStackTrace();
            }
           project.getLogger().lifecycle("Cybench launch result items:" + results.size());
        });

    }
    private static Map<String, Object> customUserDefinedProperties(String customPropertiesStr) {
        Map<String, Object> customUserProperties = new HashMap<>();
        if (customPropertiesStr != null && !customPropertiesStr.isEmpty()){
            String [] pairs = customPropertiesStr.split(";") ;
            for (String pair:pairs){
                String [] kv = pair.split("=");
                if (kv.length == 2){
                    customUserProperties.put(kv[0],kv[1]) ;
                }
            }
        }


        return customUserProperties;
    }

    private void UpdateFieldViaReflection(Class<?> clazz, Object target, String fieldName, Object value) {
        try {
            Field listField = clazz.getDeclaredField(fieldName);
            makeWriteable(listField);
            listField.set(target, value);
        } catch (Exception e) {
            throw new GradleException(
                    "Error while instantiating tests: unable to set '" + fieldName + "' on " + clazz.getSimpleName() +
                            ". This plugin version doesn't seem to be compatible with JMH " + 1.26 +
                            ". Please report to the plugin authors at https://github.com/melix/jmh-gradle-plugin/.", e);
        }
    }
    private static void makeWriteable(Field listField) throws NoSuchFieldException, IllegalAccessException {
        listField.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(listField, listField.getModifiers() & ~Modifier.FINAL);
    }
    public void cybenchTaskWithDirectJMH(Project project, SourceSet sourceSets) {
        project.getTasks().create("cybench", task -> {
            task.doLast(it -> project.javaexec(javaExecSpec -> {
                javaExecSpec.setClasspath(sourceSets.getRuntimeClasspath());
                javaExecSpec.setMain(JMH_RUNNER);
                javaExecSpec.setMaxHeapSize("4096m");
               project.getLogger().lifecycle("CLASSPATH: " +   System.getProperty("java.class.path"));
                javaExecSpec.setWorkingDir(new File("C:/streams/k2/development/k2-benchmarks/test/build/classes/java/main"));
                ArrayList<String> toJmhRunner = new ArrayList<>();
                toJmhRunner.add("-i");
                toJmhRunner.add("2");
                toJmhRunner.add("-f");
                toJmhRunner.add("2");
                toJmhRunner.add("-wf");
                toJmhRunner.add("2");
                toJmhRunner.add("-wi");
                toJmhRunner.add("2");
                toJmhRunner.add("-rf");
                toJmhRunner.add("json");
                javaExecSpec.setArgs(toJmhRunner);
            }));
        });
    }
//            ClassLoader currentThreadClassLoader = Thread.currentThread().getContextClassLoader();
//            URLClassLoader urlClassLoader = null;
//            try {
//                urlClassLoader = new URLClassLoader(new URL[]{new File(classPath).toURI().toURL()}, currentThreadClassLoader);
//            } catch (MalformedURLException e) {
//                e.printStackTrace();
//            }
//            Thread.currentThread().setContextClassLoader(urlClassLoader);
//        --------------------------------------------------------------------------------------------------------
//    public static void main(String[] args) throws Exception {
//        int forks = 1;
//        int measurementIterations = 1;
//        int warmUpIterations = 1;
//        int warmUpSeconds = 5;
//        int threads = 1;
//        OptionsBuilder optBuild = new OptionsBuilder();
//        Options opt = optBuild
//                .forks(forks)
//                .measurementIterations(measurementIterations)
//                .warmupIterations(warmUpIterations)
//                .warmupTime(TimeValue.seconds(warmUpSeconds))
//                .threads(threads)
//                .shouldDoGC(true)
//                .detectJvmArgs()
//                .build();
//        Runner runner = new Runner(opt);
//        Collection<RunResult> results = null;
//        try {
//            results = runner.run();
//        } catch (RunnerException e) {
//            e.printStackTrace();
//        }
//       project.getLogger().lifecycle("Cybench launch result items:" + results.size());
//    }
//        --------------------------------------------------------------------------------------------------------
//    private void configureJMHBenchmarkLocation(JavaPluginConvention pluginConvention) {
//        SourceSet benchmarkSourceSet = pluginConvention.getSourceSets().create(BENCHMARK_SOURCESET_NAME);
//        SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets();
//        SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
//        SourceSet testSourceSet = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
//
//        ConfigurationContainer configurations = project.getConfigurations();
//        benchmarkSourceSet.setCompileClasspath(project.files(mainSourceSet.getOutput(), testSourceSet.getOutput(),
//                configurations.getByName(COMPILE_BENCHMARK_NAME)));
//        benchmarkSourceSet.setRuntimeClasspath(project.files(mainSourceSet.getOutput(), testSourceSet.getOutput(),
//                benchmarkSourceSet.getOutput(), configurations.getByName(RUNTIME_BENCHMARK_NAME)));
//    }
//        --------------------------------------------------------------------------------------------------------
//
//    private void configureConfigurations() {
//        project.getRepositories().add(project.getRepositories().mavenCentral());
//
//        ConfigurationContainer configurations = project.getConfigurations();
//        Configuration benchmarkCompile = configurations.getByName("benchmarkCompile");
//        Configuration jmh = configurations.create(JMH_CONFIGURATION_NAME);
//
//        DependencyHandler dependencies = project.getDependencies();
//        dependencies.add(JMH_CONFIGURATION_NAME, "org.openjdk.jmh:jmh-core:" + JMH_VERSION);
//        dependencies.add(JMH_CONFIGURATION_NAME, "org.openjdk.jmh:jmh-generator-annprocess:" + JMH_VERSION);
//
//        benchmarkCompile.extendsFrom(jmh , configurations.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME));
//    }

//        --------------------------------------------------------------------------------------------------------
//    private void defineBenchmarkJmhTask() {
//        Task benchmarkJmhTask = project.getTasks().create(BENCHMARK_JMH_TASK_NAME, BenchmarkJmhTask.class);
//        benchmarkJmhTask.setGroup(GRADLE_TASK_GROUP);
//        benchmarkJmhTask.setDescription("Runs JMH benchmark tasks");
//        benchmarkJmhTask.dependsOn(project.getTasks().getByName("compileJava"));
//        benchmarkJmhTask.dependsOn(project.getTasks().getByName("processBenchmarkResources"));
//        benchmarkJmhTask.dependsOn(project.getTasks().getByName("compileBenchmarkJava"));
//    }

//        --------------------------------------------------------------------------------------------------------
    /*
     * Add IDE support for benchmarks in test scopes if the IntelliJ or Eclipse plugins are available.
     */
//    private void configureIDESupport(final JavaPluginConvention javaPluginConvention) {
//        final ConfigurationContainer configurations = project.getConfigurations();
//        final PluginContainer plugins = project.getPlugins();
//
//        project.afterEvaluate(new Action<Project>() {
//            @Override
//            public void execute(Project project) {
//                if (plugins.hasPlugin(EclipsePlugin.class)) {
//                    EclipsePlugin eclipsePlugin = plugins.getPlugin(EclipsePlugin.class);
//                    EclipseClasspath eclipseClasspath = eclipsePlugin.getModel().getClasspath();
//                    eclipseClasspath.getPlusConfigurations().add(configurations.getByName(COMPILE_BENCHMARK_NAME));
//                    eclipseClasspath.getPlusConfigurations().add(configurations.getByName(RUNTIME_BENCHMARK_NAME));
//                    eclipsePlugin.getModel().setClasspath(eclipseClasspath);
//                }
//
//                if (plugins.hasPlugin(IdeaPlugin.class)) {
//                    IdeaPlugin ideaPlugin = plugins.getPlugin(IdeaPlugin.class);
//                    IdeaModule ideaModule = ideaPlugin.getModel().getModule();
//                    SourceSet benchmarkSourceSet = javaPluginConvention.getSourceSets().getByName(BENCHMARK_SOURCESET_NAME);
//
//                    Set<File> testSourceDirs = ideaModule.getTestSourceDirs();
//                    testSourceDirs.addAll(benchmarkSourceSet.getAllJava().getSrcDirs());
//                    testSourceDirs.addAll(benchmarkSourceSet.getResources().getSrcDirs());
//                    ideaModule.setTestSourceDirs(testSourceDirs);
//                    Collection<Configuration> testPlusScope = ideaModule.getScopes().get("TEST").get("plus");
//                    testPlusScope.add(configurations.getByName(COMPILE_BENCHMARK_NAME));
//                    testPlusScope.add(configurations.getByName(RUNTIME_BENCHMARK_NAME));
//                }
//            }
//        });
//    }

//        --------------------------------------------------------------------------------------------------------

    //        project.javaexec(javaExecSpec ->{
//            javaExecSpec.setClasspath(sourceSets.getRuntimeClasspath());
//            javaExecSpec.setMain(JMH_RUNNER);
//        });

//
//        --------------------------------------------------------------------------------------------------------
//        project.task("cybenchTest").doLast( task -> {
//           project.getLogger().lifecycle("Cybench test exec");
//            int forks = 1;
//            int measurementIterations = 1;
//            int warmUpIterations = 1;
//            int warmUpSeconds = 5;
//            int threads = 1;
//            OptionsBuilder optBuild = new OptionsBuilder();
//            Options opt = optBuild
//                .forks(forks)
//                .measurementIterations(measurementIterations)
//                .warmupIterations(warmUpIterations)
//                .warmupTime(TimeValue.seconds(warmUpSeconds))
//                .threads(threads)
//                .shouldDoGC(true)
//                .detectJvmArgs()
//                .build();
//            Runner runner = new Runner(opt);
//            Collection<RunResult> results = null;
//            javaExecSpec.setMain(JMH_RUNNER);
//            JavaPluginConvention jpc = project.getConvention().getPlugin(JavaPluginConvention.class);
//            SortedMap<String, SourceSet> fcClasspath = jpc.getSourceSets().getAsMap();
//            for (String set : fcClasspath.keySet()) {
//                FileCollection collection =  fcClasspath.get(set).getRuntimeClasspath();
//                javaExecSpec.setClasspath(collection);
//            }
//            try {
//                results = runner.run();
//            } catch (RunnerException e) {
//                e.printStackTrace();
//            }
//           project.getLogger().lifecycle("Cybench launch result items:" + results.size());
//        });

//        --------------------------------------------------------------------------------------------------------
}
