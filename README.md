# DFast (Don Fast)

**Zero-config, hardware-aware performance meta-optimizer cho Minecraft** — chuyên **Windows + Intel + Nvidia RTX 2000+**.

DFast **không** phải một render engine mới (Sodium/Nvidium/Lithium đã làm rất tốt phần đó). DFast là **lớp điều phối + tự-cấu-hình** mà chưa mod nào làm: cài **1 mod DFast**, nó tự nhận phần cứng máy bạn rồi tối ưu JVM & stack cho đúng máy đó — bạn **không phải chỉnh gì**.

## Ba trụ
1. **Auto-tune** — nhận CPU/RAM/GPU/OS → chọn JVM profile + stack tối ưu (ví dụ: máy ≤16GB dùng Compact Object Headers giảm ~22% RAM; máy ≥32GB dùng Generational ZGC cho frametime mượt).
2. **Anti-conflict** — đảm bảo các mod tối ưu không đánh nhau (chỉ 1 lighting engine, đúng cặp version…).
3. **Windows-native micro-layer** (Panama FFM) — high-res frame-pacing timer, process priority/EcoQoS, soft P-core hint. Thứ mod cross-platform bỏ trống.

## Nền tảng
- Minecraft **1.21.1** · Fabric Loader 0.19.3 · Java **21+** (khuyến nghị 25) · Fabric API.
- Tối ưu cho Nvidia RTX 2000+ (Turing trở lên), CPU Intel hybrid, Windows 11.

## Cài đặt
Tải file `.jar` mới nhất từ [Releases](https://github.com/DonQuaan/DFast/releases), thả vào thư mục `mods/`.

## Build
```bash
./gradlew build
# jar ở build/libs/
```

## License
**PolyForm Noncommercial 1.0.0 + DonQuaan Addendum** — source-available, **phi thương mại**. Được sửa/phân phối/custom tùy ý nhưng phải **ghi nguồn DonQuaan/DFast**, **cấm thương mại**, và **cấm tổ chức >3 thành viên**. Xem [LICENSE.md](LICENSE.md).

*Tác giả: DonQuaan (yangdawn).*
