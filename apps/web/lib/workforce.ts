import type { components } from "@/lib/api-schema"

// DTO types come from the API's OpenAPI spec; run `npm run gen:api` after changing them.
type Schemas = components["schemas"]

export type AttendanceRecord = Schemas["AttendanceRecordResponse"]
export type CompanyHoliday = Schemas["CompanyHolidayResponse"]
export type LeaveRequest = Schemas["LeaveRequestView"]
export type LeaveBalance = Schemas["BalanceView"]
export type LeaveCarryOver = Schemas["CarryOverView"]
export type PayrollPeriod = Schemas["PayrollPeriodResponse"]
export type Payslip = Schemas["PayslipResponse"]
export type SalaryHistory = Schemas["SalaryHistoryView"]
export type PayrollProfile = Schemas["Profile"]
export type TaxDependent = Schemas["Dependent"]

export type LeaveType = LeaveRequest["leaveType"]
export type LeaveSalaryTreatment = LeaveRequest["salaryTreatment"]

/** Request bodies, keyed by schema name, for `satisfies` checks at each call site. */
export type WorkforceBody = Pick<Schemas,
  | "CheckInRequest" | "CheckOutRequest" | "AdjustAttendanceRequest" | "ApproveOvertimeRequest"
  | "CompanyHolidayRequest" | "CreateLeaveCommand" | "ReviewBody"
  | "AdjustLeaveEntitlementRequest" | "SetCarriedOverLeaveRequest"
  | "SetSalaryCommand" | "ProfileCommand" | "DependentCommand" | "EndDependentCommand"
  | "CreatePayrollPeriodRequest" | "PayrollActionRequest"
>
