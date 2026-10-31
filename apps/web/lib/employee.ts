"use client"

import type { components } from "@/lib/api-schema"
import { authorizedRequest } from "@/lib/auth/client"
import type { Portal } from "@/lib/auth/types"

// DTO types come from the API's OpenAPI spec; run `npm run gen:api` after changing them.
type Schemas = components["schemas"]

export type EmployeeAccountSummary = Schemas["EmployeeAccountSummaryResponse"]
export type EmployeeSummary = Schemas["EmployeeSummaryResponse"]
export type EmployeeAssignment = Schemas["EmployeeAssignmentResponse"]
export type EmployeeDetail = Schemas["EmployeeDetailResponse"]
export type EmployeeSensitiveData = Schemas["EmployeeSensitiveResponse"]
export type EmployeeCreationResponse = Schemas["EmployeeCreationResponse"]

export type CreateEmployeeInput = Schemas["CreateEmployeeRequest"]
export type UpdateEmployeeInput = Schemas["UpdateEmployeeRequest"]
export type AssignEmployeeInput = Schemas["AssignEmployeeRequest"]
export type EmployeeSensitiveInput = Schemas["UpdateEmployeeSensitiveRequest"]
export type SelfProfile = Schemas["SelfProfileResponse"]
export type SelfProfileInput = Schemas["UpdateSelfProfileRequest"]

export type EmploymentStatus = EmployeeDetail["employmentStatus"]
export type Gender = NonNullable<EmployeeDetail["gender"]>
export type EducationLevel = NonNullable<EmployeeDetail["highestEducationLevel"]>
export type EmploymentType = EmployeeAssignment["employmentType"]

// The label records below must list every enum value, so a value added in the API fails typecheck.
export const EMPLOYMENT_STATUSES = [
  "PROBATION", "ACTIVE", "RESIGNED", "TERMINATED", "RETIRED",
] as const satisfies readonly EmploymentStatus[]

export const GENDERS = ["MALE", "FEMALE", "OTHER", "UNDISCLOSED"] as const satisfies readonly Gender[]

export const EDUCATION_LEVELS = [
  "HIGH_SCHOOL", "COLLEGE", "BACHELOR", "MASTER", "DOCTORATE",
] as const satisfies readonly EducationLevel[]

export const EMPLOYMENT_STATUS_LABELS: Record<EmploymentStatus, string> = {
  PROBATION: "Thử việc",
  ACTIVE: "Đang làm việc",
  RESIGNED: "Đã nghỉ việc",
  TERMINATED: "Đã chấm dứt",
  RETIRED: "Đã nghỉ hưu",
}

export const GENDER_LABELS: Record<Gender, string> = {
  MALE: "Nam",
  FEMALE: "Nữ",
  OTHER: "Khác",
  UNDISCLOSED: "Không tiết lộ",
}

export const EDUCATION_LEVEL_LABELS: Record<EducationLevel, string> = {
  HIGH_SCHOOL: "Trung học phổ thông",
  COLLEGE: "Cao đẳng",
  BACHELOR: "Đại học",
  MASTER: "Thạc sĩ",
  DOCTORATE: "Tiến sĩ",
}

export const EMPLOYMENT_END_STATUSES = ["RESIGNED", "TERMINATED", "RETIRED"] as const satisfies readonly EmploymentStatus[]

export type EmploymentEndStatus = (typeof EMPLOYMENT_END_STATUSES)[number]

/** Action labels for ending employment, as opposed to the resulting status labels above. */
export const EMPLOYMENT_END_LABELS: Record<EmploymentEndStatus, string> = {
  RESIGNED: "Nghỉ việc",
  TERMINATED: "Chấm dứt hợp đồng",
  RETIRED: "Nghỉ hưu",
}

export function isEmploymentEnded(status: EmploymentStatus): boolean {
  return (EMPLOYMENT_END_STATUSES as readonly EmploymentStatus[]).includes(status)
}

