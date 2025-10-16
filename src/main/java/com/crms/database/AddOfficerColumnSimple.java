package com.crms.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class AddOfficerColumnSimple {

    public static void main(String[] args) {
      
        String url = "jdbc:mysql://root:cSCQKFHfZgrGIuqNluixhVjdtOVYQtRU@centerbeam.proxy.rlwy.net:43099/railway";
        String user = "root";
        String password = "cSCQKFHfZgrGIuqNluixhVjdtOVYQtRU";

        String addColumnSQL = "ALTER TABLE crime_reports ADD COLUMN officer_id INT;";
        String addFKSQL = "ALTER TABLE crime_reports " +
                          "ADD CONSTRAINT fk_officer FOREIGN KEY (officer_id) REFERENCES officers(officer_id);";

        try {
            // Load MySQL driver (optional for modern JDBC but safe)
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection conn = DriverManager.getConnection(url, user, password);
                 Statement stmt = conn.createStatement()) {

                System.out.println("Connected to Railway MySQL.");

                // Add column (ignore error if already exists)
                try {
                    stmt.executeUpdate(addColumnSQL);
                    System.out.println("column 'officer_id' added to 'crime_reports'.");
                } catch (Exception e) {
                    System.out.println("Column might already exist: " + e.getMessage());
                }

                // Add foreign key constraint
                try {
                    stmt.executeUpdate(addFKSQL);
                    System.out.println("Foreign key constraint added successfully.");
                } catch (Exception e) {
                    System.out.println("Constraint might already exist: " + e.getMessage());
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Migration complete.");
    }
}
