package com.donquaan.dfast.latency;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

final class HudCanvas {
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F, 1.0F};

    private final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new ByteBufferBuilder(1536));
    private final Matrix4f projection = new Matrix4f();
    private TextureTarget target;
    private Integer textureId;
    private VertexBuffer quad;
    private float quadX = Float.NaN;
    private float quadY = Float.NaN;
    private float quadWidth = Float.NaN;
    private float quadHeight = Float.NaN;

    void redraw(Minecraft minecraft, FormattedCharSequence[] lines, int[] colors, int x, int y, int step) {
        Font font = minecraft.font;
        int width = 0;
        for (FormattedCharSequence line : lines) {
            width = Math.max(width, font.width(line));
        }
        width += 1;
        int height = Math.max(1, lines.length * step);
        double scale = minecraft.getWindow().getGuiScale();
        int pixelWidth = (int) Math.ceil(width * scale);
        int pixelHeight = (int) Math.ceil(height * scale);
        if (target == null) {
            target = new TextureTarget(pixelWidth, pixelHeight, false, Minecraft.ON_OSX);
        } else if (target.width != pixelWidth || target.height != pixelHeight) {
            target.resize(pixelWidth, pixelHeight, Minecraft.ON_OSX);
        }
        textureId = target.getColorTextureId();
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.clear(Minecraft.ON_OSX);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(
                projection.setOrtho(0.0F, (float) (pixelWidth / scale), (float) (pixelHeight / scale), 0.0F, 1000.0F, 21000.0F),
                VertexSorting.ORTHOGRAPHIC_Z);
        target.bindWrite(true);
        GuiGraphics graphics = new GuiGraphics(minecraft, buffers);
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(font, lines[i], 0, i * step, colors[i]);
        }
        graphics.flush();
        RenderSystem.restoreProjectionMatrix();
        RenderTarget main = minecraft.getMainRenderTarget();
        main.bindWrite(true);
        placeQuad(x, y, (float) (pixelWidth / scale), (float) (pixelHeight / scale));
    }

    void draw() {
        if (quad == null) {
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ShaderInstance shader = GameRenderer.getPositionTexShader();
        shader.setSampler("Sampler0", textureId);
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        }
        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }
        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(WHITE);
        }
        shader.apply();
        quad.bind();
        quad.draw();
        VertexBuffer.unbind();
        shader.clear();
        RenderSystem.disableBlend();
    }

    private void placeQuad(float x, float y, float width, float height) {
        if (quad != null && x == quadX && y == quadY && width == quadWidth && height == quadHeight) {
            return;
        }
        if (quad == null) {
            quad = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(x, y, 0.0F).setUv(0.0F, 1.0F);
        builder.addVertex(x, y + height, 0.0F).setUv(0.0F, 0.0F);
        builder.addVertex(x + width, y + height, 0.0F).setUv(1.0F, 0.0F);
        builder.addVertex(x + width, y, 0.0F).setUv(1.0F, 1.0F);
        quad.bind();
        quad.upload(builder.buildOrThrow());
        VertexBuffer.unbind();
        quadX = x;
        quadY = y;
        quadWidth = width;
        quadHeight = height;
    }
}
