package com.crms.model;

public abstract class User {
    protected String userID;
    protected String name;
    protected String role;

    public User(String userID, String name, String role) {
        this.userID = userID;
        this.name = name;
        this.role = role;
    }

    public abstract void login();
    public abstract void logout();

    // Getters
    public String getUserID() { return userID; }
    public String getName() { return name; }
    public String getRole() { return role; }

    @Override
    public String toString() {
        return String.format("[%s] %s (ID: %d)", role, name, userID);
    }
}
