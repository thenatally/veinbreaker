package tally.veinbreaker;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.spongepowered.include.com.google.gson.Gson;
import org.spongepowered.include.com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

public class VeinBreakerConfig {
    public static class BlockConfig {
        public List<String> blocks = List.of();
        public List<String> tools = List.of("*");
    }

    public int maxScan = 100;
    public Map<String, BlockConfig> categories = Map.of(
        "logs", new BlockConfig(),
        "ores", new BlockConfig()
    );

    public int ticksPerIteration = 1;
    public boolean dropItems = true; 
    public int maxBlocksPerVein = 1000;
    public boolean enableVeinBreaking = true; 

    public static final Path CONFIG_PATH = FabricLoader.getInstance()
        .getConfigDir().resolve("veinbreaker.json");

    public static VeinBreakerConfig INSTANCE = new VeinBreakerConfig();

    public static void load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                save();
            }

            String json = Files.readString(CONFIG_PATH);
            INSTANCE = new Gson().fromJson(new java.io.StringReader(json), VeinBreakerConfig.class);

        } catch (IOException e) {
            System.err.println("[VeinBreaker] Failed to load config: " + e);
        }
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(INSTANCE, writer);
        } catch (IOException e) {
            System.err.println("[VeinBreaker] Failed to save config: " + e);
        }
    }

    public void processCategories() {
        for (Map.Entry<String, BlockConfig> entry : categories.entrySet()) {
            String categoryName = entry.getKey();
            BlockConfig config = entry.getValue();
        }
    }
}
