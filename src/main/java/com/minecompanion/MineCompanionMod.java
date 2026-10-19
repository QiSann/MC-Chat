package com.minecompanion;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MineCompanion AI 模组通用入口类
 */
public class MineCompanionMod implements ModInitializer {
    public static final String MOD_ID = "minecompanion";
    public static final String MOD_NAME = "MineCompanion AI";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] 模组核心层正在初始化喵~ (=^･ω･^=)", MOD_NAME);
    }
}
