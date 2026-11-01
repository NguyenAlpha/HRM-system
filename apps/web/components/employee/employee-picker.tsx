"use client"

import { useState } from "react"
import { useQuery } from "@tanstack/react-query"

import {
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
} from "@/components/ui/combobox"
import type { Portal } from "@/lib/auth/types"
import { employeeRequest, type EmployeeSummary } from "@/lib/employee"
import type { Page } from "@/lib/pagination"

/** Cache key of the employee directory (see docs/CACHING.md). */
export const EMPLOYEE_DIRECTORY_KEY = ["employee-directory"] as const

// Spring's largest page by default: the directory holds everyone in the caller's scope up to this many.
const DIRECTORY_SIZE = 2000
// The list shows this many matches at most; typing narrows it.
const SHOWN_MATCHES = 50

/**
 * Every employee the caller may list, shared by all pickers: searching filters it in memory, so typing
 * never calls the API. Employees and their accounts change in many places, so rather than invalidating
 * it from each, a picker that starts using it shows the cached copy at once and refreshes it in the
 * background. With `enabled` false it only reads what is already cached.
 */
export function useEmployeeDirectory(portal: Portal, enabled = true) {
  return useQuery({
    queryKey: [...EMPLOYEE_DIRECTORY_KEY, portal],
    queryFn: () => employeeRequest<Page<EmployeeSummary>>(portal, `?page=0&size=${DIRECTORY_SIZE}&sort=employeeCode,asc`)
      .then((page) => page.content),
    enabled,
  })
}

const defaultLabel = (employee: EmployeeSummary) => `${employee.employeeCode} · ${employee.fullName}`

/** Searchable employee select: type part of a code or name to narrow the list. */
export function EmployeePicker({
  id, portal, value, onChange, filter, label = defaultLabel, placeholder = "Gõ mã hoặc tên nhân sự...", clearable, disabled, invalid,
}: {
  id: string
  portal: Portal
  /** Selected employee id; null for none. */
  value: number | null
  onChange: (employee: EmployeeSummary | null) => void
  /** Which employees may be picked here. */
  filter?: (employee: EmployeeSummary) => boolean
  /** Text shown for an employee, also what typing searches. */
  label?: (employee: EmployeeSummary) => string
  /** Shown while nothing is picked. */
  placeholder?: string
  /** Offers a button that clears the choice, for optional fields. */
  clearable?: boolean
  disabled?: boolean
  /** Marks the field as failing form validation. */
  invalid?: boolean
}) {
  // Fetched only once the user opens the picker (or it already holds a value to name), since many
  // pages show a picker that is seldom used.
  const [opened, setOpened] = useState(false)
  const { data: directory, isLoading, isError } = useEmployeeDirectory(portal, opened || value !== null)
  const candidates = filter ? (directory ?? []).filter(filter) : directory ?? []
  const selected = candidates.find((employee) => employee.id === value) ?? null

  return (
    <Combobox
      items={candidates}
      value={selected}
      onValueChange={onChange}
      itemToStringLabel={label}
      isItemEqualToValue={(item, current) => item.id === current.id}
      limit={SHOWN_MATCHES}
      disabled={disabled}
      onOpenChange={(open) => { if (open) setOpened(true) }}
    >
      <ComboboxInput id={id} className="w-full" disabled={disabled} aria-invalid={invalid}
        onFocus={() => setOpened(true)} showClear={clearable && selected !== null} placeholder={placeholder} />
      <ComboboxContent>
        <ComboboxEmpty>
          {isLoading ? "Đang tải danh sách nhân sự..."
            : isError ? "Không tải được danh sách nhân sự." : "Không tìm thấy nhân sự phù hợp."}
        </ComboboxEmpty>
        <ComboboxList>
          {(employee: EmployeeSummary) => (
            <ComboboxItem key={employee.id} value={employee}>{label(employee)}</ComboboxItem>
          )}
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  )
}
