# CLAUDE.md — DFast

> Kế thừa doctrine `DEV'S BRAIN/CLAUDE.md`. File này thêm luật riêng cho mod DFast.
> DFast = **meta-optimizer** (auto-tune + anti-conflict + Windows-native layer), **KHÔNG** phải render engine tự viết. Chi tiết: [PLAN.md](PLAN.md).

## 1. Nguyên tắc bất di bất dịch
- **KHÔNG tự viết lại** Sodium/Lithium/Nvidium/render engine (team-scale, parity-hell, đã có mod làm). DFast dùng chúng, không thay chúng.
- **spark là GATE:** mọi module/tối ưu phải chứng minh giảm MSPT / tăng 1%-low bằng **số đo thật** (seed cố định, before/after) mới được giữ. Không giữ vì cảm tính.
- **Mixin kỷ luật:** chỉ MixinExtras, target hẹp, không `@Redirect`/`@Overwrite` chồng lên hot class (bài học GlCommandEncoder). Sau mỗi build → grep `Mixin apply failed`.
- **Native = Panama FFM** (không JNI), module cô lập, guard `java>=22` + fallback im lặng. Rủi ro crash JVM cao → chỉ Phase 3, có gate.

## 2. Nền & version
- MC 1.21.1 · Fabric Loader 0.19.3 · Yarn 1.21.1+build.3 · Fabric API 0.116.13 · Loom 1.8.9.
- DFast core compile **Java 21** (tương thích rộng); module native multi-release Java 22+.
- Máy dev master: [[reference-po-machine-jvm]] (Java 25 sẵn).

## 3. Lộ trình (PLAN §5)
- **Phase 1 (MVP):** khung + auto-detect phần cứng + JVM profile (COH vs ZGC auto theo RAM) + bundle/version-pin + anti-conflict gate + spark harness.
- **Phase 2:** Windows-native mỏng (high-res timer, priority/EcoQoS, soft P-core hint) + CI matrix + advise HAGS/ReBAR.
- **Phase 3 (thử nghiệm, tách biệt):** module native FFM + thử Vector API hẹp + (rủi ro cao) module render riêng.

## 4. Release & License
- Repo `github.com/DonQuaan/DFast` · **git tag = version mỗi bản** · GitHub Actions → Release + jar (tải 1 click). Không tự tăng MAJOR khi chưa master duyệt.
- License: **PolyForm Noncommercial 1.0.0 + DonQuaan Addendum** (source-available, phi thương mại, ghi nguồn, ≤3 thành viên). Xem `LICENSE.md`.

## 5. Cấu trúc
| Đường | Vai trò |
|---|---|
| `src/main/java/com/donquaan/dfast/` | code mod (entrypoints, HardwareProfile, mixin/) |
| `src/main/resources/` | fabric.mod.json, dfast.mixins.json, assets |
| `.github/workflows/build.yml` | CI build + release on tag |
| `PLAN.md` | kế hoạch tổng thể (đã chốt qua hội đồng) |
