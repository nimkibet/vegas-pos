package com.pos.controller;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.pos.database.DatabaseManager;
import com.pos.entity.Sale;
import com.pos.entity.Shift;
import com.pos.entity.User;
import com.pos.service.AuthenticationService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shift Controller
 * Handles shift management - open/close register, X & Z reports.
 */
public class ShiftController {
    
    private static final Logger logger = LoggerFactory.getLogger(ShiftController.class);
    
    private static ShiftController instance;
    
    private final DatabaseManager dbManager;
    private final AuthenticationService authService;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private Shift currentShift;
    
    private ShiftController() {
        this.dbManager = DatabaseManager.getInstance();
        this.authService = AuthenticationService.getInstance();
    }
    
    public static synchronized ShiftController getInstance() {
        if (instance == null) {
            instance = new ShiftController();
        }
        return instance;
    }
    
    /**
     * Clear current shift state - call this on logout
     */
    public void clearShift() {
        currentShift = null;
        logger.info("Shift state cleared");
    }
    
    /**
     * Force reset the singleton - useful for testing or complete reset
     */
    public static void resetInstance() {
        if (instance != null && instance.currentShift != null) {
            logger.info("Clearing shift on reset");
        }
        instance = null;
    }
    
    /**
     * Get current active shift
     */
    public Shift getCurrentShift() {
        return currentShift;
    }
    
    /**
     * Check if there is an active shift
     */
    public boolean hasActiveShift() {
        return currentShift != null && currentShift.getStatus() == Shift.Status.OPEN;
    }
    
    /**
     * Prompt to open register with float amount
     */
    public boolean promptOpenRegister() {
        Dialog<BigDecimal> dialog = new Dialog<>();
        dialog.setTitle("Open Register");
        dialog.setHeaderText("Enter your starting cash float");
        
        ButtonType openButton = new ButtonType("Open Register", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(openButton, cancelButton);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        
        Label instructionLabel = new Label("Enter the cash in the drawer at shift start:");
        instructionLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #4a5568;");
        
        TextField floatField = new TextField();
        floatField.setPromptText("0.00");
        floatField.setStyle("-fx-font-size: 14; -fx-padding: 8;");
        
        content.getChildren().addAll(instructionLabel, floatField);
        dialog.getDialogPane().setContent(content);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == openButton) {
                try {
                    String value = floatField.getText().trim();
                    if (value.isEmpty()) {
                        return BigDecimal.ZERO;
                    }
                    return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });
        
        Optional<BigDecimal> result = dialog.showAndWait();
        
        if (result.isPresent() && result.get() != null) {
            return openRegister(result.get());
        }
        
        return false;
    }
    
    /**
     * Open register with specified float
     */
    public boolean openRegister(BigDecimal floatAmount) {
        try {
            Optional<User> userOpt = authService.getCurrentUser();
            if (userOpt.isEmpty()) {
                logger.error("No user logged in to open register");
                return false;
            }
            
            currentShift = new Shift(userOpt.get().getId());
            currentShift.setStartingFloat(floatAmount);
            currentShift.setUser(userOpt.get());
            
            dbManager.insertShift(currentShift);
            
            logger.info("Register opened with float: {}", floatAmount);
            return true;
            
        } catch (Exception e) {
            logger.error("Error opening register", e);
            return false;
        }
    }
    
    /**
     * Show close register dialog with Z-report
     */
    public boolean promptCloseRegister() {
        if (!hasActiveShift()) {
            showError("No active shift to close");
            return false;
        }
        
        try {
            loadShiftSales();
            
            Dialog<BigDecimal> dialog = new Dialog<>();
            dialog.setTitle("Close Register");
            dialog.setHeaderText("Z-Report - Shift Summary");
            
            ButtonType closeButton = new ButtonType("Close Register", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            ButtonType printButton = new ButtonType("Print Report", ButtonBar.ButtonData.OTHER);
            dialog.getDialogPane().getButtonTypes().addAll(closeButton, printButton, cancelButton);
            
            VBox content = createZReportContent();
            dialog.getDialogPane().setContent(content);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == closeButton || dialogButton == printButton) {
                    return BigDecimal.ONE;
                }
                return null;
            });
            
            Optional<BigDecimal> result = dialog.showAndWait();
            
