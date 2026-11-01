"use client"

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
 * it from each, a picker that appears shows the cached copy at once and refreshes it in the background.
 */
export function useEmployeeDirectory(portal: Portal) {
  return useQuery({
    queryKey: [...EMPLOYEE_DIRECTORY_KEY, portal],
    queryFn: () => employeeRequest<Page<EmployeeSummary>>(portal, `?page=0&size=${DIRECTORY_SIZE}&sort=employeeCode,asc`)
      .then((page) => page.content),
  })
}

const defaultLabel = (employee: EmployeeSummary) => `${employee.employeeCode} · ${employee.fullName}`

/** Searchable employee select: type part of a code or name to narrow the list. */
export function EmployeePicker({ id, portal, value, onChange, filter, label = defaultLabel, disabled }: {
  id: string
  portal: Portal
  /** Selected employee id; null for none. */
  value: number | null
  onChange: (employee: EmployeeSummary | null) => void
  /** Which employees may be picked here. */
  filter?: (employee: EmployeeSummary) => boolean
  /** Text shown for an employee, also what typing searches. */
  label?: (employee: EmployeeSummary) => string
  disabled?: boolean
}) {
  const { data: directory, isPending } = useEmployeeDirectory(portal)
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
      disabled={disabled || isPending}
    >
      <ComboboxInput id={id} className="w-full" disabled={disabled || isPending}
        placeholder={isPending ? "Đang tải..." : "Gõ mã hoặc tên nhân sự..."} />
      <ComboboxContent>
        <ComboboxEmpty>Không tìm thấy nhân sự phù hợp.</ComboboxEmpty>
        <ComboboxList>
          {(employee: EmployeeSummary) => (
            <ComboboxItem key={employee.id} value={employee}>{label(employee)}</ComboboxItem>
          )}
        </ComboboxList>
      </ComboboxContent>
    </Combobox>
  )
}
