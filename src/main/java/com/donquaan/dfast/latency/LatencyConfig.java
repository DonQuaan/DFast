package com.donquaan.dfast.latency;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Cấu hình "Zero-Latency Mode" (DFast v0.2.0). Lưu dạng .properties thuần (java.util,
 * KHÔNG phụ thuộc thư viện ngoài) trong thư mục config của Fabric.
 *
 * <p>Mặc định: bật limiter, frames-in-flight = 1 (trễ thấp nhất), HUD TẮT (người dùng
 * tự bật bằng phím). Hỏng file → dùng mặc định, không crash.
 */
public final class LatencyConfig {

    /** Bật giới hạn hàng đợi frame (GL fence). */
    public boolean renderQueueLimiter = true;
    /** Số frame CPU được xếp trước GPU: 1 = trễ thấp nhất, 2 = cân bằng, tối đa 3. */
    public int framesInFlight = 1;
    /** HUD esports hiện trên màn hình (người dùng bật bằng phím). */
    public boolean hudEnabled = false;
    /** Mã phím GLFW để bật/tắt HUD (mặc định F9). */
    public int hudKeyCode = org.lwjgl.glfw.GLFW.GLFW_KEY_F9;

    private static final String FILE = "dfast-latency.properties";

    public static LatencyConfig load() {
        LatencyConfig c = new LatencyConfig();
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve(FILE);
            if (Files.exists(p)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(p)) {
                    props.load(in);
                }
                c.renderQueueLimiter = Boolean.parseBoolean(
                        props.getProperty("renderQueueLimiter", String.valueOf(c.renderQueueLimiter)));
                c.framesInFlight = clampFrames(parseInt(props.getProperty("framesInFlight"), c.framesInFlight));
                c.hudEnabled = Boolean.parseBoolean(
                        props.getProperty("hudEnabled", String.valueOf(c.hudEnabled)));
                c.hudKeyCode = parseInt(props.getProperty("hudKeyCode"), c.hudKeyCode);
            } else {
                c.save(); // ghi mặc định lần đầu để người dùng biết có gì chỉnh
            }
        } catch (Exception e) {
            // hỏng config → giữ mặc định, không làm sập game
        }
        return c;
    }

    public void save() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve(FILE);
            Files.createDirectories(p.getParent());
            Properties props = new Properties();
            props.setProperty("renderQueueLimiter", String.valueOf(renderQueueLimiter));
            props.setProperty("framesInFlight", String.valueOf(framesInFlight));
            props.setProperty("hudEnabled", String.valueOf(hudEnabled));
            props.setProperty("hudKeyCode", String.valueOf(hudKeyCode));
            try (OutputStream out = Files.newOutputStream(p)) {
                props.store(out, "DFast v0.2.0 - cau hinh Zero-Latency Mode");
            }
        } catch (Exception e) {
            // im lặng — không lưu được config không phải lỗi chết người
        }
    }

    private static int clampFrames(int v) {
        return v < 1 ? 1 : (v > 3 ? 3 : v);
    }

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
