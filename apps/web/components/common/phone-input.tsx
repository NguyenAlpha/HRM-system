"use client"

import type { ComponentProps } from "react"

import { Input } from "@/components/ui/input"
import { sanitizePhone } from "@/lib/phone"

/** A phone field that only takes digits and a leading +; works with useState and react-hook-form alike. */
export function PhoneInput({ onChange, ...props }: ComponentProps<typeof Input>) {
  return (
    <Input type="tel" inputMode="tel" {...props}
      onChange={(event) => {
        event.target.value = sanitizePhone(event.target.value)
        onChange?.(event)
      }} />
  )
}
