package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.gl.GlBuffer;

import static org.lwjgl.opengl.GL30C.GL_R8UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL42.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL45C.nglClearNamedBufferData;

public class Gl46MeshletViewport extends Viewport<Gl46MeshletViewport> {
    GlBuffer visibilityBuffer;
    public Gl46MeshletViewport(Gl46MeshletsFarWorldRenderer renderer) {
        this.visibilityBuffer = new GlBuffer(renderer.maxSections*4L).zero();
    }

    protected void delete0() {
        this.visibilityBuffer.free();
    }
}
