package me.cortex.voxy.common.storage.other;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.StorageCompressor;
import me.cortex.voxy.common.storage.config.CompressorConfig;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;

//Compresses the section data
public class CompressionStorageAdaptor extends DelegatingStorageAdaptor {
    private final StorageCompressor compressor;
    public CompressionStorageAdaptor(StorageCompressor compressor, StorageBackend delegate) {
        super(delegate);
        this.compressor = compressor;
    }


    //TODO: figure out a nicer way w.r.t scratch buffer shit
    @Override
    public MemoryBuffer getSectionData(long key, MemoryBuffer scratch) {
        var data = this.delegate.getSectionData(key, scratch);
        if (data == null) {
            return null;
        }
        return this.compressor.decompress(data);
    }

    @Override
    public void setSectionData(long key, MemoryBuffer data) {
        var cdata = this.compressor.compress(data);
        this.delegate.setSectionData(key, cdata);
        cdata.free();
    }

    @Override
    public void close() {
        this.compressor.close();
        super.close();
    }

    public static class Config extends DelegateStorageConfig {
        public CompressorConfig compressor;

        @Override
        public StorageBackend build(ConfigBuildCtx ctx) {
            return new CompressionStorageAdaptor(this.compressor.build(ctx), this.delegate.build(ctx));
        }

        public static String getConfigTypeName() {
            return "CompressionAdaptor";
        }
    }
}
