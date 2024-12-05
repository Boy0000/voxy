package me.cortex.voxy.client.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.cortex.voxy.client.core.Capabilities;
import me.cortex.voxy.client.saver.ContextSelectionSystem;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.opengl.GL;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

public class VoxyConfig {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();
    public static VoxyConfig CONFIG = loadOrCreate();

    public boolean enabled = true;
    public boolean ingestEnabled = true;
    //public int renderDistance = 128;
    public int serviceThreads = Math.max(Runtime.getRuntime().availableProcessors()/2, 1);
    public String defaultSaveConfig;
    public int subDivisionSize = 128;
    //public int renderQuality = 256;//Smaller is higher quality


    public static VoxyConfig loadOrCreate() {
        var path = getConfigPath();
        if (Files.exists(path)) {
            try (FileReader reader = new FileReader(path.toFile())) {
                var cfg = GSON.fromJson(reader, VoxyConfig.class);
                if (cfg.defaultSaveConfig == null) {
                    //Shitty gson being a pain TODO: replace with a proper fix
                    cfg.defaultSaveConfig = ContextSelectionSystem.DEFAULT_STORAGE_CONFIG;
                }
                return cfg;
            } catch (IOException e) {
                System.err.println("Could not parse config");
                e.printStackTrace();
            }
        }
        var config = new VoxyConfig();
        config.defaultSaveConfig = ContextSelectionSystem.DEFAULT_STORAGE_CONFIG;
        return config;
    }
    public void save() {
        try {
            Files.writeString(getConfigPath(), GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("Failed to write config file");
            e.printStackTrace();
        }
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("voxy-config.json");
    }
}
