package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class JvmFlagsTest {
    @Test
    void keepsOnlyValueFreeOrNumericFlags() {
        List<String> arguments = List.of(
                "-Xmx8G", "-Xms8G", "-Xss4m", "-XX:+UseZGC", "-XX:-UseAdaptiveSizePolicy", "-XX:MaxGCPauseMillis=50",
                "-XX:G1NewSizePercent=30", "-XX:SoftMaxHeapSize=6g", "-XX:StackShadowPages=32",
                "-XX:StartFlightRecording=filename=C:/Users/alice/bench.jfr,settings=profile",
                "-XX:HeapDumpPath=C:\\Users\\alice\\dumps", "-XX:ErrorFile=/home/alice/err.log",
                "-Xlog:gc*:file=C:\\Users\\alice\\gc.log", "-Xbootclasspath/a:C:/Users/alice/x.jar",
                "-Dminecraft.launcher.brand=prism", "-Djava.library.path=C:/Users/alice/natives",
                "-javaagent:C:/Users/alice/agent.jar", "-Xmx", "-XX:+", "-Xmx8G;calc");
        String kept = JvmFlags.sanitize(arguments);
        assertEquals("-Xmx8G -Xms8G -Xss4m -XX:+UseZGC -XX:-UseAdaptiveSizePolicy -XX:MaxGCPauseMillis=50 "
                + "-XX:G1NewSizePercent=30 -XX:SoftMaxHeapSize=6g -XX:StackShadowPages=32", kept);
        assertFalse(kept.contains("alice"));
        assertFalse(kept.contains("/") || kept.contains("\\") || kept.contains(":/"));
    }

    @Test
    void emptyInputGivesEmptyString() {
        assertEquals("", JvmFlags.sanitize(List.of()));
    }
}
