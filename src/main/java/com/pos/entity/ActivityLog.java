package com.pos.entity;

import java.time.LocalDateTime;
import java.util.UUID;

public class ActivityLog {
    
    public enum ActionType {
        REMOVE_ITEM("Remove Item"),
        CLEAR_CART("Clear Cart"),
        OPEN_DRAWER("Open Cash Drawer"),
        REFUND("Refund"),
        VOID_SALE("Void Sale"),
        APPROVE_PRODUCT("Approve Product"),
        REJECT_PRODUCT("Reject Product"),
        STOCK_ADJUSTMENT("Stock Adjustment"),
        SHRINKAGE("Shrinkage"),
        LOGIN("Login"),
        LOGOUT("Logout"),
        USER_MANAGEMENT("User Management");
        
        private final String displayName;
        
        ActionType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private String id;
    private String userId;
    private String userName;
    private ActionType actionType;
    private String targetDescription;
    private String details;
    private boolean isSynced;
    private LocalDateTime createdAt;
    
    public ActivityLog() {
        this.id = UUID.randomUUID().toString();
        this.isSynced = false;
        this.createdAt = LocalDateTime.now();
    }
    
    public ActivityLog(String userId, String userName, ActionType actionType, String targetDescription) {
        this();
        this.userId = userId;
        this.userName = userName;
        this.actionType = actionType;
        this.targetDescription = targetDescription;
    }
    
    public ActivityLog(String userId, String userName, ActionType actionType, String targetDescription, String details) {
        this(userId, userName, actionType, targetDescription);
        this.details = details;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public ActionType getActionType() {
        return actionType;
    }
    
    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }
    
    public String getTargetDescription() {
        return targetDescription;
    }
    
    public void setTargetDescription(String targetDescription) {
        this.targetDescription = targetDescription;
    }
    
    public String getDetails() {
        return details;
    }
    
    public void setDetails(String details) {
        this.details = details;
    }
    
    public boolean isSynced() {
        return isSynced;
    }
    
    public void setSynced(boolean synced) {
        isSynced = synced;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public ActivityLog(String id, String userId, String userName, ActionType actionType, 
                        String targetDescription, String details, boolean isSynced, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.actionType = actionType;
        this.targetDescription = targetDescription;
        this.details = details;
        this.isSynced = isSynced;
        this.createdAt = createdAt;
    }
    
    public String getLogMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(userName).append(" ").append(actionType.getDisplayName());
        if (targetDescription != null && !targetDescription.isEmpty()) {
            sb.append(": ").append(targetDescription);
        }
        if (details != null && !details.isEmpty()) {
            sb.append(" (").append(details).append(")");
        }
        return sb.toString();
    }
}