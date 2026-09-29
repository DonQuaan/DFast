package com.donquaan.dfast.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class GlProbe {
    private static final int GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX = 0x9047;
    private static final List<String> WATCHED = List.of(
            "GL_ARB_buffer_storage",
            "GL_ARB_multi_draw_indirect",
            "GL_ARB_indirect_parameters",
            "GL_ARB_bindless_texture",
            "GL_ARB_gl_spirv",
            "GL_KHR_parallel_shader_compile",
            "GL_NV_mesh_shader",
            "GL_NV_command_list",
            "GL_NV_shader_buffer_load",
            "GL_NVX_gpu_memory_info");

    private GlProbe() {
    }

    static JsonObject probe() {
        JsonObject gl = new JsonObject();
        gl.addProperty("vendor", GlUtil.getVendor());
        gl.addProperty("renderer", GlUtil.getRenderer());
        gl.addProperty("version", GlUtil.getOpenGLVersion());
        int count = GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS);
        Set<String> present = new HashSet<>();
        for (int i = 0; i < count; i++) {
            present.add(GL30.glGetStringi(GL11.GL_EXTENSIONS, i));
        }
        gl.addProperty("extensionCount", count);
        JsonArray watched = new JsonArray();
        for (String name : WATCHED) {
            if (present.contains(name)) {
                watched.add(name);
            }
        }
        gl.add("extensions", watched);
        if (present.contains("GL_NVX_gpu_memory_info")) {
            gl.addProperty("dedicatedVideoMemoryMiB", GL11.glGetInteger(GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX) / 1024);
        }
        return gl;
    }
}
