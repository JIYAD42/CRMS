package com.crms.controller;

import com.crms.database.DatabaseHelper;
import com.crms.model.Admin;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for Admin Dashboard.
 * Handles dashboard counts, audit logs, and admin actions.
 */
public class AdminDashboardController implements Initializable {

    @FXML private Label totalUsersCount;
    @FXML private Label openCasesCount;
    @FXML private Label newReportsCount;
    @FXML private Label recentActivityLog;

    private Admin currentAdmin;
    private Timeline refreshTimeline;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Nothing yet — actual data loads in initData(Admin)
    }

    /**
     * Initialize dashboard with logged-in Admin data.
     */
    public void initData(Admin admin) {
        stopAutoRefresh();
        this.currentAdmin = admin;
        System.out.println("Logged in as Admin: " + admin.getName());

        refreshDashboard(); // Initial load

        // Auto-refresh every 10 seconds
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> refreshDashboard()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void stopAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
    }

    /**
     * Refresh all dashboard sections.
     */
    private void refreshDashboard() {
        loadUserStats();
        loadCaseStats();
        loadReportStats();
        loadRecentAuditLogs();
    }

    /* ------------------------
       Dashboard Data Loaders
       ------------------------ */

    private void loadUserStats() {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) totalUsersCount.setText(String.valueOf(rs.getInt(1)));

        } catch (SQLException e) {
            e.printStackTrace();
            totalUsersCount.setText("Err");
        }
    }

    private void loadCaseStats() {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM cases WHERE status='OPEN'");
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) openCasesCount.setText(String.valueOf(rs.getInt(1)));

        } catch (SQLException e) {
            e.printStackTrace();
            openCasesCount.setText("Err");
        }
    }

    private void loadReportStats() {
        // SQLite uses DATETIME('now', '-1 day') instead of NOW() - INTERVAL
        String sql = "SELECT COUNT(*) FROM crime_reports WHERE created_at >= DATETIME('now', '-1 day')";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) newReportsCount.setText(String.valueOf(rs.getInt(1)));

        } catch (SQLException e) {
            e.printStackTrace();
            newReportsCount.setText("Err");
        }
    }

    private void loadRecentAuditLogs() {
        List<String> logs = new ArrayList<>();
        String sql = "SELECT user_id, action, action_time FROM audit_logs ORDER BY action_time DESC LIMIT 5";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String user = rs.getString("user_id");
                String action = rs.getString("action");
                Timestamp ts = rs.getTimestamp("action_time");
                logs.add(ts + " - " + user + ": " + action);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (logs.isEmpty()) recentActivityLog.setText("[No recent activity]");
        else recentActivityLog.setText(String.join("\n", logs));
    }

    /* ------------------------
       Navigation Actions
       ------------------------ */

    @FXML
    public void goToUserManagement(ActionEvent event) {
        insertAuditLog("Navigated to User Management");
        switchScene(event, "/com/crms/views/UserManagement.fxml", "User Management - CRMS");
    }

    @FXML
    public void goToReportsAudit(ActionEvent event) {
        insertAuditLog("Navigated to Reports & Audit");
        switchScene(event, "/com/crms/views/ReportsAudit.fxml", "Reports & Audit - CRMS");
    }

    @FXML
    public void generateReport(ActionEvent event) {
        insertAuditLog("Generated Summary Report");
        System.out.println("Generating Summary Report...");
    }

    /* ------------------------
       Utility Methods
       ------------------------ */

    private void insertAuditLog(String action) {
        final String sql = "INSERT INTO audit_logs (user_id, action) VALUES (?, ?)";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, currentAdmin != null ? currentAdmin.getUserID() : "SYSTEM");
            ps.setString(2, action);
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }

        loadRecentAuditLogs();
    }

    private void switchScene(ActionEvent event, String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            // Pass admin to next controller if it has initData(Admin)
            Object controller = loader.getController();
            try {
                controller.getClass().getMethod("initData", Admin.class).invoke(controller, currentAdmin);
            } catch (Exception ignored) {}

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
