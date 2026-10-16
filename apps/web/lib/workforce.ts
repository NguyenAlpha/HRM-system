import type { components } from "@/lib/api-schema"

// DTO types come from the API's OpenAPI spec; run `npm run gen:api` after changing them.
type Schemas = components["schemas"]

export type AttendanceRecord = Schemas["AttendanceRecordResponse"]
export type CompanyHoliday = Schemas["CompanyHolidayResponse"]
export type OvertimeApprovalItem = Schemas["OvertimeApprovalItemResponse"]
export type TimesheetRow = Schemas["TimesheetRowResponse"]
export type LeaveRequest = Schemas["LeaveRequestView"]
export type LeaveBalance = Schemas["BalanceView"]
export type ResignationRequest = Schemas["ResignationRequestView"]
export type PayrollPeriod = Schemas["PayrollPeriodResponse"]
export type Payslip = Schemas["PayslipResponse"]
export type PayrollAdjustment = Schemas["PayrollAdjustmentResponse"]
export type PayrollAdjustmentType = PayrollAdjustment["type"]
export type SalaryHistory = Schemas["SalaryHistoryView"]
export type PayrollProfile = Schemas["Profile"]
export type TaxDependent = Schemas["Dependent"]
export type PositionAllowanceSummary = Schemas["PositionAllowanceSummary"]
export type PositionAllowance = Schemas["PositionAllowanceView"]
export type SeniorityAllowance = Schemas["SeniorityAllowanceView"]
export type CompanyProfile = Schemas["CompanyProfileResponse"]

export type AttendanceStatus = AttendanceRecord["status"]
export type LeaveType = LeaveRequest["leaveType"]
export type LeaveSalaryTreatment = LeaveRequest["salaryTreatment"]
export type LeaveStatus = LeaveRequest["status"]
export type ResignationStatus = ResignationRequest["status"]
export type PayrollPeriodStatus = PayrollPeriod["status"]

/** Request bodies, keyed by schema name, for `satisfies` checks at each call site. */
export type WorkforceBody = Pick<Schemas,
  | "AdjustAttendanceRequest" | "RecordPunchesRequest" | "ApproveOvertimeRequest"
  | "CompanyHolidayRequest" | "CreateLeaveCommand" | "ReviewBody"
  | "CreateResignationRequestRequest" | "ApproveResignationBody" | "RejectResignationBody"
  | "SetSalaryCommand" | "ProfileCommand" | "DependentCommand" | "EndDependentCommand"
  | "CreatePayrollPeriodRequest" | "PayrollActionRequest" | "CreatePayrollAdjustmentRequest"
  | "SetPositionAllowanceCommand" | "SetSeniorityAllowanceCommand" | "CompanyProfileRequest"
>

// The label records below must list every enum value, so a value added in the API fails typecheck.
export const ATTENDANCE_STATUS_LABELS: Record<AttendanceStatus, string> = {
  PRESENT: "Có mặt",
  PAID_LEAVE: "Nghỉ có lương",
  UNPAID_LEAVE: "Nghỉ không lương",
  MATERNITY_LEAVE: "Nghỉ thai sản",
  SICK_LEAVE: "Nghỉ ốm",
  UNAUTHORIZED_ABSENCE: "Vắng không phép",
  HOLIDAY: "Ngày lễ",
  MISSING_PUNCH: "Thiếu chấm công",
}

export const LEAVE_TYPES = ["ANNUAL", "SICK", "MATERNITY", "UNPAID", "OTHER"] as const satisfies readonly LeaveType[]

export const LEAVE_TYPE_LABELS: Record<LeaveType, string> = {
  ANNUAL: "Phép năm",
  SICK: "Nghỉ ốm",
  MATERNITY: "Thai sản",
  UNPAID: "Không lương",
  OTHER: "Khác",
}

export const LEAVE_SALARY_TREATMENTS = [
  "EMPLOYER_PAID", "SOCIAL_INSURANCE", "UNPAID",
] as const satisfies readonly LeaveSalaryTreatment[]

export const LEAVE_SALARY_TREATMENT_LABELS: Record<LeaveSalaryTreatment, string> = {
  EMPLOYER_PAID: "Công ty trả lương",
  SOCIAL_INSURANCE: "Bảo hiểm xã hội chi trả",
  UNPAID: "Không lương",
}

// Mirrors LeaveType.defaultSalaryTreatment() on the server, which rejects any other value
// unless the caller holds request.manage.
export const DERIVED_SALARY_TREATMENT: Record<LeaveType, LeaveSalaryTreatment> = {
  ANNUAL: "EMPLOYER_PAID",
  SICK: "SOCIAL_INSURANCE",
  MATERNITY: "SOCIAL_INSURANCE",
  UNPAID: "UNPAID",
  OTHER: "UNPAID",
}

export const LEAVE_STATUS_LABELS: Record<LeaveStatus, string> = {
  DRAFT: "Nháp",
  PENDING: "Chờ duyệt",
  APPROVED: "Đã duyệt",
  REJECTED: "Từ chối",
  CANCELLED: "Đã hủy",
}

export const RESIGNATION_STATUS_LABELS: Record<ResignationStatus, string> = {
  PENDING: "Chờ duyệt",
  APPROVED: "Đã duyệt",
  REJECTED: "Từ chối",
  CANCELLED: "Đã rút",
}

export const PAYROLL_PERIOD_STATUS_LABELS: Record<PayrollPeriodStatus, string> = {
  DRAFT: "Nháp",
  CALCULATED: "Đã tính",
  APPROVED: "Đã duyệt",
  PAID: "Đã trả",
  LOCKED: "Đã khóa",
  CANCELLED: "Đã hủy",
}
