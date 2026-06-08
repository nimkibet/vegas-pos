package com.pos.service;

import com.pos.entity.Product;
import com.pos.util.BrandingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.*;
import java.awt.print.*;
import javax.print.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Label Printer Service
 * Handles shelf tag printing using java.awt.print.
 */
public class LabelPrinterService implements Printable {
    
    private static final Logger logger = LoggerFactory.getLogger(LabelPrinterService.class);
    private static LabelPrinterService instance;
    
    private PrinterJob printerJob;
    private PageFormat pageFormat;
    private Product currentProduct;
    private boolean isWholesalePrice;
    
    // Label dimensions (in points - 1/72 inch)
    private static final double LABEL_WIDTH = 280;  // ~4 inches
    private static final double LABEL_HEIGHT = 180; // ~2.5 inches
    
    /**
     * Private constructor for singleton pattern
     */
    private LabelPrinterService() {
        this.printerJob = PrinterJob.getPrinterJob();
        this.pageFormat = new PageFormat();
        pageFormat.setOrientation(PageFormat.LANDSCAPE);
        
        // Set custom paper size for shelf labels
        Paper labelPaper = new Paper();
        labelPaper.setSize(LABEL_WIDTH, LABEL_HEIGHT);
        labelPaper.setImageableArea(0, 0, LABEL_WIDTH, LABEL_HEIGHT);
        pageFormat.setPaper(labelPaper);
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized LabelPrinterService getInstance() {
        if (instance == null) {
            instance = new LabelPrinterService();
        }
        return instance;
    }
    
    /**
     * Print a single product label
     */
    public boolean printLabel(Product product, boolean wholesalePrice) {
        this.currentProduct = product;
        this.isWholesalePrice = wholesalePrice;
        
        try {
            printerJob.setPrintable(this, pageFormat);
            
            if (printerJob.printDialog()) {
                printerJob.print();
                logger.info("Label printed for product: {}", product.getName());
                return true;
            }
            
            return false;
            
        } catch (PrinterException e) {
            logger.error("Error printing label", e);
            return false;
        }
    }
    
    /**
     * Print multiple product labels
     */
    public boolean printLabels(List<Product> products, boolean wholesalePrice) {
        try {
            List<Product> printList = new ArrayList<>(products);
            
            printerJob.setPrintable(new MultiLabelPrintable(printList, wholesalePrice, pageFormat), pageFormat);
            
            if (printerJob.printDialog()) {
                printerJob.print();
                logger.info("Labels printed for {} products", products.size());
                return true;
            }
            
            return false;
            
        } catch (PrinterException e) {
            logger.error("Error printing labels", e);
            return false;
        }
    }
    
    /**
     * Printable interface method
     */
    @Override
    public int print(Graphics graphics, PageFormat pf, int pageIndex) throws PrinterException {
        if (pageIndex != 0) {
            return NO_SUCH_PAGE;
        }
        
        Graphics2D g2d = (Graphics2D) graphics;
        g2d.translate(pf.getImageableX(), pf.getImageableY());
        
        drawLabel(g2d, currentProduct, isWholesalePrice);
        
        return PAGE_EXISTS;
    }
    
    /**
     * Draw a single label
     */
    private void drawLabel(Graphics2D g2d, Product product, boolean wholesale) {
        // Set up fonts
        Font titleFont = new Font("Arial", Font.BOLD, 24);
        Font priceFont = new Font("Arial", Font.BOLD, 36);
        Font barcodeFont = new Font("Code128", Font.PLAIN, 24);
        Font labelFont = new Font("Arial", Font.PLAIN, 12);
        
        // Get price
        BigDecimal price = wholesale ? product.getWholesalePrice() : product.getRetailPrice();
        String priceLabel = wholesale ? "WHOLESALE" : "RETAIL";
        
        // Draw background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, (int)LABEL_WIDTH, (int)LABEL_HEIGHT);
        
        // Draw border
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRect(1, 1, (int)LABEL_WIDTH - 3, (int)LABEL_HEIGHT - 3);
        
        // Draw product name (top)
        g2d.setFont(titleFont);
        String productName = product.getName();
        if (productName.length() > 15) {
            productName = productName.substring(0, 15);
        }
        drawCenteredString(g2d, productName, (int)LABEL_WIDTH / 2, 30);
        
        // Draw price label
        g2d.setFont(labelFont);
        drawCenteredString(g2d, priceLabel, (int)LABEL_WIDTH / 2, 55);
        
        // Draw price (large)
        g2d.setFont(priceFont);
        String priceText = formatCurrency(price);
        drawCenteredString(g2d, priceText, (int)LABEL_WIDTH / 2, 95);
        
        // Draw barcode (bottom)
        if (product.getBarcode() != null && !product.getBarcode().isEmpty()) {
            g2d.setFont(barcodeFont);
            drawCenteredString(g2d, product.getBarcode(), (int)LABEL_WIDTH / 2, 140);
        }
    }
    
    /**
     * Draw centered string
     */
    private void drawCenteredString(Graphics2D g2d, String text, int x, int y) {
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();
        
        g2d.drawString(text, x - textWidth / 2, y + textHeight / 4);
    }
    
    /**
     * Format currency
     */
    private String formatCurrency(BigDecimal amount) {
        return BrandingConstants.CURRENCY_SYMBOL + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    
    /**
     * Get list of available printers
     */
    public String[] getAvailablePrinters() {
        PrintService[] services = PrinterJob.lookupPrintServices();
        String[] printerNames = new String[services.length];
        
        for (int i = 0; i < services.length; i++) {
            printerNames[i] = services[i].getName();
        }
        
        return printerNames;
    }
    
    /**
     * Set the printer
     */
    public boolean setPrinter(String printerName) {
        PrintService[] services = PrinterJob.lookupPrintServices();
        
        for (PrintService service : services) {
            if (service.getName().equals(printerName)) {
                try {
                    printerJob.setPrintService(service);
                    logger.info("Printer set to: {}", printerName);
                    return true;
                } catch (PrinterException e) {
                    logger.error("Error setting printer", e);
                    return false;
                }
            }
        }
        
        logger.warn("Printer not found: {}", printerName);
        return false;
    }
    
    /**
     * Print test label
     */
    public boolean printTestLabel() {
        Product testProduct = new Product();
        testProduct.setName("Test Product");
        testProduct.setBarcode("123456789012");
        testProduct.setRetailPrice(new BigDecimal("9.99"));
        testProduct.setWholesalePrice(new BigDecimal("5.99"));
        
        return printLabel(testProduct, false);
    }
    
    /**
     * Inner class for printing multiple labels
     */
    private static class MultiLabelPrintable implements Printable {
        private final List<Product> products;
        private final boolean wholesale;
        private final PageFormat pageFormat;
        
        public MultiLabelPrintable(List<Product> products, boolean wholesale, PageFormat pageFormat) {
            this.products = products;
            this.wholesale = wholesale;
            this.pageFormat = pageFormat;
        }
        
        @Override
        public int print(Graphics graphics, PageFormat pf, int pageIndex) throws PrinterException {
            int productsPerPage = 4; // 2x2 grid
            int startIndex = pageIndex * productsPerPage;
            
            if (startIndex >= products.size()) {
                return NO_SUCH_PAGE;
            }
            
            Graphics2D g2d = (Graphics2D) graphics;
            g2d.translate(pf.getImageableX(), pf.getImageableY());
            
            int endIndex = Math.min(startIndex + productsPerPage, products.size());
            
            for (int i = startIndex; i < endIndex; i++) {
                int row = (i - startIndex) / 2;
                int col = (i - startIndex) % 2;
                
                int x = col * (int)LABEL_WIDTH;
                int y = row * (int)LABEL_HEIGHT;
                
                g2d.translate(x, y);
                drawSingleLabel(g2d, products.get(i), wholesale);
                g2d.translate(-x, -y);
            }
            
            return PAGE_EXISTS;
        }
        
        private void drawSingleLabel(Graphics2D g2d, Product product, boolean wholesale) {
            // Simplified version of drawLabel for multi-label
            Font titleFont = new Font("Arial", Font.BOLD, 14);
            Font priceFont = new Font("Arial", Font.BOLD, 20);
            Font barcodeFont = new Font("Arial", Font.PLAIN, 10);
            
            BigDecimal price = wholesale ? product.getWholesalePrice() : product.getRetailPrice();
            
            // Border
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(1));
            g2d.drawRect(0, 0, (int)LABEL_WIDTH - 1, (int)LABEL_HEIGHT - 1);
            
            // Product name
            g2d.setFont(titleFont);
            String name = product.getName();
            if (name.length() > 20) {
                name = name.substring(0, 20);
            }
            drawLabelString(g2d, name, 10, 20);
            
            // Price
            g2d.setFont(priceFont);
            drawLabelString(g2d, formatCurrency(price), 10, 50);
            
            // Barcode
            if (product.getBarcode() != null) {
                g2d.setFont(barcodeFont);
                drawLabelString(g2d, product.getBarcode(), 10, 70);
            }
        }
        
        private void drawLabelString(Graphics2D g2d, String text, int x, int y) {
            g2d.drawString(text, x, y);
        }
        
        private String formatCurrency(BigDecimal amount) {
            return BrandingConstants.CURRENCY_SYMBOL + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
