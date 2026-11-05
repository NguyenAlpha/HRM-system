/**
 * Mirrors the API's ValidationPatterns.PERSON_NAME: letters of any language (Vietnamese marks included) with spaces
 * or . ' - between words, so "Nguyễn Văn An" and "O'Brien" pass but "33333333333" or "An 2" do not. Test the trimmed
 * value.
 */
export const PERSON_NAME_PATTERN = /^[\p{L}\p{M}]+(?:[ .'-]+[\p{L}\p{M}]+)*\.?$/u
export const PERSON_NAME_MESSAGE = "Chỉ gồm chữ cái, khoảng trắng và các dấu . ' -"

/** The message for an invalid name, "" when it is fine or empty; for `setCustomValidity` on a plain input. */
export function personNameError(value: string): string {
  const name = value.trim()
  return name === "" || PERSON_NAME_PATTERN.test(name) ? "" : PERSON_NAME_MESSAGE
}
