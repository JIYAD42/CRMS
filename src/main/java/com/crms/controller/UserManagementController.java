package com.crms.controller;

import com.crms.database.DatabaseHelper;
import com.crms.model.Admin;

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
import java.security.MessageDigest;
import java.sql.*;

public class UserManagementController {

    @FXML private TableView<UserRow> usersTableView;
    @FXML private TextField nameField;
    @FXML private TextField userIdField;
    @FXML private TextField passwordField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private Button saveButton;

    private Admin currentAdmin;

    private ObservableList<UserRow> usersList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Initialize role ComboBox
        roleComboBox.setItems(FXCollections.observableArrayList("ADMIN", "OFFICER"));

        // Initialize TableView columns
        TableColumn<UserRow, String> idCol = new TableColumn<>("User ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("userID"));

        TableColumn<UserRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<UserRow, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));

        TableColumn<UserRow, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));

        usersTableView.getColumns().addAll(idCol, nameCol, roleCol, activeCol);
        usersTableView.setItems(usersList);

        loadUsers();
    }

    public void initData(Admin admin) {
        this.currentAdmin = admin;
    }

    private void loadUsers() {
        usersList.clear();
        String sql = "SELECT user_id, name, role, is_active FROM users";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                usersList.add(new UserRow(
                        rs.getString("user_id"),
                        rs.getString("name"),
                        rs.getString("role"),
                        rs.getBoolean("is_active")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
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

    @FXML
    public void loadSelectedUser(ActionEvent event) {
        UserRow selected = usersTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            userIdField.setText(selected.getUserID());
            nameField.setText(selected.getName());
            roleComboBox.setValue(selected.getRole());
            passwordField.clear(); // don't display password
        }
    }

    @FXML
    public void handleDeactivateUser(ActionEvent event) {
        UserRow selected = usersTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            String sql = "UPDATE users SET is_active = FALSE WHERE user_id = ?";
            try (Connection conn = DatabaseHelper.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, selected.getUserID());
                ps.executeUpdate();
                writeAuditLog("Deactivated user: " + selected.getUserID());
                loadUsers();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    public void handleSaveUser(ActionEvent event) {
        String id = userIdField.getText().trim();
        String name = nameField.getText().trim();
        String role = roleComboBox.getValue();
        String password = passwordField.getText();

        if (name.isBlank() || role == null || role.isBlank()) return;

        try (Connection conn = DatabaseHelper.getConnection()) {

    boolean exists = false;
    if (id != null && !id.isBlank()) {
        try (PreparedStatement check = conn.prepareStatement("SELECT 1 FROM users WHERE user_id = ?")) {
            check.setString(1, id);
            ResultSet rs = check.executeQuery();
            exists = rs.next(); // true if found
        }
    }

    if (!exists) {
        // INSERT
        String newID = (id == null || id.isBlank()) ? "user" + System.currentTimeMillis() : id;
        String sql = "INSERT INTO users (user_id, name, role, password_hash) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newID);
            ps.setString(2, name);
            ps.setString(3, role);
            ps.setString(4, hashPassword(password));
            ps.executeUpdate();
            writeAuditLog("Added new user: " + newID);
        }
    } else {
        // UPDATE
        String sql = "UPDATE users SET name = ?, role = ?"
                + (password != null && !password.isBlank() ? ", password_hash = ?" : "")
                + " WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, role);
            int index = 3;
            if (password != null && !password.isBlank()) {
                ps.setString(index++, hashPassword(password));
            }
            ps.setString(index, id);
            ps.executeUpdate();
            writeAuditLog("Updated user: " + id);
        }
    }

    loadUsers();
    handleClearForm(null);

} catch (SQLException e) {
    e.printStackTrace();
}

    }

    @FXML
    public void handleClearForm(ActionEvent event) {
        userIdField.clear();
        nameField.clear();
        passwordField.clear();
        roleComboBox.setValue(null);
        usersTableView.getSelectionModel().clearSelection();
    }

    private String hashPassword(String password) {
        if (password == null || password.isBlank()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private void writeAuditLog(String action) {
        String sql = "INSERT INTO audit_logs (user_id, action) VALUES (?, ?)";
        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "SYSTEM"); // replace with actual logged-in user if available
            ps.setString(2, action);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // TableView row wrapper
    public static class UserRow {
        private final String userID;
        private final String name;
        private final String role;
        private final boolean active;

        public UserRow(String userID, String name, String role, boolean active) {
            this.userID = userID;
            this.name = name;
            this.role = role;
            this.active = active;
        }

        public String getUserID() { return userID; }
        public String getName() { return name; }
        public String getRole() { return role; }
        public boolean getActive() { return active; }
    }
}
