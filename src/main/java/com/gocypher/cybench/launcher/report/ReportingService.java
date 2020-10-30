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

package com.gocypher.cybench.launcher.report;

import com.gocypher.cybench.core.utils.JSONUtils;
import com.gocypher.cybench.core.utils.SecurityUtils;
import com.gocypher.cybench.launcher.model.BenchmarkOverviewReport;
import com.gocypher.cybench.launcher.model.BenchmarkReport;
import com.gocypher.cybench.launcher.model.SecuredReport;
import com.gocypher.cybench.launcher.utils.ComputationUtils;
import com.gocypher.cybench.launcher.utils.SecurityBuilder;
import com.jcabi.manifests.Manifests;
import org.gradle.api.Project;
import org.openjdk.jmh.results.RunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

public class ReportingService {
    private static final Logger LOG = LoggerFactory.getLogger(ReportingService.class);
    private static ReportingService instance ;

    private ReportingService (){

    }

    public static ReportingService getInstance (){
        if (instance == null ){
            instance = new ReportingService() ;
        }
        return instance ;
    }

    public BenchmarkOverviewReport createBenchmarkReport (Collection<RunResult> jmhResults, Map<String,Map<String,String>>customBenchmarksMetadata, Project project){
        BenchmarkOverviewReport overviewReport = new BenchmarkOverviewReport() ;
        for (RunResult item :jmhResults){
            BenchmarkReport report = new BenchmarkReport() ;
            if (item.getPrimaryResult() != null) {
                report.setScore(item.getPrimaryResult().getScore());
                report.setUnits(item.getPrimaryResult().getScoreUnit());
                if (item.getPrimaryResult().getStatistics() != null){
                    report.setN(item.getPrimaryResult().getStatistics().getN());
                    report.setMeanScore(item.getPrimaryResult().getStatistics().getMean());
                    report.setMaxScore(item.getPrimaryResult().getStatistics().getMax());
                    report.setMinScore(item.getPrimaryResult().getStatistics().getMin());
                    if (!Double.isNaN(item.getPrimaryResult().getStatistics().getStandardDeviation())) {
                        report.setStdDevScore(item.getPrimaryResult().getStatistics().getStandardDeviation());
                    }
                }

            }
            if (item.getParams() != null) {
                report.setName(item.getParams().getBenchmark());
                report.setMode(item.getParams().getMode().shortLabel());
                //System.out.println("id:"+item.getParams().id());
                //System.out.println("Mode"+item.getParams().getMode().longLabel());
            }

//            System.out.println("result.getSecondaryResults(): "+item.getSecondaryResults().keySet());

            report.setGcCalls(getScoreFromJMHSecondaryResult(item,"gc.count"));
            report.setGcTime(getScoreFromJMHSecondaryResult(item,"gc.time"));
            report.setGcAllocationRate(getScoreFromJMHSecondaryResult(item,"gc.alloc.rate"));
            report.setGcAllocationRateNorm(getScoreFromJMHSecondaryResult(item,"gc.alloc.rate.norm"));
            report.setGcChurnPsEdenSpace(getScoreFromJMHSecondaryResult(item,"gc.churn.PS_Eden_Space"));
            report.setGcChurnPsEdenSpaceNorm(getScoreFromJMHSecondaryResult(item,"gc.churn.PS_Eden_Space.norm"));
            report.setGcChurnPsSurvivorSpace(getScoreFromJMHSecondaryResult(item,"gc.churn.PS_Survivor_Space"));
            report.setGcChurnPsSurvivorSpaceNorm(getScoreFromJMHSecondaryResult(item,"gc.churn.PS_Survivor_Space.norm"));

            report.setThreadsAliveCount(getScoreFromJMHSecondaryResult(item,"threads.alive"));
            report.setThreadsDaemonCount(getScoreFromJMHSecondaryResult(item,"threads.daemon"));
            report.setThreadsStartedCount(getScoreFromJMHSecondaryResult(item,"threads.started"));

            report.setThreadsSafePointSyncTime(getScoreFromJMHSecondaryResult(item,"rt.safepointSyncTime"));
            report.setThreadsSafePointTime(getScoreFromJMHSecondaryResult(item,"rt.safepointTime"));
            report.setThreadsSafePointsCount(getScoreFromJMHSecondaryResult(item,"rt.safepoints"));

            report.setThreadsSyncContendedLockAttemptsCount(getScoreFromJMHSecondaryResult(item,"rt.sync.contendedLockAttempts"));
            report.setThreadsSyncMonitorFatMonitorsCount(getScoreFromJMHSecondaryResult(item,"rt.sync.fatMonitors"));
            report.setThreadsSyncMonitorFutileWakeupsCount(getScoreFromJMHSecondaryResult(item,"rt.sync.futileWakeups"));
            report.setThreadsSyncMonitorDeflations(getScoreFromJMHSecondaryResult(item,"rt.sync.monitorDeflations"));
            report.setThreadsSyncMonitorInflations(getScoreFromJMHSecondaryResult(item,"rt.sync.monitorInflations"));
            report.setThreadsSyncNotificationsCount(getScoreFromJMHSecondaryResult(item,"rt.sync.notifications"));

            report.setThreadsSyncParksCount(getScoreFromJMHSecondaryResult(item,"rt.sync.parks"));

            report.setThreadsSafePointsInterval(getScoreFromJMHSecondaryResult(item,"safepoints.interval"));
            report.setThreadsSafePointsPause(getScoreFromJMHSecondaryResult(item,"safepoints.pause"));
            report.setThreadsSafePointsPauseAvg(getScoreFromJMHSecondaryResult(item,"safepoints.pause.avg"));
            report.setThreadsSafePointsPauseCount(getScoreFromJMHSecondaryResult(item,"safepoints.pause.count"));
            report.setThreadsSafePointsPauseTTSP(getScoreFromJMHSecondaryResult(item,"safepoints.ttsp"));
            report.setThreadsSafePointsPauseTTSPAvg(getScoreFromJMHSecondaryResult(item,"safepoints.ttsp.avg"));
            report.setThreadsSafePointsPauseTTSPCount(getScoreFromJMHSecondaryResult(item,"safepoints.ttsp.count"));

            /*System.out.println("Score:"+result.getPrimaryResult().getScore());
            System.out.println("Stats:"+result.getPrimaryResult().getStatistics());
            System.out.println("getBenchmarkResults:"+result.getBenchmarkResults().size());
            System.out.println("getAggregatedResult:"+result.getAggregatedResult().getBenchmarkResults());
            System.out.println("getSecondaryResults:"+result.getSecondaryResults());
            System.out.println("\n\n");
            */

            //System.out.println("Report class name:"+report.getReportClassName());
            //String metaInfoData = report.getReportClassName()
            String manifestData = null ;
            if (Manifests.exists("customBenchmarkMetadata")) {
                manifestData = Manifests.read("customBenchmarkMetadata");
            }
            Map<String,Map<String,String>> benchmarksMetadata =  ComputationUtils.parseCustomBenchmarkMetadata(manifestData);
            Map<String, String> benchProps;
            if(manifestData != null) {
                benchProps = prepareBenchmarkProperties(report.getReportClassName(), benchmarksMetadata);
            } else {
                benchProps = prepareBenchmarkProperties(report.getReportClassName(), customBenchmarksMetadata);
            }
            benchProps.putIfAbsent("benchCategory", "CUSTOM");
            benchProps.putIfAbsent("benchContext", "Custom");
            benchProps.putIfAbsent("benchVersion", "Other");
            report.setCategory(benchProps.get("benchCategory"));
            report.setContext(benchProps.get("benchContext"));
            report.setVersion(benchProps.get("benchVersion"));
            report.recalculateScoresToMatchNewUnits();
//            LOG.info("RAPORT {}", report);
            overviewReport.addToBenchmarks(report);
        }

        overviewReport.setTimestamp(System.currentTimeMillis());
        overviewReport.setTimestampUTC(ZonedDateTime.now( ZoneOffset.UTC ).toInstant().toEpochMilli());
        overviewReport.computeScores();

        return overviewReport ;
    }
    public Map<String, String> prepareBenchmarkProperties(String className, Map<String,Map<String,String>>customBenchmarksMetadata){
        Map<String, String> benchmarkProperties = new HashMap<>();
        try {
            if (customBenchmarksMetadata.get(className) != null) {
                if (customBenchmarksMetadata.get(className).get("category") != null) {
                    benchmarkProperties.put("benchCategory", customBenchmarksMetadata.get(className).get("category"));
                }
                if (customBenchmarksMetadata.get(className).get("context") != null) {
                    benchmarkProperties.put("benchContext", customBenchmarksMetadata.get(className).get("context"));
                }
                if (customBenchmarksMetadata.get(className).get("version") != null) {
                    benchmarkProperties.put("benchVersion", customBenchmarksMetadata.get(className).get("version"));
                }
            }
            return benchmarkProperties;
        } catch (Exception e) {
            LOG.error("Error on resolving benchmarks category, context and version: class={}", className, e);
        }
        return benchmarkProperties;
    }
    public Map<String, String> prepareBenchmarkSettings(String className, Map<String,Map<String,String>>customBenchmarksMetadata){
        Map<String, String> benchmarkProperties = new HashMap<>();
        try {
            if (customBenchmarksMetadata.get(className) != null) {
                if (customBenchmarksMetadata.get(className).get("context") != null) {
                    benchmarkProperties.put("benchContext", customBenchmarksMetadata.get(className).get("context"));
                }
                if (customBenchmarksMetadata.get(className).get("version") != null) {
                    benchmarkProperties.put("benchVersion", customBenchmarksMetadata.get(className).get("version"));
                }
            }
            benchmarkProperties.putIfAbsent("benchContext", "Custom");
            benchmarkProperties.putIfAbsent("benchVersion", "Other");
            return benchmarkProperties;
        } catch (Exception e) {
            LOG.error("Error on resolving category: class={}", className, e);
        }
        return benchmarkProperties;
    }

