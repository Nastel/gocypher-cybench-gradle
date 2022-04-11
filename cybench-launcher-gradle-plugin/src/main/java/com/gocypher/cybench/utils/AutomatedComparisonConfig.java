package com.gocypher.cybench.utils;

public class AutomatedComparisonConfig {
    private String scope;
    private String method;
    private String threshold;
    private String compareVersion;
    private Double percentChangeAllowed;
    private Double deviationsAllowed;
    private Integer numLatestReports;
    private Integer anomaliesAllowed;

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getScope() {
        return scope;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }

    public void setThreshold(String threshold) {
        this.threshold = threshold;
    }

    public String getThreshold() {
        return threshold;
    }

    public void setCompareVersion(String compareVersion) {
        this.compareVersion = compareVersion;
    }

    public String getCompareVersion() {
        return compareVersion;
    }

    public void setDeviationsAllowed(Double deviationsAllowed) {
        this.deviationsAllowed = deviationsAllowed;
    }

    public Double getDeviationsAllowed() {
        return deviationsAllowed;
    }

    public void setPercentChangeAllowed(Double percentChangeAllowed) {
        this.percentChangeAllowed = percentChangeAllowed;
    }

    public Double getPercentChangeAllowed() {
        return percentChangeAllowed;
    }

    public void setNumLatestReports(Integer numLatestReports) {
        this.numLatestReports = numLatestReports;
    }

    public Integer getNumLatestReports() {
        return numLatestReports;
    }

    public void setAnomaliesAllowed(Integer anomaliesAllowed) {
        this.anomaliesAllowed = anomaliesAllowed;
    }

    public Integer getAnomaliesAllowed() {
        return anomaliesAllowed;
    }

}
