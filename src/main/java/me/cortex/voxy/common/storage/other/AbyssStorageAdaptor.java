package me.cortex.voxy.common.storage.other;

import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;
import me.cortex.voxy.common.world.WorldEngine;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class AbyssStorageAdaptor extends DelegatingStorageAdaptor {
    public AbyssStorageAdaptor(StorageBackend delegate) {
        super(delegate);
    }

    public int getSection(long pos) {
        int lvl = WorldEngine.getLevel(pos);
        int y = WorldEngine.getY(pos);

        int start = -8 >> lvl;
        if (y >= start) return 0;

        int delta = 16 >> lvl;
        return (start - y + delta - 1) / delta;
    }

    public long transform(long pos) {
        int lvl = WorldEngine.getLevel(pos);
        int x = WorldEngine.getX(pos);
        int y = WorldEngine.getY(pos);
        int z = WorldEngine.getZ(pos);

        int section = getSection(pos);

        return WorldEngine.getWorldSectionId(
            lvl,
            x + section * (512 >> lvl),
            y + section * (16 >> lvl),
            z
        );
    }

    @Override
    public ByteBuffer getSectionData(long key) {
        key = transform(key);
        key = WorldEngine.newToOldId(key);
        return super.getSectionData(key);
    }

    @Override
    public void setSectionData(long key, ByteBuffer data) {
        //Dont save data if its a transformed position
        if (getSection(key) > 0) return;
        key = WorldEngine.newToOldId(key);
        super.setSectionData(key, data);
    }

    @Override
    public void deleteSectionData(long key) {
        //Dont delete save data if its a transformed position
        if (getSection(key) > 0) return;
        key = WorldEngine.newToOldId(key);
        super.deleteSectionData(key);
    }

    public static class Config extends StorageConfig {
        public StorageConfig delegate;

        @Override
        public StorageBackend build(ConfigBuildCtx ctx) {
            return new AbyssStorageAdaptor(this.delegate.build(ctx));
        }

        @Override
        public List<StorageConfig> getChildStorageConfigs() {
            return List.of(this.delegate);
        }

        public static String getConfigTypeName() {
            return "AbyssStorageAdaptor";
        }
    }
}
