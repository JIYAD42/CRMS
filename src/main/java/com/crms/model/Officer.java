package com.crms.model;

public class Officer extends User {

    public Officer(String userID, String name) {
        super(userID, name, "Officer");
    }

    @Override
    public void login() {
        System.out.println("Officer " + name + " logged in.");
    }

    @Override
    public void logout() {
        System.out.println("Officer " + name + " logged out.");
    }

    public void fileCrimeReport(CrimeReport report) {
        System.out.println("Officer " + name + " filed report: " + report.getReportID());
    }

    public void updateCaseStatus(CrimeCase crimeCase, String status) {
        crimeCase.updateStatus(status);
        System.out.println("Case " + crimeCase.getCaseID() + " updated to: " + status);
    }

    public void searchRecords(String query) {
        System.out.println("Searching for records matching: " + query);
    }
}
