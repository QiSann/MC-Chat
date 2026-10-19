package com.minecompanion.client;

import com.minecompanion.MineCompanionMod;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MineCompanion AI 模组客户端入口类
 */
public class MineCompanionClient implements ClientModInitializer {
    public static final Logger CLIENT_LOGGER = LoggerFactory.getLogger(MineCompanionMod.MOD_ID + "-client");

    @Override
    public void onInitializeClient() {
        CLIENT_LOGGER.info("[{}] 客户端陪伴感知与聊天渲染层初始化完毕喵！(=^･ω･^=)", MineCompanionMod.MOD_NAME);
    }
}
