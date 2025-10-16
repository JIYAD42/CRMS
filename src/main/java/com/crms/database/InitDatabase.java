package com.crms.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class InitDatabase {

    private static final String DB_URL = "jdbc:sqlite:crms.db"; // SQLite DB file

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            // -------------------
            // Users table
            // -------------------
            String createUsers = "CREATE TABLE IF NOT EXISTS users (" +
                    "user_id TEXT PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "role TEXT NOT NULL," +  // 'ADMIN' or 'OFFICER'
                    "password_hash TEXT NOT NULL," +
                    "is_active INTEGER DEFAULT 1," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");";
            stmt.execute(createUsers);

            // Sample users
            stmt.execute("INSERT OR IGNORE INTO users (user_id, name, role, password_hash) VALUES " +
                    "('admin01', 'Administrator', 'ADMIN', 'admin123hashed')," +
                    "('officer01', 'Officer One', 'OFFICER', 'officer123hashed');");

            // -------------------
            // Cases table
            // -------------------
            String createCases = "CREATE TABLE IF NOT EXISTS cases (" +
                    "case_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "title TEXT NOT NULL," +
                    "status TEXT DEFAULT 'OPEN'," +
                    "assigned_officer_id TEXT," +
                    "report_details TEXT," +
                    "log_notes TEXT," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (assigned_officer_id) REFERENCES users(user_id)" +
                    ");";
            stmt.execute(createCases);

            // Sample case
            stmt.execute("INSERT OR IGNORE INTO cases (title, status, assigned_officer_id, report_details, log_notes) VALUES " +
                    "('Robbery Investigation', 'OPEN', 'officer01', 'Initial report filed.', 'Awaiting evidence.');");

            // -------------------
            // Crime reports table
            // -------------------
            String createReports = "CREATE TABLE IF NOT EXISTS crime_reports (" +
                    "report_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "case_id INTEGER," +
                    "assigned_officer_id TEXT," +
                    "crime_type TEXT NOT NULL," +
                    "incident_date TEXT NOT NULL," +
                    "incident_time TEXT," +
                    "location TEXT," +
                    "description TEXT," +
                    "reporter_name TEXT," +
                    "reporter_contact TEXT," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (case_id) REFERENCES cases(case_id)" +
                    "FOREIGN KEY (assigned_officer_id) REFERENCES users(user_id)" +
                    ");";
            stmt.execute(createReports);

            // -------------------
            // Suspects table
            // -------------------
            String createSuspects = "CREATE TABLE IF NOT EXISTS suspects (" +
                    "suspect_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "case_id INTEGER NOT NULL," +
                    "name TEXT NOT NULL," +
                    "age INTEGER," +
                    "gender TEXT," + // 'MALE','FEMALE','OTHER'
                    "address TEXT," +
                    "notes TEXT," +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (case_id) REFERENCES cases(case_id)" +
                    ");";
            stmt.execute(createSuspects);

            // -------------------
            // Audit logs table
            // -------------------
            String createAuditLogs = "CREATE TABLE IF NOT EXISTS audit_logs (" +
                    "log_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "user_id TEXT," +
                    "action TEXT," +
                    "action_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY (user_id) REFERENCES users(user_id)" +
                    ");";
            stmt.execute(createAuditLogs);

            System.out.println("All tables created successfully with sample users and a sample case!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
