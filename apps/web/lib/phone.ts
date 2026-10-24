import { z } from "zod"

/** Mirrors the API's ValidationPatterns.PHONE: an optional leading + and 8 to 11 digits. */
const PHONE_PATTERN = /^\+?\d{8,11}$/
const PHONE_MESSAGE = "Chỉ gồm 8 đến 11 chữ số, có thể bắt đầu bằng dấu +"
/** The API's @Size(max = 12). */
const PHONE_MAX_LENGTH = 12

/** Optional phone field of a zod form; an empty value is allowed and sent as null. */
export const phoneSchema = z.string().trim()
  .refine((value) => value === "" || PHONE_PATTERN.test(value), PHONE_MESSAGE)
  .optional()

/**
 * Keeps digits and a leading + only, then cuts to the API's length. Cutting after filtering means
 * a pasted "+84 912 345 678" keeps all its digits, which an input maxLength would not.
 */
export function sanitizePhone(value: string): string {
  return value.replace(/[^\d+]/g, "").replace(/(?!^)\+/g, "").slice(0, PHONE_MAX_LENGTH)
}
