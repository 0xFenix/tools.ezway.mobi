# j2me-menu-merger

Gộp nhiều game J2ME (.jar) thành một menu duy nhất, chạy trên PC qua Microemulator.

## Chạy

Build một lần duy nhất:

```bat
mvn package
```

Jar thành phẩm: `target/merger.jar` — vừa là CLI vừa là GUI (không cần kèm file nào khác).

```bat
java -jar target/merger.jar                 :: mở GUI
java -jar target/merger.jar --gui           :: mở GUI
java -jar target/merger.jar --out multi.jar --copies 6 game1.jar game2.jar
```

Options CLI: `--out`, `--copies 1..25` (mặc định 10, theo tool gốc),
`--name`, `--vendor`, `--display`, `--icon icon.png`.

## Chạy game đã gộp

```bat
java -jar Microemulator.jar multi.jar
```

## Source

- `src/main/java/classes/MergerCLI.java` — logic gộp (package `classes` để dùng
  chung transformer gốc `class_t`/`class_k`/`class_c`).
- `src/main/java/merger/Main.java` — router CLI/GUI.
- `src/main/java/merger/MergerGUI.java` — giao diện Swing + FlatLaf (sáng/tối).
- `src/main/resources/` — runtime nhúng (`MZMOD*.jar`, `icon.png`).
- `libs/transformer.jar` — bytecode transformer gốc (tách từ Advanced-Menu.jar,
  không có trên Maven Central nên để trong repo, chỉ dùng lúc compile + đóng gói).

## Publish GitHub

Đổi `groupId`/`url` trong `pom.xml` sang tài khoản của bạn, rồi:

```bat
git remote add origin https://github.com/<user>/j2me-menu-merger.git
git push -u origin master
```

File đính kèm release: `target/merger.jar`.
