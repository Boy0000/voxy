package me.cortex.voxy.client.core.rendering;

import com.sun.jna.NativeLibrary;
import me.cortex.voxy.client.core.AbstractRenderWorldInteractor;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.PrintfInjector;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.model.ModelManager;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderDataFactory;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.hierarchical.DebugRenderer;
import me.cortex.voxy.client.core.rendering.hierarchical.HierarchicalOcclusionRenderer;
import me.cortex.voxy.client.core.rendering.hierarchical.INodeInteractor;
import me.cortex.voxy.client.core.rendering.hierarchical.MeshManager;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.client.mixin.joml.AccessFrustumIntersection;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

import static org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteri;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL40.glDrawElementsIndirect;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL42.GL_FRAMEBUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;
import static org.lwjgl.opengl.GL45.nglClearNamedBufferSubData;

public class Gl46HierarchicalRenderer implements IRenderInterface<Gl46HierarchicalViewport>, AbstractRenderWorldInteractor {
    private final HierarchicalOcclusionRenderer sectionSelector;
    private final MeshManager meshManager = new MeshManager();

    private final List<String> printfQueue = new ArrayList<>();
    private final PrintfInjector printf = new PrintfInjector(100000, 10, line->{
        if (line.startsWith("LOG")) {
            System.err.println(line);
        }
        this.printfQueue.add(line);
    }, this.printfQueue::clear);

    private final GlBuffer renderSections = new GlBuffer(100_000 * 4 + 4).zero();
    private final GlBuffer debugNodeQueue = new GlBuffer(1000000*4+4).zero();


    private final DebugRenderer debugRenderer = new DebugRenderer();

    private final ConcurrentLinkedDeque<Mapper.StateEntry> blockStateUpdates = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Mapper.BiomeEntry> biomeUpdates = new ConcurrentLinkedDeque<>();

    protected final ConcurrentLinkedDeque<BuiltSection> buildResults = new ConcurrentLinkedDeque<>();

    private final ModelManager modelManager;
    private RenderGenerationService sectionGenerationService;
    private Consumer<BuiltSection> resultConsumer;

    public Gl46HierarchicalRenderer(ModelManager model) {
        this.modelManager = model;

        this.sectionSelector = new HierarchicalOcclusionRenderer(new INodeInteractor() {
            @Override
            public void watchUpdates(long pos) {
                //System.err.println("Watch: " + pos);
            }

            @Override
            public void unwatchUpdates(long pos) {
                //System.err.println("Unwatch: " + pos);
            }

            @Override
            public void requestMesh(long pos) {
                Gl46HierarchicalRenderer.this.sectionGenerationService.enqueueTask(
                        WorldEngine.getLevel(pos),
                        WorldEngine.getX(pos),
                        WorldEngine.getY(pos),
                        WorldEngine.getZ(pos)
                );
            }

            @Override
            public void setMeshUpdateCallback(Consumer<BuiltSection> mesh) {
                Gl46HierarchicalRenderer.this.resultConsumer = mesh;
            }
        }, this.meshManager, this.printf);
    }

    @Override
    public void setupRender(Frustum frustum, Camera camera) {
        {//Tick upload and download queues
            UploadStream.INSTANCE.tick();
            DownloadStream.INSTANCE.tick();
        }

        {
            boolean didHaveBiomeChange = false;

            //Do any BiomeChanges
            while (!this.biomeUpdates.isEmpty()) {
                var update = this.biomeUpdates.pop();
                var biomeReg = MinecraftClient.getInstance().world.getRegistryManager().get(RegistryKeys.BIOME);
                this.modelManager.addBiome(update.id, biomeReg.get(Identifier.of(update.biome)));
                didHaveBiomeChange = true;
            }

            if (didHaveBiomeChange) {
                UploadStream.INSTANCE.commit();
            }

            int maxUpdatesPerFrame = 40;

            //Do any BlockChanges
            while ((!this.blockStateUpdates.isEmpty()) && (maxUpdatesPerFrame-- > 0)) {
                var update = this.blockStateUpdates.pop();
                this.modelManager.addEntry(update.id, update.state);
            }
        }
    }

    @Override
    public void renderFarAwayOpaque(Gl46HierarchicalViewport viewport) {
        //Process all the build results
        while (!this.buildResults.isEmpty()) {
            this.resultConsumer.accept(this.buildResults.pop());
        }


        //Render terrain from previous frame (renderSections)



        if (true) {//Run the hierarchical selector over the buffer to generate the set of render sections
            var i = new int[1];
            glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, i);
            this.sectionSelector.doHierarchicalTraversalSelection(viewport, i[0], this.renderSections, this.debugNodeQueue);

            this.debugRenderer.render(viewport, this.sectionSelector.getNodeDataBuffer(), this.debugNodeQueue);
        }


        this.printf.download();
    }

    @Override
    public void renderFarAwayTranslucent(Gl46HierarchicalViewport viewport) {

    }

    @Override
    public void addDebugData(List<String> debug) {
        debug.add("Printf Queue: ");
        debug.addAll(this.printfQueue);
        this.printfQueue.clear();
    }







    @Override
    public void addBlockState(Mapper.StateEntry stateEntry) {
        this.blockStateUpdates.add(stateEntry);
    }

    @Override
    public void addBiome(Mapper.BiomeEntry biomeEntry) {
        this.biomeUpdates.add(biomeEntry);
    }




    @Override
    public void processBuildResult(BuiltSection section) {
        this.buildResults.add(section);
    }

    @Override
    public void sectionUpdated(WorldSection worldSection) {

    }





    @Override
    public void initPosition(int X, int Z) {
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                for (int y = -1; y <= 0; y++) {
                    long pos = WorldEngine.getWorldSectionId(4, x,y,z);
                    this.sectionSelector.nodeManager.insertTopLevelNode(pos);
                }
            }
        }
    }

    @Override
    public void setCenter(int x, int y, int z) {

    }






    @Override
    public boolean generateMeshlets() {
        return false;
    }

    @Override
    public void setRenderGen(RenderGenerationService renderService) {
        this.sectionGenerationService = renderService;
    }

    @Override
    public Gl46HierarchicalViewport createViewport() {
        return new Gl46HierarchicalViewport(this);
    }




    @Override
    public void shutdown() {
        this.meshManager.free();
        this.sectionSelector.free();
        this.printf.free();
        this.debugRenderer.free();
    }
}
