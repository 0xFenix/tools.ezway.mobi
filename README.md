# j2me-menu-merger

Gộp nhiều game J2ME (.jar) thành một menu duy nhất, chạy trên PC qua Microemulator.

## Chạy

Build một lần duy nhất (cần JDK 17+ để compile; jar ra target Java 8/8/11):

```bat
mvn package
```

Jar thành phẩm:
- `desktop/target/merger.jar` — CLI + GUI (Java 8 trở lên, không cần kèm file nào khác).
- `web/target/merger-web.jar` — backend web Javalin (Java 11 trở lên, xem `DEPLOY.md`).

```bat
java -jar desktop/target/merger.jar                 :: mở GUI: chọn 1 file game + Copies + Merge
java -jar desktop/target/merger.jar --gui           :: mở GUI
java -jar desktop/target/merger.jar --out Client-Avatar-menu-x6.jar --copies 6 Client-Avatar.jar
java -jar web/target/merger-web.jar --port 8080     :: chạy bản web
```

GUI tự động: file gộp lưu cùng thư mục `<tên-game>-menu-xN.jar`
(N = số copies, trùng tên thì tự thêm `-2`, `-3`...).

Manifest file gộp tự lấy từ jar input, không cần nhập:
`MIDlet-Name = <tên game>-xN`, `Vendor = avt.ezway.mobi`,
menu title = `<tên game> Adv Menu`.
Options CLI `--name/--vendor/--display/--icon` chỉ dùng khi muốn ghi đè.

## Chạy game đã gộp

```bat
java -jar Microemulator.jar multi.jar
```

## Source

- `core/src/main/java/classes/MergerCLI.java` — logic gộp byte[] in/out (package
  `classes` để dùng chung transformer gốc `class_t`/`class_k`/`class_c`). Java 8.
- `core/src/main/resources/` — runtime nhúng (`MZMOD*.jar`, `icon.png`,
  transformer `classes/*.class`).
- `desktop/src/main/java/merger/` — `Main` (router CLI/GUI) + `MergerGUI`
  (Swing + FlatLaf sáng/tối). Java 8.
- `web/src/main/java/merger/` — `WebMain` + `WebServer` (Javalin:
  `GET /`, `GET /api/health`, `POST /api/merge`). Java 11.
- `web/src/main/resources/web/index.html` — frontend một trang.
- `libs/transformer.jar` — bytecode transformer gốc (tách từ Advanced-Menu.jar,
  không có trên Maven Central nên để trong repo, chỉ dùng lúc compile).

## Publish GitHub

Đổi `groupId`/`url` trong `pom.xml` sang tài khoản của bạn, rồi:

```bat
git remote add origin https://github.com/<user>/j2me-menu-merger.git
git push -u origin master
```

File đính kèm release: `desktop/target/merger.jar` (desktop),
`web/target/merger-web.jar` (web).
