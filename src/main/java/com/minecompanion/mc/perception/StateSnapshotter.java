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
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 结构化生存态势快照器 (StateSnapshotter)
 * 负责从当前游戏客户端中提取玩家生存指标、背包真实食物/装备、时空坐标群系以及周围【存活】威胁态势，
 * 确保 AI 100% 基于玩家真实背包物资与存活怪物发言，绝不虚构玩家没有的金苹果或已死亡的怪物。
 */
public class StateSnapshotter {

    /**
     * 获取当前玩家生存、背包真实食物与环境全貌快照
     */
    public static String takeSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        if (player == null || world == null) {
            return "【环境数据】当前不在游戏中。";
        }

        StringBuilder sb = new StringBuilder();

        // 1. 生命、死亡状态与饱食度
        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        int foodLevel = player.getHungerManager().getFoodLevel();

        if (player.isDead() || health <= 0.0f) {
            sb.append("【生存状态】💀 玩家当前已经死亡！(生命: 0.0/").append(maxHealth).append(")");
        } else {
            sb.append(String.format("【生存状态】生命: %.1f/%.1f", health, maxHealth));
            if (health <= 6.0f) {
                sb.append(" (⚠️残血濒死!)");
            } else if (health <= 12.0f) {
                sb.append(" (受伤掉血)");
            }
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

        // 3. 🌟 背包真实食物与快捷栏装备扫描（彻底杜绝乱劝玩家吃身上没有的金苹果！）
        sb.append(scanRealInventoryFoodAndGear(player)).append("\n");

        // 4. 时空环境与维度
        BlockPos pos = player.getBlockPos();
        String dimName = world.getRegistryKey().getValue().toString();
        if (dimName.equals("minecraft:overworld")) dimName = "主世界";
        else if (dimName.equals("minecraft:the_nether")) dimName = "下界";
        else if (dimName.equals("minecraft:the_end")) dimName = "末地";

        long timeOfDay = world.getTimeOfDay() % 24000;
        String timeDesc = (timeOfDay < 12000) ? "白天" : "夜晚";
        if (timeOfDay >= 13000 && timeOfDay <= 23000) timeDesc = "深夜";

        sb.append(String.format("【时空环境】维度: %s, 坐标: (X:%d, Y:%d, Z:%d), 时间: %s",
                dimName, pos.getX(), pos.getY(), pos.getZ(), timeDesc));
        if (world.isRaining()) {
            sb.append(world.isThundering() ? ", 天气: 雷暴" : ", 天气: 下雨");
        } else {
            sb.append(", 天气: 晴朗");
        }
        sb.append("\n");

        // 5. 🌟 周围 16 格【活体】生物雷达与怪物数量分级（严格剔除已死亡/倒地中的怪物！）
        Box scanBox = player.getBoundingBox().expand(16.0);
        List<Entity> nearbyEntities = world.getOtherEntities(player, scanBox);
        List<String> aliveThreats = new ArrayList<>();
        int friendlyCount = 0;

        for (Entity e : nearbyEntities) {
            if (!(e instanceof LivingEntity living)) continue;
            // 严格过滤掉已经死亡、血量归零、正在播放死亡倒地动画或已被移除的实体
            if (e.isRemoved() || !living.isAlive() || living.isDead() || living.getHealth() <= 0.0f || living.deathTime > 0) {
                continue;
            }

            if (e instanceof HostileEntity || (e instanceof MobEntity mob && mob.getTarget() != null)) {
                Identifier id = Registries.ENTITY_TYPE.getId(e.getType());
                String modName = FabricLoader.getInstance().getModContainer(id.getNamespace())
                        .map(m -> m.getMetadata().getName()).orElse(id.getNamespace());
                double dist = player.distanceTo(e);
                aliveThreats.add(String.format("%s(血%.0f,距%.1f格,%s)",
                        e.getName().getString(), living.getHealth(), dist, modName));
            } else {
                friendlyCount++;
            }
        }

        int threatCount = aliveThreats.size();
        sb.append("【周围活体态势】友好生物: ").append(friendlyCount).append("只; ");
        if (threatCount == 0) {
            sb.append("当前周围 16 格内没有任何活着的怪物（如果刚才有怪物，它已经被打死消灭了！切勿再提醒玩家躲避已经死掉的怪物！）");
        } else if (threatCount <= 2) {
            sb.append("附近有零星活着的怪物(").append(threatCount).append("只): ")
              .append(String.join("、", aliveThreats));
        } else if (threatCount <= 4) {
            sb.append("⚠️注意！周围同时有 ").append(threatCount).append(" 只活着的怪物正在游荡/逼近: ")
              .append(String.join("、", aliveThreats));
        } else {
            sb.append("🚨【高危·怪物群包围】周围足足聚集了 ").append(threatCount).append(" 只活着的怪物！太多了，正处于被群殴/包围的危险中，优先包含: ")
              .append(String.join("、", aliveThreats.stream().limit(5).toList()));
        }

        return sb.toString();
    }

