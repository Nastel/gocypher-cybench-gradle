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

package com.gocypher.cybench.utils;

public class LauncherConfiguration {
    public static final String DEFAULT_NAME = "CyBench Report";
    private int forks = 1;
    private int threads = 1;
    private int measurementIterations = 1;
    private int measurementSeconds = 10;
    private int warmUpIterations = 1;
    private int warmUpSeconds = 5;
    private double expectedScore = -1.0d;
    private boolean shouldSendReportToCyBench = false;
    private boolean shouldStoreReportToFileSystem = true;
    private String reportUploadStatus = "public";
    private String reportsFolder = "";
    private String reportName = DEFAULT_NAME;
    private String userBenchmarkMetadata = "";
    private String userProperties = "";

    private boolean useCyBenchBenchmarkSettings = true;
    
    private boolean shouldFailBuildOnReportDeliveryFailure = false;

    private boolean skip =  false;

    public String getUserProperties() {
        return userProperties;
    }

    public void setUserProperties(String userProperties) {
        this.userProperties = userProperties;
    }

    public int getForks() {
        return forks;
    }

    public void setForks(int forks) {
        this.forks = forks;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getMeasurementIterations() {
        return measurementIterations;
    }

    public void setMeasurementIterations(int measurementIterations) {
        this.measurementIterations = measurementIterations;
    }

    public int getWarmUpIterations() {
        return warmUpIterations;
    }

    public void setWarmUpIterations(int warmUpIterations) {
        this.warmUpIterations = warmUpIterations;
    }

    public int getWarmUpSeconds() {
        return warmUpSeconds;
    }

    public void setWarmUpSeconds(int warmUpSeconds) {
        this.warmUpSeconds = warmUpSeconds;
    }

    public double getExpectedScore() {
        return expectedScore;
    }

    public void setExpectedScore(double expectedScore) {
        this.expectedScore = expectedScore;
    }

    public boolean isShouldSendReportToCyBench() {
        return shouldSendReportToCyBench;
    }

    public void setShouldSendReportToCyBench(boolean shouldSendReportToCyBench) {
        this.shouldSendReportToCyBench = shouldSendReportToCyBench;
    }

    public boolean isShouldStoreReportToFileSystem() {
        return shouldStoreReportToFileSystem;
    }

    public void setShouldStoreReportToFileSystem(boolean shouldStoreReportToFileSystem) {
        this.shouldStoreReportToFileSystem = shouldStoreReportToFileSystem;
    }

    public String getReportUploadStatus() {
        return reportUploadStatus;
    }

    public void setReportUploadStatus(String reportUploadStatus) {
        this.reportUploadStatus = reportUploadStatus;
    }

    public String getReportsFolder() {
        return reportsFolder;
    }

    public void setReportsFolder(String reportsFolder) {
        this.reportsFolder = reportsFolder;
    }

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public String getUserBenchmarkMetadata() {
        return userBenchmarkMetadata;
    }

    public void setUserBenchmarkMetadata(String userBenchmarkMetadata) {
        this.userBenchmarkMetadata = userBenchmarkMetadata;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public int getMeasurementSeconds() {
        return measurementSeconds;
    }

    public void setMeasurementSeconds(int measurementSeconds) {
        this.measurementSeconds = measurementSeconds;
    }

    public boolean isShouldFailBuildOnReportDeliveryFailure() {
        return shouldFailBuildOnReportDeliveryFailure;
    }

    public void setShouldFailBuildOnReportDeliveryFailure(boolean shouldFailBuildOnReportDeliveryFailure) {
        this.shouldFailBuildOnReportDeliveryFailure = shouldFailBuildOnReportDeliveryFailure;
    }

    public boolean isUseCyBenchBenchmarkSettings() {
        return useCyBenchBenchmarkSettings;
    }

    public void setUseCyBenchBenchmarkSettings(boolean useCyBenchBenchmarkSettings) {
        this.useCyBenchBenchmarkSettings = useCyBenchBenchmarkSettings;
    }
}
