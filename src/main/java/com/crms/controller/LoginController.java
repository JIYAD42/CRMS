package com.crms.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.crms.database.DatabaseHelper;
import com.crms.model.Admin;
import com.crms.model.Officer;
import com.crms.model.User;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.scene.Node;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    private User loggedInUser;

    /**
     * Authenticate user against the database.
     * Returns the role as stored in DB (e.g., "ADMIN", "OFFICER") or null if invalid.
     */
    public static String authenticateUser(String userId, String passwordHash) {
        String role = null;

        String sql = "SELECT role FROM users WHERE user_id = ? AND password_hash = ? AND is_active = TRUE";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            stmt.setString(2, passwordHash);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                role = rs.getString("role");
                System.out.println("User role: " + role);
            } else {
                System.out.println("Invalid credentials or inactive user");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return role;
    }

    /**
     * Handle login button click.
     */
    public void handleLoginButton(ActionEvent event) {
        String userId = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        // TODO: Replace with actual hashed password logic
        String passwordHash = password; // placeholder

        String role = authenticateUser(userId, passwordHash);

        if (role == null) {
            showError("Invalid credentials or inactive user");
            return;
        }

        String name = getUserNameFromDB(userId);

        switch (role.toUpperCase()) {
            case "ADMIN":
                loggedInUser = new Admin(userId, name);
                logLogin(userId, "Admin login"); // <-- audit log
                loggedInUser.login();
                openAdminDashboard(event, (Admin) loggedInUser);
                break;

            case "OFFICER":
                loggedInUser = new Officer(userId, name);
                logLogin(userId, "Officer login"); // <-- audit log
                loggedInUser.login();
                openOfficerDashboard(event, (Officer) loggedInUser);
                break;

            default:
                showError("Unknown role: " + role);
                return;
        }
    }

    /**
     * Insert a login action into audit_logs table.
     */
    private void logLogin(String userId, String action) {
        String sql = "INSERT INTO audit_logs (user_id, action) VALUES (?, ?)";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            ps.setString(2, action);
            ps.executeUpdate();
            System.out.println("Audit log inserted for " + userId);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieve user's display name from DB.
     */
    private String getUserNameFromDB(String userId) {
        String sql = "SELECT name FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void openAdminDashboard(ActionEvent event, Admin admin) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/crms/views/AdminDashboard.fxml"));
            Parent root = loader.load();

            com.crms.controller.AdminDashboardController controller = loader.getController();
            controller.initData(admin);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Admin Dashboard - CRMS");
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openOfficerDashboard(ActionEvent event, Officer officer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/crms/views/OfficerDashboard.fxml"));
            Parent root = loader.load();

            com.crms.controller.OfficerDashboardController controller = loader.getController();
            controller.initData(officer);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Officer Dashboard - CRMS");
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Login Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
