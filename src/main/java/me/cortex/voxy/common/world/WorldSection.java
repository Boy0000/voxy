package me.cortex.voxy.common.world;


import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

//Represents a loaded world section at a specific detail level
// holds a 32x32x32 region of detail
public final class WorldSection {
    public static final boolean VERIFY_WORLD_SECTION_EXECUTION = System.getProperty("voxy.verifyWorldSectionExecution", "true").equals("true");

    //TODO: should make it dynamically adjust the size allowance based on memory pressure/WorldSection allocation rate (e.g. is it doing a world import)
    private static final int ARRAY_REUSE_CACHE_SIZE = 300;
    //TODO: maybe just swap this to a ConcurrentLinkedDeque
    private static final Deque<long[]> ARRAY_REUSE_CACHE = new ArrayDeque<>(1024);


    public final int lvl;
    public final int x;
    public final int y;
    public final int z;
    public final long key;


    //Serialized states
    long metadata;
    long[] data = null;


    private final ActiveSectionTracker tracker;
    public final AtomicBoolean inSaveQueue = new AtomicBoolean();

    //When the first bit is set it means its loaded
    private final AtomicInteger atomicState = new AtomicInteger(1);

    WorldSection(int lvl, int x, int y, int z, ActiveSectionTracker tracker) {
        this.lvl = lvl;
        this.x = x;
        this.y = y;
        this.z = z;
        this.key = WorldEngine.getWorldSectionId(lvl, x, y, z);
        this.tracker = tracker;

        if (!ARRAY_REUSE_CACHE.isEmpty()) {
            synchronized (ARRAY_REUSE_CACHE) {
                this.data = ARRAY_REUSE_CACHE.poll();
            }
        }
        if (this.data == null) {
            this.data = new long[32 * 32 * 32];
        }
    }

    @Override
    public int hashCode() {
        return ((x*1235641+y)*8127451+z)*918267913+lvl;
    }

    public boolean tryAcquire() {
        int state = this.atomicState.updateAndGet(val -> {
            if ((val&1) != 0) {
                return val+2;
            }
            return val;
        });
        return (state&1) != 0;
    }

    public int acquire() {
        int state = this.atomicState.addAndGet(2);
        if (VERIFY_WORLD_SECTION_EXECUTION) {
            if ((state & 1) == 0) {
                throw new IllegalStateException("Tried to acquire unloaded section");
            }
        }
        return state>>1;
    }

    public int getRefCount() {
        return this.atomicState.get()>>1;
    }

    //TODO: add the ability to hint to the tracker that yes the section is unloaded, try to cache it in a secondary cache since it will be reused/needed later
    public int release() {
        int state = this.atomicState.addAndGet(-2);
        if (VERIFY_WORLD_SECTION_EXECUTION) {
            if (state < 1) {
                throw new IllegalStateException("Section got into an invalid state");
            }
            if ((state & 1) == 0) {
                throw new IllegalStateException("Tried releasing a freed section");
            }
        }
        if ((state>>1)==0) {
            this.tracker.tryUnload(this);
        }
        return state>>1;
    }

    //Returns true on success, false on failure
    boolean trySetFreed() {
        int witness = this.atomicState.compareAndExchange(1, 0);
        if (VERIFY_WORLD_SECTION_EXECUTION) {
            if ((witness & 1) == 0 && witness != 0) {
                throw new IllegalStateException("Section marked as free but has refs");
            }
        }
        boolean isFreed = witness == 1;
        if (isFreed) {
            if (ARRAY_REUSE_CACHE.size() < ARRAY_REUSE_CACHE_SIZE) {
                synchronized (ARRAY_REUSE_CACHE) {
                    ARRAY_REUSE_CACHE.add(this.data);
                }
            }
            this.data = null;
        }
        return isFreed;
    }

    public void assertNotFree() {
        if (VERIFY_WORLD_SECTION_EXECUTION) {
            if ((this.atomicState.get() & 1) == 0) {
                throw new IllegalStateException();
            }
        }
    }

    public static int getIndex(int x, int y, int z) {
        int M = (1<<5)-1;
        if (VERIFY_WORLD_SECTION_EXECUTION) {
            if (x < 0 || x > M || y < 0 || y > M || z < 0 || z > M) {
                throw new IllegalArgumentException("Out of bounds: " + x + ", " + y + ", " + z);
            }
        }
        return ((y&M)<<10)|((z&M)<<5)|(x&M);
    }

    public long set(int x, int y, int z, long id) {
        int idx = getIndex(x,y,z);
        long old = this.data[idx];
        this.data[idx] = id;
        return old;
    }

    //Generates a copy of the data array, this is to help with atomic operations like rendering
    public long[] copyData() {
        this.assertNotFree();
        return Arrays.copyOf(this.data, this.data.length);
    }

    public void copyDataTo(long[] cache) {
        this.assertNotFree();
        if (cache.length != this.data.length) throw new IllegalArgumentException();
        System.arraycopy(this.data, 0, cache, 0, this.data.length);
    }
}

//TODO: for serialization, make a huffman encoding tree on the integers since that should be very very efficent for compression
