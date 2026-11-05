# Deploy bản web lên Oracle VPS

Bản web là `merger-web.jar` (module `web/`, Javalin 7, Java 17) —
build riêng, desktop (`merger.jar`, Java 8) không ảnh hưởng.

## 1. Chạy thử local

```bat
mvn -DskipTests package
java -jar web/target/merger-web.jar --port 8080
```

Mở `http://localhost:8080`, upload `Client-Avatar.jar`, Copies 6, Merge
→ tải về `Client-Avatar-menu-x6.jar`.

`GET /api/health` để kiểm tra service sống.

## 2. VPS Oracle (Ubuntu)

```bash
sudo apt update && sudo apt install -y docker.io docker-compose-plugin
git clone <repo> j2me-embedder && cd j2me-embedder
docker compose up -d --build
curl localhost:8080/api/health
```

## 3. Public qua Cloudflare (free)

- Cách dễ: `cloudflared tunnel --url http://localhost:8080`
  (không cần mở port, không cần nginx).
- Cách chuẩn: trỏ DNS domain về VPS qua Cloudflare, dựng nginx
  reverse-proxy `domain -> 127.0.0.1:8080`, bật SSL Full.

## Giới hạn bản web

- Upload ~10MB, Copies tối đa 25 (bằng desktop).
- `POST /api/merge` trả `202 {"jobId":...}` (job async), poll tiến độ
  `GET /api/jobs/:id` → `{status, done, total, current}`, tải file ở
  `GET /api/jobs/:id/download`. Kết quả giữ trong RAM 10 phút rồi xóa.
- Merge chạy song song tối đa 10 slot (theo CPU), mỗi slot cách ly ClassLoader
  riêng vì transformer core không thread-safe; thừa (10 việc chờ) thì trả
  429 + Retry-After.
- Lỗi merge trả JSON `{"error":"..."}` (mã 422) — thường là jar
  obfuscate nặng / thiếu preverify / bytecode sửa ẩu.
- Thống kê (`GET /api/stats`: online/truy cập/đã ghép) lưu ở
  `stats.json` trong thư mục chạy jar — backup chung với jar khi
  chuyển VPS, xóa file để reset số liệu.
