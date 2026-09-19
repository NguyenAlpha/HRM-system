# HRM

Human resource management application with a Spring Boot API and a Next.js web application.

## Projects

| Directory | Stack | Run locally |
| --- | --- | --- |
| `api` | Spring Boot / Maven | `./mvnw spring-boot:run` |
| `apps/web` | Next.js 16, App Router, TypeScript, Tailwind CSS | `npm install && npm run dev` |

## Development

Start the Spring Boot API, then create `apps/web/.env.local` from `apps/web/.env.example`. The web app uses `http://localhost:8080` as its default API URL.