    protected String getVersion(String fullClassName) {
        try {
            Class<?> clazz = Class.forName(fullClassName);
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Manifest manifest = new Manifest(loader.getResourceAsStream("META-INF/MANIFEST.MF"));
            String benchmarkPackageString = clazz.getPackage().getName().replace(".", "-")+"-version";
            return manifest.getMainAttributes().getValue(benchmarkPackageString);
        } catch (Exception e) {
            LOG.info("Could not locate the benchmark version",e);
        }
        return null;
    }

    public String prepareReportForDelivery(SecurityBuilder securityBuilder, BenchmarkOverviewReport report) {
        try {
            LOG.info("Preparing report: encrypt and sign...");
            SecuredReport securedReport = createSecuredReport(report);
            securityBuilder.generateSecurityHashForReport(securedReport.getReport());
            securedReport.setSignatures(securityBuilder.buildSignatures());
            String plainReport = JSONUtils.marshalToJson(securedReport);
            return SecurityUtils.encryptReport(plainReport);
        } finally {
            LOG.info("Report prepared: encrypted and signed");
        }
    }

    private SecuredReport createSecuredReport (BenchmarkOverviewReport report){
        SecuredReport securedReport = new SecuredReport() ;
        securedReport.setReport(JSONUtils.marshalToJson(report));
        return securedReport ;
    }

    private Double getScoreFromJMHSecondaryResult (RunResult result, String key){
        if (result != null && result.getSecondaryResults() != null){
            for (String keyValue:result.getSecondaryResults().keySet()) {
//                System.out.println(keyValue+" : "+key);
                if (keyValue.contains(key)){
//                    System.out.println("--------------------------Value-------------------------");
//                    System.out.println(key);
//                    System.out.println("--------------------------Value-------------------------");
                    if(result.getSecondaryResults().get(keyValue)!= null) {
                        return result.getSecondaryResults().get(keyValue).getScore();
                    }
                }
            }
        }
        return null ;
    }


}
