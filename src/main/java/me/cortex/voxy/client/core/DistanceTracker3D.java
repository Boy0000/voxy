package me.cortex.voxy.client.core;

//Contains the logic to determine what is loaded and at what LoD level, dispatches render changes
// also determines what faces are built etc

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.cortex.voxy.client.core.rendering.RenderTracker;
import me.cortex.voxy.client.core.util.RingUtil;
import net.minecraft.client.MinecraftClient;
import java.util.HashMap;
import java.util.Map;

//Can use ring logic
// i.e. when a player moves the rings of each lod change (how it was doing in the original attempt)
// also have it do directional quad culling and rebuild the chunk if needed (this shouldent happen very often) (the reason is to significantly reduce draw calls)
// make the rebuild range like +-5 chunks along each axis (that means at higher levels, should only need to rebuild like)
// 4 sections or something
public class DistanceTracker3D {
    private final TransitionRing2D[] loDRings;
    private final TransitionRing2D[] cacheLoadRings;
    private final TransitionRing2D[] cacheUnloadRings;
    private final TransitionRing2D mostOuterNonClampedRing;
    private final RenderTracker tracker;
    private final int renderDistance;

    public DistanceTracker3D(RenderTracker tracker, int[] lodRingScales, int renderDistance) {
        this.loDRings = new TransitionRing2D[lodRingScales.length];
        this.cacheLoadRings = new TransitionRing2D[lodRingScales.length];
        this.cacheUnloadRings = new TransitionRing2D[lodRingScales.length];
        this.tracker = tracker;
        this.renderDistance = renderDistance;

        boolean wasRdClamped = false;
        //The rings 0+ start at 64 vanilla rd, no matter what the game is set at, that is if the game is set to 32 rd
        // there will still be 32 chunks untill the first lod drop
        // if the game is set to 16, then there will be 48 chunks until the drop
        for (int i = 0; i < this.loDRings.length; i++) {
            int scaleP = lodRingScales[i];
            boolean isTerminatingRing = ((lodRingScales[i]+2)<<(1+i) >= renderDistance)&&renderDistance>0;
            if (isTerminatingRing) {
                scaleP = Math.max(renderDistance >> (1+i), 1);
                wasRdClamped = true;
            }
            int scale = scaleP;

            //TODO: FIXME: check that the level shift is right when inc/dec
            int capRing = i;
            this.loDRings[i] = new TransitionRing2D((isTerminatingRing?5:6)+i, isTerminatingRing?scale<<1:scale, (x, y, z) -> {
                if (isTerminatingRing) {
                    add(capRing, x, y, z);
                } else
                    this.dec(capRing+1, x, y, z);
            }, (x, y, z) -> {
                if (isTerminatingRing) {
                    remove(capRing, x, y, z);
                    //remove(capRing, (x<<1), (z<<1));
                } else
                    this.inc(capRing+1, x, y, z);
            });

            if (isTerminatingRing) {
                break;
            }
        }
        if (!wasRdClamped) {
            this.mostOuterNonClampedRing = new TransitionRing2D(5+this.loDRings.length, Math.max(renderDistance, 2048)>>this.loDRings.length, (x, y, z)->
                    add(this.loDRings.length, x, y, z), (x, y, z)->{
                if (renderDistance > 0) {
                    remove(this.loDRings.length, x, y, z);
                }
            });
        } else {
            this.mostOuterNonClampedRing = null;
        }
    }

    private void inc(int lvl, int x, int y, int z) {
        this.tracker.inc(lvl, x, y, z);
    }

    private void dec(int lvl, int x, int y, int z) {
        this.tracker.dec(lvl, x, y, z);
    }

    private void add(int lvl, int x, int y, int z) {
        this.tracker.add(lvl, x, y, z);
    }

    private void remove(int lvl, int x, int y, int z) {
        this.tracker.remove(lvl, x, y, z);
        this.tracker.removeCache(lvl, x, y, z);
    }

    //How it works is there are N ring zones (one zone for each lod boundary)
    // the transition zone is what determines what lods are rendered etc (and it biases higher lod levels cause its easier)
    // the transition zone is only ever checked when the player moves 1<<(4+lodlvl) blocks, its position is set

