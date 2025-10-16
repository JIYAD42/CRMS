package com.crms.controller;

import com.crms.database.DatabaseHelper;
import com.crms.model.CrimeCase;
import com.crms.model.Officer;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import javafx.util.Duration;

import java.io.IOException;
import java.sql.*;

public class OfficerDashboardController {

    @FXML private Label assignedCasesCount;
    @FXML private Label openReportsCount;
    @FXML private TableView<CrimeCase> activeCasesTable;

    private Officer currentOfficer;
    private ObservableList<CrimeCase> caseList = FXCollections.observableArrayList();
    private Timeline refreshTimeline;

    @FXML
    public void initialize() {
        // Initialize TableView columns
        TableColumn<CrimeCase, Integer> idCol = new TableColumn<>("Case ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("caseID"));

        TableColumn<CrimeCase, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));

        TableColumn<CrimeCase, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        activeCasesTable.getColumns().addAll(idCol, titleCol, statusCol);
        activeCasesTable.setItems(caseList);
    }

    public void initData(Officer officer) {
        // Stop any previous refresh
        stopAutoRefresh();

        this.currentOfficer = officer;
        System.out.println("Logged in as: " + officer.getName());

        // Initial load
        refreshDashboard();

        // Start auto-refresh every 10 seconds
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> refreshDashboard()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void refreshDashboard() {
        if (currentOfficer == null) return;

        try (Connection conn = DatabaseHelper.getConnection()) {
            // Assigned cases count
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) AS total FROM cases WHERE assigned_officer_id=?")) {
                ps.setString(1, currentOfficer.getUserID());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) assignedCasesCount.setText(String.valueOf(rs.getInt("total")));
                }
            }

            // Open reports count

            // Load active cases
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT case_id, title, status FROM cases WHERE assigned_officer_id=?")) {
                ps.setString(1, currentOfficer.getUserID());
                try (ResultSet rs = ps.executeQuery()) {
                    caseList.clear(); // clear old data to prevent duplication
                    while (rs.next()) {
                        int id = rs.getInt("case_id");
                        String title = rs.getString("title");
                        String status = rs.getString("status");
                        caseList.add(new CrimeCase(id, title, status)); // use proper constructor
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

   @FXML
public void goToCrimeReporting(ActionEvent event) {
    switchScene(event, "/com/crms/views/CrimeReporting.fxml",
        c -> ((com.crms.controller.CrimeReportingController)c).initData(currentOfficer));
}

@FXML
public void goToCaseManagement(ActionEvent event) {
    switchScene(event, "/com/crms/views/CaseManagement.fxml",
        c -> ((com.crms.controller.CaseManagementController)c).initData(currentOfficer));
}

    // Generic scene switcher with controller initializer
    private <T> void switchScene(ActionEvent event, String fxmlPath, ControllerInitializer<T> initializer) {
        stopAutoRefresh(); // stop refresh before switching
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            T controller = loader.getController();
            initializer.init(controller);

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Call this on logout or scene change to stop auto-refresh
    public void stopAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
    }

    // Functional interface for generic scene switching
    @FunctionalInterface
    private interface ControllerInitializer<T> {
        void init(T controller);
    }
}
