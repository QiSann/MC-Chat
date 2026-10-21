package com.minecompanion.mc.modcompat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 跨模组运行时自省引擎
 * 负责扫描整合包模组列表、提取物品/方块高级 Tooltip 与所属 Mod 归属，
 * 以及反查当前世界 RecipeManager 内存真实合成表（天然兼容 KubeJS / CraftTweaker 魔改）
 */
public class ModIntrospector {

    /**
     * 1. 扫描整合包已安装的所有功能性 Mod 摘要（过滤掉底层前置库）
     */
    public static String getInstalledModsSummary() {
        List<String> modNames = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            // 过滤掉底层运行库，只保留实际玩法和内容模组
            if (id.startsWith("fabric") || id.equals("minecraft") || id.equals("java") || id.equals("mixinextras")) {
                continue;
            }
            modNames.add(mod.getMetadata().getName() + " (" + id + ")");
        }
        return "当前整合包已安装功能模组(" + modNames.size() + "个): "
                + String.join(", ", modNames.stream().limit(35).toList());
    }

    /**
     * 2. 深度自省物品堆（ItemStack）：提取模组归属、完整 Tooltip 与当前存档真实合成表
     */
    public static String inspectItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "【当前手持】：空手（未手持任何物品）";
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        String namespace = itemId.getNamespace();

        // 反查所属 Mod 的名称
        Optional<ModContainer> modContainer = FabricLoader.getInstance().getModContainer(namespace);
        String modDisplayName = modContainer.map(m -> m.getMetadata().getName()).orElse(namespace);

        // 提取物品高级 Tooltip（包含模组作者编写的说明、FE/RF 能量、多方块提示等）
        List<Text> tooltipLines = stack.getTooltip(client.player, TooltipContext.Default.ADVANCED);
        String tooltipText = tooltipLines.stream()
                .map(Text::getString)
                .map(s -> s.replaceAll("§[0-9a-fk-or]", "").trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" | "));

        // 从当前世界 RecipeManager 反查该物品的真实合成表（100% 兼容魔改）
        List<String> recipes = lookupRecipesForItem(stack);

        StringBuilder sb = new StringBuilder();
        sb.append("【物品全息自省数据】\n");
        sb.append("- 物品名称: ").append(stack.getName().getString()).append(" (ID: ").append(itemId).append(")\n");
        sb.append("- 所属模组: ").append(modDisplayName).append(" (命名空间: ").append(namespace).append(")\n");
        sb.append("- 详细说明(Tooltip): ").append(tooltipText).append("\n");

        if (!recipes.isEmpty()) {
            sb.append("- 游戏内真实合成配方:\n  ").append(String.join("\n  ", recipes));
        } else {
            sb.append("- 游戏内真实合成配方: 无常规工作台合成表（可能通过特殊机器加工、战利品箱或自然生成获取）");
        }

        return sb.toString();
    }

    /**
     * 3. 深度自省玩家准星注视的目标（方块或实体）
     */
    public static String inspectCrosshairTarget() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.crosshairTarget == null || client.world == null) {
            return "【视线焦点】：正注视着远方空气";
        }

        HitResult hit = client.crosshairTarget;
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            BlockState state = client.world.getBlockState(blockHit.getBlockPos());
            Identifier blockId = Registries.BLOCK.getId(state.getBlock());
            String namespace = blockId.getNamespace();
            String modName = FabricLoader.getInstance().getModContainer(namespace)
                    .map(m -> m.getMetadata().getName()).orElse(namespace);

            return "【视线焦点-方块】: " + state.getBlock().getName().getString()
                    + " (ID: " + blockId + ", 所属模组: " + modName
                    + ", 坐标: " + blockHit.getBlockPos().toShortString() + ")";
        } else if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
            String namespace = entityId.getNamespace();
            String modName = FabricLoader.getInstance().getModContainer(namespace)
                    .map(m -> m.getMetadata().getName()).orElse(namespace);

            String extraInfo = "";
            if (entity instanceof LivingEntity living) {
                extraInfo = String.format(", 生命值: %.1f/%.1f", living.getHealth(), living.getMaxHealth());
            }

            return "【视线焦点-实体】: " + entity.getName().getString()
                    + " (ID: " + entityId + ", 所属模组: " + modName + extraInfo + ")";
        }

        return "【视线焦点】：正注视着远方";
    }

    /**
     * 从当前世界的 RecipeManager 中反查能够产出目标物品的前 3 条配方
     */
    private static List<String> lookupRecipesForItem(ItemStack target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return List.of();
        List<String> matchedRecipes = new ArrayList<>();

        try {
            for (Recipe<?> recipe : client.world.getRecipeManager().values()) {
                ItemStack output = recipe.getOutput(client.world.getRegistryManager());
                if (output != null && !output.isEmpty() && ItemStack.areItemsEqual(output, target)) {
                    String ingredients = recipe.getIngredients().stream()
                            .filter(ing -> !ing.isEmpty())
                            .map(ModIntrospector::formatIngredient)
                            .collect(Collectors.joining(" + "));
                    String recipeType = Registries.RECIPE_TYPE.getId(recipe.getType()).toString();
                    matchedRecipes.add("[" + recipeType + "] 原料: " + ingredients + " -> 产出: "
                            + output.getName().getString() + "x" + output.getCount());

                    if (matchedRecipes.size() >= 3) break;
                }
            }
        } catch (Exception ignored) {
        }

        return matchedRecipes;
    }

    private static String formatIngredient(Ingredient ingredient) {
        ItemStack[] stacks = ingredient.getMatchingStacks();
        if (stacks.length == 0) return "未知原料";
        return stacks[0].getName().getString();
    }
}
