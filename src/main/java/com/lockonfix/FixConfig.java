package com.lockonfix;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration for the Lock-On Movement Fix mod.
 * Config file: lockonmovementfix-client.toml
 */
public class FixConfig {

    public static final ForgeConfigSpec CLIENT_CONFIG;
    
    // Movement
    public static final ForgeConfigSpec.DoubleValue TURN_SPEED;
    public static final ForgeConfigSpec.DoubleValue IDLE_TURN_SPEED;
    
    // Lock-On Range
    public static final ForgeConfigSpec.IntValue LOCK_ON_RANGE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Movement Settings").push("movement");
        
        TURN_SPEED = builder
                .comment(
                    "How fast the player turns while moving (per-tick interpolation factor).",
                    "Lower = smoother/slower turns, Higher = snappier/faster turns.",
                    "0.1 = very smooth drift, 0.5 = balanced, 1.0 = instant snap (no smoothing)."
                )
                .defineInRange("turnSpeed", 0.45, 0.05, 1.0);
        
        IDLE_TURN_SPEED = builder
                .comment(
                    "How fast the player turns to face the target when standing still (for attacks).",
                    "Should be higher than turnSpeed so attacks aim at the enemy quickly.",
                    "0.5 = smooth, 0.8 = fast, 1.0 = instant."
                )
                .defineInRange("idleTurnSpeed", 0.7, 0.05, 1.0);
        
        builder.pop();

        builder.comment("Lock-On Range Settings").push("lockOnRange");
        
        LOCK_ON_RANGE = builder
                .comment(
                    "Maximum lock-on range in blocks.",
                    "Epic Fight's default is around 16-20 blocks, which is too short",
                    "for flying bosses like the Wither and Ender Dragon.",
                    "This overrides Epic Fight's internal lock-on range."
                )
                .defineInRange("lockOnRange", 64, 8, 256);
        
        builder.pop();

        CLIENT_CONFIG = builder.build();
    }
}
