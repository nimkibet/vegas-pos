package com.pos.service;

import com.pos.entity.Sale;
import com.pos.entity.Product;
import com.pos.entity.SaleItem;
import com.pos.util.BrandingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * ESC/POS Service
 * Handles receipt printing and cash drawer control using ESC/POS commands.
 */
public class ESCPosService {
    
    private static final Logger logger = LoggerFactory.getLogger(ESCPosService.class);
    private static ESCPosService instance;
    
    // ESC/POS Commands
    private static final byte[] ESC = {0x1B};
    private static final byte[] GS = {0x1D};
    private static final byte[] LF = {0x0A};
    
    // Command constants
    private static final byte[] INITIALIZE = {0x1B, 0x40};           // Initialize printer
    private static final byte[] ALIGN_LEFT = {0x1B, 0x61, 0x00};      // Left align
    private static final byte[] ALIGN_CENTER = {0x1B, 0x61, 0x01};    // Center align
    private static final byte[] ALIGN_RIGHT = {0x1B, 0x61, 0x02};     // Right align
    private static final byte[] BOLD_ON = {0x1B, 0x45, 0x01};         // Bold on
    private static final byte[] BOLD_OFF = {0x1B, 0x45, 0x00};        // Bold off
    private static final byte[] UNDERLINE_ON = {0x1B, 0x2D, 0x01};   // Underline on
    private static final byte[] UNDERLINE_OFF = {0x1B, 0x2D, 0x00};  // Underline off
    private static final byte[] FONT_SIZE_NORMAL = {0x1D, 0x21, 0x00}; // Normal font
    private static final byte[] FONT_SIZE_DOUBLE = {0x1D, 0x21, 0x11}; // Double height and width
    private static final byte[] FONT_SIZE_LARGE = {0x1D, 0x21, 0x22}; // Large font
    private static final byte[] CUT_PAPER = {0x1D, 0x56, 0x00};       // Cut paper
    private static final byte[] PARTIAL_CUT = {0x1D, 0x56, 0x01};    // Partial cut
    
    // Cash drawer commands
    private static final byte[] OPEN_DRAWER = {(byte)0x1B, (byte)0x70, (byte)0x00, (byte)0x19, (byte)0xFA};  // Open drawer
    private static final byte[] KICK_DRAWER = {(byte)0x1B, (byte)0x70, (byte)0x01, (byte)0x19, (byte)0xFA}; // Kick drawer (pin 2)
    
    // Settings
    private String printerHost;
    private int printerPort;
    private int connectionTimeout;
    
