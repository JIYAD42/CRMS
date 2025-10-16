package com.crms.controller;

import com.crms.database.DatabaseHelper;
import com.crms.model.Admin;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;

public class ReportsAuditController {

    // ===== FXML Bindings =====
    @FXML private ComboBox<String> reportTypeComboBox;
    @FXML private DatePicker dateFromPicker;
    @FXML private DatePicker dateToPicker;
    @FXML private Label reportTitleLabel;

    @FXML private TableView<CrimeCaseRow> reportResultsTable;
    @FXML private TableView<AuditLogEntry> auditLogTable;
    @FXML private TableView<CrimeReportRow> crimeReportsTable;

    // Crime Reports Table Columns (must exist in FXML)
    @FXML private TableColumn<CrimeReportRow, Integer> reportIdCol;
    @FXML private TableColumn<CrimeReportRow, String> crimeTypeCol;
    @FXML private TableColumn<CrimeReportRow, String> officerCol;
    @FXML private TableColumn<CrimeReportRow, String> statusCol;
    @FXML private TableColumn<CrimeReportRow, Timestamp> createdCol;

    @FXML private TextField auditUserFilterField;

    // ===== Data Lists =====
    private final ObservableList<CrimeReportRow> crimeReportList = FXCollections.observableArrayList();
    private final ObservableList<CrimeCaseRow> reportCaseList = FXCollections.observableArrayList();
    private final ObservableList<AuditLogEntry> auditLogList = FXCollections.observableArrayList();

    private Timeline refreshTimeline;
    private Admin currentAdmin;

    // ===== INITIALIZE =====
    @FXML
    public void initialize() {
        initializeCrimeReportsTable();
        initializeReportResultsTable();
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
            controller.initData(currentAdmin); // pass current admin as officer

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Admin Dashboard - CRMS");
            stage.show();
        } catch (IOException e) {
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
        crimeReportList.clear();
        String sql = """
                SELECT report_id, crime_type, assigned_officer_id, status, created_at
                FROM crime_reports
                WHERE status = 'SUBMITTED'
                ORDER BY created_at DESC
                """;

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                crimeReportList.add(new CrimeReportRow(
                        rs.getInt("report_id"),
                        rs.getString("crime_type"),
                        rs.getString("assigned_officer_id"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== Reports Table =====
    private void initializeReportResultsTable() {
        TableColumn<CrimeCaseRow, Integer> idCol = new TableColumn<>("Case ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("caseID"));

        TableColumn<CrimeCaseRow, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));

        TableColumn<CrimeCaseRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        TableColumn<CrimeCaseRow, String> officerCol = new TableColumn<>("Assigned Officer");
        officerCol.setCellValueFactory(new PropertyValueFactory<>("assignedOfficer"));

        TableColumn<CrimeCaseRow, Timestamp> createdCol = new TableColumn<>("Created At");
        createdCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));

        TableColumn<CrimeCaseRow, Timestamp> updatedCol = new TableColumn<>("Updated At");
        updatedCol.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));

