package com.crms.model;

public class Admin extends User {

    public Admin(String userID, String name) {
        super(userID, name, "Admin");
    }

    @Override
    public void login() {
        System.out.println("Admin " + name + " logged in.");
    }

    @Override
    public void logout() {
        System.out.println("Admin " + name + " logged out.");
    }

    public void addUser(User user) {
        System.out.println("Added new user: " + user);
    }

    public void assignCase(CrimeCase crimeCase, Officer officer) {
        System.out.println("Assigned " + crimeCase.getTitle() + " to Officer " + officer.getName());
        crimeCase.setAssignedOfficer(officer);
    }

    public void generateReport() {
        System.out.println("Generating system report...");
    }
}
