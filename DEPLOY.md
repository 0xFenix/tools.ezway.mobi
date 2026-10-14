# Deploy bản web lên Oracle VPS

Tool chạy bằng chính `merger.jar`, không cần build riêng.

## 1. Chạy thử local

```bat
mvn -DskipTests package
java -jar target/merger.jar --serve --port 8080
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

- Upload ~30MB, Copies tối đa 10 (desktop 25).
- Lỗi merge trả JSON `{"error":"..."}` (mã 422) — thường là jar
  obfuscate nặng / thiếu preverify / bytecode sửa ẩu.
