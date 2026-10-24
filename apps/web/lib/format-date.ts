/**
 * How dates read across the app: 24/10/2026, 24/10/2026 08:05 and 24/10/2026 → 31/10/2026. The API
 * and the date inputs keep ISO (2026-10-24); only what is shown goes through these.
 */

const DATE_TIME_PARTS = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Ho_Chi_Minh",
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
})

/** A YYYY-MM-DD date as dd/MM/yyyy; "—" when there is none. */
export function formatDate(value: string | null | undefined): string {
  if (!value) return "—"
  const [year, month, day] = value.slice(0, 10).split("-")
  return year && month && day ? `${day}/${month}/${year}` : value
}

/** An instant as dd/MM/yyyy HH:mm in Vietnam time; "—" when there is none. */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—"
  const parts = Object.fromEntries(DATE_TIME_PARTS.formatToParts(new Date(value)).map((part) => [part.type, part.value]))
  return `${parts.day}/${parts.month}/${parts.year} ${parts.hour}:${parts.minute}`
}

/** A date range; an open end reads `openEnd` ("nay" unless told otherwise). */
export function formatDateRange(from: string, to: string | null | undefined, openEnd = "nay"): string {
  return `${formatDate(from)} → ${to ? formatDate(to) : openEnd}`
}
