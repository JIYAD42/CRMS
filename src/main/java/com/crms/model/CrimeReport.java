package com.crms.model;

public class CrimeReport {
    private int reportID;
    private String reporterInfo;
    private CrimeCase linkedCase;

    public CrimeReport(int reportID, String reporterInfo, CrimeCase linkedCase) {
        this.reportID = reportID;
        this.reporterInfo = reporterInfo;
        this.linkedCase = linkedCase;
    }

    public boolean validateReport() {
        return reporterInfo != null && !reporterInfo.isBlank();
    }

    // Getters
    public int getReportID() { return reportID; }
    public String getReporterInfo() { return reporterInfo; }
    public CrimeCase getLinkedCase() { return linkedCase; }
}