    //if the center suddenly changes (say more than 1<<(7+lodlvl) block) then invalidate the entire ring and recompute
    // the lod sections
    public void setCenter(int x, int y, int z) {
        for (var ring : this.cacheLoadRings) {
            if (ring!=null)
                ring.update(x, y, z);
        }
        if (this.mostOuterNonClampedRing!=null)
            this.mostOuterNonClampedRing.update(x, y, z);

        //Update in reverse order (biggest lod to smallest lod)
        for (int i = this.loDRings.length-1; -1<i; i-- ) {
            var ring = this.loDRings[i];
            if (ring != null)
                ring.update(x, y, z);
        }
        for (var ring : this.cacheUnloadRings) {
            if (ring!=null)
                ring.update(x, y, z);
        }
    }

    public void init(int x, int y, int z) {
        for (var ring : this.cacheLoadRings) {
            if (ring != null)
                ring.setCenter(x, y, z);
        }

        for (var ring : this.cacheUnloadRings) {
            if (ring != null)
                ring.setCenter(x, y, z);
        }

        for (var ring : this.loDRings) {
            if (ring != null)
                ring.setCenter(x, y, z);
        }
        if (this.mostOuterNonClampedRing!=null)
            this.mostOuterNonClampedRing.setCenter(x, y, z);

        var thread = new Thread(()-> {
            for (var ring : this.cacheLoadRings) {
                if (ring != null)
                    ring.fill(x, y, z);
            }

            for (var ring : this.cacheUnloadRings) {
                if (ring != null)
                    ring.fill(x, y, z);
            }

            //This is an ungodly terrible hack to make the lods load in a semi ok order
            for (var ring : this.loDRings)
                if (ring != null)
                    ring.fill(x, y, z);

            if (this.mostOuterNonClampedRing!=null)
                this.mostOuterNonClampedRing.fill(x, y, z);

            for (int i = this.loDRings.length - 1; 0 <= i; i--) {
                if (this.loDRings[i] != null) {
                    this.loDRings[i].fill(x, y, z);
                }
            }
        });
        thread.setName("LoD Ring Initializer");
        thread.start();
        //TODO: FIXME: need to destory on shutdown
    }


    //TODO: add a new class thing that can track the central axis point so that
    // geometry can be rebuilt with new flags with correct facing geometry built
    // (could also make it so that it emits 3x the amount of draw calls, but that seems very bad idea)


    private interface Transition2DCallback {
        void callback(int x, int y, int z);
    }
    private static final class TransitionRing2D {
        private final int triggerRangeSquared;
        private final int shiftSize;
        private final Transition2DCallback enter;
        private final Transition2DCallback exit;
        private final int radius;

        private int lastUpdateX;
        private int lastUpdateY;
        private int lastUpdateZ;

        private int currentX;
        private int currentY;
        private int currentZ;

        //Note radius is in shiftScale
        private TransitionRing2D(int shiftSize, int radius, Transition2DCallback onEntry, Transition2DCallback onExit) {
            this(shiftSize, radius, onEntry, onExit, 0, 0, 0);
        }
        private TransitionRing2D(int shiftSize, int radius, Transition2DCallback onEntry, Transition2DCallback onExit, int ix, int iy, int iz) {
            //trigger just less than every shiftSize scale
            this.triggerRangeSquared = 1<<((shiftSize<<1) - 1);
            this.shiftSize = shiftSize;
            this.enter = onEntry;
            this.exit = onExit;
            this.radius = radius;
        }

        // https://stackoverflow.com/questions/29566711/how-to-compose-a-key-for-a-hashmap-from-3-integers
        private static final class PrelKey{
            int a, b, c;
            public PrelKey(int a, int b, int c) {
                this.a = a;
                this.b = b;
                this.c = c;
            }
       
            public int hashCode() {
                return (a << 10 ^ b << 5 ^ c);
            }
       
            public boolean equals(Object o) {
                if(!(o instanceof PrelKey)) return false;
                PrelKey k = (PrelKey) o;
                return (k.a == a && k.b == b && k.c == c);
            }
       }

        private PrelKey Prel(int x, int y, int z) {
            // return (Integer.toUnsignedLong(this.currentZ + z)<<32)|Integer.toUnsignedLong(this.currentX + x);
            return new PrelKey(this.currentX + x, this.currentY + y, this.currentZ + z);
        }

