import { Bell, CircleCheck, Printer, Search } from "lucide-react"

import { cn } from "@/lib/utils"

// Illustrations of the product drawn in markup; every name and figure is sample data.

function WindowFrame({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        "overflow-hidden rounded-2xl border border-[#dfe5e1] bg-white shadow-[0_30px_80px_-20px_rgb(12_60_42/0.35)]",
        className,
      )}
    >
      <div className="flex items-center gap-2 border-b border-[#edf0ee] bg-[#f7f9f8] px-4 py-2.5">
        <span className="size-2.5 rounded-full bg-[#f2a6a0]" />
        <span className="size-2.5 rounded-full bg-[#f3d38a]" />
        <span className="size-2.5 rounded-full bg-[#9fd8b8]" />
        <span className="ml-3 hidden h-5 flex-1 max-w-64 items-center rounded-md bg-white px-2 text-[10px] text-[#8a968f] ring-1 ring-[#e6ebe8] sm:flex">
          hrm.company.vn/dashboard
        </span>
      </div>
      {children}
    </div>
  )
}

const HEADCOUNT = [
  { unit: "Kinh doanh", value: 34 },
  { unit: "Kỹ thuật", value: 46 },
  { unit: "Vận hành", value: 22 },
  { unit: "Kế toán", value: 11 },
  { unit: "Nhân sự", value: 8 },
]

const REQUESTS = [
  { name: "Trần Minh Anh", type: "Nghỉ phép năm · 2 ngày", status: "Chờ duyệt", tone: "amber" },
  { name: "Lê Quốc Huy", type: "Nghỉ ốm · 1 ngày", status: "Đã duyệt", tone: "green" },
  { name: "Phạm Thu Hà", type: "Nghỉ phép năm · 0,5 ngày", status: "Đã duyệt", tone: "green" },
]

