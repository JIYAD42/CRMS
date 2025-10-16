package com.crms.controller;

import com.crms.database.DatabaseHelper;
import com.crms.model.CrimeCase;
import com.crms.model.CrimeReport;
import com.crms.model.Officer;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class CrimeReportingController implements Initializable {

    @FXML private ComboBox<String> crimeTypeComboBox;
    @FXML private DatePicker datePicker;
    @FXML private TextField timeField;
    @FXML private TextField locationField;
    @FXML private TextArea descriptionArea;
    @FXML private TextField reporterNameField;
    @FXML private TextField reporterContactField;
    @FXML private TextField linkedCaseIDField;
    @FXML private Label statusMessage;

    private Officer currentOfficer;
    private String currentUserId = "system";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        crimeTypeComboBox.getItems().setAll("THEFT","ASSAULT","VANDALISM","FRAUD","OTHER");

    }

    public void initData(Officer officer) {
        this.currentOfficer = officer;
    }

    @FXML
    private void handleBack(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/crms/views/OfficerDashboard.fxml"));
            Parent root = loader.load();

            OfficerDashboardController controller = loader.getController();
            controller.initData(currentOfficer);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.setTitle("Officer Dashboard - CRMS");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleReportSubmission(ActionEvent event) {
        clearStatus();

        String crimeType = crimeTypeComboBox.getValue();
        LocalDate incidentDate = datePicker.getValue();
        String timeText = timeField.getText();
        String location = trimToNull(locationField.getText());
        String description = trimToNull(descriptionArea.getText());
        String reporterName = trimToNull(reporterNameField.getText());
        String reporterContact = trimToNull(reporterContactField.getText());
        String linkedCaseText = trimToNull(linkedCaseIDField.getText());

        if (crimeType == null || crimeType.isBlank()) {
            setStatus("Please select a crime type.", true); return;
        }
        if (incidentDate == null) {
            setStatus("Please select the incident date.", true); return;
        }
        if (reporterName == null || reporterName.isBlank()) {
            setStatus("Please enter reporter name.", true); return;
        }

        Time incidentTimeSql = null;
        if (timeText != null && !timeText.isBlank()) {
            try {
                LocalTime lt = LocalTime.parse(timeText.length() <= 5 ? timeText + ":00" : timeText);
                incidentTimeSql = Time.valueOf(lt);
            } catch (Exception e) {
                setStatus("Invalid time format. Use HH:mm (24-hour).", true); return;
            }
        }

        Integer linkedCaseId = null;
        if (linkedCaseText != null && !linkedCaseText.isBlank()) {
            try {
                linkedCaseId = Integer.parseInt(linkedCaseText.trim());
                if (!caseExists(linkedCaseId)) {
                    setStatus("Linked case ID not found: " + linkedCaseId, true); return;
                }
            } catch (NumberFormatException nfe) {
                setStatus("Linked Case ID must be an integer.", true); return;
            }
        }

        try {
            int reportId = insertCrimeReport(
                    linkedCaseId, crimeType, Date.valueOf(incidentDate),
                    incidentTimeSql, location, description,
                    reporterName, reporterContact
            );
            if (reportId <= 0) { setStatus("Failed to save report.", true); return; }

            CrimeCase linkedCase = null;
            if (linkedCaseId != null) linkedCase = fetchCrimeCase(linkedCaseId);

            String reporterInfo = buildReporterInfoString(reporterName, reporterContact);
            CrimeReport modelReport = new CrimeReport(reportId, reporterInfo, linkedCase);

            if (!modelReport.validateReport()) {
                insertAuditLog(currentUserId, "Created report id=" + reportId + " but validation failed.");
                setStatus("Report saved but validation failed.", true); return;
            }

            insertAuditLog(currentUserId, "Created crime report with id=" + reportId);
            setStatus("Report registered successfully (Report ID: " + reportId + ").", false);
            clearFormFields();

        } catch (SQLException ex) {
            ex.printStackTrace();
            setStatus("Database error: " + ex.getMessage(), true);
        }
    }

    /* ----------------- Helpers ----------------- */

    public void clearStatus() {
        statusMessage.setText("");
        statusMessage.setStyle("-fx-text-fill: black;");
    }

    private void setStatus(String message, boolean error) {
        statusMessage.setText(message);
        statusMessage.setStyle(error ? "-fx-text-fill:red; -fx-font-weight:bold;" :
                                      "-fx-text-fill:green; -fx-font-weight:bold;");
    }

    private String trimToNull(String s) { return (s == null || s.trim().isEmpty()) ? null : s.trim(); }

    private String buildReporterInfoString(String name, String contact) {
        return "name=" + (name != null ? name : "") + ";contact=" + (contact != null ? contact : "");
    }

    private boolean caseExists(int caseId) {
        final String sql = "SELECT 1 FROM cases WHERE case_id=?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, caseId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    private int insertCrimeReport(Integer caseId, String crimeType, Date date, Time time,
                              String location, String description,
                              String reporterName, String reporterContact) throws SQLException {

    final String sql = "INSERT INTO crime_reports " +
            "(case_id, assigned_officer_id, crime_type, incident_date, incident_time, location, description, reporter_name, reporter_contact) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    try (Connection conn = DatabaseHelper.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        if (caseId != null) ps.setInt(1, caseId); else ps.setNull(1, Types.INTEGER);

        // Add current officer
        if (currentOfficer != null) ps.setString(2, currentOfficer.getUserID());
        else ps.setNull(2, Types.VARCHAR);

        ps.setString(3, crimeType);
        ps.setDate(4, date);
        if (time != null) ps.setTime(5, time); else ps.setNull(5, Types.TIME);
        if (location != null) ps.setString(6, location); else ps.setNull(6, Types.VARCHAR);
        if (description != null) ps.setString(7, description); else ps.setNull(7, Types.CLOB);
        if (reporterName != null) ps.setString(8, reporterName); else ps.setNull(8, Types.VARCHAR);
        if (reporterContact != null) ps.setString(9, reporterContact); else ps.setNull(9, Types.VARCHAR);

        int affected = ps.executeUpdate();
        if (affected == 0) return -1;

        try (ResultSet rs = ps.getGeneratedKeys()) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }
}


    private CrimeCase fetchCrimeCase(int caseId) {
        final String sql = "SELECT case_id, title, status FROM cases WHERE case_id=?";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String title = rs.getString("title");
                    String status = rs.getString("status");
                    return new CrimeCase(caseId, title, status);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    private void insertAuditLog(String userId, String action) {
        final String sql = "INSERT INTO audit_logs (user_id, action) VALUES (?, ?)";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId != null ? userId : "system");
            ps.setString(2, action);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private List<String> fetchCrimeTypesFromDb() {
        List<String> types = new ArrayList<>();
        final String sql = "SELECT DISTINCT crime_type FROM crime_reports WHERE crime_type IS NOT NULL";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) { types.add(rs.getString(1)); }
        } catch (SQLException e) { e.printStackTrace(); }
        return types;
    }

    private void clearFormFields() {
        crimeTypeComboBox.getSelectionModel().clearSelection();
        datePicker.setValue(null);
        timeField.clear();
        locationField.clear();
        descriptionArea.clear();
        reporterNameField.clear();
        reporterContactField.clear();
        linkedCaseIDField.clear();
    }
}
