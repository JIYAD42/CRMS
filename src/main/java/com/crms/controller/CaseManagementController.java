package com.crms.controller;

import com.crms.database.DatabaseHelper;
import com.crms.model.CrimeCase;
import com.crms.model.Officer;
import com.crms.model.Suspect;

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

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CaseManagementController {

    @FXML private TableView<CrimeCase> casesTable;
    @FXML private TableColumn<CrimeCase, Integer> caseIdColumn;
    @FXML private TableColumn<CrimeCase, String> caseTitleColumn;
    @FXML private TableColumn<CrimeCase, String> caseStatusColumn;

    @FXML private Label caseIdLabel;
    @FXML private TextField caseTitleField;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private ComboBox<Officer> officerComboBox;
    @FXML private TextArea reportDetailsArea;
    @FXML private TextArea logNotesArea;
    @FXML private TableView<Suspect> suspectsTable;

    private Officer currentOfficer;
    private CrimeCase currentCase;
    private ObservableList<Suspect> suspectList = FXCollections.observableArrayList();
    private ObservableList<CrimeCase> caseList = FXCollections.observableArrayList();
    private String currentUserId = "system";

    @FXML
    public void initialize() {
        statusComboBox.getItems().setAll("Open", "In Progress", "Closed");
        officerComboBox.getItems().setAll(fetchOfficers());

        caseIdColumn.setCellValueFactory(new PropertyValueFactory<>("caseID"));
        caseTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        caseStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        casesTable.setItems(caseList);

        casesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldCase, newCase) -> {
            if (newCase != null) loadCase(newCase.getCaseID());
        });

        TableColumn<Suspect, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("suspectID"));
        TableColumn<Suspect, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<Suspect, Integer> ageCol = new TableColumn<>("Age");
        ageCol.setCellValueFactory(new PropertyValueFactory<>("age"));
        TableColumn<Suspect, String> historyCol = new TableColumn<>("Criminal History");
        historyCol.setCellValueFactory(new PropertyValueFactory<>("history"));

        suspectsTable.getColumns().setAll(idCol, nameCol, ageCol, historyCol);
        suspectsTable.setItems(suspectList);

        reportDetailsArea.textProperty().addListener((obs, oldText, newText) -> updateReportDetails(newText));
        logNotesArea.textProperty().addListener((obs, oldText, newText) -> updateLogNotes(newText));

        loadAllCases();
    }

    public void initData(Officer officer) {
        this.currentOfficer = officer;
        this.currentUserId = officer.getUserID();
    }

    private void loadAllCases() {
        caseList.clear();
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT case_id, title, status FROM cases");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("case_id");
                String title = rs.getString("title");
                String status = rs.getString("status");
                caseList.add(new CrimeCase(id, title, status));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadCase(int caseId) {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT case_id, title, status, assigned_officer_id, report_details, log_notes FROM cases WHERE case_id=?")) {

            ps.setInt(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String title = rs.getString("title");
                    String status = rs.getString("status");
                    String officerId = rs.getString("assigned_officer_id");

                    currentCase = new CrimeCase(caseId, title, status);

                    caseIdLabel.setText(String.valueOf(caseId));
                    caseTitleField.setText(title);
                    statusComboBox.setValue(status);

                    if (officerId != null) {
                        Officer officer = fetchOfficer(officerId);
                        currentCase.setAssignedOfficer(officer);
                        officerComboBox.setValue(officer);
                    } else {
                        officerComboBox.setValue(null);
                    }

                    reportDetailsArea.setText(rs.getString("report_details"));
                    logNotesArea.setText(rs.getString("log_notes"));

                    loadSuspects(caseId);
                    insertAuditLog(currentUserId, "Loaded case ID " + caseId);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
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
    public void handleUpdateStatus(ActionEvent event) {
        if (currentCase == null) return;

        String newStatus = statusComboBox.getValue();
        Officer assigned = officerComboBox.getValue();

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE cases SET status=?, assigned_officer_id=? WHERE case_id=?")) {

            ps.setString(1, newStatus);
            if (assigned != null) ps.setString(2, assigned.getUserID());
            else ps.setNull(2, Types.VARCHAR);
            ps.setInt(3, currentCase.getCaseID());
            ps.executeUpdate();

            currentCase.setStatus(newStatus);
            currentCase.setAssignedOfficer(assigned);

        } catch (SQLException e) {
            e.printStackTrace();
        }

        insertAuditLog(currentUserId, "Updated case ID " + currentCase.getCaseID() + " status to " + newStatus);
        loadAllCases();
    }

    @FXML
    public void handleCloseCase(ActionEvent event) {
        if (currentCase == null) return;

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE cases SET status='Closed' WHERE case_id=?")) {
            ps.setInt(1, currentCase.getCaseID());
            ps.executeUpdate();
            currentCase.setStatus("Closed");
            statusComboBox.setValue("Closed");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        insertAuditLog(currentUserId, "Closed case ID " + currentCase.getCaseID());
        loadAllCases();
    }

    @FXML
    public void handleAddSuspect(ActionEvent event) {
        if (currentCase == null) return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText("Enter new suspect details as: name,age,history");
        dialog.setTitle("Add Suspect");
        dialog.showAndWait().ifPresent(input -> {
            try {
                String[] parts = input.split(",");
                if (parts.length < 2) return;
                String name = parts[0].trim();
                int age = Integer.parseInt(parts[1].trim());
                String history = parts.length >= 3 ? parts[2].trim() : "";

                try (Connection conn = DatabaseHelper.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO suspects (case_id, name, age, notes) VALUES (?, ?, ?, ?)",
                             Statement.RETURN_GENERATED_KEYS)) {

                    ps.setInt(1, currentCase.getCaseID());
                    ps.setString(2, name);
                    ps.setInt(3, age);
                    ps.setString(4, history);
                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        int suspectId = rs.next() ? rs.getInt(1) : -1;
                        Suspect s = new Suspect(suspectId, name, age, history);
                        suspectList.add(s);
                        currentCase.addSuspect(s);
                    }
                }

                insertAuditLog(currentUserId, "Added suspect '" + name + "' to case ID " + currentCase.getCaseID());

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @FXML
    public void handleViewSuspectDetails(ActionEvent event) {
        Suspect s = suspectsTable.getSelectionModel().getSelectedItem();
        if (s != null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Suspect Details");
            alert.setContentText("Name: " + s.getName() + "\nAge: " + s.getAge() + "\nHistory: " + s.getHistory());
            alert.showAndWait();

            insertAuditLog(currentUserId, "Viewed suspect ID " + s.getSuspectID() + " details in case ID " + currentCase.getCaseID());
        }
    }

    private void updateReportDetails(String text) {
        if (currentCase == null) return;
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE cases SET report_details=? WHERE case_id=?")) {
            ps.setString(1, text);
            ps.setInt(2, currentCase.getCaseID());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateLogNotes(String text) {
        if (currentCase == null) return;
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE cases SET log_notes=? WHERE case_id=?")) {
            ps.setString(1, text);
            ps.setInt(2, currentCase.getCaseID());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private List<Officer> fetchOfficers() {
        List<Officer> officers = new ArrayList<>();
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT user_id, name FROM users WHERE role='OFFICER'");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) officers.add(new Officer(rs.getString("user_id"), rs.getString("name")));
        } catch (SQLException e) { e.printStackTrace(); }
        return officers;
    }

    private Officer fetchOfficer(String id) {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT user_id, name FROM users WHERE user_id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new Officer(rs.getString("user_id"), rs.getString("name"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    private void loadSuspects(int caseId) {
        suspectList.clear();
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT suspect_id, name, age, notes FROM suspects WHERE case_id=?")) {
            ps.setInt(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    suspectList.add(new Suspect(rs.getInt("suspect_id"), rs.getString("name"), rs.getInt("age"), rs.getString("notes")));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void insertAuditLog(String userId, String action) {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO audit_logs (user_id, action) VALUES (?, ?)")) {
            ps.setString(1, userId != null ? userId : "system");
            ps.setString(2, action);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}
