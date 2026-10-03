package com.example.solvalheimtickfix;

import com.example.solvalheimtickfix.compat.SolCompat;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SolValheimTickFix implements ModInitializer {
    public static final String MOD_ID = "solvalheimtickfix";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SolCompat.bootstrap();
    }
}
