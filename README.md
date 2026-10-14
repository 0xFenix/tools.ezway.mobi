# j2me-embedder

Gộp nhiều game J2ME (.jar) thành một menu duy nhất, chạy trên PC qua Microemulator.

## File chính

- `merger.jar` — core duy nhất: vừa là CLI vừa là GUI (không cần kèm file nào khác).

## Chạy

Double-click `merger.jar`, hoặc:

```bat
java -jar merger.jar                 :: mở GUI
java -jar merger.jar --gui           :: mở GUI
java -jar merger.jar --out multi.jar --copies 6 game1.jar game2.jar
```

Options CLI: `--out`, `--copies 1..25` (mặc định 10, theo tool gốc),
`--name`, `--vendor`, `--display`, `--icon icon.png`.

## Chạy game đã gộp

```bat
java -jar Microemulator.jar multi.jar
```

## Source

- `merger/src/classes/MergerCLI.java` — logic gộp (package `classes` để dùng
  chung transformer gốc `class_t`/`class_k`/`class_c` nhúng trong jar).
- `merger/src/merger/Main.java` — router CLI/GUI.
- `merger/src/merger/MergerGUI.java` — giao diện Swing.

## Build lại merger.jar

```bat
build.bat
```

(cần `Advanced-Menu.jar` đặt cùng thư mục — script lấy transformer
`classes/*`, `MZMOD*.jar`, `icon.png` từ đó rồi đóng gói fat jar.)
