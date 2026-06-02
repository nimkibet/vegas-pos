package com.pos.controller;

import com.pos.database.DatabaseManager;
import com.pos.entity.CustomerDebtSummary;
import com.pos.util.BrandingConstants;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class AdminDebtorsController {
    private static final Logger logger = LoggerFactory.getLogger(AdminDebtorsController.class);

    @FXML private TableView<CustomerDebtSummary> debtorsTable;
    @FXML private TableColumn<CustomerDebtSummary, String> colCustomerName;
    @FXML private TableColumn<CustomerDebtSummary, String> colPhone;
    @FXML private TableColumn<CustomerDebtSummary, Integer> colPendingBatches;
    @FXML private TableColumn<CustomerDebtSummary, String> colTotalDebt;

    private final DatabaseManager dbManager = DatabaseManager.getInstance();
    private final ObservableList<CustomerDebtSummary> debtorList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
    }

    private void setupTable() {
        debtorsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colCustomerName.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getCustomer().getName()));
        colPhone.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getCustomer().getPhone()));
        colPendingBatches.setCellValueFactory(new PropertyValueFactory<>("pendingBatches"));
        colTotalDebt.setCellValueFactory(cellData -> 
            new SimpleStringProperty(BrandingConstants.CURRENCY_SYMBOL + String.format("%.2f", cellData.getValue().getTotalDebt())));
        
        debtorsTable.setItems(debtorList);
    }

    public void loadDebtorsData() {
        try {
            List<CustomerDebtSummary> debtors = dbManager.getAllDebtors();
            debtorList.setAll(debtors);
            logger.info("Loaded {} debtors", debtors.size());
        } catch (SQLException e) {
            logger.error("Error loading debtors data", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText(null);
            alert.setContentText("Could not load debtors: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleRefresh() {
        loadDebtorsData();
    }
}
