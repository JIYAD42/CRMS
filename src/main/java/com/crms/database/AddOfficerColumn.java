package com.crms.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class AddOfficerColumn {

    public static void main(String[] args) {

        // --- JDBC URL, username, password ---
        String url = "jdbc:mysql://root:cSCQKFHfZgrGIuqNluixhVjdtOVYQtRU@centerbeam.proxy.rlwy.net:43099/railway";
        String user = "root";
        String password = "cSCQKFHfZgrGIuqNluixhVjdtOVYQtRU";

        // --- SQL commands ---
        String addColumnSQL = "ALTER TABLE crime_reports ADD COLUMN officer_id INT;";
        String addFKSQL = "ALTER TABLE crime_reports "
                        + "ADD CONSTRAINT fk_officer "
                        + "FOREIGN KEY (officer_id) REFERENCES officers(officer_id);";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            System.out.println("Adding column officer_id...");
            stmt.executeUpdate(addColumnSQL);
            System.out.println("Column added successfully.");

            System.out.println("Adding foreign key constraint...");
            stmt.executeUpdate(addFKSQL);
            System.out.println("Foreign key added successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
