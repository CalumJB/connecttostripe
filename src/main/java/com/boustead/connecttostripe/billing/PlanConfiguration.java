package com.boustead.connecttostripe.billing;

public class PlanConfiguration {
    private final String planName;
    private final int monthlySyncLimit;
    private final String displayName;
    private final boolean unlimited;

    public PlanConfiguration(String planName, int monthlySyncLimit, String displayName) {
        this.planName = planName;
        this.monthlySyncLimit = monthlySyncLimit;
        this.displayName = displayName;
        this.unlimited = monthlySyncLimit == -1;
    }

    public String getPlanName() {
        return planName;
    }

    public int getMonthlySyncLimit() {
        return monthlySyncLimit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isUnlimited() {
        return unlimited;
    }

    public boolean canPerformSync(int currentUsage) {
        return unlimited || currentUsage < monthlySyncLimit;
    }

    public String getLimitMessage() {
        return unlimited ? "Unlimited syncs" : monthlySyncLimit + " syncs per month";
    }
}