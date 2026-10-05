const MINIMUM_AGE = 17
const BUSINESS_TIME_ZONE = "Asia/Ho_Chi_Minh"

export function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate()
}

export function maximumEmployeeBirthDate(): string {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: BUSINESS_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date())
  const part = (type: string) => Number(parts.find((item) => item.type === type)?.value)
  const year = part("year") - MINIMUM_AGE
  const month = part("month")
  const day = Math.min(part("day"), daysInMonth(year, month))
  return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`
}

export function isEligibleEmployeeBirthDate(value: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return false
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  return month >= 1 && month <= 12 && day >= 1
    && day <= daysInMonth(year, month) && value <= maximumEmployeeBirthDate()
}
