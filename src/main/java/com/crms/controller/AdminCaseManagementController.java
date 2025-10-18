package com.crms.controller;

import com.crms.database.DatabaseHelper;
import com.crms.model.CrimeCase;
import com.crms.model.Officer;
import com.crms.model.Suspect;
import com.crms.model.Admin;

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

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AdminCaseManagementController {

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

    private Admin currentAdmin;
    private CrimeCase currentCase;
    private ObservableList<Suspect> suspectList = FXCollections.observableArrayList();
    private ObservableList<CrimeCase> caseList = FXCollections.observableArrayList();
    private String currentUserId = "SYSTEM";

    @FXML
    public void initialize() {
        statusComboBox.getItems().setAll("Open", "In Progress", "Closed");
        officerComboBox.getItems().setAll(fetchOfficers());

        caseIdColumn.setCellValueFactory(new PropertyValueFactory<>("caseID"));
        caseTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        caseStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        casesTable.setItems(caseList);

        casesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldCase, newCase) -> {
            if (newCase != null) {
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() {
                        loadCase(newCase.getCaseID());
                        return null;
                    }
                };
                task.setOnSucceeded(e -> casesTable.refresh());
                new Thread(task).start();
            }
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
    }

    public void initData(Admin admin) {
        this.currentAdmin = admin;
        this.currentUserId = admin != null ? admin.getUserID() : "SYSTEM";
        loadAllCases(); // Admin sees all cases
    }

    private void loadAllCases() {
        caseList.clear();
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT case_id, title, status FROM cases");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                caseList.add(new CrimeCase(
                        rs.getInt("case_id"),
                        rs.getString("title"),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadCase(int caseId) {
        Task<Void> task = new Task<>() {
            private CrimeCase loadedCase;
            private Officer officer;
            private List<Suspect> suspects = new ArrayList<>();
            private String reportDetails;
            private String logNotes;

            @Override
            protected Void call() throws Exception {
                try (Connection conn = DatabaseHelper.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT case_id, title, status, assigned_officer_id, report_details, log_notes FROM cases WHERE case_id=?")) {
                    ps.setInt(1, caseId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            loadedCase = new CrimeCase(caseId, rs.getString("title"), rs.getString("status"));
                            String officerId = rs.getString("assigned_officer_id");
                            if (officerId != null) officer = fetchOfficer(officerId);
                            reportDetails = rs.getString("report_details");
                            logNotes = rs.getString("log_notes");
                        }
                    }
                }

                try (Connection conn = DatabaseHelper.getConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "SELECT suspect_id, name, age, notes FROM suspects WHERE case_id=?")) {
                    ps.setInt(1, caseId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            suspects.add(new Suspect(
                                    rs.getInt("suspect_id"),
                                    rs.getString("name"),
                                    rs.getInt("age"),
                                    rs.getString("notes")
                            ));
                        }
                    }
                }
                return null;
            }

            @Override
            protected void succeeded() {
                currentCase = loadedCase;
                if (currentCase != null) currentCase.setAssignedOfficer(officer);

                caseIdLabel.setText(String.valueOf(currentCase.getCaseID()));
                caseTitleField.setText(currentCase.getTitle());
                statusComboBox.setValue(currentCase.getStatus());
                officerComboBox.setValue(officer);

                reportDetailsArea.setText(reportDetails);
                logNotesArea.setText(logNotes);

                suspectList.setAll(suspects);
                insertAuditLog(currentUserId, "Loaded case ID " + caseId);
            }
        };
        new Thread(task).start();
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

        } catch (SQLException e) { e.printStackTrace(); }

        insertAuditLog(currentUserId, "Updated case ID " + currentCase.getCaseID() + " status to " + newStatus);
        loadAllCases();
    }

    @FXML
    public void handleCloseCase(ActionEvent event) {
        if (currentCase == null) return;

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE cases SET status='Closed' WHERE case_id=?")) {
            ps.setInt(1, currentCase.getCaseID());
            ps.executeUpdate();

            currentCase.setStatus("Closed");
            statusComboBox.setValue("Closed");
        } catch (SQLException e) { e.printStackTrace(); }

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
            } catch (Exception e) { e.printStackTrace(); }
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

            insertAuditLog(currentUserId, "Viewed suspect ID " + s.getSuspectID() + " in case ID " + currentCase.getCaseID());
        }
    }

    private void updateReportDetails(String text) {
        if (currentCase == null) return;
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE cases SET report_details=? WHERE case_id=?")) {
            ps.setString(1, text);
            ps.setInt(2, currentCase.getCaseID());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void updateLogNotes(String text) {
        if (currentCase == null) return;
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE cases SET log_notes=? WHERE case_id=?")) {
            ps.setString(1, text);
            ps.setInt(2, currentCase.getCaseID());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
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

    private void insertAuditLog(String userId, String action) {
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO audit_logs (user_id, action) VALUES (?, ?)")) {
            ps.setString(1, userId != null ? userId : "SYSTEM");
            ps.setString(2, action);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}
