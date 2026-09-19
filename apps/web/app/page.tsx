import Link from "next/link"

export default function HomePage() {
  return (
    <main className="page-shell">
      <section className="hero" aria-labelledby="page-title">
        <p className="eyebrow">HRM PLATFORM</p>
        <h1 id="page-title">Human resource management, ready to build.</h1>
        <p className="description">
          Next.js App Router frontend is connected as a standalone application alongside the Spring Boot API.
        </p>
        <div className="actions">
          <Link className="button primary" href="/">Start developing</Link>
          <a className="button secondary" href="https://nextjs.org/docs" target="_blank" rel="noreferrer">
            Next.js documentation
          </a>
        </div>
      </section>
      <section className="next-steps" aria-label="Next steps">
        <article>
          <span>01</span>
          <h2>Build screens</h2>
          <p>Add routes under <code>app/</code> for authentication, employees, and attendance.</p>
        </article>
        <article>
          <span>02</span>
          <h2>Connect the API</h2>
          <p>Set <code>NEXT_PUBLIC_API_URL</code> in <code>.env.local</code> before calling Spring Boot.</p>
        </article>
        <article>
          <span>03</span>
          <h2>Run locally</h2>
          <p>Install dependencies, then run <code>npm run dev</code>.</p>
        </article>
      </section>
    </main>
  )
}
