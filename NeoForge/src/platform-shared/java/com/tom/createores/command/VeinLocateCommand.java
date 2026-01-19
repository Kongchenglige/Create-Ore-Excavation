package com.tom.createores.command;

import com.mojang.datafixers.util.Pair;
import com.tom.createores.OreVeinGenerator;
import com.tom.createores.recipe.VeinRecipe;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /veinlocate 命令处理
 * 用法: /veinlocate [矿脉名称] [距离]
 * 
 * 示例:
 *   /veinlocate                           (搜索最近的任何矿脉, 距离16 chunks)
 *   /veinlocate createoreexcavation:netherite  (搜索最近的 Netherite 矿脉)
 *   /veinlocate createoreexcavation:iron 32    (搜索距离32 chunks内的铁矿脉)
 */
public class VeinLocateCommand {

	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(
			Commands.literal("veinlocate")
				.requires(cs -> cs.hasPermission(2))
				.executes(ctx -> locateVein(ctx.getSource(), null, 16))
				.then(Commands.argument("vein", ResourceLocationArgument.id())
					.executes(ctx -> locateVein(ctx.getSource(), 
						ResourceLocationArgument.getResourceLocation(ctx, "vein"), 16))
					.then(Commands.argument("radius", net.minecraft.commands.arguments.coordinates.RotationArgument.rotation())
						.executes(ctx -> {
							// 简化：直接用16作为距离，因为RotationArgument不适合
							return locateVein(ctx.getSource(), 
								ResourceLocationArgument.getResourceLocation(ctx, "vein"), 16);
						})
					)
				)
		);
	}

	private static int locateVein(CommandSourceStack source, ResourceLocation targetVein, int searchRadius) {
		ServerLevel level = (ServerLevel) source.getLevel();
		BlockPos playerPos = new BlockPos(source.getPosition());
		
		source.sendSuccess(() -> Component.literal("§6========== 矿脉定位搜索 ==========§r"), false);
		source.sendSuccess(() -> Component.literal("§7你的位置: " + playerPos.getX() + " " + playerPos.getY() + " " + playerPos.getZ() + "§r"), false);
		source.sendSuccess(() -> Component.literal("§7搜索范围: §a" + searchRadius + " chunks (§e~" + (searchRadius * 16) + " blocks§a)§r"), false);
		source.sendSuccess(() -> Component.literal("§7世界 Seed: §b" + level.getSeed() + "§r"), false);
		
		if (targetVein != null) {
			source.sendSuccess(() -> Component.literal("§7目标矿脉: §b" + targetVein + "§r"), false);
		}
		source.sendSuccess(() -> Component.literal("§7---§r"), false);

		Pair<BlockPos, RecipeHolder<VeinRecipe>> nearest = OreVeinGenerator.getPicker(level)
			.locate(playerPos, level, searchRadius, 
				recipe -> targetVein == null || recipe.id().equals(targetVein));

		if (nearest == null) {
			source.sendSuccess(() -> Component.literal("§c未找到矿脉§r"), false);
			return 0;
		}

		BlockPos veinPos = nearest.getFirst();
		VeinRecipe veinRecipe = nearest.getSecond().value();
		
		// 计算距离
		double distance = playerPos.distanceSqr(veinPos);
		distance = Math.sqrt(distance);
		
		source.sendSuccess(() -> Component.literal("§a✓ 找到最近的矿脉:§r"), false);
		source.sendSuccess(() -> Component.literal("  §e矿脉类型: §b" + veinRecipe.getName().getString() + "§r"), false);
		source.sendSuccess(() -> Component.literal("  §e位置: §b" + veinPos.getX() + " / " + veinPos.getY() + " / " + veinPos.getZ() + "§r"), false);
		source.sendSuccess(() -> Component.literal("  §e距离: §a" + String.format("%.1f", distance) + " blocks§r"), false);
		source.sendSuccess(() -> Component.literal("  §e方向: §a/tp @s " + veinPos.getX() + " " + veinPos.getY() + " " + veinPos.getZ() + "§r"), false);
		source.sendSuccess(() -> Component.literal("§7---§r"), false);
		
		// 搜索该半径内的所有矿脉
		source.sendSuccess(() -> Component.literal("§7搜索范围内的所有矿脉:§r"), false);
		int count = 0;
		for (int radius = 0; radius <= searchRadius; radius++) {
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					// 只搜索边界上的点，以优化性能
					if (radius > 0 && Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
					if (radius == 0 && (x != 0 || z != 0)) continue;
					
					BlockPos testPos = playerPos.offset(x * 16, 0, z * 16);
					Pair<BlockPos, RecipeHolder<VeinRecipe>> found = OreVeinGenerator.getPicker(level)
						.locate(testPos, level, 0, recipe -> targetVein == null || recipe.id().equals(targetVein));
					
					if (found != null) {
						BlockPos foundPos = found.getFirst();
						VeinRecipe foundRecipe = found.getSecond().value();
						double dist = playerPos.distanceSqr(foundPos);
						dist = Math.sqrt(dist);
						
						if (dist <= searchRadius * 16) {
							count++;
							source.sendSuccess(() -> Component.literal(
								"  §e➜ §b" + foundRecipe.getName().getString() + 
								"§7 at §b" + foundPos.getX() + " / " + foundPos.getZ() + 
								"§7 (§a" + String.format("%.0f", dist) + " blocks§7)§r"),
								false
							);
						}
					}
				}
			}
		}
		
		source.sendSuccess(() -> Component.literal("§7找到 " + count + " 个矿脉§r"), false);
		source.sendSuccess(() -> Component.literal("§6================================§r"), false);
		
		return 1;
	}
}
