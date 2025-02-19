package me.cortex.voxy.client.mixin.chunky;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxelCore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.OptionalChunk;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.popcraft.chunky.platform.FabricWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

@Mixin(FabricWorld.class)
public class MixinFabricWorld {
    /*@WrapOperation(method = "getChunkAtAsync", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ChunkHolder;load(Lnet/minecraft/world/chunk/ChunkStatus;Lnet/minecraft/server/world/ServerChunkLoadingManager;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<OptionalChunk<Chunk>> captureGeneratedChunk(ChunkHolder instance, ChunkStatus chunkStatus, ServerChunkLoadingManager serverChunkLoadingManager, Operation<CompletableFuture<OptionalChunk<Chunk>>> original) {
        var future = original.call(instance, chunkStatus, serverChunkLoadingManager);
        return future.thenApplyAsync(res->{
            res.ifPresent(chunk -> {
                var core = ((IGetVoxelCore)(MinecraftClient.getInstance().worldRenderer)).getVoxelCore();
                if (core != null && VoxyConfig.CONFIG.ingestEnabled) {
                    core.enqueueIngest((WorldChunk) chunk);
                }
            });
            return res;
        });
    }*/
}