export function DashboardMockup() {
  const max = Math.max(...HEADCOUNT.map((item) => item.value))
  return (
    <WindowFrame>
      <div className="grid grid-cols-[9.5rem_1fr] text-[11px] max-sm:grid-cols-1">
        <aside className="border-r border-[#edf0ee] bg-[#f9fbfa] p-3 max-sm:hidden">
          <div className="mb-4 flex items-center gap-2 px-1">
            <span className="grid size-6 place-items-center rounded-md bg-[#147a55] text-[10px] font-extrabold text-white">H</span>
            <strong className="tracking-[0.08em]">HRM</strong>
          </div>
          {["Tổng quan", "Nhân sự", "Chấm công", "Nghỉ phép", "Bảng lương", "Báo cáo"].map((label, index) => (
            <div
              key={label}
              className={cn(
                "mb-0.5 rounded-md px-2 py-1.5 text-[#4f5d56]",
                index === 0 && "bg-[#e7f5ee] font-semibold text-[#0d5f42]",
              )}
            >
              {label}
            </div>
          ))}
        </aside>

        <div className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-[9px] font-extrabold tracking-[0.14em] text-[#147a55]">TỔNG QUAN</p>
              <p className="mt-0.5 text-sm font-semibold">Chào buổi sáng, Ngọc Lan</p>
            </div>
            <div className="flex gap-1.5 text-[#8a968f]">
              <span className="grid size-6 place-items-center rounded-md ring-1 ring-[#e6ebe8]"><Search className="size-3" /></span>
              <span className="grid size-6 place-items-center rounded-md ring-1 ring-[#e6ebe8]"><Bell className="size-3" /></span>
            </div>
          </div>

          <div className="mt-3 grid grid-cols-3 gap-2">
            {[
              ["Đang làm việc", "121", "+4 tháng này"],
              ["Đi làm hôm nay", "96%", "116 / 121"],
              ["Đơn chờ duyệt", "7", "3 nghỉ phép mới"],
            ].map(([label, value, note]) => (
              <div key={label} className="rounded-lg border border-[#edf0ee] p-2.5">
                <p className="text-[9px] text-[#849089]">{label}</p>
                <p className="mt-1 text-lg font-semibold tracking-tight">{value}</p>
                <p className="text-[9px] text-[#147a55]">{note}</p>
              </div>
            ))}
          </div>

          <div className="mt-2 grid grid-cols-[1.1fr_1fr] gap-2 max-sm:grid-cols-1">
            <div className="rounded-lg border border-[#edf0ee] p-2.5">
              <p className="font-semibold">Nhân sự theo phòng ban</p>
              <div className="mt-3 flex h-24 items-end gap-2">
                {HEADCOUNT.map((item) => (
                  <div key={item.unit} className="flex flex-1 flex-col items-center gap-1">
                    <span className="text-[9px] text-[#64706a]">{item.value}</span>
                    <div
                      className="w-full rounded-t bg-gradient-to-t from-[#147a55] to-[#3fb886]"
                      style={{ height: `${(item.value / max) * 4.5}rem` }}
                    />
                  </div>
                ))}
              </div>
              <div className="mt-1 flex gap-2">
                {HEADCOUNT.map((item) => (
                  <span key={item.unit} className="flex-1 truncate text-center text-[8px] text-[#8a968f]">{item.unit}</span>
                ))}
              </div>
            </div>

            <div className="rounded-lg border border-[#edf0ee] p-2.5">
              <p className="font-semibold">Đơn từ gần đây</p>
              <ul className="mt-2 grid gap-2">
                {REQUESTS.map((request) => (
                  <li key={request.name} className="flex items-center gap-2">
                    <span className="grid size-6 shrink-0 place-items-center rounded-full bg-[#e7f5ee] text-[9px] font-bold text-[#0d5f42]">
                      {request.name.split(" ").at(-1)?.[0]}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-medium">{request.name}</span>
                      <span className="block truncate text-[9px] text-[#849089]">{request.type}</span>
                    </span>
                    <span
                      className={cn(
                        "shrink-0 rounded-full px-1.5 py-0.5 text-[8px] font-semibold",
                        request.tone === "green" ? "bg-[#e7f5ee] text-[#0d5f42]" : "bg-amber-50 text-amber-700",
                      )}
                    >
                      {request.status}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      </div>
    </WindowFrame>
  )
}

// Day kinds for September 2026 (starts on a Tuesday): w = worked, p = annual leave, s = sick leave, h = holiday, o = Sunday.
const SEPTEMBER = "hhwwwowwswwwowwpwwwowwwwwwowww"
const DAY_STYLE: Record<string, string> = {
  w: "bg-[#e7f5ee] text-[#0d5f42]",
  p: "bg-sky-100 text-sky-800",
  s: "bg-amber-100 text-amber-800",
  h: "bg-rose-100 text-rose-700",
  o: "bg-transparent text-[#b4bdb8]",
}

export function AttendanceMockup() {
  return (
    <div className="rounded-2xl border border-[#dfe5e1] bg-white p-5 shadow-[0_24px_60px_-24px_rgb(12_60_42/0.3)]">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-[10px] font-extrabold tracking-[0.14em] text-[#147a55]">BẢNG CÔNG</p>
          <p className="mt-0.5 font-semibold">Tháng 09/2026</p>
        </div>
        <span className="rounded-full bg-[#e7f5ee] px-2.5 py-1 text-xs font-semibold text-[#0d5f42]">22 ngày công</span>
      </div>
      <div className="mt-4 grid grid-cols-7 gap-1.5 text-center text-[10px]">
        {["T2", "T3", "T4", "T5", "T6", "T7", "CN"].map((day) => (
          <span key={day} className="pb-1 font-semibold text-[#8a968f]">{day}</span>
        ))}
        {Array.from({ length: 1 }, (_, index) => <span key={`pad-${index}`} />)}
        {[...SEPTEMBER].map((kind, index) => (
          <span key={index} className={cn("grid aspect-square place-items-center rounded-md text-xs font-medium", DAY_STYLE[kind])}>
            {index + 1}
          </span>
        ))}
      </div>
      <div className="mt-4 flex flex-wrap gap-x-4 gap-y-1.5 text-[11px] text-[#64706a]">
        {[["w", "Đi làm"], ["p", "Nghỉ phép"], ["s", "Nghỉ ốm"], ["h", "Ngày lễ"]].map(([kind, label]) => (
          <span key={kind} className="flex items-center gap-1.5">
            <i className={cn("size-2.5 rounded-sm", DAY_STYLE[kind])} />
            {label}
          </span>
        ))}
      </div>
    </div>
  )
}

export function LeaveMockup() {
  return (
    <div className="grid gap-3">
      <div className="rounded-2xl border border-[#dfe5e1] bg-white p-5 shadow-[0_24px_60px_-24px_rgb(12_60_42/0.3)]">
        <p className="text-[10px] font-extrabold tracking-[0.14em] text-[#147a55]">PHÉP NĂM 2026</p>
        <div className="mt-3 flex items-end justify-between">
          <p className="text-4xl font-semibold tracking-tight">8<span className="text-lg text-[#8a968f]"> / 12 ngày</span></p>
          <p className="text-xs text-[#64706a]">Đã dùng 4 ngày</p>
        </div>
        <div className="mt-3 h-2 overflow-hidden rounded-full bg-[#edf2ef]">
          <div className="h-full w-1/3 rounded-full bg-gradient-to-r from-[#147a55] to-[#3fb886]" />
        </div>
      </div>
      <div className="ml-8 rounded-2xl border border-[#dfe5e1] bg-white p-4 shadow-[0_24px_60px_-24px_rgb(12_60_42/0.3)] max-sm:ml-0">
        <div className="flex items-start gap-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-sky-100 text-sm font-bold text-sky-800">A</span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold">Trần Minh Anh</p>
            <p className="text-xs text-[#64706a]">Nghỉ phép năm · 22–23/10/2026</p>
          </div>
          <span className="rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-semibold text-amber-700">Chờ duyệt</span>
        </div>
        <div className="mt-3 flex gap-2">
          <span className="flex-1 rounded-lg bg-[#147a55] py-1.5 text-center text-xs font-semibold text-white">Duyệt</span>
          <span className="flex-1 rounded-lg py-1.5 text-center text-xs font-semibold text-[#4f5d56] ring-1 ring-[#dfe5e1]">Từ chối</span>
        </div>
      </div>
    </div>
  )
}

const PAYSLIP: [string, string, boolean?][] = [
  ["Lương cơ bản", "18.000.000"],
  ["Phụ cấp ăn trưa", "730.000"],
  ["Phụ cấp xăng xe", "500.000"],
  ["BHXH (8%)", "−1.440.000", true],
  ["BHYT (1,5%)", "−270.000", true],
  ["BHTN (1%)", "−180.000", true],
  ["Thuế TNCN", "−312.000", true],
]

export function PayslipMockup() {
  return (
    <div className="rounded-2xl border border-[#dfe5e1] bg-white p-5 shadow-[0_24px_60px_-24px_rgb(12_60_42/0.3)]">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-[10px] font-extrabold tracking-[0.14em] text-[#147a55]">PHIẾU LƯƠNG</p>
          <p className="mt-0.5 font-semibold">Tháng 09/2026 · Nguyễn Văn Bình</p>
        </div>
        <span className="flex items-center gap-1 rounded-lg px-2 py-1 text-xs font-medium text-[#4f5d56] ring-1 ring-[#dfe5e1]">
          <Printer className="size-3.5" /> In
        </span>
      </div>
      <dl className="mt-4 grid gap-2 text-sm">
        {PAYSLIP.map(([label, amount, deduction]) => (
          <div key={label} className="flex justify-between">
            <dt className="text-[#64706a]">{label}</dt>
            <dd className={cn("tabular-nums", deduction && "text-rose-700")}>{amount}</dd>
          </div>
        ))}
      </dl>
      <div className="mt-4 flex items-center justify-between rounded-xl bg-[#e7f5ee] px-4 py-3">
        <span className="flex items-center gap-1.5 text-sm font-semibold text-[#0d5f42]">
          <CircleCheck className="size-4" /> Thực nhận
        </span>
        <span className="text-xl font-semibold tabular-nums text-[#0d5f42]">17.028.000 ₫</span>
      </div>
    </div>
  )
}
