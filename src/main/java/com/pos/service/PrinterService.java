package com.pos.service;

import com.pos.entity.Product;
import com.pos.entity.Sale;
import com.pos.entity.SaleItem;
import com.pos.util.BrandingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import javax.swing.*;
import javax.swing.filechooser.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Printer Service
 * Unified thermal printer service for Vegas Supermarket POS.
 * Supports both ESC/POS thermal printer and Development Mode (PDF/System Print).
 */
public class PrinterService {
    
    private static final Logger logger = LoggerFactory.getLogger(PrinterService.class);
    private static PrinterService instance;
    
    // Development Mode toggle - set to true to use system print dialog
    public static final boolean DEV_MODE = true;
    
    // ESC/POS Commands
    private static final byte[] ESC = {0x1B};
    private static final byte[] GS = {0x1D};
    private static final byte[] LF = {0x0A};
    
    // Receipt width in mm (80mm standard)
    private static final double RECEIPT_WIDTH_MM = 80.0;
    private static final double RECEIPT_HEIGHT_MM = 200.0;
    
    // Command constants
    private static final byte[] INITIALIZE = {0x1B, 0x40};
    private static final byte[] ALIGN_LEFT = {0x1B, 0x61, 0x00};
    private static final byte[] ALIGN_CENTER = {0x1B, 0x61, 0x01};
    private static final byte[] BOLD_ON = {0x1B, 0x45, 0x01};
    private static final byte[] BOLD_OFF = {0x1B, 0x45, 0x00};
    private static final byte[] FONT_SIZE_NORMAL = {0x1D, 0x21, 0x00};
    private static final byte[] FONT_SIZE_DOUBLE = {0x1D, 0x21, 0x11};
    private static final byte[] FONT_SIZE_TRIPLE = {0x1D, 0x21, 0x22};
    private static final byte[] CUT_PAPER = {0x1D, 0x56, 0x00};
    private static final byte[] PARTIAL_CUT = {0x1D, 0x56, 0x01};
    private static final byte[] OPEN_DRAWER = {(byte)0x1B, (byte)0x70, (byte)0x00, (byte)0x19, (byte)0xFA};
    private static final byte[] LINE_FEED_3MM = {0x1B, 0x4A, 0x10};
    
    // Settings
    private String printerHost;
    private int printerPort;
    private int connectionTimeout;
    
