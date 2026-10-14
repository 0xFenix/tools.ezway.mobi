# AdvMenuJ2me

Gộp nhiều bản game Java (J2ME) thành một menu duy nhất — mỗi bản 1 nick, chạy trên PC qua Microemulator.

## Tải về dùng ngay

Vào trang **Releases** tải file `AdvMenuJ2me-vX.jar` mới nhất (chạy từ Java 8 trở lên, không cần kèm file nào khác):

```bat
java -jar AdvMenuJ2me-v1.0.0.jar                 :: mở GUI: chọn 1 file game + Copies + Merge
java -jar AdvMenuJ2me-v1.0.0.jar --gui           :: mở GUI
java -jar AdvMenuJ2me-v1.0.0.jar --out Client-Avatar-menu-x6.jar --copies 6 Client-Avatar.jar       :: CLI
```

GUI tự động: file gộp lưu cùng thư mục file gốc, tên `<tên-game>-menu-xN.jar`
(N = số copies, trùng tên thì tự thêm `-2`, `-3`...).

Manifest file gộp tự lấy từ jar input, không cần nhập:
`MIDlet-Name = <tên game>-xN`, `Vendor = avt.ezway.mobi`,
menu title = `<tên game> Adv Menu`.
Options CLI `--name/--vendor/--display/--icon` chỉ dùng khi muốn ghi đè.

## Chạy game đã gộp

```bat
java -jar Microemulator.jar <game>-menu-xN.jar
```

## Tự build từ source

Cần JDK 17+ để compile (jar thành phẩm target Java 8 / 8 / 17):

```bat
mvn -DskipTests package
```

Jar thành phẩm:
- `desktop/target/merger.jar` — CLI + GUI (chạy từ Java 8).
- `web/target/merger-web.jar` — bản web (chạy từ Java 17):

```bat
java -jar web/target/merger-web.jar --port 8080     :: mở http://localhost:8080
```

Bản web: upload 1 file `.jar` (≤ 10MB) → chọn copies (1–25) → tải file gộp về.
Chi tiết deploy xem `DEPLOY.md`.

## Source

- `core/src/main/java/classes/MergerCLI.java` — logic gộp byte[] in/out (package
  `classes` để dùng chung transformer gốc (Ban 2.5 MZMOD) `class_t`/`class_k`/`class_c`). Java 8.
- `core/src/main/resources/` — runtime nhúng (`MZMOD*.jar`, `icon.png`,
  transformer `classes/*.class`).
- `desktop/src/main/java/merger/` — `Main` (router CLI/GUI) + `MergerGUI`
  (Swing + FlatLaf sáng/tối). Java 8.
- `web/src/main/java/merger/` — `WebMain` + `WebServer` (Javalin:
  `GET /`, `GET /api/health`, `POST /api/merge`). Java 17.
- `web/frontend/` — frontend một trang (Vue 3 + Vite + Tailwind CSS v3),
  build tự động vào jar web bằng `frontend-maven-plugin`.
- `libs/transformer.jar` — bytecode transformer gốc (tách từ Advanced-Menu.jar,
  không có trên Maven Central nên để trong repo, chỉ dùng lúc compile).