    /**
     * Private constructor for singleton pattern
     */
    private ESCPosService() {
        this.printerHost = "localhost";
        this.printerPort = 9100; // Standard ESC/POS port
        this.connectionTimeout = 5000;
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized ESCPosService getInstance() {
        if (instance == null) {
            instance = new ESCPosService();
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
     * Print a receipt
     */
    public boolean printReceipt(Sale sale) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printerHost, printerPort), connectionTimeout);
            OutputStream out = socket.getOutputStream();
            
            // Initialize printer
            out.write(INITIALIZE);
            
            // Print receipt content
            printReceiptHeader(out, sale);
            printReceiptItems(out, sale.getItems());
            printReceiptTotals(out, sale);
            printReceiptFooter(out);
            
            // Cut paper
            out.write(PARTIAL_CUT);
            out.flush();
            
            logger.info("Receipt printed successfully for sale: {}", sale.getId());
            return true;
            
        } catch (Exception e) {
            logger.error("Error printing receipt", e);
            return false;
        }
    }
    
    /**
     * Print receipt header
     */
    private void printReceiptHeader(OutputStream out, Sale sale) throws Exception {
        // Store name (bold, large)
        out.write(ALIGN_CENTER);
        out.write(BOLD_ON);
        out.write(FONT_SIZE_DOUBLE);
        out.write("MY POS STORE".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        // Address
        out.write(FONT_SIZE_NORMAL);
        out.write("123 Main Street".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        out.write("City, State 12345".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        out.write("Tel: (555) 123-4567".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        // Separator
        out.write("--------------------------------".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        // Date and sale number
        out.write(BOLD_OFF);
        out.write(ALIGN_LEFT);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        LocalDateTime dateTime = sale.getCreatedAt();
        String dateStr = sdf.format(Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()));
        out.write(("Date: " + dateStr).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        out.write(("Sale #: " + sale.getId().substring(0, 8)).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        // Separator
        out.write("--------------------------------".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
    }
    
    /**
     * Print receipt items
     */
    private void printReceiptItems(OutputStream out, List<SaleItem> items) throws Exception {
        // Header
        out.write(BOLD_ON);
        out.write(String.format("%-18s %6s", "Item", "Total").getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        out.write(BOLD_OFF);
        
        // Items
        for (SaleItem item : items) {
            String name = item.getProductName();
            if (name.length() > 18) {
                name = name.substring(0, 18);
            }
            String line = String.format("%-18s %6s", 
                name, 
                formatCurrency(item.getTotalPrice()));
            out.write(line.getBytes(StandardCharsets.UTF_8));
            out.write(LF);
            
            // Quantity and unit price
            String qtyLine;
            if (item.isBoxSale() && item.getProduct() != null) {
                Product p = item.getProduct();
                int per = Math.max(1, p.getPiecesPerBulk());
                int boxes = per > 0 ? item.getQuantity() / per : 1;
                if (boxes < 1) {
                    boxes = 1;
                }
                BigDecimal boxPrice = p.getBulkPrice() != null ? p.getBulkPrice() : BigDecimal.ZERO;
                qtyLine = String.format("   %d box(es) @ %s ea.",
                        boxes,
                        formatCurrency(boxPrice));
            } else {
                qtyLine = String.format("   %d x %s",
                        item.getQuantity(),
                        formatCurrency(item.getUnitPrice()));
            }
            out.write(qtyLine.getBytes(StandardCharsets.UTF_8));
            out.write(LF);
        }
        
        // Separator
        out.write("--------------------------------".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
    }
    
    /**
     * Print receipt totals
     */
    private void printReceiptTotals(OutputStream out, Sale sale) throws Exception {
        out.write(ALIGN_LEFT);
        out.write(BOLD_ON);
        
        // Subtotal
        out.write(String.format("%-20s %12s", "Subtotal:", formatCurrency(sale.getSubtotal())).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        // Tax
        if (sale.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            out.write(String.format("%-20s %12s", "Tax:", formatCurrency(sale.getTaxAmount())).getBytes(StandardCharsets.UTF_8));
            out.write(LF);
        }
        
        // Discount
        if (sale.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            out.write(String.format("%-20s %12s", "Discount:", "-" + formatCurrency(sale.getDiscountAmount())).getBytes(StandardCharsets.UTF_8));
            out.write(LF);
        }
        
        // Total (large)
        out.write(FONT_SIZE_DOUBLE);
        out.write(String.format("%-20s %12s", "TOTAL:", formatCurrency(sale.getTotal())).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        out.write(FONT_SIZE_NORMAL);
        out.write(BOLD_OFF);
        
        // Payment info
        out.write(LF);
        out.write(String.format("%-20s %12s", "Paid:", formatCurrency(sale.getAmountPaid())).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        out.write(String.format("%-20s %12s", "Change:", formatCurrency(sale.getChangeGiven())).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        // Payment method
        out.write(String.format("Payment: %s", sale.getPaymentMethod().getDisplayName()).getBytes(StandardCharsets.UTF_8));
        out.write(LF);
    }
    
    /**
     * Print receipt footer
     */
    private void printReceiptFooter(OutputStream out) throws Exception {
        // Separator
        out.write("--------------------------------".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        // Footer message
        out.write(ALIGN_CENTER);
        out.write(BOLD_ON);
        out.write("Thank you for your business!".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        out.write(BOLD_OFF);
        
        // Barcode (CODE128 format)
        out.write(GS);
        out.write(new byte[]{0x6B, 0x00}); // CODE128, no checksum
        out.write("POS123456789".getBytes(StandardCharsets.UTF_8));
        out.write(LF);
        
        // Extra line feeds
        out.write(LF);
        out.write(LF);
        out.write(LF);
    }
    
    /**
     * Open cash drawer
     */
    public boolean openCashDrawer() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printerHost, printerPort), connectionTimeout);
            OutputStream out = socket.getOutputStream();
            
            // Send drawer open command
            out.write(OPEN_DRAWER);
            out.flush();
            
            logger.info("Cash drawer opened");
            return true;
            
        } catch (Exception e) {
            logger.error("Error opening cash drawer", e);
            return false;
        }
    }
    
    /**
     * Pulse cash drawer (alternative method)
     */
    public boolean pulseCashDrawer() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printerHost, printerPort), connectionTimeout);
            OutputStream out = socket.getOutputStream();
            
            // Send pulse command
            out.write(KICK_DRAWER);
            out.flush();
            
            logger.info("Cash drawer pulsed");
            return true;
            
        } catch (Exception e) {
            logger.error("Error pulsing cash drawer", e);
            return false;
        }
    }
    
    /**
     * Check if printer is connected
     */
    public boolean isPrinterConnected() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printerHost, printerPort), connectionTimeout);
            return socket.isConnected();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Print test page
     */
    public boolean printTestPage() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printerHost, printerPort), connectionTimeout);
            OutputStream out = socket.getOutputStream();
            
            out.write(INITIALIZE);
            out.write(ALIGN_CENTER);
            out.write(FONT_SIZE_DOUBLE);
            out.write("TEST PAGE".getBytes(StandardCharsets.UTF_8));
            out.write(LF);
            out.write(FONT_SIZE_NORMAL);
            out.write(LF);
            out.write("Printer is working!".getBytes(StandardCharsets.UTF_8));
            out.write(LF);
            out.write(LF);
            out.write(PARTIAL_CUT);
            out.flush();
            
            logger.info("Test page printed");
            return true;
            
        } catch (Exception e) {
            logger.error("Error printing test page", e);
            return false;
        }
    }
    
    /**
     * Format currency
     */
    private String formatCurrency(BigDecimal amount) {
        return BrandingConstants.CURRENCY_SYMBOL + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
