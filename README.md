# irc_remote_app

Ứng dụng Android biến điện thoại thành remote cho thiết bị trong nhà:

- **Hồng ngoại (IR)** trên điện thoại có cổng IR blaster (Xiaomi, Redmi, POCO…): TV, điều hòa,
  quạt, đèn, loa, đầu thu, máy chiếu… với hơn 7.000 bộ mã gộp từ các cơ sở dữ liệu mở.
- **Qua Wi-Fi**, dùng được cả trên điện thoại không có cổng IR:
  - **Android TV / Google TV** (Mi Box, TV chạy Android): giao thức Android TV Remote v2, ghép nối
    bằng mã trên TV; D-pad hoặc bàn rê, mở ứng dụng, gõ chữ, giọng nói.
  - **TV LG webOS**: giao thức SSAP, ghép nối bằng cách chấp nhận trên TV; con trỏ chuột thật,
    danh sách ứng dụng của TV, chọn HDMI, gõ chữ, bật TV bằng Wake-on-LAN.

```
pipeline/   Python + C++: tải nguồn, giải mã protocol, chuẩn hóa, khử trùng -> out/irdb.sqlite
android/    App Kotlin + Jetpack Compose
  ir/       phát IR qua ConsumerIrManager
  atv/      Android TV Remote v2 (protobuf, TLS, ghép nối, mDNS)
  webos/    LG webOS (WebSocket SSAP, pointer socket, SSDP + dò cổng, Wake-on-LAN)
```

## Build cơ sở dữ liệu mã IR

Repo không chứa dữ liệu mã IR; pipeline tải nguồn rồi build ra `pipeline/out/irdb.sqlite`.
Cần Python 3.10+ và g++ (chỉ cho phần điều hòa).

```bash
cd pipeline
./fetch_sources.sh          # clone Flipper-IRDB, irdb, IRremoteESP8266 vào pipeline/sources/
./irremote/build_ac.sh      # biên dịch IRremoteESP8266 cho PC, sinh trạng thái điều hòa
python3 build_db.py         # -> out/irdb.sqlite
```

Pipeline làm các bước:

1. **Giải mã**: mọi tín hiệu dạng protocol (NEC, RC5/RC6, Sony, Samsung, Kaseikyo, JVC, Denon,
   Sharp, RCA, DirecTV, Dish…, xem `irc/encoders.py`) được render ra mảng micro-giây. App chỉ việc
   phát mảng này, không cần encoder.
2. **Điều hòa**: `irremote/gen_ac.cpp` gọi `IRac::sendAc()` của IRremoteESP8266 cho từng protocol
   và từng model remote: tắt, 4 chế độ × 16–30 °C, tốc độ quạt, đảo gió. Mỗi trạng thái có `params`
   để app dựng bảng điều khiển điều hòa.
3. **Phân loại**: loại thiết bị lấy từ thư mục nguồn; bộ mã không rõ loại được suy luận từ tên nút
   (`irc/normalize.py`). Tên hãng và tên nút được chuẩn hóa (`POWER`, `VOL_UP`, `CH_UP`, `DIGIT_1`…).
4. **Khử trùng**: bộ mã có toàn bộ tín hiệu nằm trong một bộ mã lớn hơn cùng hãng thì bị gộp.

Schema xem `pipeline/schema.sql`.

## Build app

Cần JDK 17 và Android SDK 35.

```bash
cd android
./gradlew assembleDebug     # đóng gói ../pipeline/out/irdb.sqlite vào assets
```

- **IR**: chọn loại thiết bị → hãng → thử lần lượt các bộ mã → lưu bộ mã đúng → remote dựng theo
  loại thiết bị (D-pad, âm lượng/kênh, phím số, media, bảng điều hòa…).
- **Android TV / LG**: Thêm thiết bị → ô "qua Wi-Fi" ở đầu danh sách → chọn TV tìm được (hoặc nhập
  IP) → ghép nối. Điện thoại và TV phải cùng mạng Wi-Fi.

### Thử giao thức với TV thật

Có các bài test JVM chạy trực tiếp tới TV (bỏ qua nếu không đặt biến môi trường):

```bash
cd android
WEBOS_HOST=192.168.1.20 WEBOS_STORE=/tmp/webos.properties ./gradlew testDebugUnitTest --tests '*WebOsProbeTest*'
ATV_HOST=192.168.1.30 ./gradlew testDebugUnitTest --tests '*AtvProtocolTest*'
```

## Giấy phép

Mã nguồn: [MIT](LICENSE). Nguồn dữ liệu mã IR, font và thư viện có giấy phép riêng, xem
[NOTICE.md](NOTICE.md).

> **Lưu ý khi phát hành APK có kèm dữ liệu irdb**: phải mở issue báo cho dự án irdb, giữ dòng ghi
> công (đã có ở màn hình Nguồn dữ liệu) và cung cấp bản app khi được yêu cầu. Có thể build không
> kèm irdb bằng `python3 build_db.py --sources flipper,flipper_converted,irremoteesp8266`.

Dự án không liên quan và không được bảo trợ bởi LG, Xiaomi, Google hay các hãng thiết bị khác.
