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
        // FREE TIER NOT CURRENTLY ACTIVE
        planConfigurations.put("FREE", new PlanConfiguration("FREE", 20, "Free"));

        // TEST Plan - No card requested
        planConfigurations.put("price_1S4HTfF7gMXUJNvvp4PdUPt6", new PlanConfiguration("TEST", 20, "Test"));


        // STARTER Plan - Map your actual price ID
        // DEMO
        //MONTHLY
        planConfigurations.put("price_1S0cJdF8MQhGKD54ctMhQpHi", new PlanConfiguration("STARTER", 100, "Starter"));
        //YEARLY
        planConfigurations.put("price_1S0cJdF8MQhGKD54s6EoTxbc", new PlanConfiguration("STARTER", 100, "Starter"));
        // PROD
        //MONTHLY
        planConfigurations.put("price_1S3AxWF7gMXUJNvvy4u8zwQW", new PlanConfiguration("STARTER", 100, "Starter"));
        //YEARLY
        planConfigurations.put("price_1S3AxWF7gMXUJNvvks1oI86T", new PlanConfiguration("STARTER", 100, "Starter"));
        
        // STANDARD Plan - Map your actual price ID
        //DEMO
        //MONTHLY
        planConfigurations.put("price_1S0cKDF8MQhGKD543LhbZcQc", new PlanConfiguration("STANDARD", 1000, "Standard"));
        //YEARLY
        planConfigurations.put("price_1S0cKjF8MQhGKD54uf2Lf9SH", new PlanConfiguration("STANDARD", 1000, "Standard"));
        //PROD
        //MONTHLY
        planConfigurations.put("price_1S3AxUF7gMXUJNvvfvp1gTlB", new PlanConfiguration("STANDARD", 1000, "Standard"));
        //YEARLY
        planConfigurations.put("price_1S3AxUF7gMXUJNvvaHImQmmI", new PlanConfiguration("STANDARD", 1000, "Standard"));


        // PRO Plan - Map your actual price ID
        //DEMO
        //MONTHLY
        planConfigurations.put("price_1S0cLSF8MQhGKD54ae8vB3tz", new PlanConfiguration("PRO", 5000, "Pro"));
        //YEARLY
        planConfigurations.put("price_1S0cLSF8MQhGKD542b2g6b2s", new PlanConfiguration("PRO", 5000, "Pro"));
        //PROD
        //MONTHLY
        planConfigurations.put("price_1S3AxOF7gMXUJNvvgXXMa1zN", new PlanConfiguration("PRO", 5000, "Pro"));
        //YEARLY
        planConfigurations.put("price_1S3AxOF7gMXUJNvvC6ngQIVO", new PlanConfiguration("PRO", 5000, "Pro"));
        
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