export const EMPLOYMENT_TYPES = ["FULL_TIME", "PART_TIME", "TEMPORARY"] as const satisfies readonly EmploymentType[]

export const EMPLOYMENT_TYPE_LABELS: Record<EmploymentType, string> = {
  FULL_TIME: "Toàn thời gian",
  PART_TIME: "Bán thời gian",
  TEMPORARY: "Thời vụ",
}

/** Filters of the employee list; an empty string means "no filter". */
export interface EmployeeFilters {
  keyword: string
  employmentStatus: "" | EmploymentStatus
  organizationUnitId: string
  positionId: string
}

/** Query string (with leading `?`) for one page of the filtered employee list. */
export function employeeListQuery(page: number, size: number, filters: EmployeeFilters, sort: string): string {
  const params = new URLSearchParams({ page: String(page), size: String(size), sort })
  if (filters.keyword.trim()) params.set("keyword", filters.keyword.trim())
  if (filters.employmentStatus) params.set("employmentStatus", filters.employmentStatus)
  if (filters.organizationUnitId) params.set("organizationUnitId", filters.organizationUnitId)
  if (filters.positionId) params.set("positionId", filters.positionId)
  return `?${params}`
}

export function employeeRequest<T>(portal: Portal, path: string, init?: RequestInit): Promise<T> {
  return authorizedRequest<T>(portal, `/employees${path}`, { ...init, cache: "no-store" })
}

/**
 * The employee whose code is exactly `code` (any letter case), or null. Searches the list, so it
 * needs `employee.list.read` and only finds employees in the caller's scope.
 */
export async function findEmployeeByCode(portal: Portal, code: string): Promise<EmployeeSummary | null> {
  const wanted = code.trim().toLowerCase()
  const page = await employeeRequest<{ content: EmployeeSummary[] }>(portal, employeeListQuery(0, 20,
    { keyword: wanted, employmentStatus: "", organizationUnitId: "", positionId: "" }, "id,asc"))
  return page.content.find((employee) => employee.employeeCode.toLowerCase() === wanted) ?? null
}

export function employeeMutation<T>(portal: Portal, path: string, method: "POST" | "PUT" | "DELETE", body?: unknown): Promise<T> {
  return employeeRequest<T>(portal, path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export function createEmployee(input: CreateEmployeeInput): Promise<EmployeeCreationResponse> {
  return employeeMutation<EmployeeCreationResponse>("hrm", "", "POST", input)
}

export function getEmployeeSensitiveData(employeeId: number): Promise<EmployeeSensitiveData> {
  return employeeRequest<EmployeeSensitiveData>("hrm", `/${employeeId}/sensitive`)
}

export function updateEmployeeSensitiveData(
  employeeId: number,
  input: EmployeeSensitiveInput,
): Promise<EmployeeSensitiveData> {
  return employeeMutation<EmployeeSensitiveData>("hrm", `/${employeeId}/sensitive`, "PUT", input)
}

export function endEmployeeEmployment(
  employeeId: number,
  endStatus: EmploymentEndStatus,
  terminationDate: string,
  terminationReason: string,
): Promise<EmployeeDetail> {
  return employeeMutation<EmployeeDetail>("hrm", `/${employeeId}/employment-end`, "POST", {
    endStatus,
    terminationDate,
    terminationReason,
  } satisfies Schemas["EndEmploymentRequest"])
}

/** Cancels an employment end that still waits for its last working day. */
export function cancelScheduledEmploymentEnd(employeeId: number): Promise<EmployeeDetail> {
  return employeeMutation<EmployeeDetail>("hrm", `/${employeeId}/employment-end`, "DELETE")
}

export function softDeleteEmployee(employeeId: number, deletionReason: string): Promise<null> {
  return employeeMutation<null>("hrm", `/${employeeId}/soft-delete`, "POST", { deletionReason })
}
