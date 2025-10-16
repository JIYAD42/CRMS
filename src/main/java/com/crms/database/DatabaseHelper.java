package com.crms.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseHelper {
    private static final String DB_URL = "jdbc:sqlite:crms.db"; // stored in project root

    static {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            System.out.println("SQLite driver loaded successfully!");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.err.println("Failed to load SQLite JDBC driver!");
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void closePool() {
        // SQLite doesn’t use a pool, so nothing to close
    }
}
