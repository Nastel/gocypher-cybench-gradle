/*
 * Copyright (C) 2020, K2N.IO.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package com.gocypher.cybench;

import com.gocypher.cybench.core.annotation.BenchmarkTag;
import com.gocypher.cybench.core.utils.IOUtils;
import com.gocypher.cybench.core.utils.JMHUtils;
import com.gocypher.cybench.core.utils.JSONUtils;
import com.gocypher.cybench.launcher.environment.model.HardwareProperties;
import com.gocypher.cybench.launcher.environment.model.JVMProperties;
import com.gocypher.cybench.launcher.environment.services.CollectSystemInformation;
import com.gocypher.cybench.launcher.model.BenchmarkOverviewReport;
import com.gocypher.cybench.launcher.model.BenchmarkReport;
import com.gocypher.cybench.launcher.report.DeliveryService;
import com.gocypher.cybench.launcher.report.ReportingService;
import com.gocypher.cybench.launcher.utils.ComputationUtils;
import com.gocypher.cybench.launcher.utils.Constants;
import com.gocypher.cybench.launcher.utils.SecurityBuilder;
import com.gocypher.cybench.utils.LauncherConfiguration;
import com.sun.org.apache.bcel.internal.Repository;
import com.sun.org.apache.bcel.internal.classfile.JavaClass;
import com.sun.org.apache.bcel.internal.classfile.Method;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.Classpath;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.HotspotRuntimeProfiler;
import org.openjdk.jmh.profile.HotspotThreadProfiler;
import org.openjdk.jmh.profile.SafepointsProfiler;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.BenchmarkListEntry;
import org.openjdk.jmh.runner.CompilerHints;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Launcher implements Plugin<Project> {
    private static final String BENCHMARK_LIST_FILE = "\\META-INF\\BenchmarkList";
    private static final String COMPILER_HINT_FILE = "\\META-INF\\CompilerHints";
    private static final String MAIN_SOURCE_ROOT = "\\classes\\java\\main";
    private static final String TEST_SOURCE_ROOT = "\\classes\\java\\test";
    private static final  String BENCH_SOURCE = "Gradle plugin";


    @Override
    public void apply(Project project) {
        LauncherConfiguration configuration = project.getExtensions().create("cybenchJMH", LauncherConfiguration.class);
        SourceSet sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getAt("main");
//        ConfigurableFileCollection classpath = project.files();
//        project
//                .getTasksByName("compileJava", true)
//                .forEach(task -> classpath = Classpath.plus(((JavaCompile)task).getClasspath()));
//        classpath = Classpath.plus(project.getTasks().getByName("jar").getOutputs().getFiles());
        try {
            cybenchJMHReflectiveTask(project,sourceSets, configuration);
            project.getTasks().getByName("testClasses").finalizedBy("cybenchRun");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void execute(String buildPath,  LauncherConfiguration configuration, Project project) throws GradleException {
        long start = System.currentTimeMillis();
        project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
        project.getLogger().lifecycle("                                 Starting CyBench benchmarks                             ");
        project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
        System.setProperty("collectHw", "true");
        boolean isReportSentSuccessFully = false ;
        try {
            project.getLogger().lifecycle("Collecting hardware, software information...");
            HardwareProperties hwProperties = CollectSystemInformation.getEnvironmentProperties();
            project.getLogger().lifecycle("Collecting JVM properties...");
            JVMProperties jvmProperties = CollectSystemInformation.getJavaVirtualMachineProperties();

            SecurityBuilder securityBuilder = new SecurityBuilder();
            Map<String, Object> benchmarkSettings = new HashMap<>();
            Map<String, Map<String, String>> customBenchmarksMetadata = ComputationUtils.parseBenchmarkMetadata(configuration.getUserProperties());

            benchmarkSettings.put("benchSource", BENCH_SOURCE);
            benchmarkSettings.put("benchWarmUpIteration",configuration.getWarmUpIterations());
            benchmarkSettings.put("benchWarmUpSeconds", configuration.getWarmUpSeconds());
            benchmarkSettings.put("benchMeasurementIteration", configuration.getMeasurementIterations());
            benchmarkSettings.put("benchMeasurementSeconds", configuration.getMeasurementSeconds());
            benchmarkSettings.put("benchForkCount", configuration.getForks());

            benchmarkSettings.put("benchThreadCount", configuration.getThreads());
            benchmarkSettings.put("benchReportName", configuration.getReportName());

           project.getLogger().lifecycle("Executing benchmarks...");

            OptionsBuilder optBuild = new OptionsBuilder();
            Options opt;
            if(configuration.isUseCyBenchBenchmarkSettings()) {
                opt = optBuild
                        .forks(configuration.getForks())
                        .measurementIterations(configuration.getMeasurementIterations())
                        .measurementTime(TimeValue.seconds(configuration.getMeasurementSeconds()))
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
            }else{
                opt = optBuild.shouldDoGC(true)
                        .addProfiler(GCProfiler.class)
                        .addProfiler(HotspotThreadProfiler.class)
                        .addProfiler(HotspotRuntimeProfiler.class)
                        .addProfiler(SafepointsProfiler.class)
                        .detectJvmArgs()
                        .build();
            }
            Runner runner = new Runner(opt);
            BenchmarkList benchmarkList;
            CompilerHints compilerHints;
            File benchmarkListFile = new File(buildPath + TEST_SOURCE_ROOT + BENCHMARK_LIST_FILE);
            File compilerHintFile = new File(buildPath + TEST_SOURCE_ROOT + COMPILER_HINT_FILE);
            if(benchmarkListFile.exists() && compilerHintFile.exists()) {
                benchmarkList = BenchmarkList.fromFile(buildPath + TEST_SOURCE_ROOT +  BENCHMARK_LIST_FILE);
                compilerHints = CompilerHints.fromFile(buildPath + TEST_SOURCE_ROOT+  COMPILER_HINT_FILE);
            }else{
                benchmarkList = BenchmarkList.fromFile(buildPath + MAIN_SOURCE_ROOT +  BENCHMARK_LIST_FILE);
                compilerHints = CompilerHints.fromFile(buildPath + MAIN_SOURCE_ROOT +  COMPILER_HINT_FILE);
            }
            UpdateFieldViaReflection(runner, "list", runner.getClass(), benchmarkList);
            UpdateFieldViaReflection(compilerHints, "defaultList", CompilerHints.class, compilerHints);


            Map<String, String> generatedFingerprints = new HashMap<>();
            Map<String, String> manualFingerprints = new HashMap<>();
            Map<String, String> classFingerprints = new HashMap<>();
            fingerprintAndHashGeneration(project, benchmarkList, generatedFingerprints, manualFingerprints, classFingerprints);

            Collection<RunResult> results = runner.run();
            BenchmarkOverviewReport report = ReportingService.getInstance().createBenchmarkReport(results, customBenchmarksMetadata);
            report.updateUploadStatus(configuration.getReportUploadStatus());

            report.getEnvironmentSettings().put("environment", hwProperties);
            report.getEnvironmentSettings().put("jvmEnvironment", jvmProperties);
            report.getEnvironmentSettings().put("unclassifiedProperties", CollectSystemInformation.getUnclassifiedProperties());
            report.getEnvironmentSettings().put("userDefinedProperties", customUserDefinedProperties(configuration.getUserProperties()));
            report.setBenchmarkSettings(benchmarkSettings);

            for (String s : report.getBenchmarks().keySet()) {
                List<BenchmarkReport> custom = report.getBenchmarks().get(s).stream().collect(Collectors.toList());
                custom.stream().forEach(benchmarkReport -> {
                    String name = benchmarkReport.getName();
                    benchmarkReport.setClassFingerprint(classFingerprints.get(name));
                    benchmarkReport.setGeneratedFingerprint(generatedFingerprints.get(name));
                    benchmarkReport.setManualFingerprint(manualFingerprints.get(name));

                });
            }

           project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
           project.getLogger().lifecycle("      Report score - "+report.getTotalScore());
           project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
            String reportJSON = JSONUtils.marshalToPrettyJson(report);
            if (configuration.getExpectedScore() > 0 ) {
                if (report.getTotalScore().doubleValue() < configuration.getExpectedScore()) {
                    throw new GradleException("CyBench score is less than expected:" + report.getTotalScore().doubleValue() + " < "+configuration.getExpectedScore());
                }
            }
            String reportEncrypted = ReportingService.getInstance().prepareReportForDelivery(securityBuilder, report);
            String responseWithUrl;
            if (report.isEligibleForStoringExternally() && configuration.isShouldSendReportToCyBench()) {
                responseWithUrl = DeliveryService.getInstance().sendReportForStoring(reportEncrypted);
                project.getLogger().lifecycle("responseWithUrl: "+ responseWithUrl);
                project.getLogger().lifecycle(" report.getClass().getClassLoader(): "+ report.getClass().getClassLoader());
                report.setReportURL(responseWithUrl);
                if (responseWithUrl != null && !responseWithUrl.isEmpty()){
                    isReportSentSuccessFully = true ;
                }
            } else {
               project.getLogger().lifecycle("You may submit your report '"+ IOUtils.getReportsPath(configuration.getReportsFolder(), Constants.CYB_REPORT_CYB_FILE)+"' manually at "+ Constants.CYB_UPLOAD_URL);
            }
            project.getLogger().lifecycle(reportJSON) ;
            project.getLogger().lifecycle("isReportSentSuccessFully: "+ isReportSentSuccessFully);
            if (configuration.isShouldStoreReportToFileSystem()) {
                project.getLogger().lifecycle("Saving test results to '" + IOUtils.getReportsPath(configuration.getReportsFolder(), ComputationUtils.createFileNameForReport(configuration.getReportName(),start,report.getTotalScore(),false)) + "'");
                IOUtils.storeResultsToFile(IOUtils.getReportsPath(configuration.getReportsFolder(), ComputationUtils.createFileNameForReport(configuration.getReportName(),start,report.getTotalScore(),false)), reportJSON);
                project.getLogger().lifecycle("Saving encrypted test results to '" + IOUtils.getReportsPath(configuration.getReportsFolder(), ComputationUtils.createFileNameForReport(configuration.getReportName(),start,report.getTotalScore(),true)) + "'");
                IOUtils.storeResultsToFile(IOUtils.getReportsPath(configuration.getReportsFolder(), ComputationUtils.createFileNameForReport(configuration.getReportName(),start,report.getTotalScore(),true)), reportEncrypted);
            }
           project.getLogger().lifecycle("Removing all temporary auto-generated files....");
           IOUtils.removeTestDataFiles();
           project.getLogger().lifecycle("Removed all temporary auto-generated files!!!");
        }catch (Throwable t){
            if (t.getMessage() != null && t.getMessage().contains("/META-INF/BenchmarkList")) {
               project.getLogger().warn("-------------------No benchmark tests found-------------------");
            }
            else {
                throw new GradleException("Error during benchmarks run", t);
            }
        }
        if (!isReportSentSuccessFully && configuration.isShouldSendReportToCyBench() &&  configuration.isShouldFailBuildOnReportDeliveryFailure()){
            throw new GradleException("Error during benchmarks run, report was not sent to CyBench as configured!");
        }
       project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
       project.getLogger().lifecycle("         Finished CyBench benchmarking ("+ ComputationUtils.formatInterval(System.currentTimeMillis() - start) +")");
       project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
    }
    private static void fingerprintAndHashGeneration(Project project, BenchmarkList benchmarkList,  Map<String, String> generatedFingerprints,  Map<String, String> manualFingerprints,  Map<String, String> classFingerprints){
        SourceSet sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getAt("main");
        FileCollection test = sourceSets.getRuntimeClasspath();
        List<URL> urls = new ArrayList<URL>();
        for(File name : test){
            try {

                project.getLogger().lifecycle("Dependency Class loader: "+name);
                URL url = name.toURI().toURL();
                urls.add(url);
            }catch (MalformedURLException ex){
                project.getLogger().error("Class not found in the classpath for execution", ex);
            }
        }
        project.getLogger().lifecycle("------------------------------------------------------------------------------------");
        Set<BenchmarkListEntry> all = benchmarkList.getAll(new JMHUtils.SilentOutputFormat(), Collections.EMPTY_LIST);
        List<String> benchmarkNames = all.stream().map(BenchmarkListEntry::getUserClassQName).collect(Collectors.toList());
        URL[] urlsArray = urls.toArray(new URL[0]);
        try(URLClassLoader cl = new URLClassLoader(urlsArray)){
            for (String benchmarkClass : benchmarkNames) {
                Class<?> cls = cl.loadClass(benchmarkClass);
                JavaClass javaClass = Repository.lookupClass(cls);
                Class benchmarkAnnotationClass = cl.loadClass("org.openjdk.jmh.annotations.Benchmark");
                List<String> benchmarkMethods = new ArrayList<>();
                for (java.lang.reflect.Method method1 : cls.getMethods()) {
                    if (method1.getAnnotation(benchmarkAnnotationClass) != null) {
                        String name = method1.getName();
                        benchmarkMethods.add(name);
                    }
                }
                for (Method method : javaClass.getMethods()) {
                    try {
                        if (benchmarkMethods.contains(method.getName())) {
                            String hash = hashByteArray(concatArrays(method.getName().getBytes(), method.getSignature().getBytes() ,  method.getCode().getCode()));
                            generatedFingerprints.put(cls.getName() + "." +method.getName(), hash);
                        }
                    } catch (Exception e) {
                        project.getLogger().error("Failed to compute hash for method {} in class {}", method.getName(),cls, e);
                    }
                }
                String classHash = computeClassHash(cls, project);
                java.lang.reflect.Method[] methods = cls.getMethods();
                for (java.lang.reflect.Method method : methods) {
                    if (method.getAnnotation(benchmarkAnnotationClass) != null) {
                        Class benchmarkAnnotationTagClass = cl.loadClass("com.gocypher.cybench.core.annotation.BenchmarkTag");
                        Annotation annotation = method.getAnnotation(benchmarkAnnotationTagClass);
                        if (annotation != null) {
                            String tag = null;
                            if(annotation.toString().contains("com.gocypher.cybench.core.annotation.BenchmarkTag")) {
                                String result = StringUtils.substringBetween(annotation.toString(), "(", ")");
                                tag = result.replace("tag=", "");
                            }
                            manualFingerprints.put(cls.getName() + "." + method.getName(), tag);
                        }
                    }
                    classFingerprints.put(cls.getName() + "." + method.getName(), classHash);
                }
            }
        } catch(Exception exc){
            project.getLogger().error("Class not found in the classpath for execution", exc);
        }
    }
    protected static byte[] concatArrays(byte[] ... bytes) {
        String collect = Arrays.stream(bytes).map(String::new).collect(Collectors.joining());
        return collect.getBytes();
    }
    private static String hashByteArray(byte[] classBytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.reset();
        byte[] digested = md.digest(classBytes);
        StringBuffer sb = new StringBuffer();
        for (byte b : digested) {
            sb.append(Integer.toHexString(0xff & b));
        }
        return sb.toString();
    }


    public void cybenchJMHReflectiveTask(Project project, SourceSet sourceSets, LauncherConfiguration configuration) {
        StringBuilder classpath = new StringBuilder();
        String buildPath = String.valueOf(project.getBuildDir());
        String testBuildPath = project.getBuildDir() + TEST_SOURCE_ROOT;
        FileCollection test = sourceSets.getRuntimeClasspath();
        classpath.append(test.getAsPath()).append(";").append(testBuildPath);

        System.setProperty("java.class.path",classpath.toString());
        project.task("cybenchRun").doLast( task -> {
            if(!configuration.isSkip() && System.getProperty("skipCybench") == null ) {
                try {
                    execute(buildPath, configuration, project);
                } catch (GradleException e) {
                    e.printStackTrace();
                }
            }else {
                project.getLogger().lifecycle("Skipping CyBench execution");
            }
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

    public static byte[] getObjectBytes(Object obj){
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ObjectOutputStream out;
            out = new ObjectOutputStream(bos);
            out.writeObject(obj);
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return  new byte[]{};
    }

    public static String computeClassHash(Class<?> clazz, Project project) {
        if (clazz != null) {
            try {
                byte[] classBytes = getObjectBytes(clazz);
                String classMD5Hash = hashByteArray(classBytes);
                return classMD5Hash;
            } catch (Exception e) {
                project.getLogger().lifecycle("Failed to compute hash for class {}", clazz, e);
            }
        }
        return null;
    }

    public static void generateMethodFingerprints(Class<?> benchmarkClass, Map<String, String> manualFingerprints, Map<String, String> classFingerprints) {

    }

    private void UpdateFieldViaReflection(Object target, String fieldName, Class<?> classObject, Object value) {
        try {
            Field listField = classObject.getDeclaredField(fieldName);
            updateFileAccess(listField);
            listField.set(target, value);
        } catch (Exception e) {
            throw new GradleException(
                    "Error : unable to set '" + fieldName + "' on " + classObject.getSimpleName(), e);
        }
    }
    private static void updateFileAccess(Field listField) throws NoSuchFieldException, IllegalAccessException {
        listField.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(listField, listField.getModifiers() & ~Modifier.FINAL);
    }

}
