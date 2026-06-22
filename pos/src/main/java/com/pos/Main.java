package com.pos;

import com.pos.database.DatabaseManager;
import com.pos.controller.LoginController;
import com.pos.service.AuthenticationService;
import com.pos.service.SyncService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Application Entry Point
 * Initializes the POS system and launches the JavaFX UI.
 */
public class Main extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static Stage primaryStage;
    
    @Override
    public void start(Stage stage) throws Exception {
        try {
            // Initialize database
            initializeDatabase();
            
            // Start sync service
            startSyncService();
            
            // Setup JavaFX stage
            primaryStage = stage;
            
            // Set custom application icon
            try {
                stage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/logo.png")));
            } catch (Exception e) {
                logger.warn("Failed to load application icon: {}", e.getMessage());
            }
            
            showLoginScreen();

            stage.setTitle("Vegas Supermarket POS");
            stage.setResizable(false);
            stage.sizeToScene();
            stage.centerOnScreen();

            stage.getProperties().put("windowsStyle", "decorated");
            
            stage.setOnCloseRequest(event -> {
                System.out.println("Application closing... Initiating automated backup.");
                DatabaseManager.getInstance().performAutomatedBackup();
                System.out.println("Backup complete. Shutting down.");
            });

            stage.show();
            
            logger.info("POS System started successfully");
            
        } catch (Exception e) {
            logger.error("Failed to start POS System", e);
            throw e;
        }
    }
    
    /**
     * Initialize the database
     */
    private void initializeDatabase() {
        try {
            DatabaseManager.getInstance().initialize();
            logger.info("Database initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    /**
     * Start the sync service
     */
    private void startSyncService() {
        try {
            SyncService.getInstance().start();
            logger.info("Sync service started");
        } catch (Exception e) {
            logger.warn("Failed to start sync service: {}", e.getMessage());
        }
    }
    
    /**
     * Show the login screen
     */
    public void showLoginScreen() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            
            // Set primary stage on controller for navigation
            LoginController loginController = loader.getController();
            loginController.setPrimaryStage(primaryStage);
            
            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.setTitle("POS System - Login");
            primaryStage.setResizable(false);
            primaryStage.sizeToScene();
            
        } catch (Exception e) {
            logger.error("Failed to load login screen", e);
        }
    }
    
    /**
     * Show the main POS screen after successful login
     */
    public void showPOSScreen() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pos.fxml"));
            Parent root = loader.load();
            
            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.setTitle("POS System - Main");
            primaryStage.setMaximized(true);
            primaryStage.setResizable(true);
            
        } catch (Exception e) {
            logger.error("Failed to load POS screen", e);
        }
    }
    
    @Override
    public void stop() throws Exception {
        logger.info("POS System shutting down");
        
        // Stop sync service
        SyncService.getInstance().stop();
        
        // Close database
        DatabaseManager.getInstance().close();
        
        super.stop();
    }
    
    /**
     * Main entry point
     */
    public static void main(String[] args) {
        launch(args);
    }
}
