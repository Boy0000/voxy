package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.UnsafeUtil;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.VoxyCommon;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.util.zstd.Zstd.*;

public class SaveLoadSystem {
    public static final boolean VERIFY_HASH_ON_LOAD  = VoxyCommon.isVerificationFlagOn("verifySectionHash");
    public static final boolean VERIFY_MEMORY_ACCESS = VoxyCommon.isVerificationFlagOn("verifyMemoryAccess");
    public static final int BIGGEST_SERIALIZED_SECTION_SIZE = 32 * 32 * 32 * 8 * 2 + 8;

    public static int lin2z(int i) {//y,z,x
        int x = i&0x1F;
        int y = (i>>10)&0x1F;
        int z = (i>>5)&0x1F;
        return Integer.expand(x,0b1001001001001)|Integer.expand(y,0b10010010010010)|Integer.expand(z,0b100100100100100);

        //zyxzyxzyxzyxzyx
    }

    public static int z2lin(int i) {
        int x = Integer.compress(i, 0b1001001001001);
        int y = Integer.compress(i, 0b10010010010010);
        int z = Integer.compress(i, 0b100100100100100);
        return x|(y<<10)|(z<<5);
    }


    private static final ThreadLocal<short[]> SHORT_CACHE = ThreadLocal.withInitial(()->new short[32*32*32]);
    private static final ThreadLocal<long[]> LONG_CACHE = ThreadLocal.withInitial(()->new long[32*32*32]);
    private static final ThreadLocal<Long2ShortOpenHashMap> OTHER_THING_CACHE = ThreadLocal.withInitial(()-> {
        var thing = new Long2ShortOpenHashMap(512);
        thing.defaultReturnValue((short) -1);
        return thing;
    });


    //TODO: Cache like long2short and the short and other data to stop allocs
    public static MemoryBuffer serialize(WorldSection section) {
        var data = section.copyData();
        var compressed = SHORT_CACHE.get();
        Long2ShortOpenHashMap LUT = OTHER_THING_CACHE.get();LUT.clear();
        long[] lutValues = LONG_CACHE.get();//If there are more than this many states in a section... im concerned
        short lutIndex = 0;
        long pHash = 99;
        for (int i = 0; i < data.length; i++) {
            long block = data[i];
            short mapping = LUT.putIfAbsent(block, lutIndex);
            if (mapping == -1) {
                mapping = lutIndex++;
                lutValues[mapping] = block;
            }
            compressed[lin2z(i)] = mapping;
            pHash *= 127817112311121L;
            pHash ^= pHash>>31;
            pHash += 9918322711L;
            pHash ^= block;
        }

        MemoryBuffer raw = new MemoryBuffer(compressed.length*2L+lutIndex*8L+512);
        long ptr = raw.address;

        long hash = section.key^(lutIndex*1293481298141L);
        MemoryUtil.memPutLong(ptr, section.key); ptr += 8;

        long metadata = 0;
        metadata |= Byte.toUnsignedLong(section.nonEmptyChildren);
        MemoryUtil.memPutLong(ptr, metadata); ptr += 8;

        hash ^= metadata; hash *= 1242629872171L;

        MemoryUtil.memPutInt(ptr, lutIndex); ptr += 4;
        for (int i = 0; i < lutIndex; i++) {
            long id = lutValues[i];
            MemoryUtil.memPutLong(ptr, id); ptr += 8;
            hash *= 1230987149811L;
            hash += 12831;
            hash ^= id;
        }
        hash ^= pHash;

        UnsafeUtil.memcpy(compressed, ptr); ptr += compressed.length*2L;

        MemoryUtil.memPutLong(ptr, hash); ptr += 8;

        return raw.subSize(ptr-raw.address);
    }

    public static boolean deserialize(WorldSection section, MemoryBuffer data) {
        long ptr = data.address;
        long key = MemoryUtil.memGetLong(ptr); ptr += 8; if (VERIFY_MEMORY_ACCESS && data.size<=(ptr-data.address)) throw new IllegalStateException("Memory access OOB");

        long metadata = MemoryUtil.memGetLong(ptr); ptr += 8; if (VERIFY_MEMORY_ACCESS && data.size<=(ptr-data.address)) throw new IllegalStateException("Memory access OOB");
        section.nonEmptyChildren = (byte) (metadata&0xFF);

        int lutLen = MemoryUtil.memGetInt(ptr); ptr += 4; if (VERIFY_MEMORY_ACCESS && data.size<=(ptr-data.address)) throw new IllegalStateException("Memory access OOB");
        if (lutLen > 32*32*32) {
            throw new IllegalStateException("lutLen impossibly large, max size should be 32768 but got size " + lutLen);
        }
        //TODO: cache this in a thread local
        long[] lut = LONG_CACHE.get();
        long hash = 0;
        if (VERIFY_HASH_ON_LOAD) {
            hash = key ^ (lutLen * 1293481298141L);
            hash ^= metadata; hash *= 1242629872171L;
        }
        for (int i = 0; i < lutLen; i++) {
            lut[i] = MemoryUtil.memGetLong(ptr); ptr += 8; if (VERIFY_MEMORY_ACCESS && data.size<=(ptr-data.address)) throw new IllegalStateException("Memory access OOB");
            if (VERIFY_HASH_ON_LOAD) {
                hash *= 1230987149811L;
                hash += 12831;
                hash ^= lut[i];
            }
        }

        if (section.key != key) {
            //throw new IllegalStateException("Decompressed section not the same as requested. got: " + key + " expected: " + section.key);
            System.err.println("Decompressed section not the same as requested. got: " + key + " expected: " + section.key);
            return false;
        }

        int nonEmptyBlockCount = 0;
        for (int i = 0; i < section.data.length; i++) {
            long state = lut[MemoryUtil.memGetShort(ptr)]; ptr += 2; if (VERIFY_MEMORY_ACCESS && data.size<=(ptr-data.address)) throw new IllegalStateException("Memory access OOB");
            nonEmptyBlockCount += Mapper.isAir(state)?0:1;
            section.data[z2lin(i)] = state;
        }
        section.nonEmptyBlockCount = nonEmptyBlockCount;

        if (VERIFY_HASH_ON_LOAD) {
            long pHash = 99;
            for (long block : section.data) {
                pHash *= 127817112311121L;
                pHash ^= pHash >> 31;
                pHash += 9918322711L;
                pHash ^= block;
            }
            hash ^= pHash;

            long expectedHash = MemoryUtil.memGetLong(ptr); ptr += 8; if (VERIFY_MEMORY_ACCESS && data.size<(ptr-data.address)) throw new IllegalStateException("Memory access OOB");
            if (expectedHash != hash) {
                //throw new IllegalStateException("Hash mismatch got: " + hash + " expected: " + expectedHash);
                System.err.println("Hash mismatch got: " + hash + " expected: " + expectedHash + " removing region");
                return false;
            }
        }
        return true;
    }
}
