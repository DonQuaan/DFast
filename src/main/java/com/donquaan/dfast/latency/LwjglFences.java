package com.donquaan.dfast.latency;

import com.donquaan.dfast.core.FenceApi;
import org.lwjgl.opengl.GL32C;

final class LwjglFences implements FenceApi {
    @Override
    public long insert() {
        return GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    @Override
    public WaitResult await(long sync, long timeoutNanos) {
        return classify(GL32C.glClientWaitSync(sync, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, timeoutNanos));
    }

    @Override
    public void delete(long sync) {
        GL32C.glDeleteSync(sync);
    }

    static WaitResult classify(int glResult) {
        return switch (glResult) {
            case GL32C.GL_ALREADY_SIGNALED, GL32C.GL_CONDITION_SATISFIED -> WaitResult.SIGNALED;
            case GL32C.GL_TIMEOUT_EXPIRED -> WaitResult.TIMEOUT;
            default -> WaitResult.FAILED;
        };
    }
}