    /**
     * 统计周围 16 格内仍然存活的敌对怪物总数（供 ProactiveScheduler 判断怪物群包围）
     */
    public static int countNearbyAliveHostiles(ClientPlayerEntity player, ClientWorld world) {
        if (player == null || world == null) return 0;
        Box scanBox = player.getBoundingBox().expand(16.0);
        int count = 0;
        for (Entity e : world.getOtherEntities(player, scanBox)) {
            if (!(e instanceof LivingEntity living)) continue;
            if (e.isRemoved() || !living.isAlive() || living.isDead() || living.getHealth() <= 0.0f || living.deathTime > 0) {
                continue;
            }
            if (e instanceof HostileEntity || (e instanceof MobEntity mob && mob.getTarget() != null)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 查找距离玩家最近的一只活着且正在攻击/靠近的怪物名称
     */
    public static String findNearestAliveAttackerName(ClientPlayerEntity player, ClientWorld world) {
        if (player == null || world == null) return "";
        if (player.getAttacker() != null && player.getAttacker().isAlive() && !player.getAttacker().isDead()) {
            return player.getAttacker().getName().getString();
        }
        Box scanBox = player.getBoundingBox().expand(8.0);
        Entity nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Entity e : world.getOtherEntities(player, scanBox)) {
            if (!(e instanceof LivingEntity living)) continue;
            if (e.isRemoved() || !living.isAlive() || living.isDead() || living.getHealth() <= 0.0f || living.deathTime > 0) {
                continue;
            }
            if (e instanceof HostileEntity || (e instanceof MobEntity mob && mob.getTarget() != null)) {
                double d = player.distanceTo(e);
                if (d < minDist) {
                    minDist = d;
                    nearest = e;
                }
            }
        }
        return nearest != null ? nearest.getName().getString() : "";
    }

    /**
     * 扫描玩家背包中真实存在的食物/回血品与快捷栏武器
     */
    private static String scanRealInventoryFoodAndGear(ClientPlayerEntity player) {
        Map<String, Integer> foodMap = new LinkedHashMap<>();
        List<String> hotbarGear = new ArrayList<>();

        // 扫描副手
        for (ItemStack stack : player.getInventory().offHand) {
            if (!stack.isEmpty() && isFoodOrHealingItem(stack)) {
                foodMap.merge(stack.getName().getString(), stack.getCount(), Integer::sum);
            }
        }

        // 扫描主背包 (0~35)
        for (int i = 0; i < player.getInventory().main.size(); i++) {
            ItemStack stack = player.getInventory().main.get(i);
            if (stack.isEmpty()) continue;
            String itemName = stack.getName().getString();

            if (isFoodOrHealingItem(stack)) {
                foodMap.merge(itemName, stack.getCount(), Integer::sum);
            } else if (i < 9 && hotbarGear.size() < 4) {
                hotbarGear.add(itemName);
            }
        }

        StringBuilder sb = new StringBuilder("【背包真实物资】");
        if (foodMap.isEmpty()) {
            sb.append("食物/回血品: 【完全没有！】(玩家身上没有任何吃的或金苹果/药水！切勿让玩家吃东西，只能让他逃跑、垫方块或找掩体)");
        } else {
            String foods = foodMap.entrySet().stream()
                    .limit(6)
                    .map(e -> e.getKey() + "x" + e.getValue())
                    .collect(Collectors.joining("、"));
            sb.append("身上实际拥有的食物: [").append(foods).append("] (提醒回血时只能提列表里有的食物，严禁提身上没有的金苹果！)");
        }

        if (!hotbarGear.isEmpty()) {
            sb.append("; 快捷栏工具/武器: [").append(String.join("、", hotbarGear)).append("]");
        }
        return sb.toString();
    }

    private static boolean isFoodOrHealingItem(ItemStack stack) {
        if (stack.getItem().isFood()) return true;
        String idPath = Registries.ITEM.getId(stack.getItem()).getPath();
        return idPath.contains("potion") || idPath.contains("golden_apple") || idPath.contains("totem");
    }

    private static String formatStatusEffect(StatusEffectInstance inst) {
        String name = Registries.STATUS_EFFECT.getId(inst.getEffectType()).getPath();
        int amp = inst.getAmplifier() + 1;
        return name + (amp > 1 ? amp + "级" : "");
    }
}
