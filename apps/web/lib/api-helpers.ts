"use client"

import { AuthApiError } from "@/lib/auth/types"
import { RATE_LIMIT_FALLBACK_MESSAGE } from "@/lib/rate-limit"

const FALLBACK_MESSAGE = "Không thể hoàn tất thao tác. Vui lòng thử lại."

/**
 * What users read for each API error code. The API's own messages are English and technical,
 * so they are never shown; Vietnamese ones (validation, the BFF) are shown as they are.
 */
const ERROR_MESSAGES: Record<string, string> = {
  USERNAME_TAKEN: "Tên đăng nhập đã được sử dụng.",
  EMAIL_TAKEN: "Email đã được sử dụng.",
  INVALID_CREDENTIALS: "Tên đăng nhập hoặc mật khẩu không đúng.",
  ACCOUNT_PENDING: "Tài khoản chưa được kích hoạt. Hãy dùng link kích hoạt đã được gửi.",
  ACCOUNT_LOCKED: "Tài khoản đang bị khóa. Vui lòng liên hệ quản trị viên.",
  ACCOUNT_DISABLED: "Tài khoản đã bị vô hiệu hóa.",
  ACCOUNT_NOT_FOUND: "Không tìm thấy tài khoản.",
  EMPLOYEE_ACCOUNT_EXISTS: "Nhân viên này đã có tài khoản.",
  ACCOUNT_PROVISIONING_NOT_ALLOWED: "Không thể cấp tài khoản cho nhân viên này.",
  ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED: "Không thể đổi trạng thái tài khoản như vậy.",
  UNAUTHORIZED: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
  REFRESH_TOKEN_INVALID: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
  REFRESH_TOKEN_EXPIRED: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
  ACTIVATION_TOKEN_INVALID: "Mã kích hoạt không hợp lệ hoặc đã được sử dụng.",
  ACTIVATION_TOKEN_EXPIRED: "Mã kích hoạt đã hết hạn. Hãy đề nghị cấp lại.",
  ACCOUNT_ACTIVATION_NOT_ALLOWED: "Tài khoản này không thể kích hoạt.",
  EMPLOYEE_NOT_FOUND: "Không tìm thấy nhân viên.",
  EMPLOYEE_ASSIGNMENT_NOT_FOUND: "Không tìm thấy phân công.",
  EMPLOYEE_CODE_TAKEN: "Mã nhân viên đã tồn tại.",
  EMPLOYMENT_STATUS_TRANSITION_NOT_ALLOWED: "Không thể đổi trạng thái làm việc như vậy.",
  ORGANIZATION_UNIT_NOT_FOUND: "Không tìm thấy đơn vị.",
  JOB_POSITION_NOT_FOUND: "Không tìm thấy chức danh.",
  LOCATION_NOT_FOUND: "Không tìm thấy địa điểm.",
  ORGANIZATION_UNIT_CODE_TAKEN: "Mã đơn vị đã tồn tại.",
  WORK_LOCATION_CODE_TAKEN: "Mã địa điểm đã tồn tại.",
  JOB_POSITION_CODE_TAKEN: "Mã chức danh đã tồn tại.",
  WORK_SHIFT_NOT_FOUND: "Không tìm thấy ca làm việc.",
  WORK_SHIFT_CODE_TAKEN: "Mã ca làm việc đã tồn tại.",
  WORK_SHIFT_SCHEDULE_INVALID: "Giờ của ca làm việc không hợp lệ.",
  ORGANIZATION_HIERARCHY_INVALID: "Cấp cha không hợp lệ cho mục này.",
  ORGANIZATION_RESOURCE_IN_USE: "Mục này đang được sử dụng nên không thể thay đổi.",
  COMPANY_OWNER_ALREADY_EXISTS: "Doanh nghiệp đã có Company Owner.",
  COMPANY_LOGO_INVALID: "Logo phải là ảnh PNG, JPG hoặc WebP, tối đa 512 KB.",
  REQUEST_NOT_FOUND: "Không tìm thấy đơn.",
  REQUEST_ALREADY_PROCESSED: "Đơn này đã được xử lý.",
  APPROVER_NOT_FOUND: "Chưa xác định được người duyệt cho đơn này.",
  INSUFFICIENT_LEAVE_BALANCE: "Số ngày phép còn lại không đủ.",
  LEAVE_OUTSIDE_WORK_SCHEDULE: "Khoảng thời gian này không có giờ làm theo ca, không cần tạo đơn nghỉ.",
  AFTER_EMPLOYMENT_END: "Ngày hiệu lực không được sau ngày làm việc cuối của nhân sự.",
  ATTENDANCE_NOT_FOUND: "Không tìm thấy dữ liệu chấm công.",
  PAYROLL_PERIOD_NOT_FOUND: "Không tìm thấy kỳ lương.",
  PAYROLL_PERIOD_LOCKED: "Kỳ lương đã duyệt hoặc khóa nên không thể thay đổi.",
  PAYSLIP_NOT_FOUND: "Không tìm thấy phiếu lương.",
  ROLE_NOT_FOUND: "Không tìm thấy vai trò.",
  ROLE_ASSIGNMENT_NOT_FOUND: "Không tìm thấy lần gán vai trò.",
  ROLE_ASSIGNMENT_EXISTS: "Tài khoản đã có vai trò này trong khoảng thời gian đó.",
  ROLE_ASSIGNMENT_NOT_ALLOWED: "Không được phép gán vai trò này.",
  ROLE_ASSIGNMENT_REQUEST_NOT_FOUND: "Không tìm thấy đề xuất.",
  ROLE_ASSIGNMENT_REQUEST_EXISTS: "Đã có đề xuất tương tự đang chờ duyệt.",
  ROLE_ASSIGNMENT_REQUEST_ALREADY_PROCESSED: "Đề xuất này đã được xử lý.",
  PERMISSION_OVERRIDE_NOT_FOUND: "Không tìm thấy ngoại lệ quyền.",
  PERMISSION_OVERRIDE_EXISTS: "Ngoại lệ quyền này đã tồn tại.",
  PERMISSION_OVERRIDE_NOT_ALLOWED: "Không được phép tạo ngoại lệ quyền này.",
  PERMISSION_NOT_FOUND: "Không tìm thấy quyền.",
  FORBIDDEN: "Bạn không có quyền thực hiện thao tác này.",
  VALIDATION_ERROR: "Dữ liệu nhập chưa hợp lệ. Vui lòng kiểm tra lại.",
  RESOURCE_NOT_FOUND: "Không tìm thấy dữ liệu.",
  METHOD_NOT_ALLOWED: FALLBACK_MESSAGE,
  CONFLICT: "Dữ liệu trùng hoặc mâu thuẫn với dữ liệu đã có.",
  CONCURRENT_MODIFICATION: "Dữ liệu vừa được người khác thay đổi. Vui lòng tải lại trang.",
  // The BFF normally replaces the message with one that says how long to wait.
  RATE_LIMIT_EXCEEDED: RATE_LIMIT_FALLBACK_MESSAGE,
  RATE_LIMIT_POLICY_NOT_FOUND: "Không tìm thấy mức giới hạn này.",
  RATE_LIMIT_POLICY_REQUIRED: "Mức chống dò mật khẩu không được tắt.",
  INTERNAL_ERROR: "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.",
}

