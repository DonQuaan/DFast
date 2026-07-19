# PLAN — DFast (Don Fast)

> Mod tối ưu Minecraft của master (yangdawn/DonQuaan). MC 1.21.1 Fabric · Java 25 · chuyên **Windows + Intel + Nvidia RTX 2000+**.
> Repo: `github.com/DonQuaan/DFast` · License: PolyForm Noncommercial + điều khoản riêng.
> Lập trên hội đồng 6 chuyên gia (2026-07-12). Nguyên tắc: **đo bằng spark, không tự huyễn hoặc; làm cái khả thi + đột phá thật.**

---

## 0. TL;DR — DFast thực sự nên là gì

Hội đồng (đặc biệt phản biện gắt) chốt: **DFast KHÔNG nên tự viết render engine / mesh shader / SIMD mesher / DirectStorage / DLSS.** Đó là việc quy mô **team, nhiều năm, "parity-hell"**, và **phần lớn ecosystem ĐÃ làm** (Nvidium có mesh shader; Sodium 0.9 có async occlusion; Reflex AntiLag có low-latency; ModernFix có startup; C2ME/Moonrise có chunk song song). 1 người + AI **không bảo trì nổi** + kế thừa mọi break của upstream (Nvidium vỡ liên tục mỗi lần Sodium update — bằng chứng thật).

**Đột phá THẬT + khả thi:** DFast là **mod ĐẦU TIÊN thuộc loại "zero-config, hardware-aware performance meta-mod cho Windows/Intel/Nvidia"** — thứ chưa ai làm. Không đua kỹ thuật render với Sodium; mà **điều phối + tự-cấu-hình + thêm lớp native Windows mỏng** mà mọi mod cross-platform bỏ trống.

---

## 1. Định vị lại "gom mọi mod về 1" (reframe bắt buộc)

"Gom về 1" **KHÔNG** = merge source Sodium/Lithium/Nvidium vào 1 codebase — vì: (1) vi phạm license, (2) kế thừa mọi break upstream, (3) không ai bảo trì nổi.

"Gom về 1" **ĐÚNG** = DFast làm **3 việc mà không mod nào gộp lại**:
- **(A) Bundle + version-pin + auto-configure** đúng stack tốt-nhất-lớp cho **đúng máy đích** (Nvidia Turing+/Intel hybrid/Win11/32GB) — người dùng cài **1 mod DFast**, nó tự kéo & ghim đúng Sodium/Lithium/… hợp nhau.
- **(B) Auto-gate chống xung đột** — chỉ 1 lighting engine, cặp Nvidium↔Sodium đúng version, tự tắt module khi mod đối tác đã làm. (Kiến-trúc-hoá bài học crash GlCommandEncoder + version-mismatch ta gặp cả phiên nay.)
- **(C) Lớp platform-native mỏng** — JVM flags đúng + Windows timer/priority/QoS — thứ mod cross-platform không dám đụng.

→ Với người dùng: **cài 1 mod, không cần chỉnh gì, máy Windows/Intel/Nvidia tự chạy tối ưu nhất.** Đó là giá trị chưa tồn tại.

---

## 2. ĐÃ có mod làm — DFast DÙNG, không phát minh lại

| Mảng | Đã có | DFast làm gì |
|---|---|---|
| GPU-driven terrain (mesh/task shader, occlusion, MDI) | **Nvidium** | Bundle + pin đúng version + gate với Sodium |
| Async occlusion culling | **Sodium 0.9** (26.x) — *không áp 1.21.1* | Ghi nhận, không tự viết |
| Reflex / low-latency | **Reflex AntiLag** (258K DL, mọi GPU) | Bundle nếu hợp, không tự cầu NvAPI |
| Giảm RAM ~22% | **JEP 519 Compact Object Headers** (Java 25, 1 flag) | Áp qua JVM profile |
| Startup / model stutter | **ModernFix** | Bundle |
| Worldgen/chunk song song | **C2ME / Moonrise / Noisium** | Bundle + gate (chọn 1 đường) |
| Lighting song song | **Starlight / ScalableLux / Moonrise** | Bundle (chỉ 1) |
| Tick / AI / collision | **Lithium** | Bundle |
| Config module bật/tắt | **CaffeineConfig** | Tái dùng pattern |

---

## 3. BỎ / HOÃN — quá tham vọng cho 1 người + AI

Tự viết mesh/task shader · HZB Nanite-style · GPU entity skinning · `NV_command_list` · Vulkan backend riêng + DLSS · GPU compute chunk mesher · SIMD qua Vector API (*còn incubator ở Java 25*) · off-heap FFM storage (*SIGSEGV không catch được*) · large pages (*cần SeLockMemoryPrivilege*) · DirectStorage (*D3D12 interop cực khó, MC dùng zlib*) · **hard P-core affinity** (*Intel KHUYẾN CÁO KHÔNG — chặn Thread Director*) · lock-free MPMC/SoA rewrite (*correctness minefield*).

---

