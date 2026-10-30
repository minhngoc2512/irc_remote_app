# irc_remote_app

Ứng dụng Android điều khiển thiết bị bằng cổng hồng ngoại (IR blaster) của điện thoại, kèm một
pipeline gộp nhiều cơ sở dữ liệu mã IR mở thành một file SQLite đã chuẩn hóa và phân loại.

```
pipeline/   Python + C++: tải nguồn, giải mã protocol, chuẩn hóa, khử trùng -> out/irdb.sqlite
android/    App Kotlin + Jetpack Compose, đọc irdb.sqlite và phát qua ConsumerIrManager
```

## Nguồn dữ liệu

| Nguồn | Nội dung | Giấy phép |
|---|---|---|
| [Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB) | ~1.900 remote đã phân loại + `_Converted_` (Pronto, IR_Plus) | CC0-1.0 |
| [irdb](https://github.com/probonopd/irdb) | ~3.200 bộ mã dạng protocol/device/function | [Có điều kiện](https://github.com/probonopd/irdb/blob/master/LICENSE.md) |
| [IRremoteESP8266](https://github.com/crankyoldgit/IRremoteESP8266) | Bộ mã hóa ~65 protocol điều hòa → sinh trạng thái đầy đủ | LGPL-2.1 |

> **irdb**: trước khi phát hành app phải mở issue báo cho dự án irdb, giữ dòng ghi công (đã có ở
> màn hình Giới thiệu) và cung cấp bản app khi được yêu cầu. Có thể bỏ nguồn này bằng
> `--sources flipper,flipper_converted,irremoteesp8266`.

## Build cơ sở dữ liệu

Cần Python 3.10+ và g++ (chỉ cho phần điều hòa).

```bash
cd pipeline
./fetch_sources.sh          # clone 3 nguồn vào pipeline/sources/
./irremote/build_ac.sh      # biên dịch IRremoteESP8266 cho PC, sinh trạng thái điều hòa
python3 build_db.py         # -> out/irdb.sqlite
```

Pipeline làm các bước:

1. **Giải mã** — mọi tín hiệu dạng protocol (NEC, RC5/RC6, Sony, Samsung, Kaseikyo, JVC, Denon,
   Sharp, RCA, DirecTV, Dish…, xem `irc/encoders.py`) được render ra mảng micro-giây. App chỉ việc
   phát mảng này, không cần encoder.
2. **Điều hòa** — `irremote/gen_ac.cpp` gọi `IRac::sendAc()` của IRremoteESP8266 cho từng protocol:
   tắt, 4 chế độ × 16–30 °C, tốc độ quạt, đảo gió. Mỗi trạng thái có `params` để app dựng bảng
   điều khiển điều hòa.
3. **Phân loại** — loại thiết bị lấy từ thư mục nguồn; bộ mã không rõ loại được suy luận từ tên nút
   (`irc/normalize.py`). Tên hãng và tên nút được chuẩn hóa (`POWER`, `VOL_UP`, `CH_UP`, `DIGIT_1`…).
4. **Khử trùng** — bộ mã có toàn bộ tín hiệu nằm trong một bộ mã lớn hơn cùng hãng thì bị gộp.

Schema xem `pipeline/schema.sql`.

## Build app

Cần JDK 17 và Android SDK 35.

```bash
cd android
./gradlew assembleDebug     # đóng gói ../pipeline/out/irdb.sqlite vào assets
```

App: chọn loại thiết bị → hãng → thử lần lượt các bộ mã (bấm Power/Vol…) → lưu bộ mã đúng →
remote dựng theo loại thiết bị (nút nguồn, âm lượng/kênh, D-pad, phím số, media, bảng điều hòa,
cùng các nút riêng của thiết bị).