        public void update(int x, int y, int z) {
            long dx = this.lastUpdateX - x;
            long dy = this.lastUpdateY - y;
            long dz = this.lastUpdateZ - z;
            long distSquared =  dx*dx + dy*dy + dz*dz;
            if (distSquared < this.triggerRangeSquared) {
                return;
            }

            //Update the last update position
            int maxStep = this.triggerRangeSquared/2;
            this.lastUpdateX += Math.min(maxStep,Math.max(-maxStep, x-this.lastUpdateX));
            this.lastUpdateY += Math.min(maxStep,Math.max(-maxStep, y-this.lastUpdateY));
            this.lastUpdateZ += Math.min(maxStep,Math.max(-maxStep, z-this.lastUpdateZ));

            //Compute movement if it happened
            int nx = x>>this.shiftSize;
            int ny = y>>this.shiftSize;
            int nz = z>>this.shiftSize;

            if (nx == this.currentX && ny == this.currentY && nz == this.currentZ) {
                //No movement
                return;
            }

            // System.out.println("xyz " + this.currentX + " -> " + nx + ", " + this.currentY + " -> " + ny + ", " + this.currentZ + " -> " + nz + ", ");


            //FIXME: not right, needs to only call load/unload on entry and exit, cause atm its acting like a loaded circle

            // Long2IntOpenHashMap ops = new Long2IntOpenHashMap();
            Map<PrelKey, Integer> ops = new HashMap<>();
            while (true) {
                int dir = nz < this.currentZ ? -1 : 1;
                if (nz != this.currentZ) {
                    for (int cx = -this.radius; cx <= this.radius; cx++) {
                        for (int cy = -this.radius; cy <= this.radius; cy++) {
                            int cz = this.radius;
                            ops.put(Prel(cx, cy, cz + Math.max(0, dir)), dir);
                            ops.put(Prel(cx, cy, -cz + Math.min(0, dir)), -dir);
                        }
                    }
                    this.currentZ += dir;
                }

                dir = ny < this.currentY ? -1 : 1;
                if (ny != this.currentY) {
                    for (int cz = -this.radius; cz <= this.radius; cz++) {
                        for (int cx = -this.radius; cx <= this.radius; cx++) {
                            int cy = this.radius;
                            ops.put(Prel(cx, cy + Math.max(0, dir), cz), dir);
                            ops.put(Prel(cx, -cy + Math.min(0, dir), cz), -dir);
                        }
                    }
                    this.currentY += dir;
                }

                dir = nx < this.currentX ? -1 : 1;
                if (nx != this.currentX) {
                    for (int cz = -this.radius; cz <= this.radius; cz++) {
                        for (int cy = -this.radius; cy <= this.radius; cy++) {
                            int cx = this.radius;
                            ops.put(Prel(cx + Math.max(0, dir), cy, cz), dir);
                            ops.put(Prel(-cx + Math.min(0, dir), cy, cz), -dir);
                        }
                    }
                    this.currentX += dir;
                }

                //Only break once the coords match
                if (nx == this.currentX && ny == this.currentY && nz == this.currentZ) {
                    break;
                }
            }


            ops.forEach((pos, val)->{
                if (val > 0) {
                    // System.out.println("enter " + pos.a + " " + pos.b + " " + pos.c);
                    this.enter.callback(pos.a, pos.b, pos.c);
                }
                if (val < 0) {
                    // System.out.println("exit " + pos.a + " " + pos.b + " " + pos.c);
                    this.exit.callback(pos.a, pos.b, pos.c);
                }
            });
            ops.clear();
        }

        public void fill(int x, int y, int z) {
            this.fill(x, y, z, null);
        }

        public void fill(int x, int y, int z, Transition2DCallback outsideCallback) {
            int cx = x>>this.shiftSize;
            int cy = y>>this.shiftSize;
            int cz = z>>this.shiftSize;

            int r2 = this.radius*this.radius;
            for (int a = -this.radius; a <= this.radius; a++) {
                for (int b = -this.radius; b <= this.radius; b++) {
                    for (int c = -this.radius; c <= this.radius; c++) {
                        this.enter.callback(a + cx, b + cy, c + cz);
                    }
                }
            }
        }

        public void setCenter(int x, int y, int z) {
            int cx = x>>this.shiftSize;
            int cy = y>>this.shiftSize;
            int cz = z>>this.shiftSize;
            this.currentX = cx;
            this.currentY = cy;
            this.currentZ = cz;
            this.lastUpdateX = x + (((int)(Math.random()*4))<<(this.shiftSize-4));
            this.lastUpdateY = y + (((int)(Math.random()*4))<<(this.shiftSize-4));
            this.lastUpdateZ = z + (((int)(Math.random()*4))<<(this.shiftSize-4));
        }
    }
}
