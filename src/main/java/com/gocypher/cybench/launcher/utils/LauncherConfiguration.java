package com.gocypher.cybench.launcher.utils;

public class LauncherConfiguration {
    private int forks = 1;
    private int threads = 1;
    private int measurementIterations = 1;
    private int warmUpIterations = 1;
    private int warmUpSeconds = 5;
    private double expectedScore = -1.0d;
    private boolean shouldSendReportToCyBench = false;
    private boolean shouldStoreReportToFileSystem = true;
    private String reportUploadStatus = "public";
    private String reportsFolder = "";
    private String reportName = "CyBench Report";
    private String userBenchmarkMetadata = "";
    private String userProperties = "";

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
}
