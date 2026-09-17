# DFast v0.2.0 — "Zero-Latency Mode" (spec)

> Kế thừa [PLAN.md](PLAN.md) + [CLAUDE.md](CLAUDE.md). Cờ đầu v0.2.0 do master chốt (2026-09-17): **B — Low-Latency Pipeline**, làm full, HUD bật tùy chọn.
> Định vị không đổi: DFast = **meta-optimizer**, KHÔNG viết lại render engine. v0.2.0 thêm **tầng cắt độ trễ** mà chưa mod nào chạm.

## 0. Một câu
Biến Minecraft "cảm giác" như game FPS pro bằng cách **cắt độ trễ chuột-tới-hình (click-to-photon)** — đo bằng **mili-giây thật**, không chạy theo con số FPS.

## 1. Nguyên tắc bất di bất dịch cho bản này
- **Đo, không cảm tính.** Mỗi kỹ thuật phải chứng minh cắt được ms thật (harness nội bộ, seed cố định, before/after) mới giữ. Không cắt được → gỡ. Đây là **cổng bắt buộc**, không nới dù master bỏ "spike" riêng.
- **Không hứa nhãn Nvidia Reflex nếu chưa bind được.** OpenGL có thể không nhận full Reflex SDK. Nếu vậy: giao "Reflex-class latency" bằng #1–#4 và **nói thẳng**, tuyệt đối không dán nhãn giả.
- **Mixin kỷ luật.** Chỉ MixinExtras, `@Inject` target hẹp vào render loop, KHÔNG `@Overwrite`/`@Redirect` chồng hot class. Sau build: grep `Mixin apply failed`.
- **Native = Panama FFM**, module cô lập, guard `java>=22` + fallback im lặng (core vẫn chạy trên Java 21). Máy master Java 25 → chạy đủ.

## 2. Kiến trúc — package `com.donquaan.dfast.latency`

| Module | Cơ chế | Điều khiển | Độ chắc |
|---|---|---|---|
| **RenderQueueLimiter** | Chèn `glFenceSync` cuối frame + chờ fence trước khi bắt frame mới → giới hạn frame CPU xếp trước còn 1. Mixin `@Inject` TAIL vào vòng render (ứng viên: `MinecraftClient#render` / `RenderSystem.flipFrame`). | `framesInFlight` = 1/2/off | ✅ Chắc |
| **NvapiLowLatency** | FFM → `nvapi64.dll`: ghi driver profile cho tiến trình Java bật **Low Latency Ultra**. *ID setting NVAPI chính xác xác minh theo header lúc code — không bịa hằng số.* | on/off | ✅ Chắc (cần xác minh setting ID) |
| **FramePacer** | High-res timer (`timeBeginPeriod` qua `winmm.dll` FFM hoặc waitable timer 0.5ms) → nhịp frame đều, giảm jitter trễ. Kéo từ Phase 2. | on/off, target ms | ✅ Chắc |
| **InputAccelerator** | Ép **raw mouse input** ON, gỡ smoothing/accumulation nhân tạo, poll input đầu frame. *Lợi ích nhỏ hơn #1 — ghi nhận trung thực.* | on/off | ✅ Khả thi (win vừa) |
| **ReflexBridge** *(experimental)* | Thử Streamline/Reflex markers (simulation/render). MC là OpenGL → SDK ưu tiên DX/Vulkan, **có thể không bind**. Thử; fail → log + dựa #1–4. | auto, tắt được | ⚠️ Chưa chắc |
| **LatencyHud** | Fabric HUD render callback: overlay frametime · 1%-low · **latency ms ước tính** (đo từ input-poll → buffer-swap bằng marker riêng, không cần Reflex SDK). Keybind bật/tắt. | on/off + keybind | ✅ Chắc |
| **LatencyHarness** | Đo before/after nội bộ, seed cố định, log ms. **Cổng giữ/gỡ tính năng.** | dev flag | ✅ Chắc |

## 3. Config (mục `latency` trong config DFast)
`framesInFlight`, `nvapiLowLatency`, `framePacing`+`targetMs`, `rawInput`, `hud`+`hudKeybind`, `reflexBridge`(auto). Mặc định: bật #1–#4 + HUD tắt (người dùng tự bật phím).

## 4. Cổng thành công (định nghĩa "xong")
- **Chỉ tiêu (giả thuyết, harness xác nhận):** cắt được ≥1 frame độ trễ click-to-photon (≈ 3–8ms tùy FPS) trên máy master.
- HUD hiện latency ms realtime, số khớp harness.
- Không kỹ thuật nào làm tụt 1%-low hay tăng MSPT (spark gate).
- Build CI xanh, không `Mixin apply failed`.

## 5. Rủi ro (nói thẳng)
1. **#2 NVAPI:** setting ID Low Latency Ultra phải xác minh theo header NVAPI thật; nếu khác OpenGL, thử qua profile chung.
2. **#5 Reflex nhãn:** nhiều khả năng không bind OpenGL → fallback #1–4, không giả nhãn.
3. **FFM native Java 22+:** trên Java 21 các phần native = no-op (core vẫn chạy). Máy master Java 25 → đủ.
4. **Mixin hot render path:** rủi ro crash cao → target hẹp, MixinExtras, grep sau build.

## 6. Release (luật nhà 8)
- `mod_version=0.2.0` → **git tag `v0.2.0`** kèm mô tả → GitHub Actions → Release + `dfast-0.2.0.jar` (tải 1 click).
- Không tự tăng MAJOR khi chưa master duyệt.

## 7. Thứ tự làm
1. Khung package `latency` + config + LatencyHarness (đo trước).
2. #1 RenderQueueLimiter → đo → giữ nếu cắt ms.
3. #3 FramePacer → đo.
4. #2 NvapiLowLatency (FFM) → đo.
5. #4 InputAccelerator → đo.
6. #6 LatencyHud (bật bằng phím).
7. #5 ReflexBridge (thử; fallback nếu fail).
8. Build CI xanh → tag `v0.2.0` → Release.
