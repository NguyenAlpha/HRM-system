"use client"

import { authorizedRequest } from "@/lib/auth/client"
import type { EmploymentStatus } from "@/lib/employee"

export interface DistributionItem {
  key: string
  label: string
  count: number
  percentage: number
}

export interface HrWorkforceReport {
  asOfDate: string
  totalEmployees: number
  averageSeniorityYears: number
  educationDistribution: DistributionItem[]
  seniorityDistribution: DistributionItem[]
  missingEducation: number
}

export interface PayrollSalaryReport {
  asOfDate: string
  currency: string
  totalEmployees: number
  employeesWithBasicSalary: number
  averageBasicSalary: number | null
  minimumBasicSalary: number | null
  maximumBasicSalary: number | null
  salaryDistribution: DistributionItem[]
  missingBasicSalary: number
}

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
