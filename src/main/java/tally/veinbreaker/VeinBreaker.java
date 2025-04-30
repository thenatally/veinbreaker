package tally.veinbreaker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.*;

public class VeinBreaker implements ModInitializer {
    private static final Queue<ScheduledBreak> breakQueue = new ArrayDeque<>();

    @Override
    public void onInitialize() {
        VeinBreakerConfig.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    LiteralArgumentBuilder.<ServerCommandSource>literal("veinbreaker")
                            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("reload")
                                    .executes(ctx -> {
                                        VeinBreakerConfig.load();
                                        ctx.getSource().sendFeedback(
                                                () -> Text.literal("§a[VeinBreaker] Config reloaded."), false);
                                        return 1;
                                    })));
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient || !VeinBreakerConfig.INSTANCE.enableVeinBreaking)
                return true;

            if (shouldVeinBreak(world, state, player)) {
                Set<BlockPos> visited = new HashSet<>();
                visited.add(pos);
                if (VeinBreakerConfig.INSTANCE.ticksPerIteration == 0) {
                    breakConnected((ServerWorld) world, pos, state.getBlock(), visited, 0);
                } else {
                    breakQueue.add(new ScheduledBreak((ServerWorld) world, pos, state.getBlock(), visited, 0));
                }
            }

            return true;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int perTick = VeinBreakerConfig.INSTANCE.ticksPerIteration;
            for (int i = 0; i < perTick && !breakQueue.isEmpty(); i++) {
                ScheduledBreak task = breakQueue.poll();
                if (task != null) {
                    continueBreaking(task);
                }
            }
        });
    }

    private boolean shouldVeinBreak(World world, BlockState state, PlayerEntity player) {
        Identifier id = world.getRegistryManager().get(RegistryKeys.BLOCK).getId(state.getBlock());
        String tool = player.getMainHandStack().getItem().toString();

        for (Map.Entry<String, VeinBreakerConfig.BlockConfig> entry : VeinBreakerConfig.INSTANCE.categories.entrySet()) {
            VeinBreakerConfig.BlockConfig config = entry.getValue();
            if (matchesConfig(state, id, tool, config)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesConfig(BlockState state, Identifier id, String tool, VeinBreakerConfig.BlockConfig config) {
        for (String blockEntry : config.blocks) {
            if (blockEntry.startsWith("#")) {
                TagKey<Block> tag = TagKey.of(RegistryKeys.BLOCK, new Identifier(blockEntry.substring(1)));
                if (state.isIn(tag))
                    return matchesTool(tool, config.tools);
            } else if (blockEntry.contains("*")) {
                String pattern = blockEntry.replace(".", "\\.").replace("*", ".*");
                if (id.toString().matches(pattern))
                    return matchesTool(tool, config.tools);
            } else {
                if (id.toString().equals(blockEntry))
                    return matchesTool(tool, config.tools);
            }
        }
        return false;
    }

    private boolean matchesTool(String tool, List<String> toolPatterns) {
        for (String toolPattern : toolPatterns) {
            if (toolPattern.equals("*") || tool.matches(toolPattern.replace("*", ".*"))) {
                return true;
            }
        }
        return false;
    }

    private void breakConnected(ServerWorld world, BlockPos origin, Block block, Set<BlockPos> visited, int depth) {
        if (depth > VeinBreakerConfig.INSTANCE.maxScan || visited.size() > VeinBreakerConfig.INSTANCE.maxBlocksPerVein)
            return;

        for (BlockPos offset : getNeighbors(origin)) {
            if (visited.contains(offset))
                continue;
            visited.add(offset);

            BlockState neighbor = world.getBlockState(offset);
            if (neighbor.getBlock() == block) {
                world.breakBlock(offset, VeinBreakerConfig.INSTANCE.dropItems);
                breakConnected(world, offset, block, visited, depth + 1);
            }
        }
    }

    private void continueBreaking(ScheduledBreak task) {
        if (task.depth > VeinBreakerConfig.INSTANCE.maxScan
                || task.visited.size() > VeinBreakerConfig.INSTANCE.maxBlocksPerVein)
            return;

        List<BlockPos> nextBatch = new ArrayList<>();
        for (BlockPos offset : getNeighbors(task.origin)) {
            if (task.visited.contains(offset))
                continue;
            task.visited.add(offset);

            BlockState neighbor = task.world.getBlockState(offset);
            if (neighbor.getBlock() == task.block) {
                task.world.breakBlock(offset, VeinBreakerConfig.INSTANCE.dropItems);
                nextBatch.add(offset);
            }
        }

        for (BlockPos next : nextBatch) {
            breakQueue.add(new ScheduledBreak(task.world, next, task.block, task.visited, task.depth + 1));
        }
    }

    private List<BlockPos> getNeighbors(BlockPos pos) {
        return List.of(
                pos.north(), pos.south(), pos.east(), pos.west(),
                pos.up(), pos.down());
    }

    private static class ScheduledBreak {
        public final ServerWorld world;
        public final BlockPos origin;
        public final Block block;
        public final Set<BlockPos> visited;
        public final int depth;

        public ScheduledBreak(ServerWorld world, BlockPos origin, Block block, Set<BlockPos> visited, int depth) {
            this.world = world;
            this.origin = origin;
            this.block = block;
            this.visited = visited;
            this.depth = depth;
        }
    }
}
