package me.cortex.voxy.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.cortex.voxy.client.core.IGetVoxelCore;
import me.shedaniel.clothconfig2.ClothConfigDemo;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

public class VoxyConfigScreenFactory implements ModMenuApi {
    private static VoxyConfig DEFAULT;

    private static boolean ON_SAVE_RELOAD = false;

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> buildConfigScreen(parent, VoxyConfig.CONFIG);
    }

    private static Screen buildConfigScreen(Screen parent, VoxyConfig config) {
        if (DEFAULT == null) {
            DEFAULT = new VoxyConfig();
        }
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("voxy.config.title"));


        addGeneralCategory(builder, config);
        //addThreadsCategory(builder, config);
        //addStorageCategory(builder, config);

        builder.setSavingRunnable(() -> {
            //After saving the core should be reloaded/reset
            var world = (IGetVoxelCore)MinecraftClient.getInstance().worldRenderer;
            if (world != null && ON_SAVE_RELOAD) {
                world.reloadVoxelCore();
            }
            ON_SAVE_RELOAD = false;
            VoxyConfig.CONFIG.save();
        });

        return builder.build();//
    }

    private static void reload() {
        ON_SAVE_RELOAD = true;
    }

    private static void addGeneralCategory(ConfigBuilder builder, VoxyConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Text.translatable("voxy.config.general"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        category.addEntry(entryBuilder.startBooleanToggle(Text.translatable("voxy.config.general.enabled"), config.enabled)
                .setTooltip(Text.translatable("voxy.config.general.enabled.tooltip"))
                .setSaveConsumer(val -> {if (config.enabled != val) reload(); config.enabled = val;})
                .setDefaultValue(DEFAULT.enabled)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Text.translatable("voxy.config.general.ingest"), config.ingestEnabled)
                .setTooltip(Text.translatable("voxy.config.general.ingest.tooltip"))
                .setSaveConsumer(val -> config.ingestEnabled = val)
                .setDefaultValue(DEFAULT.ingestEnabled)
                .build());

        category.addEntry(entryBuilder.startIntSlider(Text.translatable("voxy.config.general.subDivisionSize"), config.subDivisionSize, 32, 256)
                .setTooltip(Text.translatable("voxy.config.general.subDivisionSize.tooltip"))
                .setSaveConsumer(val -> config.subDivisionSize = val)
                .setDefaultValue(DEFAULT.subDivisionSize)
                .build());


        category.addEntry(entryBuilder.startIntSlider(Text.translatable("voxy.config.general.serviceThreads"), config.serviceThreads, 1, Runtime.getRuntime().availableProcessors())
                .setTooltip(Text.translatable("voxy.config.general.serviceThreads.tooltip"))
                .setSaveConsumer(val ->{if (config.serviceThreads != val) reload(); config.serviceThreads = val;})
                .setDefaultValue(DEFAULT.serviceThreads)
                .build());
    }
}
