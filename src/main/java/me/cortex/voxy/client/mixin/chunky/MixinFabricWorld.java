package me.cortex.voxy.client.mixin.chunky;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cortex.voxy.client.Voxy;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxelCore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.popcraft.chunky.mixin.ServerChunkCacheMixin;
import org.popcraft.chunky.platform.FabricWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

@Mixin(FabricWorld.class)
public class MixinFabricWorld {
    @WrapOperation(method = "getChunkAtAsync", at = @At(value = "INVOKE", target = "Lorg/popcraft/chunky/mixin/ServerChunkCacheMixin;invokeGetChunkFutureMainThread(IILnet/minecraft/world/chunk/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<OptionalChunk<Chunk>> captureGeneratedChunk(ServerChunkCacheMixin instance, int i, int j, ChunkStatus chunkStatus, boolean b, Operation<CompletableFuture<OptionalChunk<Chunk>>> original) {
        var future = original.call(instance, i, j, chunkStatus, b);
        return future.thenApplyAsync(res->{
            res.ifPresent(chunk -> {
                var core = ((IGetVoxelCore)(MinecraftClient.getInstance().worldRenderer)).getVoxelCore();
                if (core != null && VoxyConfig.CONFIG.ingestEnabled) {
                    core.enqueueIngest((WorldChunk) chunk);
                }
            });
            return res;
        });
    }
}
