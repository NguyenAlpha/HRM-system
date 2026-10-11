"use client"

import type { components } from "@/lib/api-schema"
import { authorizedRequest } from "@/lib/auth/client"
import type { EmploymentStatus } from "@/lib/employee"

// DTO types come from the API's OpenAPI spec; run `npm run gen:api` after changing them.
type Schemas = components["schemas"]

export type DistributionItem = Schemas["DistributionItemResponse"]
export type HrWorkforceReport = Schemas["HrWorkforceReportResponse"]
export type PayrollSalaryReport = Schemas["PayrollSalaryReportResponse"]

export interface ReportFilters {
  asOfDate: string
  organizationUnitId: string
  workLocationId: string
  employmentStatus: EmploymentStatus | ""
}

function reportQuery(filters: ReportFilters): string {
  const query = new URLSearchParams({ asOfDate: filters.asOfDate })
  if (filters.organizationUnitId) query.set("organizationUnitId", filters.organizationUnitId)
  if (filters.workLocationId) query.set("workLocationId", filters.workLocationId)
  if (filters.employmentStatus) query.set("employmentStatus", filters.employmentStatus)
  return query.toString()
}

export function getHrWorkforceReport(filters: ReportFilters): Promise<HrWorkforceReport> {
  return authorizedRequest<HrWorkforceReport>("hrm", `/reports/hr?${reportQuery(filters)}`, {
    cache: "no-store",
  })
}

export function getPayrollSalaryReport(filters: ReportFilters): Promise<PayrollSalaryReport> {
  return authorizedRequest<PayrollSalaryReport>("hrm", `/reports/payroll?${reportQuery(filters)}`, {
    cache: "no-store",
  })
}
