package com.minecompanion.mc.perception;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 结构化生存态势快照器 (StateSnapshotter)
 * 负责从当前游戏客户端中提取玩家生存指标、时空坐标群系、状态效果以及周围威胁态势，
 * 压缩成高密度、低 Token 消耗的结构化文本注入大模型。
 */
public class StateSnapshotter {

    /**
     * 获取当前玩家生存与环境全貌快照（控制在 150 Tokens 左右）
     */
    public static String takeSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        if (player == null || world == null) {
            return "【环境数据】当前不在游戏中。";
        }

        StringBuilder sb = new StringBuilder();

        // 1. 生命与饱食度
        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        int foodLevel = player.getHungerManager().getFoodLevel();
        sb.append(String.format("【生存状态】生命: %.1f/%.1f", health, maxHealth));
        if (health <= 6.0f) {
            sb.append(" (⚠️极度危险/濒死!)");
        } else if (health <= 12.0f) {
            sb.append(" (轻伤/注意回血)");
        }
        sb.append(String.format(", 饱食度: %d/20", foodLevel));

        // 2. 状态效果 (Buff / Debuff)
        if (!player.getStatusEffects().isEmpty()) {
            String effects = player.getStatusEffects().stream()
                    .map(StateSnapshotter::formatStatusEffect)
                    .collect(Collectors.joining(", "));
            sb.append(", 状态效果: [").append(effects).append("]");
        }
        sb.append("\n");

        // 3. 时空环境与维度
        BlockPos pos = player.getBlockPos();
        String dimName = world.getRegistryKey().getValue().toString();
        // 简化常见维度名称
        if (dimName.equals("minecraft:overworld")) dimName = "主世界";
        else if (dimName.equals("minecraft:the_nether")) dimName = "下界";
        else if (dimName.equals("minecraft:the_end")) dimName = "末地";

        long timeOfDay = world.getTimeOfDay() % 24000;
        String timeDesc = (timeOfDay < 12000) ? "白天" : "夜晚";
        if (timeOfDay >= 13000 && timeOfDay <= 23000) timeDesc = "深夜(易刷怪)";

        sb.append(String.format("【时空环境】维度: %s, 坐标: (X:%d, Y:%d, Z:%d), 时间: %s",
                dimName, pos.getX(), pos.getY(), pos.getZ(), timeDesc));
        if (world.isRaining()) {
            sb.append(world.isThundering() ? ", 天气: 雷雨交加!" : ", 天气: 下雨中");
        } else {
            sb.append(", 天气: 晴朗");
        }
        sb.append("\n");

        // 4. 周围 16 格实体态势与敌对生物预警
        Box scanBox = player.getBoundingBox().expand(16.0);
        List<Entity> nearbyEntities = world.getOtherEntities(player, scanBox);
        List<String> threats = new ArrayList<>();
        int friendlyCount = 0;

        for (Entity e : nearbyEntities) {
            if (e instanceof HostileEntity || (e instanceof MobEntity mob && mob.getTarget() != null)) {
                Identifier id = Registries.ENTITY_TYPE.getId(e.getType());
                String modName = FabricLoader.getInstance().getModContainer(id.getNamespace())
                        .map(m -> m.getMetadata().getName()).orElse(id.getNamespace());
                double dist = player.distanceTo(e);
                threats.add(String.format("%s[%s](距%.1f格)", e.getName().getString(), modName, dist));
            } else if (e instanceof LivingEntity) {
                friendlyCount++;
            }
        }

        sb.append("【周边态势】周围 16 格友好生物: ").append(friendlyCount).append(" 个; ");
        if (!threats.isEmpty()) {
            sb.append("⚠️附近敌对威胁(").append(threats.size()).append("个): ")
                    .append(String.join(", ", threats.stream().limit(4).toList()));
        } else {
            sb.append("无明显敌对威胁，周围很安全");
        }

        return sb.toString();
    }

    private static String formatStatusEffect(StatusEffectInstance inst) {
        String name = Registries.STATUS_EFFECT.getId(inst.getEffectType()).getPath();
        int amp = inst.getAmplifier() + 1;
        return name + " " + (amp > 1 ? amp + "级" : "");
    }
}
