"use client"

import { authorizedRequest } from "@/lib/auth/client"
import { AuthApiError, type Portal } from "@/lib/auth/types"

export const EMPLOYMENT_STATUSES = ["PROBATION", "ACTIVE", "RESIGNED", "TERMINATED", "RETIRED"] as const
export type EmploymentStatus = typeof EMPLOYMENT_STATUSES[number]

export const GENDERS = ["MALE", "FEMALE", "OTHER", "UNDISCLOSED"] as const
export type Gender = typeof GENDERS[number]

export const EDUCATION_LEVELS = ["HIGH_SCHOOL", "COLLEGE", "BACHELOR", "MASTER", "DOCTORATE"] as const
export type EducationLevel = typeof EDUCATION_LEVELS[number]

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

export interface EmployeeAccountSummary {
  id: number
  username: string
  email: string
  status: string
}

export interface EmployeeSummary {
  id: number
  employeeCode: string
  fullName: string
  workEmail: string | null
  phone: string | null
  hireDate: string
  employmentStatus: EmploymentStatus
  terminationDate: string | null
  currentAssignment: EmployeeAssignment | null
  account: EmployeeAccountSummary | null
}

export interface EmployeeAssignment {
  id: number
  employeeId: number
  organizationUnitId: number
  organizationUnitName: string
  workLocationId: number
  workLocationName: string
  positionId: number
  positionTitle: string
  shiftId: number | null
  shiftName: string | null
  managerEmployeeId: number | null
  managerEmployeeName: string | null
  employmentType: "FULL_TIME" | "PART_TIME" | "TEMPORARY"
  effectiveFrom: string
  effectiveTo: string | null
  isPrimary: boolean
}

export interface EmployeeDetail extends EmployeeSummary {
  dateOfBirth: string | null
  gender: Gender | null
  highestEducationLevel: EducationLevel | null
  major: string | null
  institution: string | null
  graduationYear: number | null
}

export const EMPLOYMENT_TYPES = ["FULL_TIME", "PART_TIME", "TEMPORARY"] as const
export type EmploymentType = typeof EMPLOYMENT_TYPES[number]

export const EMPLOYMENT_TYPE_LABELS: Record<EmploymentType, string> = {
  FULL_TIME: "Toàn thời gian",
  PART_TIME: "Bán thời gian",
  TEMPORARY: "Thời vụ",
}

export interface CreateEmployeeInput {
  employee: {
    fullName: string
    dateOfBirth: string
    gender: Gender | null
    highestEducationLevel: EducationLevel | null
    major: string | null
    institution: string | null
    graduationYear: number | null
    workEmail: string | null
    phone: string | null
    hireDate: string
  }
  initialAssignment: {
    organizationUnitId: number
    workLocationId: number
    positionId: number
    shiftId: number | null
    managerEmployeeId: number | null
    employmentType: EmploymentType
    effectiveFrom: string
    reason: string | null
  }
}

export interface EmployeeCreationResponse {
  employee: EmployeeDetail
  initialAssignment: EmployeeAssignment
}

export interface Page<T> {
  content: T[]
  number: number
  totalElements: number
  totalPages: number
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

export function employeeErrorMessage(error: unknown): string {
  if (error instanceof AuthApiError) {
    return `${error.code}: ${error.message}${error.field ? ` (${error.field})` : ""}`
  }
  return error instanceof Error ? error.message : "Không thể hoàn tất thao tác. Vui lòng thử lại."
}

export function isSessionExpired(error: unknown): boolean {
  return error instanceof AuthApiError && (error.status === 401 || error.code === "ADMIN_PORTAL_REQUIRED")
}
