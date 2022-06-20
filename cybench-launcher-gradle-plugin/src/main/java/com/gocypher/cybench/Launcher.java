/*
 * Copyright (C) 2020-2022, K2N.IO.
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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 */

package com.gocypher.cybench;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.*;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.SafepointsProfiler;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.CompilerHints;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import com.gocypher.cybench.core.utils.IOUtils;
import com.gocypher.cybench.core.utils.JMHUtils;
import com.gocypher.cybench.core.utils.JSONUtils;
import com.gocypher.cybench.launcher.BenchmarkRunner;
import com.gocypher.cybench.launcher.environment.model.HardwareProperties;
import com.gocypher.cybench.launcher.environment.model.JVMProperties;
import com.gocypher.cybench.launcher.environment.services.CollectSystemInformation;
import com.gocypher.cybench.launcher.model.BenchmarkOverviewReport;
import com.gocypher.cybench.launcher.model.BenchmarkReport;
import com.gocypher.cybench.launcher.model.TooManyAnomaliesException;
import com.gocypher.cybench.launcher.report.DeliveryService;
import com.gocypher.cybench.launcher.report.ReportingService;
import com.gocypher.cybench.launcher.utils.ComputationUtils;
import com.gocypher.cybench.launcher.utils.Constants;
import com.gocypher.cybench.launcher.utils.SecurityBuilder;
import com.gocypher.cybench.model.ComparisonConfig;
import com.gocypher.cybench.utils.AutomatedComparisonConfig;
import com.gocypher.cybench.utils.LauncherConfiguration;
import com.gocypher.cybench.utils.PluginConstants;
import com.gocypher.cybench.utils.PluginUtils;

