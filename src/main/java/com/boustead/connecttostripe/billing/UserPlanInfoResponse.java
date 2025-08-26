package com.boustead.connecttostripe.billing;

public class UserPlanInfoResponse {
    private String planName;
    private String planDisplayName;
    private int monthlySyncLimit;
    private int currentMonthUsage;
    private int remainingSyncs;
    private String status;

    public UserPlanInfoResponse(String planName, String planDisplayName, int monthlySyncLimit, 
                               int currentMonthUsage, String status) {
        this.planName = planName;
        this.planDisplayName = planDisplayName;
        this.monthlySyncLimit = monthlySyncLimit;
        this.currentMonthUsage = currentMonthUsage;
        this.remainingSyncs = Math.max(0, monthlySyncLimit - currentMonthUsage);
        this.status = status;
    }

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getPlanDisplayName() {
        return planDisplayName;
    }

    public void setPlanDisplayName(String planDisplayName) {
        this.planDisplayName = planDisplayName;
    }

    public int getMonthlySyncLimit() {
        return monthlySyncLimit;
    }

    public void setMonthlySyncLimit(int monthlySyncLimit) {
        this.monthlySyncLimit = monthlySyncLimit;
    }


    public int getCurrentMonthUsage() {
        return currentMonthUsage;
    }

    public void setCurrentMonthUsage(int currentMonthUsage) {
        this.currentMonthUsage = currentMonthUsage;
    }

    public int getRemainingSyncs() {
        return remainingSyncs;
    }

    public void setRemainingSyncs(int remainingSyncs) {
        this.remainingSyncs = remainingSyncs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}