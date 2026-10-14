# AGENTS.md — J2ME Menu Merger

Repo gộp nhiều game J2ME (.jar) thành một menu duy nhất. User giao tiếp
tiếng Việt, muốn trả lời ngắn gọn.

## Cấu trúc

```
pom.xml            parent (aggregator: core, desktop, web)
core/              merge engine, Java 8 — classes/MergerCLI.java (byte[] in/out),
                   resources: MZMOD*.jar, icon.png, transformer classes/*.class
desktop/           CLI + Swing GUI, Java 8 → desktop/target/merger.jar
web/               Javalin 7 backend, Java 17 → web/target/merger-web.jar
  frontend/        Vue 3 + Vite 5 + Tailwind CSS v3 (PIN v3, KHÔNG lên v4)
libs/              transformer.jar (system-scope, compile-only)
```

`desktop --serve` đã bỏ; web chạy bằng `java -jar web/target/merger-web.jar --port 8080`.

## Luật bất biến (đừng phá)

- **core**: logic merge/transform bytecode đã verify — chỉ dời file, không sửa
  logic khi refactor. Regression chuẩn: `Client-Avatar.jar --copies 6` →
  **2053 entries**, manifest `MIDlet-Name: Avatar-x6`,
  `Vendor: avt.ezway.mobi`, `MIDlet-1: Avatar Adv Menu,...`.
- **Java target**: core/desktop = 8 (máy người dùng phổ thông còn JRE 8),
  web = 17. Code core/desktop KHÔNG dùng API > 8 (`List.of`, `isBlank`,
  `readAllBytes`... đều cấm). Check bằng major version class trong jar
  (52 = Java 8, 61 = Java 17).
- **Manifest/output**: auto từ jar input — `<game>-menu-xN.jar`,
  `<Tên>-xN`, vendor `avt.ezway.mobi`, menu `<Tên> Adv Menu`. Flags
  `--name/--vendor/--display/--icon` chỉ để ghi đè.
- **Web limits**: upload ~10MB, copies 1–25. Giữ đồng bộ ở 4 nơi:
  `WebServer.java` (MAX_BYTES/MAX_COPIES/health JSON), `App.vue`
  (MAX_COPIES/footer), `DEPLOY.md`.
- **Frontend**: Tailwind v3 vì browser cũ (WS2012) không render CSS v4
  (oklch/@layer). Không spinner native cho ô copies (dùng text +
  inputmode numeric + tự clamp). API giữ nguyên:
  `POST /api/merge` multipart (`game`, `copies`) → jar +
  `Content-Disposition`; lỗi JSON `{"error":...}`.
- **Javalin 7**: route qua `config.routes.get/post` (bỏ `app.get/post`);
  thêm `jackson-databind` riêng (Javalin không bundle); `SizeUnit` ở
  `io.javalin.config`. Tra docs bằng Context7 khi đụng API mới.

## Build / test

```bat
mvn -DskipTests package          :: build hết (cần JDK 17+ để compile)
mvn -DskipTests package -pl web -am   :: chỉ core + web
cd web/frontend && npm run dev   :: dev UI (proxy /api → :8080)
```

- Frontend build (Vite outDir `../target/classes/web`) do
  `frontend-maven-plugin` tự chạy trong `mvn package` (tự tải Node 20).
- Test web: `GET /`, `/api/health`, `POST /api/merge` (đúng + copies quá
  max + thiếu file). Test merge xong **xóa file /tmp** và kill đúng
  process java của test (lọc theo port trong CommandLine) — **không
  `taskkill /IM java.exe` bừa** (có process Java của VS Code).
- `target/merger.jar` bị khóa khi GUI đang mở → tắt hết cửa sổ merger
  trước khi build. Sau build xóa `dependency-reduced-pom.xml` (rác của
  shade plugin, đã gitignore).
- PowerShell: `mkdir a b c` KHÔNG chạy (dùng `New-Item -ItemType
  Directory`); `rm -f` lỗi ambiguous (dùng `Remove-Item -Force`).

## Git

- Cây luôn sạch (`git status --porcelain` rỗng) trước khi báo xong.
- Commit nhỏ theo bước, message tiếng Anh ngắn.
- Không commit: `target/`, `node_modules/`, `*-menu*.jar`,
  `Client-Avatar.jar`, `Microemulator.jar`, `*.log`, file screenshot test.
