import { redirect } from "next/navigation"

// Internal single-company app: no public landing page. The proxy sends visitors without a session on to /login.
export default function HomePage() {
  redirect("/dashboard")
}