        reportResultsTable.getColumns().setAll(idCol, titleCol, statusCol, officerCol, createdCol, updatedCol);
        reportResultsTable.setItems(reportCaseList);
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
    }

    // ===== Report Generation =====
    @FXML
    public void handleGenerateReport(ActionEvent event) {
        reportCaseList.clear();
        String reportType = reportTypeComboBox.getValue();
        LocalDate from = dateFromPicker.getValue();
        LocalDate to = dateToPicker.getValue();

        String sql = "SELECT case_id, title, status, assigned_officer_id, created_at, updated_at FROM cases WHERE 1=1";
        if (from != null) sql += " AND created_at >= '" + from + "'";
        if (to != null) sql += " AND created_at <= '" + to + "'";

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("case_id");
                String title = rs.getString("title");
                String status = rs.getString("status");
                String officerName = fetchOfficerName(rs.getString("assigned_officer_id"));
                Timestamp createdAt = rs.getTimestamp("created_at");
                Timestamp updatedAt = rs.getTimestamp("updated_at");

                reportCaseList.add(new CrimeCaseRow(id, title, status, officerName, createdAt, updatedAt));
            }
            reportTitleLabel.setText("Generated Report: " + (reportType != null ? reportType : "All Cases"));
            writeAuditLog("Generated report: " + (reportType != null ? reportType : "All Cases"));

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== Export to Excel =====
    @FXML
    public void handleExportReport(ActionEvent event) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Report");
            Row header = sheet.createRow(0);
            String[] columns = {"Case ID", "Title", "Status", "Assigned Officer", "Created At", "Updated At"};
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);

            int rowNum = 1;
            for (CrimeCaseRow c : reportCaseList) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(c.getCaseID());
                row.createCell(1).setCellValue(c.getTitle());
                row.createCell(2).setCellValue(c.getStatus());
                row.createCell(3).setCellValue(c.getAssignedOfficer());
                row.createCell(4).setCellValue(c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
                row.createCell(5).setCellValue(c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : "");
            }

            try (FileOutputStream fileOut = new FileOutputStream("Report.xlsx")) {
                workbook.write(fileOut);
            }
            writeAuditLog("Exported report to Excel");
            showAlert(Alert.AlertType.INFORMATION, "Success", "Report exported successfully.");

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Export failed: " + e.getMessage());
        }
    }

    // ===== Register Case from Report =====
    @FXML
    public void handleRegisterCase(ActionEvent event) {
        CrimeReportRow selected = crimeReportsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a report to register as a case.");
            return;
        }

        try (Connection conn = DatabaseHelper.getConnection()) {
            conn.setAutoCommit(false);

            // Update report status
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE crime_reports SET status = 'REGISTERED' WHERE report_id = ?")) {
                ps.setInt(1, selected.getReportID());
                ps.executeUpdate();
            }

            // Create new case
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

    // ===== Audit Filter =====
    @FXML
    public void handleFilterAuditLog(ActionEvent event) {
        auditLogList.clear();
        String userFilter = auditUserFilterField.getText();
        String sql = "SELECT user_id, action, action_time FROM audit_logs";
        if (userFilter != null && !userFilter.isEmpty()) sql += " WHERE user_id=?";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userFilter != null && !userFilter.isEmpty()) ps.setString(1, userFilter);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    auditLogList.add(new AuditLogEntry(
                            rs.getString("user_id"),
                            rs.getString("action"),
                            rs.getTimestamp("action_time")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== Utility Methods =====
    private void refreshTables() {
        refreshCrimeReports();
        handleGenerateReport(null);
        handleFilterAuditLog(null);
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

    private String fetchOfficerName(String userID) {
        if (userID == null || userID.isEmpty()) return "";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT name FROM users WHERE user_id=?")) {
            ps.setString(1, userID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
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
    public static class CrimeCaseRow {
        private final int caseID;
        private final SimpleStringProperty title;
        private final SimpleStringProperty status;
        private final SimpleStringProperty assignedOfficer;
        private final SimpleObjectProperty<Timestamp> createdAt;
        private final SimpleObjectProperty<Timestamp> updatedAt;

        public CrimeCaseRow(int caseID, String title, String status, String officer, Timestamp createdAt, Timestamp updatedAt) {
            this.caseID = caseID;
            this.title = new SimpleStringProperty(title);
            this.status = new SimpleStringProperty(status);
            this.assignedOfficer = new SimpleStringProperty(officer);
            this.createdAt = new SimpleObjectProperty<>(createdAt);
            this.updatedAt = new SimpleObjectProperty<>(updatedAt);
        }

        public int getCaseID() { return caseID; }
        public String getTitle() { return title.get(); }
        public String getStatus() { return status.get(); }
        public String getAssignedOfficer() { return assignedOfficer.get(); }
        public Timestamp getCreatedAt() { return createdAt.get(); }
        public Timestamp getUpdatedAt() { return updatedAt.get(); }
    }

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
