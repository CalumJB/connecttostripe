package com.boustead.connecttostripe.billing;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PlanConfigurationService {

    private final Map<String, PlanConfiguration> planConfigurations;
    private final PlanConfiguration defaultPlan;

    public PlanConfigurationService() {
        planConfigurations = new HashMap<>();
        
        // Configure your actual Stripe price IDs to plan mappings
        
        // FREE Plan - No price ID, for users without active subscriptions
        planConfigurations.put("FREE", new PlanConfiguration("FREE", 20, "Free"));
        
        // STARTER Plan - Map your actual price ID
//        price_1S0GTDF8MQhGKD54Eak9NxtO, price_1S0GTDF8MQhGKD54insZHYGe
        planConfigurations.put("price_1S0GTDF8MQhGKD54Eak9NxtO", new PlanConfiguration("STARTER", 100, "Starter"));
        planConfigurations.put("price_1S0GTDF8MQhGKD54insZHYGe", new PlanConfiguration("STARTER", 100, "Starter"));
        
        // STANDARD Plan - Map your actual price ID
        // price_1S0GTDF8MQhGKD545JYTikaY, price_1S0GTDF8MQhGKD54drvjawgT
        planConfigurations.put("price_1S0GTDF8MQhGKD545JYTikaY", new PlanConfiguration("STANDARD", 1000, "Standard"));
        planConfigurations.put("price_1S0GTDF8MQhGKD54drvjawgT", new PlanConfiguration("STANDARD", 1000, "Standard"));
        
        // PRO Plan - Map your actual price ID
        // price_1S0GTDF8MQhGKD54gC0fiaNn, price_1S0GTDF8MQhGKD54BeFYga6G
        planConfigurations.put("price_1S0GTDF8MQhGKD54gC0fiaNn", new PlanConfiguration("PRO", 5000, "Pro"));
        planConfigurations.put("price_1S0GTDF8MQhGKD54BeFYga6G", new PlanConfiguration("PRO", 5000, "Pro"));
        
        // Default plan for unknown configurations
        defaultPlan = new PlanConfiguration("UNKNOWN", 5000, "Unknown");
    }

    /**
     * Get plan configuration by price ID
     */
    public PlanConfiguration getPlanByPriceId(String priceId) {
        return Optional.ofNullable(planConfigurations.get(priceId))
                .orElse(defaultPlan);
    }

    /**
     * Get plan configuration by plan name
     */
    public PlanConfiguration getPlanByName(String planName) {
        return planConfigurations.values().stream()
                .filter(config -> config.getPlanName().equals(planName))
                .findFirst()
                .orElse(defaultPlan);
    }

    /**
     * Determine plan name from Stripe price ID
     */
    public String determinePlanName(String priceId) {
        if (priceId == null) return "UNKNOWN";
        
        // Direct lookup from price ID to plan configuration
        return getPlanByPriceId(priceId).getPlanName();
    }

    /**
     * Add or update plan configuration
     */
    public void addPlanConfiguration(String identifier, PlanConfiguration config) {
        planConfigurations.put(identifier, config);
    }
}