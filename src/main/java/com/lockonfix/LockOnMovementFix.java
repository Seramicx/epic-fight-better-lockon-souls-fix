package com.lockonfix;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(LockOnMovementFix.MOD_ID)
public class LockOnMovementFix {
    public static final String MOD_ID = "lockonmovementfix";
    private static final Logger LOGGER = LogUtils.getLogger();

    public LockOnMovementFix(FMLJavaModLoadingContext context) {
        // Register client config
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, FixConfig.CLIENT_CONFIG, "lockonmovementfix-client.toml");
        
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Lock-On Movement Fix loaded — Elden Ring style lock-on movement enabled!");
        LOGGER.info("Config: turnSpeed={}, idleTurnSpeed={}, lockOnRange={}",
                FixConfig.TURN_SPEED.getDefault(),
                FixConfig.IDLE_TURN_SPEED.getDefault(),
                FixConfig.LOCK_ON_RANGE.getDefault());
    }
}
