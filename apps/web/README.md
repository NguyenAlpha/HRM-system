# HRM Web

Next.js 16 frontend for the HRM application. It uses the App Router, TypeScript strict mode, Tailwind CSS, and an `@/*` import alias.

## Requirements

- Node.js 20.9 or newer
- npm 10 or newer

## Run locally

```bash
cp .env.example .env.local
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). The Spring Boot API is expected at `http://localhost:8080` by default; change `API_URL` in `.env.local` if needed.

## Authentication portals

| Portal | Login | Home | Accepted account |
| --- | --- | --- | --- |
| HRM workspace | `/login` | `/dashboard` | Any authenticated account without `SYSTEM_ADMIN` |
| System administration | `/admin/login` | `/admin` | Account with `SYSTEM_ADMIN` |

Access and refresh tokens are kept in separate HttpOnly cookies for each portal. Keep
`AUTH_COOKIE_SECURE=false` only for local HTTP development; set it to `true` when the web
application is served over HTTPS.

The admin dashboard links to `/admin/rbac` for role and permission CRUD, module filtering,
and granting/revoking permissions on roles. See [RBAC usage](docs/RBAC.md) for details.

## Commands

```bash
npm run dev       # development server
npm run build     # production build
npm run start     # serve production build
npm run lint      # ESLint
npm run typecheck # TypeScript validation
```

## Structure

```text
app/       App Router routes and global styles
lib/       Shared browser/API utilities
public/    Static assets
```
