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
import com.gocypher.cybench.launcher.utils.SecurityBuilder;
import com.gocypher.cybench.utils.LauncherConfiguration;
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
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Launcher implements Plugin<Project> {
    private static final String BENCHMARK_LIST_FILE = "/classes/java/main/META-INF/BenchmarkList";
    private static final String COMPILER_HINT_FILE = "/classes/java/main/META-INF/CompilerHints";

    @Override
    public void apply(Project project) {
        LauncherConfiguration configuration = project.getExtensions().create("cybenchJMH", LauncherConfiguration.class);
        SourceSet sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getAt("main");
        try {
            cybenchJMHReflectiveTask(project,sourceSets, configuration);
            project.getTasks().getByName("classes").finalizedBy("cybenchRun");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void execute(String buildPath,  LauncherConfiguration configuration, Project project) throws MojoExecutionException {
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

            //FIXME generate security hashes for report classes found on the classpath
            SecurityBuilder securityBuilder = new SecurityBuilder();
            Map<String, Object> benchmarkSettings = new HashMap<>();
            Map<String, Map<String, String>> customBenchmarksMetadata = ComputationUtils.parseBenchmarkMetadata(configuration.getUserProperties());

            benchmarkSettings.put("benchThreadCount", configuration.getThreads());
            benchmarkSettings.put("benchReportName", configuration.getReportName());

           project.getLogger().lifecycle("Executing benchmarks...");

            OptionsBuilder optBuild = new OptionsBuilder();
            Options opt = optBuild.forks(configuration.getForks())
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

            Runner runner = new Runner(opt);

            BenchmarkList benchmarkList = BenchmarkList.fromFile(buildPath+BENCHMARK_LIST_FILE);
            CompilerHints compilerHints = CompilerHints.fromFile(buildPath+COMPILER_HINT_FILE);
            UpdateFieldViaReflection(runner.getClass(), runner, "list", benchmarkList);
            UpdateFieldViaReflection(CompilerHints.class, compilerHints, "defaultList", compilerHints);

            Collection<RunResult> results = runner.run();

            BenchmarkOverviewReport report = ReportingService.getInstance().createBenchmarkReport(results, customBenchmarksMetadata);
            report.updateUploadStatus(configuration.getReportUploadStatus());

            report.getEnvironmentSettings().put("environment", hwProperties);
            report.getEnvironmentSettings().put("jvmEnvironment", jvmProperties);
            report.getEnvironmentSettings().put("unclassifiedProperties", CollectSystemInformation.getUnclassifiedProperties());
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
            String responseWithUrl;
            if (report.isEligibleForStoringExternally() && configuration.isShouldSendReportToCyBench()) {
                responseWithUrl = DeliveryService.getInstance().sendReportForStoring(reportEncrypted);
                report.setReportURL(responseWithUrl);
                if (responseWithUrl != null && !responseWithUrl.isEmpty()){
                    isReportSentSuccessFully = true ;
                }
            } else {
               project.getLogger().lifecycle("You may submit your report '"+ IOUtils.getReportsPath(configuration.getReportsFolder(), Constants.CYB_REPORT_CYB_FILE)+"' manually at "+ Constants.CYB_UPLOAD_URL);
            }
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
                throw new MojoExecutionException("Error during benchmarks run", t);
            }
        }

        if (!isReportSentSuccessFully && configuration.isShouldSendReportToCyBench() &&  configuration.isShouldFailBuildOnReportDeliveryFailure()){
            throw new MojoExecutionException("Error during benchmarks run, report was not sent to CyBench as configured!");
        }
       project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
       project.getLogger().lifecycle("         Finished CyBench benchmarking ("+ ComputationUtils.formatInterval(System.currentTimeMillis() - start) +")");
       project.getLogger().lifecycle("-----------------------------------------------------------------------------------------");
    }
    public void cybenchJMHReflectiveTask(Project project, SourceSet sourceSets, LauncherConfiguration configuration) {
        StringBuilder classpath = new StringBuilder();
        String buildPath = String.valueOf(project.getBuildDir());
        FileCollection test = sourceSets.getRuntimeClasspath();
        classpath.append(test.getAsPath()).append(";").append(buildPath);
        System.setProperty("java.class.path",classpath.toString());
        project.task("cybenchRun").doLast( task -> {
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
            updateFileAccess(listField);
            listField.set(target, value);
        } catch (Exception e) {
            throw new GradleException(
                    "Error while instantiating tests: unable to set '" + fieldName + "' on " + clazz.getSimpleName(), e);
        }
    }
    private static void updateFileAccess(Field listField) throws NoSuchFieldException, IllegalAccessException {
        listField.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(listField, listField.getModifiers() & ~Modifier.FINAL);
    }

}