    /**
     * Private constructor for singleton pattern
     */
    private PrinterService() {
        this.printerHost = "localhost";
        this.printerPort = 9100;
        this.connectionTimeout = 5000;
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized PrinterService getInstance() {
        if (instance == null) {
            instance = new PrinterService();
        }
        return instance;
    }
    
    /**
     * Configure printer settings
     */
    public void configure(String host, int port, int timeout) {
        this.printerHost = host;
        this.printerPort = port;
        this.connectionTimeout = timeout;
    }
    
    /**
     * Print transaction receipt - routes to ESC/POS or Development Mode
     */
    public boolean printTransaction(Sale sale) {
        if (DEV_MODE) {
            return printTransactionDevMode(sale);
        } else {
            return printTransactionEscPos(sale);
        }
    }
    
    /**
     * Print shelf tag - routes to ESC/POS or Development Mode
     */
    public boolean printShelfTag(Product product) {
        if (DEV_MODE) {
            return printShelfTagDevMode(product);
        } else {
            return printShelfTagEscPos(product);
        }
    }
    
    // ==================== DEVELOPMENT MODE (PDF/System Print) ====================
    
    /**
     * Development Mode: Print receipt using system print dialog
     */
    private boolean printTransactionDevMode(Sale sale) {
        try {
            // Generate text file receipt to Desktop
            String desktopPath = System.getProperty("user.home") + File.separator + "Desktop";
            String fileName = desktopPath + File.separator + "receipt_" + System.currentTimeMillis() + ".txt";
            
            File receiptFile = new File(fileName);
            
            // Build receipt content
            StringBuilder receipt = new StringBuilder();
            receipt.append("=================================\n");
            receipt.append("       VEGAS SUPERMARKET\n");
            receipt.append("    Fresh Groceries, Best Prices!\n");
            receipt.append("=================================\n");
            receipt.append("\n");
            
            // Date
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            receipt.append("Date: " + sdf.format(new Date()) + "\n");
            receipt.append("Receipt #: " + sale.getId().substring(0, 8) + "\n");
            receipt.append("---------------------------------\n");
            
            // Items
            List<SaleItem> items = sale.getItems();
            for (SaleItem item : items) {
                String name = item.getProductName();
                if (name.length() > 20) name = name.substring(0, 20);
                receipt.append(String.format("%-20s", name)).append(" ");
                receipt.append(formatCurrencyStatic(item.getTotalPrice())).append("\n");
                receipt.append(String.format("   %d x %s\n", item.getQuantity(), formatCurrencyStatic(item.getUnitPrice())));
            }
            
            receipt.append("---------------------------------\n");
            receipt.append(String.format("%-22s %10s\n", "Subtotal:", formatCurrencyStatic(sale.getSubtotal())));
            
            if (sale.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
                receipt.append(String.format("%-22s %10s\n", "Tax:", formatCurrencyStatic(sale.getTaxAmount())));
            }
            
            if (sale.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                receipt.append(String.format("%-22s %10s\n", "Discount:", "-" + formatCurrencyStatic(sale.getDiscountAmount())));
            }
            
            receipt.append("=================================\n");
            receipt.append(String.format("%-22s %10s\n", "TOTAL:", formatCurrencyStatic(sale.getTotal())));
            receipt.append("=================================\n");
            receipt.append(String.format("%-22s %10s\n", "Paid:", formatCurrencyStatic(sale.getAmountPaid())));
            receipt.append(String.format("%-22s %10s\n", "Change:", formatCurrencyStatic(sale.getChangeGiven())));
            receipt.append("\n");
            receipt.append("=================================\n");
            receipt.append("   Thank You for Shopping at\n");
            receipt.append("      Vegas Supermarket!\n");
            receipt.append("=================================\n");
            
            // Write to file
            java.nio.file.Files.write(receiptFile.toPath(), receipt.toString().getBytes(StandardCharsets.UTF_8));
            
            logger.info("Receipt saved to: {}", receiptFile.getAbsolutePath());
            
            // Show JavaFX Alert
            showAlert("Receipt saved to Desktop", "Receipt has been saved to:\n" + receiptFile.getName());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error saving receipt to file (Dev Mode)", e);
            return false;
        }
    }
    
    /**
     * Show JavaFX Information Alert
     */
    private void showAlert(String title, String message) {
        try {
            javafx.application.Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            });
        } catch (Exception e) {
            logger.warn("Could not show JavaFX alert: {}", e.getMessage());
        }
    }
    
    /**
     * Development Mode: Print shelf tag - saves to text file on Desktop
     */
    private boolean printShelfTagDevMode(Product product) {
        try {
            // Generate text file label to Desktop
            String desktopPath = System.getProperty("user.home") + File.separator + "Desktop";
            String fileName = desktopPath + File.separator + "label_" + System.currentTimeMillis() + ".txt";
            
            File labelFile = new File(fileName);
            
            // Build label content
            StringBuilder label = new StringBuilder();
            label.append("VEGAS SUPERMARKET\n");
            label.append(product.getName().toUpperCase() + "\n");
            label.append(formatCurrencyStatic(product.getRetailPrice()) + "\n");
            
            // Write to file
            java.nio.file.Files.write(labelFile.toPath(), label.toString().getBytes(StandardCharsets.UTF_8));
            
            logger.info("Label saved to: {}", labelFile.getAbsolutePath());
            
            // Show JavaFX Alert
            showLabelAlert("Label saved to Desktop", "Label has been saved to:\n" + labelFile.getName());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error saving label to file (Dev Mode)", e);
            return false;
        }
    }
    
    /**
     * Show JavaFX Information Alert for labels
     */
    private void showLabelAlert(String title, String message) {
        try {
            javafx.application.Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            });
        } catch (Exception e) {
            logger.warn("Could not show JavaFX alert: {}", e.getMessage());
        }
    }
    
    // ==================== ESC/POS MODE ====================
    
    /**
     * ESC/POS Mode: Print transaction receipt
     * Falls back to PDF if printer not found
     */
    private boolean printTransactionEscPos(Sale sale) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printerHost, printerPort), connectionTimeout);
            OutputStream out = socket.getOutputStream();
            
            out.write(INITIALIZE);
            
            printReceiptHeader(out, sale);
            printReceiptItems(out, sale.getItems());
            printReceiptTotals(out, sale);
            printReceiptFooter(out);
            
            out.write(OPEN_DRAWER);
            out.write(CUT_PAPER);
            out.flush();
            
            logger.info("Vegas Supermarket receipt printed for sale: {}", sale.getId());
            return true;
            
        } catch (Exception e) {
            logger.warn("ESC/POS printer not found, falling back to PDF: {}", e.getMessage());
            // Fall back to PDF/Dev Mode
            return printTransactionDevMode(sale);
        }
    }
    
    /**
     * ESC/POS Mode: Print shelf tag
     * Falls back to PDF if printer not found
     */
    private boolean printShelfTagEscPos(Product product) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printerHost, printerPort), connectionTimeout);
            OutputStream out = socket.getOutputStream();
            
            out.write(INITIALIZE);
            
            printShelfTagContent(out, product);
            
            for (int i = 0; i < 20; i++) {
                out.write(LINE_FEED_3MM);
            }
            
            out.write(PARTIAL_CUT);
            out.flush();
            
            logger.info("Vegas Supermarket shelf tag printed for: {}", product.getName());
            return true;
            
        } catch (Exception e) {
            logger.warn("ESC/POS printer not found, falling back to PDF: {}", e.getMessage());
            // Fall back to PDF/Dev Mode
            return printShelfTagDevMode(product);
        }
    }
    
    // ==================== RECEIPT PRINTABLE (DEV MODE) ====================
    
    /**
     * Printable implementation for receipts in Dev Mode
     */
    private static class ReceiptPrintable implements Printable {
        private final Sale sale;
        
        public ReceiptPrintable(Sale sale) {
            this.sale = sale;
        }
        
        @Override
        public int print(Graphics g, PageFormat pf, int pageIndex) {
            if (pageIndex > 0) return NO_SUCH_PAGE;
            
            Graphics2D g2d = (Graphics2D) g;
            g2d.translate(pf.getImageableX(), pf.getImageableY());
            
            // Font setup
            Font titleFont = new Font("Arial", Font.BOLD, 14);
            Font normalFont = new Font("Arial", Font.PLAIN, 10);
            Font boldFont = new Font("Arial", Font.BOLD, 10);
            Font largeFont = new Font("Arial", Font.BOLD, 16);
            
            int y = 20;
            int lineHeight = 14;
            int margin = 10;
            int width = (int) pf.getImageableWidth();
            
            // Store Name - Centered
            g2d.setFont(titleFont);
            String storeName = BrandingConstants.STORE_NAME;
            int storeNameWidth = g2d.getFontMetrics().stringWidth(storeName);
            g2d.drawString(storeName, (width - storeNameWidth) / 2, y);
            y += lineHeight;
            
            // Address
            g2d.setFont(normalFont);
            String address = BrandingConstants.STORE_ADDRESS;
            g2d.drawString(address, margin, y);
            y += lineHeight;
            
            g2d.drawString(BrandingConstants.STORE_PHONE, margin, y);
            y += lineHeight;
            
            // Tagline
            String tagline = BrandingConstants.TAGLINE;
            int taglineWidth = g2d.getFontMetrics().stringWidth(tagline);
            g2d.drawString(tagline, (width - taglineWidth) / 2, y);
            y += lineHeight + 5;
            
            // Separator
            g2d.drawLine(margin, y, width - margin, y);
            y += 5;
            
            // Date and Sale Info
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            LocalDateTime dateTime = sale.getCreatedAt();
            String dateStr = sdf.format(Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()));
            g2d.drawString("Date: " + dateStr, margin, y);
            y += lineHeight;
            g2d.drawString("Sale #: " + sale.getId().substring(0, 8), margin, y);
            y += lineHeight + 5;
            
            g2d.drawLine(margin, y, width - margin, y);
            y += 5;
            
            // Items
            g2d.setFont(boldFont);
            g2d.drawString(String.format("%-20s %8s", "Item", "Total"), margin, y);
            y += lineHeight;
            g2d.setFont(normalFont);
            
            for (SaleItem item : sale.getItems()) {
                String name = item.getProductName();
                if (name.length() > 20) name = name.substring(0, 20);
                String line = String.format("%-20s %8s", name, formatCurrencyStatic(item.getTotalPrice()));
                g2d.drawString(line, margin, y);
                y += lineHeight;
                
                String qtyLine = String.format("   %d x %s", item.getQuantity(), formatCurrencyStatic(item.getUnitPrice()));
                g2d.drawString(qtyLine, margin, y);
                y += lineHeight;
            }
            
            y += 5;
            g2d.drawLine(margin, y, width - margin, y);
            y += 5;
            
            // Totals
            g2d.setFont(boldFont);
            g2d.drawString(String.format("%-22s %10s", "Subtotal:", formatCurrencyStatic(sale.getSubtotal())), margin, y);
            y += lineHeight;
            
            if (sale.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
                g2d.drawString(String.format("%-22s %10s", "Tax:", formatCurrencyStatic(sale.getTaxAmount())), margin, y);
                y += lineHeight;
            }
            
            g2d.setFont(largeFont);
            g2d.drawString(String.format("%-22s %10s", "TOTAL:", formatCurrencyStatic(sale.getTotal())), margin, y);
            y += lineHeight + 5;
            
            g2d.setFont(normalFont);
            g2d.drawString(String.format("%-22s %10s", "Paid:", formatCurrencyStatic(sale.getAmountPaid())), margin, y);
            y += lineHeight;
            g2d.drawString(String.format("%-22s %10s", "Change:", formatCurrencyStatic(sale.getChangeGiven())), margin, y);
            y += lineHeight;
            
            g2d.drawString("Payment: " + sale.getPaymentMethod().getDisplayName(), margin, y);
            y += lineHeight + 10;
            
            // Footer
            g2d.drawLine(margin, y, width - margin, y);
            y += 5;
            
            g2d.setFont(boldFont);
            String thankYou = BrandingConstants.THANK_YOU_MESSAGE;
            int thankYouWidth = g2d.getFontMetrics().stringWidth(thankYou);
            g2d.drawString(thankYou, (width - thankYouWidth) / 2, y);
            y += lineHeight;
            
            g2d.setFont(normalFont);
            g2d.drawString(BrandingConstants.RETURN_POLICY, margin, y);
            
            return PAGE_EXISTS;
        }
    }
    
    // ==================== SHELF TAG PRINTABLE (DEV MODE) ====================
    
    /**
     * Printable implementation for shelf tags in Dev Mode
     */
    private static class ShelfTagPrintable implements Printable {
        private final Product product;
        
        public ShelfTagPrintable(Product product) {
            this.product = product;
        }
        
        @Override
        public int print(Graphics g, PageFormat pf, int pageIndex) {
            if (pageIndex > 0) return NO_SUCH_PAGE;
            
            Graphics2D g2d = (Graphics2D) g;
            g2d.translate(pf.getImageableX(), pf.getImageableY());
            
            Font normalFont = new Font("Arial", Font.PLAIN, 12);
            Font largeFont = new Font("Arial", Font.BOLD, 24);
            Font smallFont = new Font("Arial", Font.PLAIN, 8);
            
            int y = 20;
            int lineHeight = 16;
            int margin = 10;
            int width = (int) pf.getImageableWidth();
            
            // Product Name
            g2d.setFont(normalFont);
            String name = product.getName();
            if (name.length() > 25) name = name.substring(0, 25);
            g2d.drawString(name, margin, y);
            y += lineHeight + 5;
            
            // Price - Large
            g2d.setFont(largeFont);
            g2d.drawString(formatCurrencyStatic(product.getRetailPrice()), margin, y);
            y += lineHeight + 5;
            
            // Barcode (as text since we can't generate barcode images easily in AWT)
            g2d.setFont(smallFont);
            if (product.getBarcode() != null && !product.getBarcode().isEmpty()) {
                g2d.drawString("[" + product.getBarcode() + "]", margin, y);
            }
            y += lineHeight;
            
            // Store name
            g2d.setFont(smallFont);
            g2d.drawString(BrandingConstants.STORE_NAME, margin, y);
            
            return PAGE_EXISTS;
        }
    }
    
    // ==================== ESC/POS HELPERS ====================
    
    private void printReceiptHeader(OutputStream out, Sale sale) throws Exception {
        out.write(ALIGN_CENTER);
        out.write(BOLD_ON);
        out.write(FONT_SIZE_DOUBLE);
        out.write(BrandingConstants.STORE_NAME.getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        out.write(FONT_SIZE_NORMAL);
        out.write(BrandingConstants.STORE_ADDRESS.getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        out.write(BrandingConstants.STORE_PHONE.getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        out.write(BrandingConstants.TAGLINE.getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        out.write("================================".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        out.write(BOLD_OFF);
        out.write(ALIGN_LEFT);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        LocalDateTime dateTime = sale.getCreatedAt();
        String dateStr = sdf.format(Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()));
        out.write(("Date: " + dateStr).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        out.write(("Sale #: " + sale.getId().substring(0, 8)).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        out.write("================================".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
    }
    
    private void printReceiptItems(OutputStream out, List<SaleItem> items) throws Exception {
        out.write(BOLD_ON);
        out.write(String.format("%-20s %8s", "Item", "Total").getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        out.write(BOLD_OFF);
        
        for (SaleItem item : items) {
            String name = item.getProductName();
            if (name.length() > 20) name = name.substring(0, 20);
            out.write(String.format("%-20s %8s", name, formatCurrency(item.getTotalPrice())).getBytes(StandardCharsets.UTF_8));
            out.write(LF);
            out.write(String.format("   %d x %s", item.getQuantity(), formatCurrency(item.getUnitPrice())).getBytes(StandardCharsets.UTF_8));
            out.write(LF);
        }
        
        out.write("================================".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
    }
    
    private void printReceiptTotals(OutputStream out, Sale sale) throws Exception {
        out.write(ALIGN_LEFT);
        out.write(BOLD_ON);
        out.write(String.format("%-22s %10s", "Subtotal:", formatCurrency(sale.getSubtotal())).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        if (sale.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            out.write(String.format("%-22s %10s", "Tax:", formatCurrency(sale.getTaxAmount())).getBytes(StandardCharsets.UTF_8));
            out.write(LF);
        }
        
        out.write(FONT_SIZE_DOUBLE);
        out.write(String.format("%-22s %10s", "TOTAL:", formatCurrency(sale.getTotal())).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        out.write(FONT_SIZE_NORMAL);
        out.write(BOLD_OFF);
        out.write(LF);
        out.write(String.format("%-22s %10s", "Paid:", formatCurrency(sale.getAmountPaid())).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        out.write(String.format("%-22s %10s", "Change:", formatCurrency(sale.getChangeGiven())).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        out.write(String.format("Payment: %s", sale.getPaymentMethod().getDisplayName()).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
    }
    
    private void printReceiptFooter(OutputStream out) throws Exception {
        out.write("================================".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        out.write(ALIGN_CENTER);
        out.write(BOLD_ON);
        out.write(BrandingConstants.THANK_YOU_MESSAGE.getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        out.write(BOLD_OFF);
        out.write(BrandingConstants.RETURN_POLICY.getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        out.write(LF);
        out.write(LF);
        out.write(LF);
    }
    
    private void printShelfTagContent(OutputStream out, Product product) throws Exception {
        out.write(ALIGN_CENTER);
        out.write(FONT_SIZE_NORMAL);
        out.write(BOLD_OFF);
        
        String name = product.getName();
        if (name.length() > 25) name = name.substring(0, 25);
        out.write(name.getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        out.write(LF);
        
        out.write(FONT_SIZE_TRIPLE);
        out.write(BOLD_ON);
        out.write(formatCurrency(product.getRetailPrice()).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        out.write(BOLD_OFF);
        out.write(LF);
        
        if (product.getBarcode() != null && !product.getBarcode().isEmpty()) {
            out.write(new byte[]{0x1D, 0x68, 0x50});
            out.write(new byte[]{0x1D, 0x77, 0x02});
            out.write(GS);
            out.write(new byte[]{0x6B, 0x04});
            out.write(product.getBarcode().getBytes(StandardCharsets.UTF_8));
            out.write(0x00);
            out.write(LF);
        }
        
        out.write(LF);
        out.write(FONT_SIZE_NORMAL);
        out.write(BrandingConstants.STORE_NAME.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Open cash drawer - falls back gracefully if printer not found
     */
    public boolean openCashDrawer() {
        if (DEV_MODE) {
            logger.info("Cash drawer open skipped in Dev Mode");
            return true;
        }
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printerHost, printerPort), connectionTimeout);
            OutputStream out = socket.getOutputStream();
            
            out.write(INITIALIZE);
            out.write(OPEN_DRAWER);
            out.flush();
            
            logger.info("Cash drawer opened");
            return true;
            
        } catch (Exception e) {
            logger.warn("Could not open cash drawer (printer not found): {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if printer is connected
     */
    public boolean isPrinterConnected() {
        if (DEV_MODE) return true;
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printerHost, printerPort), connectionTimeout);
            return socket.isConnected();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Format currency
     */
    private String formatCurrency(BigDecimal amount) {
        return BrandingConstants.CURRENCY_SYMBOL + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    
    /**
     * Format currency (static version)
     */
    private static String formatCurrencyStatic(BigDecimal amount) {
        return BrandingConstants.CURRENCY_SYMBOL + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
