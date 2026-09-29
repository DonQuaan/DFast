package com.donquaan.dfast.core;

public interface FenceApi {
    enum WaitResult { SIGNALED, TIMEOUT, FAILED }

    long insert();

    WaitResult await(long sync, long timeoutNanos);

    void delete(long sync);
}
