"use client"

import type { components } from "@/lib/api-schema"
import { authorizedRequest } from "@/lib/auth/client"
import { AuthApiError, type Portal } from "@/lib/auth/types"

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

export function employeeRequest<T>(portal: Portal, path: string, init?: RequestInit): Promise<T> {
  return authorizedRequest<T>(portal, `/employees${path}`, { ...init, cache: "no-store" })
}

export function employeeMutation<T>(portal: Portal, path: string, method: "POST" | "PUT", body?: unknown): Promise<T> {
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

export function softDeleteEmployee(employeeId: number, deletionReason: string): Promise<null> {
  return employeeMutation<null>("hrm", `/${employeeId}/soft-delete`, "POST", { deletionReason })
}

export function employeeErrorMessage(error: unknown): string {
  if (error instanceof AuthApiError) {
    return `${error.code}: ${error.message}${error.field ? ` (${error.field})` : ""}`
  }
  return error instanceof Error ? error.message : "Không thể hoàn tất thao tác. Vui lòng thử lại."
}

export function isSessionExpired(error: unknown): boolean {
  return error instanceof AuthApiError && (error.status === 401 || error.code === "ADMIN_PORTAL_REQUIRED")
}
