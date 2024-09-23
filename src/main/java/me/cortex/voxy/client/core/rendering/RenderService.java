package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.model.ModelStore;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.rendering.building.SectionUpdateRouter;
import me.cortex.voxy.client.core.rendering.hierachical2.HierarchicalNodeManager;
import me.cortex.voxy.client.core.rendering.hierachical2.HierarchicalOcclusionTraverser;
import me.cortex.voxy.client.core.rendering.hierachical2.NodeManager2;
import me.cortex.voxy.client.core.rendering.section.AbstractSectionRenderer;
import me.cortex.voxy.client.core.rendering.section.IUsesMeshlets;
import me.cortex.voxy.client.core.rendering.section.MDICSectionRenderer;
import me.cortex.voxy.client.core.rendering.util.DownloadStream;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.common.util.MessageQueue;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.thread.ServiceThreadPool;
import me.cortex.voxy.common.world.WorldSection;
import net.minecraft.client.render.Camera;

import java.util.Arrays;
import java.util.List;

import static org.lwjgl.opengl.GL42.*;

public class RenderService<T extends AbstractSectionRenderer<J, ?>, J extends Viewport<J>> {
    public static final int STATIC_VAO = glGenVertexArrays();

    private static AbstractSectionRenderer<?, ?> createSectionRenderer(ModelStore store, int maxSectionCount, long geometryCapacity) {
        return new MDICSectionRenderer(store, maxSectionCount, geometryCapacity);
    }

    private final ViewportSelector<?> viewportSelector;
    private final AbstractSectionRenderer<J, ?> sectionRenderer;

    private final NodeManager2 nodeManager;
    private final HierarchicalOcclusionTraverser traversal;
    private final ModelBakerySubsystem modelService;
    private final RenderGenerationService renderGen;

    private final MessageQueue<WorldSection> sectionUpdateQueue;
    private final MessageQueue<BuiltSection> geometryUpdateQueue;

    public RenderService(WorldEngine world, ServiceThreadPool serviceThreadPool) {
        this.modelService = new ModelBakerySubsystem(world.getMapper());

        //Max sections: ~500k
        //Max geometry: 1 gb
        this.sectionRenderer = (T) createSectionRenderer(this.modelService.getStore(),1<<19, (1L<<30)-1024);

        //Do something incredibly hacky, we dont need to keep the reference to this around, so just connect and discard
        var router = new SectionUpdateRouter();

        this.nodeManager = new NodeManager2(1<<21, this.sectionRenderer.getGeometryManager(), router);

        this.sectionUpdateQueue = new MessageQueue<>(section -> {
            byte childExistence = section.getNonEmptyChildren();
            section.release();//TODO: move this to another thread (probably a service job to free, this is because freeing can cause a DB save which should not happen on the render thread)
            this.nodeManager.processChildChange(section.key, childExistence);
        });
        this.geometryUpdateQueue = new MessageQueue<>(this.nodeManager::processGeometryResult);

        this.viewportSelector = new ViewportSelector<>(this.sectionRenderer::createViewport);
        this.renderGen = new RenderGenerationService(world, this.modelService, serviceThreadPool, this.geometryUpdateQueue::push, this.sectionRenderer.getGeometryManager() instanceof IUsesMeshlets);

        router.setCallbacks(this.renderGen::enqueueTask, section -> {
            section.acquire();
            this.sectionUpdateQueue.push(section);
        });

        this.traversal = new HierarchicalOcclusionTraverser(this.nodeManager, 512);

        world.setDirtyCallback(router::forward);

        Arrays.stream(world.getMapper().getBiomeEntries()).forEach(this.modelService::addBiome);
        world.getMapper().setBiomeCallback(this.modelService::addBiome);

        //this.nodeManager.insertTopLevelNode(WorldEngine.getWorldSectionId(0, 0,0,0));
        //this.nodeManager.insertTopLevelNode(WorldEngine.getWorldSectionId(4, 0,0,0));

        final int H_WIDTH = 10;
        for (int x = -H_WIDTH; x <= H_WIDTH; x++) {
            for (int y = 0; y <= 0; y++) {
                for (int z = -H_WIDTH; z <= H_WIDTH; z++) {
                    this.nodeManager.insertTopLevelNode(WorldEngine.getWorldSectionId(4, x, y, z));
                }
            }
        }
    }

    public void setup(Camera camera) {
        this.modelService.tick();
    }

    public void renderFarAwayOpaque(J viewport) {
        LightMapHelper.tickLightmap();

        //Render previous geometry with the abstract renderer
        //Execute the hieracial selector
        // render delta sections

        //Hieracial is not an abstract thing but
        // the section renderer is as it might have different backends, but they all accept a buffer containing the section list


        this.sectionRenderer.renderOpaque(viewport);


        //NOTE: need to do the upload and download tick here, after the section renderer renders the world, to ensure "stable"
        // sections


        //FIXME: we only want to tick once per full frame, this is due to how the data of sections is updated
        // we basicly need the data to stay stable from one frame to the next, till after renderOpaque
        // this is because e.g. shadows, cause this pipeline to be invoked multiple times
        // which may cause the geometry to become outdated resulting in corruption rendering in renderOpaque
        //TODO: Need to find a proper way to fix this (if there even is one)
        if (true /* firstInvocationThisFrame */) {
            DownloadStream.INSTANCE.tick();

            this.sectionUpdateQueue.consume();
            this.geometryUpdateQueue.consume();
            if (this.nodeManager.writeChanges(this.traversal.getNodeBuffer())) {//TODO: maybe move the node buffer out of the traversal class
                   UploadStream.INSTANCE.commit();
            }
            //this needs to go after, due to geometry updates committed by the nodeManager
            this.sectionRenderer.getGeometryManager().tick();
        }
        UploadStream.INSTANCE.tick();

        glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT|GL_PIXEL_BUFFER_BARRIER_BIT);

        int depthBuffer = glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if (depthBuffer == 0) {
            depthBuffer = glGetFramebufferAttachmentParameteri(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        }
        this.traversal.doTraversal(viewport, depthBuffer);

        this.sectionRenderer.buildDrawCallsAndRenderTemporal(viewport, this.traversal.getRenderListBuffer());
    }

    public void renderFarAwayTranslucent(J viewport) {
        this.sectionRenderer.renderTranslucent(viewport);
    }

    public void addDebugData(List<String> debug) {
        this.modelService.addDebugData(debug);
        this.renderGen.addDebugData(debug);
        this.sectionRenderer.addDebug(debug);
        this.nodeManager.addDebug(debug);
    }

    public void shutdown() {
        this.modelService.shutdown();
        this.renderGen.shutdown();
        this.viewportSelector.free();
        this.sectionRenderer.free();
        this.traversal.free();
        //Release all the unprocessed built geometry
        this.geometryUpdateQueue.clear(BuiltSection::free);
    }

    public Viewport<?> getViewport() {
        return this.viewportSelector.getViewport();
    }
}