            if (result.isPresent()) {
                return showActualCashDialogAndClose();
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Error generating Z-report", e);
            showError("Error generating Z-report: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Create Z-Report content
     */
    private VBox createZReportContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: white;");
        
        User user = currentShift.getUser();
        String userName = user != null ? user.getFullName() : "Unknown";
        
        Label headerLabel = new Label("Z-REPORT");
        headerLabel.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #1a5f2a;");
        
        Label shiftInfo = new Label("Shift: " + currentShift.getId().substring(0, 8) + 
                                    " | Cashier: " + userName +
                                    " | Date: " + currentShift.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        shiftInfo.setStyle("-fx-font-size: 12; -fx-text-fill: #718096;");
        
        Separator sep1 = new Separator();
        
        Label salesLabel = new Label("SALES SUMMARY");
        salesLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #2d3748;");
        
        String transCount = String.format("Transactions: %d", currentShift.getTransactionCount());
        Label transLabel = new Label(transCount);
        transLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #4a5568;");
        
        String cashTotal = String.format("Cash Sales: KSh%s", currentShift.getExpectedCash() != null ? currentShift.getExpectedCash().toPlainString() : "0.00");
        Label cashLabel = new Label(cashTotal);
        cashLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #4a5568;");
        
        String mpesaTotal = String.format("M-Pesa: KSh%s", currentShift.getMpesaTotal() != null ? currentShift.getMpesaTotal().toPlainString() : "0.00");
        Label mpesaLabel = new Label(mpesaTotal);
        mpesaLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #4a5568;");
        
        String cardTotal = String.format("Card: KSh%s", currentShift.getCardTotal() != null ? currentShift.getCardTotal().toPlainString() : "0.00");
        Label cardLabel = new Label(cardTotal);
        cardLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #4a5568;");
        
        Separator sep2 = new Separator();
        
        String totalSales = String.format("TOTAL SALES: KSh%s", currentShift.getTotalSales().toPlainString());
        Label totalLabel = new Label(totalSales);
        totalLabel.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1a5f2a;");
        
        String startFloat = String.format("Starting Float: KSh%s", currentShift.getStartingFloat().toPlainString());
        Label floatLabel = new Label(startFloat);
        floatLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #718096;");
        
        content.getChildren().addAll(headerLabel, shiftInfo, sep1, salesLabel, transLabel, 
                                     cashLabel, mpesaLabel, cardLabel, sep2, totalLabel, floatLabel);
        
        return content;
    }
    
    /**
     * Show dialog to enter actual cash and close register
     */
    private boolean showActualCashDialogAndClose() {
        Dialog<BigDecimal> dialog = new Dialog<>();
        dialog.setTitle("Count Cash");
        dialog.setHeaderText("Enter the actual cash counted in the drawer");
        
        ButtonType closeButton = new ButtonType("Close Register", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(closeButton, cancelButton);
        
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        
        Label expectedLabel = new Label("Expected Cash: KSh" + currentShift.getExpectedCash().toPlainString());
        expectedLabel.setStyle("-fx-font-size: 13; -fx-text-fill: #4a5568;");
        
        Label instructionLabel = new Label("Enter actual cash counted:");
        instructionLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #718096;");
        
        TextField actualField = new TextField();
        actualField.setPromptText("0.00");
        actualField.setStyle("-fx-font-size: 14; -fx-padding: 8;");
        
        content.getChildren().addAll(expectedLabel, instructionLabel, actualField);
        dialog.getDialogPane().setContent(content);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == closeButton) {
                try {
                    String value = actualField.getText().trim();
                    if (value.isEmpty()) {
                        return currentShift.getExpectedCash();
                    }
                    return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
                } catch (NumberFormatException e) {
                    return currentShift.getExpectedCash();
                }
            }
            return null;
        });
        
        Optional<BigDecimal> result = dialog.showAndWait();
        
        if (result.isPresent() && result.get() != null) {
            return closeRegister(result.get());
        }
        
        return false;
    }
    
