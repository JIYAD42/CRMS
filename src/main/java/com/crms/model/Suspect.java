package com.crms.model;

public class Suspect {
    private int suspectID;
    private String name;
    private int age;
    private String criminalHistory;

    public Suspect(int suspectID, String name, int age, String history) {
        this.suspectID = suspectID;
        this.name = name;
        this.age = age;
        this.criminalHistory = history;
    }

    public String getHistory() {
        return criminalHistory;
    }

    public int getSuspectID() { return suspectID; }
    public String getName() { return name; }
    public int getAge() { return age; }
}
