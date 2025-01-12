package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.client.core.IGetVoxelCore;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer.class, remap = false)
public class MixinDefaultChunkRenderer {

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer;end(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V", shift = At.Shift.BEFORE))
    private void injectRender(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, CallbackInfo ci) {
        if (renderPass == DefaultTerrainRenderPasses.CUTOUT) {
            var core = ((IGetVoxelCore) MinecraftClient.getInstance().worldRenderer).getVoxelCore();
            if (core != null) {
                var stack = new MatrixStack();
                stack.loadIdentity();
                stack.multiplyPositionMatrix(new Matrix4f(matrices.modelView()));
                core.renderOpaque(stack, camera.x, camera.y, camera.z);
            }
        }
    }
}
