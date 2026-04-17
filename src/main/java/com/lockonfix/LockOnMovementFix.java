package com.lockonfix;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@Mod(LockOnMovementFix.MOD_ID)
public class LockOnMovementFix {
    public static final String MOD_ID = "lockonmovementfix";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String KEY_CATEGORY = "key.categories.lockonfix";
    public static KeyMapping TOGGLE_AUTO_LOCKON;
    public static KeyMapping SWAP_SHOULDER;

    public LockOnMovementFix(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, FixConfig.CLIENT_CONFIG, "lockonmovementfix-client.toml");

        context.getModEventBus().addListener(this::onRegisterKeyMappings);

        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Lock-On Movement Fix loaded! Souls-like lock-on movement enabled.");
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        TOGGLE_AUTO_LOCKON = new KeyMapping(
            "key.lockonfix.toggle_auto_lockon",
            InputConstants.UNKNOWN.getValue(),
            KEY_CATEGORY
        );
        event.register(TOGGLE_AUTO_LOCKON);

        SWAP_SHOULDER = new KeyMapping(
            "key.lockonfix.swap_shoulder",
            GLFW.GLFW_KEY_O,
            KEY_CATEGORY
        );
        event.register(SWAP_SHOULDER);
    }
}