public class Launcher implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        LauncherConfiguration configuration = project.getExtensions().create("cybenchJMH", LauncherConfiguration.class);
        if (configuration.getReportName().equals(LauncherConfiguration.DEFAULT_NAME)) {
            configuration.setReportName(MessageFormat.format("Benchmark for {0}:{1}:{2}", project.getGroup(),
                    project.getName(), project.getVersion()));
        }
        AutomatedComparisonConfig loadedAutoConfiguration = project.getExtensions().create("cybenchAutomation",
                AutomatedComparisonConfig.class);

        SourceSet sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets()
                .getAt("main");

        try {
            cybenchJMHReflectiveTask(project, sourceSets, configuration, loadedAutoConfiguration);
            project.getTasks().getByName("testClasses").finalizedBy("cybenchRun");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cybenchJMHReflectiveTask(Project project, SourceSet sourceSets, LauncherConfiguration configuration,
            AutomatedComparisonConfig loadedAutoConfiguration) {
        StringBuilder classpath = new StringBuilder();
        String buildPath = String.valueOf(project.getBuildDir());
        String testBuildPath = project.getBuildDir() + PluginConstants.TEST_SOURCE_ROOT;
        FileCollection test = sourceSets.getRuntimeClasspath();
        classpath.append(test.getAsPath()).append(";").append(testBuildPath);

        System.setProperty("java.class.path", classpath.toString());
        project.task("cybenchRun").doLast(task -> {
            if (!configuration.isSkip() && System.getProperty("skipCybench") == null) {
                try {
                    execute(buildPath, configuration, loadedAutoConfiguration, project);
                } catch (GradleException e) {
                    e.printStackTrace();
                }
            } else {
                project.getLogger().lifecycle("Skipping CyBench execution");
            }
        });
    }

    @SuppressWarnings("unchecked")
    public void execute(String buildPath, LauncherConfiguration configuration,
            AutomatedComparisonConfig loadedAutoConfiguration, Project project) throws GradleException {
        long start = System.currentTimeMillis();
        project.getLogger()
                .lifecycle("-----------------------------------------------------------------------------------------");
        project.getLogger()
                .lifecycle("                                 Starting CyBench benchmarks                             ");
        project.getLogger()
                .lifecycle("-----------------------------------------------------------------------------------------");
        System.setProperty("collectHw", "true");
        boolean isReportSentSuccessFully = false;

        ComparisonConfig automatedComparisonCfg;
        try {
            automatedComparisonCfg = checkConfigValidity(project, loadedAutoConfiguration);
            project.getLogger().lifecycle("** Configuration loaded: automated comparison configuration");
        } catch (Exception e) {
            automatedComparisonCfg = null;
            project.getLogger().error("Failed to parse automated comparison configuration", e);
        }

        try {
            project.getLogger().lifecycle("Collecting hardware, software information...");
            HardwareProperties hwProperties = CollectSystemInformation.getEnvironmentProperties();
            project.getLogger().lifecycle("Collecting JVM properties...");
            JVMProperties jvmProperties = CollectSystemInformation.getJavaVirtualMachineProperties();

            SecurityBuilder securityBuilder = new SecurityBuilder();
            Map<String, Object> benchmarkSettings = new HashMap<>();
            Map<String, Map<String, String>> customBenchmarksMetadata = ComputationUtils
                    .parseBenchmarkMetadata(configuration.getUserProperties());

            benchmarkSettings.put("benchSource", PluginConstants.BENCH_SOURCE);
            benchmarkSettings.put("benchWarmUpIteration", configuration.getWarmUpIterations());
            benchmarkSettings.put("benchWarmUpSeconds", configuration.getWarmUpSeconds());
            benchmarkSettings.put("benchMeasurementIteration", configuration.getMeasurementIterations());
            benchmarkSettings.put("benchMeasurementSeconds", configuration.getMeasurementSeconds());
            benchmarkSettings.put("benchForkCount", configuration.getForks());

            benchmarkSettings.put("benchThreadCount", configuration.getThreads());
            benchmarkSettings.put("benchReportName", configuration.getReportName());

            Map<String, String> PROJECT_METADATA_MAP = BenchmarkRunner.checkProjectMetadataExists();

            project.getLogger().lifecycle("Executing benchmarks...");

            OptionsBuilder optBuild = new OptionsBuilder();
            if (configuration.isUseCyBenchBenchmarkSettings()) {
                optBuild.forks(configuration.getForks()) //
                        .measurementIterations(configuration.getMeasurementIterations()) //
                        .measurementTime(TimeValue.seconds(configuration.getMeasurementSeconds())) //
                        .warmupIterations(configuration.getWarmUpIterations()) //
                        .warmupTime(TimeValue.seconds(configuration.getWarmUpSeconds())) //
                        .threads(configuration.getThreads()) //
                ;
            }

            Options opt = optBuild.shouldDoGC(true) //
                    .addProfiler(GCProfiler.class) //
                    // .addProfiler(HotspotThreadProfiler.class) // obsolete
                    // .addProfiler(HotspotRuntimeProfiler.class) // obsolete
                    .addProfiler(SafepointsProfiler.class) //
                    .detectJvmArgs() //
                    .build();

            Runner runner = new Runner(opt);
            BenchmarkList benchmarkList;
            CompilerHints compilerHints;
            File benchmarkListFile = new File(
                    buildPath + PluginConstants.TEST_SOURCE_ROOT + PluginConstants.BENCHMARK_LIST_FILE);
            File compilerHintFile = new File(
                    buildPath + PluginConstants.TEST_SOURCE_ROOT + PluginConstants.COMPILER_HINT_FILE);
            if (benchmarkListFile.exists() && compilerHintFile.exists()) {
                benchmarkList = BenchmarkList
                        .fromFile(buildPath + PluginConstants.TEST_SOURCE_ROOT + PluginConstants.BENCHMARK_LIST_FILE);
                compilerHints = CompilerHints
                        .fromFile(buildPath + PluginConstants.TEST_SOURCE_ROOT + PluginConstants.COMPILER_HINT_FILE);
            } else {
                benchmarkList = BenchmarkList
                        .fromFile(buildPath + PluginConstants.MAIN_SOURCE_ROOT + PluginConstants.BENCHMARK_LIST_FILE);
                compilerHints = CompilerHints
                        .fromFile(buildPath + PluginConstants.MAIN_SOURCE_ROOT + PluginConstants.COMPILER_HINT_FILE);
            }
            PluginUtils.updateFieldViaReflection(runner, "list", runner.getClass(), benchmarkList);
            PluginUtils.updateFieldViaReflection(compilerHints, "defaultList", CompilerHints.class, compilerHints);

            System.setProperty("checkScoreAnnotation", "false");
            Map<String, String> generatedFingerprints = new HashMap<>();
            Map<String, String> manualFingerprints = new HashMap<>();
            Map<String, String> classFingerprints = new HashMap<>();
            PluginUtils.fingerprintAndHashGeneration(project, benchmarkList, generatedFingerprints, manualFingerprints,
                    classFingerprints);

            Collection<RunResult> results = runner.run();
            BenchmarkOverviewReport report = ReportingService.getInstance().createBenchmarkReport(results,
                    customBenchmarksMetadata);

            report.getEnvironmentSettings().put("environment", hwProperties);
            report.getEnvironmentSettings().put("jvmEnvironment", jvmProperties);
            report.getEnvironmentSettings().put("unclassifiedProperties",
                    CollectSystemInformation.getUnclassifiedProperties());
            report.getEnvironmentSettings().put("userDefinedProperties",
                    ComputationUtils.customUserDefinedProperties(configuration.getUserProperties()));
            report.setBenchmarkSettings(benchmarkSettings);

            if (automatedComparisonCfg != null) {
                if (automatedComparisonCfg.getScope().equals(ComparisonConfig.Scope.WITHIN)) {
                    automatedComparisonCfg.setCompareVersion(PROJECT_METADATA_MAP.get(Constants.PROJECT_VERSION));
                }
                automatedComparisonCfg.setRange(String.valueOf(automatedComparisonCfg.getCompareLatestReports()));
                automatedComparisonCfg.setProjectName(PROJECT_METADATA_MAP.get(Constants.PROJECT_NAME));
                automatedComparisonCfg.setProjectVersion(PROJECT_METADATA_MAP.get(Constants.PROJECT_VERSION));
                report.setAutomatedComparisonConfig(automatedComparisonCfg);
            }

            URL[] urlsArray = PluginUtils.getUrlsArray(project);
            for (String s : report.getBenchmarks().keySet()) {
                List<BenchmarkReport> custom = new ArrayList<>(report.getBenchmarks().get(s));
                custom.forEach(benchmarkReport -> {
                    String name = benchmarkReport.getName();
                    benchmarkReport.setClassFingerprint(classFingerprints.get(name));
                    benchmarkReport.setGeneratedFingerprint(generatedFingerprints.get(name));
                    benchmarkReport.setManualFingerprint(manualFingerprints.get(name));
                    try (URLClassLoader cl = new URLClassLoader(urlsArray)) {
                        JMHUtils.ClassAndMethod classAndMethod = new JMHUtils.ClassAndMethod(name).invoke();
                        String clazz = classAndMethod.getClazz();
                        String method = classAndMethod.getMethod();
                        Class<?> aClass = cl.loadClass(clazz);
                        Optional<Method> benchmarkMethod = JMHUtils.getBenchmarkMethod(method, aClass);
                        PluginUtils.appendMetadataFromAnnotated(benchmarkMethod, benchmarkReport, project, cl);
                        PluginUtils.appendMetadataFromAnnotated(Optional.of(aClass), benchmarkReport, project, cl);
                        BenchmarkRunner.syncReportsMetadata(report, benchmarkReport);
                    } catch (Exception exc) {
                        project.getLogger().error("Class not found in the classpath for execution", exc);
                    }
                });
            }

            if (report.getBenchmarks() != null && !report.getBenchmarks().isEmpty()) {
                List<BenchmarkReport> customBenchmarksCategoryCheck = report.getBenchmarks().get("CUSTOM");
                report.getBenchmarks().remove("CUSTOM");
                if (customBenchmarksCategoryCheck != null) {
                    for (BenchmarkReport benchReport : customBenchmarksCategoryCheck) {
                        report.addToBenchmarks(benchReport);
                    }
                }
                report.computeScores();
                report.updateUploadStatus(configuration.getReportUploadStatus());
            }

            project.getLogger().lifecycle(
                    "-----------------------------------------------------------------------------------------");
            project.getLogger().lifecycle("      Report score - {}", report.getTotalScore());
            project.getLogger().lifecycle(
                    "-----------------------------------------------------------------------------------------");
            if (configuration.getExpectedScore() > 0) {
                if (report.getTotalScore().doubleValue() < configuration.getExpectedScore()) {
                    throw new GradleException("CyBench score is less than expected:"
                            + report.getTotalScore().doubleValue() + " < " + configuration.getExpectedScore());
                }
            }
            String reportEncrypted = ReportingService.getInstance().prepareReportForDelivery(securityBuilder, report);
            String deviceReports = null;
            String resultURL = null;
            Map<?, ?> response = new HashMap<>();
            configuration.setReportsFolder(PluginUtils.checkReportSaveLocation(configuration.getReportsFolder()));
            if (report.isEligibleForStoringExternally() && configuration.isShouldSendReportToCyBench()) {
                String tokenAndEmail = ComputationUtils.getRequestHeader(configuration.getBenchAccessToken(),
                        configuration.getEmail());
                String benchQueryToken = configuration.getBenchQueryToken();
                String responseWithUrl = DeliveryService.getInstance().sendReportForStoring(reportEncrypted,
                        tokenAndEmail, benchQueryToken);
                if (StringUtils.isNotEmpty(responseWithUrl)) {
                    response = JSONUtils.parseJsonIntoMap(responseWithUrl);
                }
                if (!response.isEmpty() && !BenchmarkRunner.isErrorResponse(response)) {
                    deviceReports = String.valueOf(response.get(Constants.REPORT_USER_URL));
                    resultURL = String.valueOf(response.get(Constants.REPORT_URL));
                    isReportSentSuccessFully = true;
                    report.setDeviceReportsURL(deviceReports);
                    report.setReportURL(resultURL);
                }
            } else {
                // project.getLogger().lifecycle("You may submit your report '"
                // + IOUtils.getReportsPath(configuration.getReportsFolder(), Constants.CYB_REPORT_CYB_FILE)
                // + "' manually at " + Constants.CYB_UPLOAD_URL);
            }
            String reportJSON = JSONUtils.marshalToPrettyJson(report);
            // project.getLogger().lifecycle(reportJSON);
            String reportFilePath = IOUtils.getReportsPath(configuration.getReportsFolder(), ComputationUtils
                    .createFileNameForReport(configuration.getReportName(), start, report.getTotalScore(), false));
            String reportCybFilePath = IOUtils.getReportsPath(configuration.getReportsFolder(), ComputationUtils
                    .createFileNameForReport(configuration.getReportName(), start, report.getTotalScore(), true));
            if (configuration.isShouldStoreReportToFileSystem()) {
                if (reportFilePath.startsWith("/")) {
                    reportFilePath = PluginConstants.DEFAULT_FILE_SAVE_LOCATION + reportFilePath;
                    reportCybFilePath = PluginConstants.DEFAULT_FILE_SAVE_LOCATION + reportCybFilePath;
                }
                project.getLogger().lifecycle("Saving test results to '{}'", reportFilePath);
                IOUtils.storeResultsToFile(reportFilePath, reportJSON);
                project.getLogger().lifecycle("Saving encrypted test results to '{}'", reportCybFilePath);
                IOUtils.storeResultsToFile(reportCybFilePath, reportEncrypted);
            }
            IOUtils.removeTestDataFiles();
            project.getLogger().lifecycle("Removed all temporary auto-generated files!!!");

            if (!response.isEmpty() && report.getUploadStatus().equals(Constants.REPORT_PRIVATE)) {
                project.getLogger().error("*** Total Reports allowed in repository: {}",
                        response.get(Constants.REPORTS_ALLOWED_FROM_SUB));
                project.getLogger().error("*** Total Reports already in repository: {}",
                        response.get(Constants.NUM_REPORTS_IN_REPO));
            }

            if (!response.isEmpty() && !BenchmarkRunner.isErrorResponse(response)) {
                project.getLogger().lifecycle("Benchmark report submitted successfully to {}", Constants.REPORT_URL);
                project.getLogger().lifecycle("You can find all device benchmarks on {}", deviceReports);
                project.getLogger().lifecycle("Your report is available at {}", resultURL);
                project.getLogger().lifecycle("NOTE: It may take a few minutes for your report to appear online");

                if (response.containsKey("automatedComparisons")) {
                    List<Map<String, Object>> automatedComparisons = (List<Map<String, Object>>) response
                            .get("automatedComparisons");
                    BenchmarkRunner.verifyAnomalies(automatedComparisons);
                }
            } else {
                String errMsg = BenchmarkRunner.getErrorResponseMessage(response);
                if (errMsg != null) {
                    project.getLogger().error("CyBench backend service sent error response: {}", errMsg);
                }
                if (BenchmarkRunner.getAllowedToUploadBasedOnSubscription(response)) {
                    // user was allowed to upload report, and there was still an error
                    project.getLogger().lifecycle("You may submit your report '{}' manually at {}",
                            IOUtils.getReportsPath(configuration.getReportsFolder(), Constants.CYB_REPORT_CYB_FILE),
                            Constants.CYB_UPLOAD_URL);
                }

            }
        } catch (TooManyAnomaliesException e) {
            throw new GradleException("Too many anomalies found during benchmarks run: " + e.getMessage());
        } catch (Throwable t) {
            if (t.getMessage() != null && t.getMessage().contains("/META-INF/BenchmarkList")) {
                project.getLogger().warn("-------------------No benchmark tests found-------------------");
            } else {
                throw new GradleException("Error during benchmarks run", t);
            }
        } finally {
            DeliveryService.getInstance().close();

            project.getLogger().lifecycle(
                    "-----------------------------------------------------------------------------------------");
            project.getLogger().lifecycle(
                    "                      Finished CyBench benchmarking ({})                                 ",
                    ComputationUtils.formatInterval(System.currentTimeMillis() - start));
            project.getLogger().lifecycle(
                    "-----------------------------------------------------------------------------------------");
        }
        if (!isReportSentSuccessFully && configuration.isShouldSendReportToCyBench()
                && configuration.isShouldFailBuildOnReportDeliveryFailure()) {
            throw new GradleException("Error during benchmarks run, report was not sent to CyBench as configured!");
        }
    }

    public ComparisonConfig checkConfigValidity(Project project, AutomatedComparisonConfig automatedComparisonConfig)
            throws Exception {
        ComparisonConfig verifiedComparisonConfig = new ComparisonConfig();

        String SCOPE_STR = automatedComparisonConfig.getScope();
        if (StringUtils.isBlank(SCOPE_STR)) {
            throw new Exception("Scope is not specified!");
        } else {
            SCOPE_STR = SCOPE_STR.toUpperCase();
        }
        ComparisonConfig.Scope SCOPE;
        String COMPARE_VERSION = automatedComparisonConfig.getCompareVersion();
        Integer NUM_LATEST_REPORTS = automatedComparisonConfig.getNumLatestReports();
        Integer ANOMALIES_ALLOWED = automatedComparisonConfig.getAnomaliesAllowed();
        String METHOD_STR = automatedComparisonConfig.getMethod();
        if (StringUtils.isBlank(METHOD_STR)) {
            throw new Exception("Method is not specified!");
        } else {
            METHOD_STR = METHOD_STR.toUpperCase();
        }
        ComparisonConfig.Method METHOD;
        String THRESHOLD_STR = automatedComparisonConfig.getThreshold();
        if (StringUtils.isNotBlank(THRESHOLD_STR)) {
            THRESHOLD_STR = THRESHOLD_STR.toUpperCase();
        }
        ComparisonConfig.Threshold THRESHOLD;
        Double PERCENT_CHANGE_ALLOWED = automatedComparisonConfig.getPercentChangeAllowed();
        Double DEVIATIONS_ALLOWED = automatedComparisonConfig.getDeviationsAllowed();

        if (NUM_LATEST_REPORTS != null) {
            if (NUM_LATEST_REPORTS < 1) {
                throw new Exception("Not enough latest reports specified to compare to!");
            }
            verifiedComparisonConfig.setCompareLatestReports(NUM_LATEST_REPORTS);
        } else {
            throw new Exception("Number of latest reports to compare to was not specified!");
        }
        if (ANOMALIES_ALLOWED != null) {
            if (ANOMALIES_ALLOWED < 1) {
                throw new Exception("Not enough anomalies allowed specified!");
            }
            verifiedComparisonConfig.setAnomaliesAllowed(ANOMALIES_ALLOWED);
        } else {
            throw new Exception("Anomalies allowed was not specified!");
        }

        if (!EnumUtils.isValidEnum(ComparisonConfig.Scope.class, SCOPE_STR)) {
            throw new Exception("Scope is invalid!");
        } else {
            SCOPE = ComparisonConfig.Scope.valueOf(SCOPE_STR);
            verifiedComparisonConfig.setScope(SCOPE);
        }
        if (!EnumUtils.isValidEnum(ComparisonConfig.Method.class, METHOD_STR)) {
            throw new Exception("Method is invalid!");
        } else {
            METHOD = ComparisonConfig.Method.valueOf(METHOD_STR);
            verifiedComparisonConfig.setMethod(METHOD);
        }

        if (SCOPE.equals(ComparisonConfig.Scope.WITHIN) && StringUtils.isNotEmpty(COMPARE_VERSION)) {
            COMPARE_VERSION = "";
            project.getLogger().warn(
                    "Automated comparison config scoped specified as WITHIN but compare version was also specified, will compare WITHIN the currently tested version.");
        } else if (SCOPE.equals(ComparisonConfig.Scope.BETWEEN) && StringUtils.isBlank(COMPARE_VERSION)) {
            throw new Exception("Scope specified as BETWEEN but no compare version specified!");
        } else if (SCOPE.equals(ComparisonConfig.Scope.BETWEEN)) {
            verifiedComparisonConfig.setCompareVersion(COMPARE_VERSION);
        }

        if (METHOD.equals(ComparisonConfig.Method.SD)) {
            if (DEVIATIONS_ALLOWED != null) {
                if (DEVIATIONS_ALLOWED <= 0) {
                    throw new Exception("Method specified as SD but not enough deviations allowed were specified!");
                }
                verifiedComparisonConfig.setDeviationsAllowed(DEVIATIONS_ALLOWED);
            } else {
                throw new Exception("Method specified as SD but deviations allowed was not specified!");
            }
        } else if (METHOD.equals(ComparisonConfig.Method.DELTA)) {
            if (!EnumUtils.isValidEnum(ComparisonConfig.Threshold.class, THRESHOLD_STR)
                    || StringUtils.isBlank(THRESHOLD_STR)) {
                throw new Exception("Method specified as DELTA but no threshold specified or threshold is invalid!");
            } else {
                THRESHOLD = ComparisonConfig.Threshold.valueOf(THRESHOLD_STR);
                verifiedComparisonConfig.setThreshold(THRESHOLD);
            }

            if (THRESHOLD.equals(ComparisonConfig.Threshold.PERCENT_CHANGE)) {
                if (PERCENT_CHANGE_ALLOWED != null) {
                    if (PERCENT_CHANGE_ALLOWED <= 0) {
                        throw new Exception(
                                "Threshold specified as PERCENT_CHANGE but percent change is not high enough!");
                    }
                    verifiedComparisonConfig.setPercentChangeAllowed(PERCENT_CHANGE_ALLOWED);
                } else {
                    throw new Exception(
                            "Threshold specified as PERCENT_CHANGE but percent change allowed was not specified!");
                }
            }
        }

        return verifiedComparisonConfig;
    }
}