## 4. Ba trụ ĐỘT PHÁ của DFast

1. **Zero-config auto-tune:** phát hiện phần cứng (GPU model/driver, CPU hybrid, RAM, Windows ver, HAGS/ReBAR) → tự chọn & cấu hình stack + JVM tối ưu. Chưa mod nào làm.
2. **Orchestration/anti-conflict layer:** đảm bảo các mod không đánh nhau (bài học cả phiên: GlCommandEncoder, Sodium branch, C2ME↔Java25…).
3. **Windows-native micro-layer (qua Panama FFM):** high-res frame-pacing timer + process priority/EcoQoS + soft P-core hint — **genuine gap**, không mod cross-platform nào chạm.

---

## 5. MVP phân kỳ (làm lớn/dễ/gain-cao trước)

### Phase 1 — MVP (nền tảng, gain miễn phí, ít rủi ro)
- **Khung mod Fabric** + CaffeineConfig (module bật/tắt) + kỷ luật mixin (chỉ MixinExtras, target hẹp, không @Redirect chồng).
- **Auto-detect phần cứng** → chọn preset.
- **JVM profile** đúng máy đích — **win lớn nhất & miễn phí:** `-XX:+UseCompactObjectHeaders` (~22% heap, ~15% ít GC). ⚠️ **Quyết định §7.**
- **Bundle + version-pin** stack lõi (Sodium/Lithium/FerriteCore/ModernFix…) + **auto-gate** chống xung đột.
- **spark before/after harness làm GATE bắt buộc** — mỗi module phải chứng minh giảm MSPT/tăng 1%-low bằng số, seed cố định. Không giữ module vì cảm tính.

### Phase 2 — Lớp OS-native mỏng (đột phá thật, rủi ro trung bình)
- **High-resolution waitable timer** (kernel32 qua FFM) cho **frame-pacing** — không mod nào dùng, cải thiện 1%-low/độ mượt.
- **Process priority + tắt EcoQoS + SOFT P-core cluster QoS hint** (soft hint, KHÔNG hard affinity — tôn trọng Intel Thread Director).
- **Detect + ADVISE (read-only)** HAGS/ReBAR + cặp Nvidium↔Sodium (chỉ đọc registry/GL, hướng dẫn — không tự sửa hệ thống).
- **CI matrix Java 21/25** + smoke-load chung Sodium/Lithium + gametest → chống lớp bug "upstream update → vỡ".

### Phase 3 — Native nâng cao (khác biệt thật, rủi ro cao — chỉ khi P1/P2 vững)
- **Module native FFM cô lập** (guard `java≥22`, multi-release JAR) — hạ tầng riêng của DFast.
- Thử **Vector API trên 1 bulk-op HẸP non-parity** (tránh worldgen/lighting cần parity byte-for-byte).

---

## 6. Kiến trúc & kỷ luật
- **Ngôn ngữ:** Java 25 (đã là runtime của PO). Loom/FFM/COH sẵn.
- **Mixin:** CaffeineConfig registry, chỉ MixinExtras, target hẹp — mọi mixin tầng render phải qua **verification gate** (grep `Mixin apply failed` sau mỗi build).
- **Native:** **Panama FFM** (không JNI) — module cô lập, có guard version + fallback im lặng.
- **Đo:** spark là GATE. Không con số → không merge.
- **Version:** scheme `MAJOR.MINOR.PATCH`, tag mỗi release, không tự tăng MAJOR khi chưa master duyệt.

## 7. ⚠️ QUYẾT ĐỊNH cần master chốt: COH vs ZGC (loại trừ nhau)
`-XX:+UseCompactObjectHeaders` (giảm ~22% RAM, win lớn nhất) **KHÔNG tương thích ZGC**. Phải chọn default cho DFast:
- **A — COH + G1GC:** RAM ít hơn hẳn, nhưng G1 pause (1%-low kém hơn chút).
- **B — Generational ZGC (không COH):** frametime mượt nhất (1%-low tốt), RAM cao hơn.
- **C — DFast tự chọn theo RAM máy:** máy ≤16GB → COH+G1; máy ≥32GB → ZGC. *(Khuyến nghị — đúng tinh thần "hardware-aware".)*

## 8. Release & License
- **Repo:** `github.com/DonQuaan/DFast` · **GitHub Actions** build jar → **Release + packages** (tải 1 click) · **git tag = version** mỗi bản.
- **License:** **PolyForm Noncommercial 1.0.0 + addendum** — sửa/phân phối/custom tùy ý NHƯNG (a) ghi nguồn DonQuaan/DFast, (b) cấm thương mại, (c) cấm tổ chức >3 người. *(Source-available, không phải OSI open-source.)*

## 9. Bước kế tiếp
1. Master duyệt định vị (§1,4) + chốt §7 (COH vs ZGC default).
2. Dựng repo + khung mod Fabric + LICENSE + CI (Phase 1 khởi động).
3. spark harness trước — để mọi thứ sau đo được.