    /**
     * Close register with actual cash count
     */
    public boolean closeRegister(BigDecimal actualCash) {
        try {
            currentShift.setActualCash(actualCash);
            currentShift.setStatus(Shift.Status.CLOSED);
            currentShift.setEndTime(LocalDateTime.now());
            
            dbManager.updateShift(currentShift);
            
            BigDecimal variance = currentShift.getVariance();
            String varianceStr = variance.compareTo(BigDecimal.ZERO) >= 0 ? 
                                "+" + variance.toPlainString() : variance.toPlainString();
            
            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Register Closed");
            successAlert.setHeaderText("Shift closed successfully");
            successAlert.setContentText("Variance: KSh" + varianceStr);
            
            ButtonType exportPdfButton = new ButtonType("Export Z-Report to PDF", ButtonBar.ButtonData.OTHER);
            ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
            successAlert.getButtonTypes().addAll(exportPdfButton, closeButton);
            
            Optional<ButtonType> result = successAlert.showAndWait();
            
            if (result.isPresent() && result.get() == exportPdfButton) {
                exportZReportToPDF();
            }
            
            currentShift = null;
            return true;
            
        } catch (Exception e) {
            logger.error("Error closing register", e);
            showError("Error closing register: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Load sales for current shift
     */
    private void loadShiftSales() {
        try {
            List<Sale> todaySales = dbManager.getTodaySales();
            
            BigDecimal cashTotal = BigDecimal.ZERO;
            BigDecimal mpesaTotal = BigDecimal.ZERO;
            BigDecimal cardTotal = BigDecimal.ZERO;
            int count = 0;
            
            LocalDateTime shiftStart = currentShift.getStartTime();
            
            for (Sale sale : todaySales) {
                if (sale.getCreatedAt().isAfter(shiftStart) && 
                    sale.getStatus() == Sale.Status.COMPLETED) {
                    
                    switch (sale.getPaymentMethod()) {
                        case CASH:
                            cashTotal = cashTotal.add(sale.getTotal());
                            break;
                        case MOBILE_MONEY:
                            mpesaTotal = mpesaTotal.add(sale.getTotal());
                            break;
                        case CARD:
                            cardTotal = cardTotal.add(sale.getTotal());
                            break;
                    }
                    count++;
                }
            }
            
            currentShift.setExpectedCash(cashTotal);
            currentShift.setMpesaTotal(mpesaTotal);
            currentShift.setCardTotal(cardTotal);
            currentShift.setTransactionCount(count);
            
            logger.info("Shift sales loaded: {} transactions, cash: {}, mpesa: {}, card: {}", 
                       count, cashTotal, mpesaTotal, cardTotal);
            
        } catch (Exception e) {
            logger.error("Error loading shift sales", e);
        }
    }
    
    /**
     * Print X-Report (current shift summary without closing)
     */
    public void printXReport() {
        if (!hasActiveShift()) {
            showError("No active shift");
            return;
        }
        
        loadShiftSales();
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("X-Report");
        alert.setHeaderText("Current Shift Summary");
        
        VBox content = createZReportContent();
        alert.getDialogPane().setContent(content);
        
        ButtonType exportPdfButton = new ButtonType("Export to PDF", ButtonBar.ButtonData.OTHER);
        ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().addAll(exportPdfButton, closeButton);
        
        Optional<ButtonType> result = alert.showAndWait();
        
        if (result.isPresent() && result.get() == exportPdfButton) {
            exportXReportToPDF();
        }
    }
    
    /**
     * Export X-Report to PDF
     */
    private void exportXReportToPDF() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save X-Report");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            fileChooser.setInitialFileName("X-Report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm")) + ".pdf");
            
            File file = fileChooser.showSaveDialog(null);
            
            if (file != null) {
                PdfWriter writer = new PdfWriter(file);
                PdfDocument pdf = new PdfDocument(writer);
                Document document = new Document(pdf);
                
                User user = currentShift.getUser();
                String userName = user != null ? user.getFullName() : "Unknown";
                
                document.add(new Paragraph("X-REPORT")
                    .setFontSize(18)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));
                
                document.add(new Paragraph("Vegas Supermarket POS")
                    .setFontSize(12)
                    .setTextAlignment(TextAlignment.CENTER));
                
                document.add(new Paragraph("Shift: " + currentShift.getId().substring(0, 8) + 
                    " | Cashier: " + userName + 
                    " | Date: " + currentShift.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER));
                
                document.add(new Paragraph("\n"));
                
                document.add(new Paragraph("SALES SUMMARY")
                    .setBold()
                    .setFontSize(12));
                
                document.add(new Paragraph("Transactions: " + currentShift.getTransactionCount()));
                document.add(new Paragraph("Cash Sales: KSh" + (currentShift.getExpectedCash() != null ? currentShift.getExpectedCash().toPlainString() : "0.00")));
                document.add(new Paragraph("M-Pesa: KSh" + (currentShift.getMpesaTotal() != null ? currentShift.getMpesaTotal().toPlainString() : "0.00")));
                document.add(new Paragraph("Card: KSh" + (currentShift.getCardTotal() != null ? currentShift.getCardTotal().toPlainString() : "0.00")));
                
                document.add(new Paragraph("\n"));
                document.add(new Paragraph("TOTAL SALES: KSh" + currentShift.getTotalSales().toPlainString())
                    .setBold()
                    .setFontSize(14));
                document.add(new Paragraph("Starting Float: KSh" + currentShift.getStartingFloat().toPlainString()));
                
                document.add(new Paragraph("\n"));
                document.add(new Paragraph("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER));
                
                document.close();
                
                showSuccess("X-Report exported to " + file.getName());
            }
        } catch (Exception e) {
            logger.error("Error exporting X-Report to PDF", e);
            showError("Error exporting PDF: " + e.getMessage());
        }
    }
    
    /**
     * Export Z-Report to PDF
     */
    private void exportZReportToPDF() {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Z-Report");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            fileChooser.setInitialFileName("Z-Report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm")) + ".pdf");
            
            File file = fileChooser.showSaveDialog(null);
            
            if (file != null) {
                PdfWriter writer = new PdfWriter(file);
                PdfDocument pdf = new PdfDocument(writer);
                Document document = new Document(pdf);
                
                User user = currentShift.getUser();
                String userName = user != null ? user.getFullName() : "Unknown";
                
                document.add(new Paragraph("Z-REPORT")
                    .setFontSize(18)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));
                
                document.add(new Paragraph("Vegas Supermarket POS")
                    .setFontSize(12)
                    .setTextAlignment(TextAlignment.CENTER));
                
                document.add(new Paragraph("Shift: " + currentShift.getId().substring(0, 8) + 
                    " | Cashier: " + userName + 
                    " | Date: " + currentShift.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER));
                
                document.add(new Paragraph("\n"));
                
                document.add(new Paragraph("SALES SUMMARY")
                    .setBold()
                    .setFontSize(12));
                
                document.add(new Paragraph("Transactions: " + currentShift.getTransactionCount()));
                document.add(new Paragraph("Cash Sales: KSh" + (currentShift.getExpectedCash() != null ? currentShift.getExpectedCash().toPlainString() : "0.00")));
                document.add(new Paragraph("M-Pesa: KSh" + (currentShift.getMpesaTotal() != null ? currentShift.getMpesaTotal().toPlainString() : "0.00")));
                document.add(new Paragraph("Card: KSh" + (currentShift.getCardTotal() != null ? currentShift.getCardTotal().toPlainString() : "0.00")));
                
                document.add(new Paragraph("\n"));
                document.add(new Paragraph("TOTAL SALES: KSh" + currentShift.getTotalSales().toPlainString())
                    .setBold()
                    .setFontSize(14));
                
                document.add(new Paragraph("\n"));
                document.add(new Paragraph("CASH RECONCILIATION")
                    .setBold()
                    .setFontSize(12));
                document.add(new Paragraph("Starting Float: KSh" + currentShift.getStartingFloat().toPlainString()));
                document.add(new Paragraph("Expected Cash: KSh" + (currentShift.getExpectedCash() != null ? currentShift.getExpectedCash().toPlainString() : "0.00")));
                document.add(new Paragraph("Actual Cash: KSh" + (currentShift.getActualCash() != null ? currentShift.getActualCash().toPlainString() : "0.00")));
                
                BigDecimal variance = currentShift.getVariance();
                String varianceStr = variance.compareTo(BigDecimal.ZERO) >= 0 ? 
                                    "+" + variance.toPlainString() : variance.toPlainString();
                document.add(new Paragraph("Variance: KSh" + varianceStr)
                    .setBold()
                    .setFontSize(12));
                
                document.add(new Paragraph("\n"));
                document.add(new Paragraph("Shift Closed: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER));
                
                document.close();
                
                showSuccess("Z-Report exported to " + file.getName());
            }
        } catch (Exception e) {
            logger.error("Error exporting Z-Report to PDF", e);
            showError("Error exporting PDF: " + e.getMessage());
        }
    }
    
    /**
     * Show error message
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Show success message
     */
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Handle logout reconciliation workflow
     * Prompts for drawer cash, calculates discrepancy, closes shift, and exports PDF
     * @return true if successful (or no shift), false if user cancelled or error
     */
    public boolean handleLogoutReconciliation() {
        if (!hasActiveShift()) {
            return true; // No active shift, safe to proceed with logout
        }

        loadShiftSales();

        TextInputDialog cashDialog = new TextInputDialog("0");
        cashDialog.setTitle("Shift Reconciliation");
        cashDialog.setHeaderText("End of Shift: Cash Reconciliation Required");
        cashDialog.setContentText("Please count and enter the total CASH currently in the drawer (KSh):");

        Optional<String> result = cashDialog.showAndWait();

        if (result.isPresent()) {
            try {
                BigDecimal actualDeclaredCash = new BigDecimal(result.get());
                BigDecimal expectedCashSales = currentShift.getExpectedCash() != null ? currentShift.getExpectedCash() : BigDecimal.ZERO;
                BigDecimal startingFloat = currentShift.getStartingFloat() != null ? currentShift.getStartingFloat() : BigDecimal.ZERO;

                BigDecimal expectedTotalInDrawer = expectedCashSales.add(startingFloat);
                BigDecimal discrepancy = actualDeclaredCash.subtract(expectedTotalInDrawer);

                // Update and close shift
                currentShift.setActualCash(actualDeclaredCash);
                currentShift.setStatus(Shift.Status.CLOSED);
                currentShift.setEndTime(LocalDateTime.now());
                dbManager.updateShift(currentShift);

                // Resolve Target Directory
                String userHome = System.getProperty("user.home");
                File vegasFolder = new File(userHome + File.separator + "Desktop" + File.separator + "vegas");

                if (!vegasFolder.exists()) {
                    vegasFolder.mkdirs();
                }

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
                File pdfOutputFile = new File(vegasFolder, "X-Report-" + timestamp + ".pdf");

                generateImprovedPDF(pdfOutputFile, expectedTotalInDrawer, actualDeclaredCash, discrepancy);

                showSuccess("Report successfully saved to:\n" + pdfOutputFile.getAbsolutePath());
                
                currentShift = null; // Clear shift
                return true;

            } catch (NumberFormatException e) {
                showError("Invalid Amount: Please input a valid numeric currency value to proceed with logout.");
                return false;
            } catch (Exception e) {
                logger.error("Error during logout reconciliation", e);
                showError("Error during reconciliation: " + e.getMessage());
                return false;
            }
        }
        return false; // User cancelled
    }

    /**
     * Generates an improved, structured end-of-shift X-Report (Z-Report)
     */
    public void generateImprovedPDF(File pdfOutputFile, BigDecimal expectedCashSales, BigDecimal actualDeclaredCash, BigDecimal discrepancy) {
        try {
            PdfWriter writer = new PdfWriter(pdfOutputFile);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            User user = currentShift.getUser();
            String userName = user != null ? user.getFullName() : "Unknown";

            // Title Block
            Table titleTable = new Table(UnitValue.createPercentArray(new float[]{100}))
                    .useAllAvailableWidth()
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY);
            titleTable.addCell(new Cell().add(new Paragraph("VEGAS SUPERMARKET").setBold().setTextAlignment(TextAlignment.CENTER))
                    .setBorder(Border.NO_BORDER));
            titleTable.addCell(new Cell().add(new Paragraph("END-OF-SHIFT X-REPORT (Z)").setBold().setTextAlignment(TextAlignment.CENTER))
                    .setBorder(Border.NO_BORDER));
            document.add(titleTable);

            // Metadata Block
            Table metaTable = new Table(UnitValue.createPercentArray(new float[]{33, 34, 33}))
                    .useAllAvailableWidth();
            metaTable.addCell(new Cell().add(new Paragraph("Shift ID: " + currentShift.getId().substring(0, 8)))
                    .setBorder(Border.NO_BORDER));
            metaTable.addCell(new Cell().add(new Paragraph("Cashier: " + userName).setTextAlignment(TextAlignment.CENTER))
                    .setBorder(Border.NO_BORDER));
            metaTable.addCell(new Cell().add(new Paragraph("Date: " + currentShift.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))).setTextAlignment(TextAlignment.RIGHT))
                    .setBorder(Border.NO_BORDER));
            document.add(metaTable);
            document.add(new Paragraph("\n"));

            // 1. SYSTEM SALES BREAKDOWN
            document.add(new Paragraph("1. SYSTEM SALES BREAKDOWN").setBold());
            Table salesTable = new Table(UnitValue.createPercentArray(new float[]{40, 30, 30}))
                    .useAllAvailableWidth();
            salesTable.addHeaderCell(new Cell().add(new Paragraph("Payment Method").setBold()));
            salesTable.addHeaderCell(new Cell().add(new Paragraph("Transaction Count").setBold().setTextAlignment(TextAlignment.CENTER)));
            salesTable.addHeaderCell(new Cell().add(new Paragraph("Total Amount").setBold().setTextAlignment(TextAlignment.RIGHT)));

            salesTable.addCell(new Cell().add(new Paragraph("Cash Sales")));
            salesTable.addCell(new Cell().add(new Paragraph(String.valueOf(currentShift.getTransactionCount())).setTextAlignment(TextAlignment.CENTER)));
            salesTable.addCell(new Cell().add(new Paragraph("KSh " + (currentShift.getExpectedCash() != null ? currentShift.getExpectedCash().toPlainString() : "0.00")).setTextAlignment(TextAlignment.RIGHT)));

            salesTable.addCell(new Cell().add(new Paragraph("M-Pesa")));
            salesTable.addCell(new Cell().add(new Paragraph("-").setTextAlignment(TextAlignment.CENTER)));
            salesTable.addCell(new Cell().add(new Paragraph("KSh " + (currentShift.getMpesaTotal() != null ? currentShift.getMpesaTotal().toPlainString() : "0.00")).setTextAlignment(TextAlignment.RIGHT)));

            salesTable.addCell(new Cell().add(new Paragraph("Card")));
            salesTable.addCell(new Cell().add(new Paragraph("-").setTextAlignment(TextAlignment.CENTER)));
            salesTable.addCell(new Cell().add(new Paragraph("KSh " + (currentShift.getCardTotal() != null ? currentShift.getCardTotal().toPlainString() : "0.00")).setTextAlignment(TextAlignment.RIGHT)));

            salesTable.addCell(new Cell().add(new Paragraph("TOTAL EXPECTED SALES").setBold()));
            salesTable.addCell(new Cell().add(new Paragraph(String.valueOf(currentShift.getTransactionCount())).setBold().setTextAlignment(TextAlignment.CENTER)));
            salesTable.addCell(new Cell().add(new Paragraph("KSh " + currentShift.getTotalSales().toPlainString()).setBold().setTextAlignment(TextAlignment.RIGHT)));
            
            document.add(salesTable);
            document.add(new Paragraph("\n"));

            // 2. CASH RECONCILIATION
            document.add(new Paragraph("2. CASH RECONCILIATION").setBold());
            Table reconTable = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                    .useAllAvailableWidth();
            reconTable.addCell(new Cell().add(new Paragraph("Starting Float:")));
            reconTable.addCell(new Cell().add(new Paragraph("KSh " + currentShift.getStartingFloat().toPlainString()).setTextAlignment(TextAlignment.RIGHT)));

            reconTable.addCell(new Cell().add(new Paragraph("Expected Cash in Drawer:")));
            reconTable.addCell(new Cell().add(new Paragraph("KSh " + expectedCashSales.toPlainString()).setTextAlignment(TextAlignment.RIGHT)));

            reconTable.addCell(new Cell().add(new Paragraph("Actual Declared Cash (Input):")));
            reconTable.addCell(new Cell().add(new Paragraph("KSh " + actualDeclaredCash.toPlainString()).setTextAlignment(TextAlignment.RIGHT)));

            reconTable.addCell(new Cell().add(new Paragraph("DISCREPANCY STATUS:").setBold()));
            String discrepancyPrefix = discrepancy.compareTo(BigDecimal.ZERO) >= 0 ? "+ KSh " : "- KSh ";
            reconTable.addCell(new Cell().add(new Paragraph(discrepancyPrefix + discrepancy.abs().toPlainString()).setBold().setTextAlignment(TextAlignment.RIGHT)));
            document.add(reconTable);

            // Discrepancy Flag
            Paragraph flag;
            if (discrepancy.compareTo(BigDecimal.ZERO) < 0) {
                flag = new Paragraph("[ ATTENTION: DEFICIT DETECTED ]").setBold().setFontColor(ColorConstants.RED).setTextAlignment(TextAlignment.CENTER);
            } else if (discrepancy.compareTo(BigDecimal.ZERO) > 0) {
                flag = new Paragraph("[ SURPLUS DETECTED ]").setBold().setFontColor(ColorConstants.BLUE).setTextAlignment(TextAlignment.CENTER);
            } else {
                flag = new Paragraph("[ BALANCED ]").setBold().setFontColor(ColorConstants.GREEN).setTextAlignment(TextAlignment.CENTER);
            }
            document.add(new Paragraph("\n"));
            document.add(flag);

            document.close();
            logger.info("Improved Z-Report exported to {}", pdfOutputFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Error generating improved PDF", e);
            showError("Error generating PDF: " + e.getMessage());
        }
    }
}