# HRM — Tổng Quan Dự Án

## 1. Sản phẩm là gì

**HRM** là ứng dụng quản lý nhân sự. Dự án được tổ chức thành hai ứng dụng độc lập: một backend Spring Boot và một web application Next.js.

**Trạng thái hiện tại:** Phase 0 — đã có backend Spring Boot khởi tạo và web base project. Chưa có module nghiệp vụ, database schema, endpoint, authentication flow hoặc UI chức năng.

---

## 2. Thành phần hiện có

| Thành phần | Mô tả |
|:---|:---|
| **Backend API** | Ứng dụng Spring Boot với entry point `HrmApplication` |
| **Web app** | Next.js App Router với trang khởi đầu và cấu hình kết nối API qua biến môi trường |

---

## 3. Tech Stack

| Thành phần | Công nghệ đang dùng |
|:---|:---|
| **Backend API** | Java 21 / Spring Boot 4.0.8 |
| **Build backend** | Maven Wrapper |
| **Web app** | Next.js 16.3.5 (App Router) + React 19 |
| **Ngôn ngữ frontend** | TypeScript, strict mode |
| **Styling** | Tailwind CSS 4 |
| **Kiểm tra frontend** | ESLint 9 + TypeScript typecheck |
| **Package manager frontend** | npm |

---

## 4. Cấu trúc project

```text
hrm/
├── api/                 # Spring Boot backend
│   ├── src/main/        # Source và application.properties
│   └── pom.xml          # Maven dependencies và build config
├── apps/
│   └── web/             # Next.js frontend
│       ├── app/         # App Router routes và global styles
│       ├── lib/         # API utility dùng chung
│       └── public/      # Static assets
├── docs/                # Tài liệu dự án
└── README.md            # Hướng dẫn khởi động nhanh
```

---

## 5. Chạy local

### Backend

```powershell
cd api
.\mvnw.cmd spring-boot:run
```

### Frontend

```powershell
cd apps\web
Copy-Item .env.example .env.local
npm install
npm run dev
```

Web app chạy tại `http://localhost:3000`. Mặc định `NEXT_PUBLIC_API_URL` trong `.env.example` trỏ đến `http://localhost:8080`, là port mặc định của Spring Boot khi chưa cấu hình port khác.

---

## 6. Tài liệu

| File | Nội dung |
|:---|:---|
| [`README.md`](../README.md) | Hướng dẫn khởi động nhanh cho toàn project |
| `docs/OVERVIEW.md` | File này — tổng quan cấu trúc và tech stack hiện có |
| [`apps/web/README.md`](../apps/web/README.md) | Cách chạy và scripts của frontend |
