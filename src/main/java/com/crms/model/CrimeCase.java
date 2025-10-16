package com.crms.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import java.util.ArrayList;
import java.util.List;

public class CrimeCase {
    private int caseID;
    private StringProperty title;
    private StringProperty status;
    private List<Suspect> suspects;
    private Officer assignedOfficer;

    // Constructor with title only (default status "Open")
    public CrimeCase(int caseID, String title) {
        this(caseID, title, "Open");
    }

    // Constructor with title and status
    public CrimeCase(int caseID, String title, String status) {
        this.caseID = caseID;
        this.title = new SimpleStringProperty(title);
        this.status = new SimpleStringProperty(status);
        this.suspects = new ArrayList<>();
    }

    

    // ---------------- Status Methods ----------------
    public String getStatus() {
        return status.get();
    }

    public void setStatus(String status) {
        this.status.set(status);
    }

    public void updateStatus(String status) {
        setStatus(status);
    }

    public void closeCase() {
        setStatus("Closed");
    }

    public StringProperty statusProperty() {
        return status;
    }

    // ---------------- Title Methods ----------------
    public String getTitle() {
        return title.get();
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public StringProperty titleProperty() {
        return title;
    }

    // ---------------- Suspects ----------------
    public void addSuspect(Suspect suspect) {
        suspects.add(suspect);
    }

    public List<Suspect> getSuspects() {
        return suspects;
    }

    // ---------------- Assigned Officer ----------------
    public Officer getAssignedOfficer() {
        return assignedOfficer;
    }

    public void setAssignedOfficer(Officer officer) {
        this.assignedOfficer = officer;
    }

    // ---------------- Case ID ----------------
    public int getCaseID() {
        return caseID;
    }
}
