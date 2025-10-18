package com.crms.controller;

import com.crms.database.DatabaseHelper;
import com.crms.model.Admin;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.sql.*;

public class ReportsAuditController {

    // ===== FXML Bindings =====
    @FXML private TableView<AuditLogEntry> auditLogTable;
    @FXML private TableView<CrimeReportRow> crimeReportsTable;
    @FXML private TableColumn<CrimeReportRow, Integer> reportIdCol;
    @FXML private TableColumn<CrimeReportRow, String> crimeTypeCol;
    @FXML private TableColumn<CrimeReportRow, String> officerCol;
    @FXML private TableColumn<CrimeReportRow, String> statusCol;
    @FXML private TableColumn<CrimeReportRow, Timestamp> createdCol;
    @FXML private TextField auditUserFilterField;

    // ===== Data Lists =====
    private final ObservableList<CrimeReportRow> crimeReportList = FXCollections.observableArrayList();
    private final ObservableList<AuditLogEntry> auditLogList = FXCollections.observableArrayList();

    private Timeline refreshTimeline;
    private Admin currentAdmin;

    // ===== INITIALIZE =====
    @FXML
    public void initialize() {
        initializeCrimeReportsTable();
        initializeAuditLogTable();

        // Auto-refresh every 10 seconds
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> refreshTables()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    @FXML
    private void handleBack(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/crms/views/AdminDashboard.fxml"));
            Parent root = loader.load();

            AdminDashboardController controller = loader.getController();
            controller.initData(currentAdmin);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Admin Dashboard - CRMS");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== Crime Reports Table =====
    private void initializeCrimeReportsTable() {
        reportIdCol.setCellValueFactory(new PropertyValueFactory<>("reportID"));
        crimeTypeCol.setCellValueFactory(new PropertyValueFactory<>("crimeType"));
        officerCol.setCellValueFactory(new PropertyValueFactory<>("officerID"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        createdCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        crimeReportsTable.setItems(crimeReportList);
        refreshCrimeReports();
    }

    private void refreshCrimeReports() {
        Task<ObservableList<CrimeReportRow>> task = new Task<>() {
            @Override
            protected ObservableList<CrimeReportRow> call() throws Exception {
                ObservableList<CrimeReportRow> list = FXCollections.observableArrayList();
                String sql = "SELECT report_id, crime_type, assigned_officer_id, status, created_at " +
                             "FROM crime_reports WHERE status = 'SUBMITTED' ORDER BY created_at DESC";

                try (Connection conn = DatabaseHelper.getConnection();
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        list.add(new CrimeReportRow(
                                rs.getInt("report_id"),
                                rs.getString("crime_type"),
                                rs.getString("assigned_officer_id"),
                                rs.getString("status"),
                                rs.getTimestamp("created_at")
                        ));
                    }
                }
                return list;
            }
        };

        task.setOnSucceeded(e -> crimeReportList.setAll(task.getValue()));
        task.setOnFailed(e -> task.getException().printStackTrace());
        new Thread(task).start();
    }

    // ===== Audit Log Table =====
    private void initializeAuditLogTable() {
        TableColumn<AuditLogEntry, String> userCol = new TableColumn<>("User ID");
        userCol.setCellValueFactory(new PropertyValueFactory<>("userID"));

        TableColumn<AuditLogEntry, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(new PropertyValueFactory<>("action"));

        TableColumn<AuditLogEntry, Timestamp> timeCol = new TableColumn<>("Action Time");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("actionTime"));

        auditLogTable.getColumns().setAll(userCol, actionCol, timeCol);
        auditLogTable.setItems(auditLogList);
        refreshAuditLogs();
    }

    @FXML
    public void handleFilterAuditLog(ActionEvent event) {
        refreshAuditLogs();
    }

    private void refreshAuditLogs() {
        Task<ObservableList<AuditLogEntry>> task = new Task<>() {
            @Override
            protected ObservableList<AuditLogEntry> call() throws Exception {
                ObservableList<AuditLogEntry> list = FXCollections.observableArrayList();
                String userFilter = auditUserFilterField.getText();
                String sql = "SELECT user_id, action, action_time FROM audit_logs";
                if (userFilter != null && !userFilter.isEmpty()) sql += " WHERE user_id=?";

                try (Connection conn = DatabaseHelper.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    if (userFilter != null && !userFilter.isEmpty()) ps.setString(1, userFilter);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(new AuditLogEntry(
                                    rs.getString("user_id"),
                                    rs.getString("action"),
                                    rs.getTimestamp("action_time")
                            ));
                        }
                    }
                }
                return list;
            }
        };

        task.setOnSucceeded(e -> auditLogList.setAll(task.getValue()));
        task.setOnFailed(e -> task.getException().printStackTrace());
        new Thread(task).start();
    }

    // ===== Register Case =====
    @FXML
    public void handleRegisterCase(ActionEvent event) {
        CrimeReportRow selected = crimeReportsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a report to register as a case.");
            return;
        }

        try (Connection conn = DatabaseHelper.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE crime_reports SET status = 'REGISTERED' WHERE report_id = ?")) {
                ps.setInt(1, selected.getReportID());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO cases (title, status, assigned_officer_id, created_at) VALUES (?, 'OPEN', ?, CURRENT_TIMESTAMP)")) {
                ps.setString(1, selected.getCrimeType());
                ps.setString(2, selected.getOfficerID());
                ps.executeUpdate();
            }

            conn.commit();
            writeAuditLog("Registered report " + selected.getReportID() + " as case");
            showAlert(Alert.AlertType.INFORMATION, "Success", "Report registered as new case.");
            refreshCrimeReports();

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Database error: " + e.getMessage());
        }
    }

    // ===== Refresh Both Tables =====
    private void refreshTables() {
        refreshCrimeReports();
        refreshAuditLogs();
    }

    private void writeAuditLog(String action) {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO audit_logs(user_id, action) VALUES (?, ?)")) {
            ps.setString(1, "SYSTEM");
            ps.setString(2, action);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void initData(Admin admin) {
        this.currentAdmin = admin;
    }

    // ===== Inner Data Classes =====
    public static class AuditLogEntry {
        private final SimpleStringProperty userID;
        private final SimpleStringProperty action;
        private final SimpleObjectProperty<Timestamp> actionTime;

        public AuditLogEntry(String userID, String action, Timestamp actionTime) {
            this.userID = new SimpleStringProperty(userID);
            this.action = new SimpleStringProperty(action);
            this.actionTime = new SimpleObjectProperty<>(actionTime);
        }

        public String getUserID() { return userID.get(); }
        public String getAction() { return action.get(); }
        public Timestamp getActionTime() { return actionTime.get(); }
    }

    public static class CrimeReportRow {
        private final int reportID;
        private final SimpleStringProperty crimeType;
        private final SimpleStringProperty officerID;
        private final SimpleStringProperty status;
        private final SimpleObjectProperty<Timestamp> createdAt;

        public CrimeReportRow(int reportID, String crimeType, String officerID, String status, Timestamp createdAt) {
            this.reportID = reportID;
            this.crimeType = new SimpleStringProperty(crimeType);
            this.officerID = new SimpleStringProperty(officerID);
            this.status = new SimpleStringProperty(status);
            this.createdAt = new SimpleObjectProperty<>(createdAt);
        }

        public int getReportID() { return reportID; }
        public String getCrimeType() { return crimeType.get(); }
        public String getOfficerID() { return officerID.get(); }
        public String getStatus() { return status.get(); }
        public Timestamp getCreatedAt() { return createdAt.get(); }
    }
}