/** Names of request fields as users know them; the API reports the field path, e.g. `employee.workEmail`. */
const FIELD_LABELS: Record<string, string> = {
  username: "Tên đăng nhập",
  usernameOrEmail: "Tên đăng nhập hoặc email",
  password: "Mật khẩu",
  newPassword: "Mật khẩu mới",
  currentPassword: "Mật khẩu hiện tại",
  fullName: "Họ tên",
  name: "Tên",
  title: "Tên chức danh",
  code: "Mã",
  roleCode: "Vai trò",
  description: "Mô tả",
  workEmail: "Email công việc",
  personalEmail: "Email cá nhân",
  email: "Email",
  phone: "Điện thoại",
  address: "Địa chỉ",
  taxCode: "Mã số thuế",
  nationalId: "Số CCCD",
  bankName: "Ngân hàng",
  bankAccountNumber: "Số tài khoản",
  bankAccountHolder: "Chủ tài khoản",
  major: "Chuyên ngành",
  institution: "Nơi đào tạo",
  graduationYear: "Năm tốt nghiệp",
  dateOfBirth: "Ngày sinh",
  hireDate: "Ngày vào làm",
  effectiveFrom: "Ngày hiệu lực",
  effectiveTo: "Ngày kết thúc",
  startDate: "Ngày bắt đầu",
  endDate: "Ngày kết thúc",
  reason: "Lý do",
  note: "Ghi chú",
  comment: "Ghi chú",
  ownershipReason: "Lý do cấp quyền sở hữu",
  directorAppointmentReason: "Lý do bổ nhiệm Director",
  maxRequests: "Số request tối đa",
  windowSeconds: "Khoảng thời gian",
}

const VIETNAMESE = /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i

/** A message users can read: never an error code or the API's English text. */
export function friendlyErrorMessage(code: string, message: string, field: string | null): string {
  const label = field ? FIELD_LABELS[field.split(".").pop() ?? field] : undefined
  if (VIETNAMESE.test(message)) {
    // Validation messages are the rule alone ("phải có từ 0 đến 30 ký tự"), so name the field.
    return code === "VALIDATION_ERROR" && label ? `${label} ${message}.` : message
  }
  if (code === "VALIDATION_ERROR" && label) return `${label} chưa hợp lệ. Vui lòng kiểm tra lại.`
  return ERROR_MESSAGES[code] ?? FALLBACK_MESSAGE
}

export function apiErrorMessage(error: unknown): string {
  if (error instanceof AuthApiError) return friendlyErrorMessage(error.code, error.message, error.field)
  // Errors thrown by the web itself already carry a Vietnamese message.
  return error instanceof Error && VIETNAMESE.test(error.message) ? error.message : FALLBACK_MESSAGE
}

export function isPortalSessionExpired(error: unknown): boolean {
  return error instanceof AuthApiError
    && (error.status === 401 || error.code === "ADMIN_PORTAL_REQUIRED" || error.code === "ADMIN_ACCESS_REQUIRED")
}
