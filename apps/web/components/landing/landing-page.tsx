import Link from "next/link"
import {
  ArrowRight,
  Briefcase,
  Building2,
  CalendarCheck,
  ChartColumn,
  CircleCheck,
  Clock,
  ShieldCheck,
  UserRound,
  Users,
  Wallet,
} from "lucide-react"

import { AttendanceMockup, DashboardMockup, LeaveMockup, PayslipMockup } from "@/components/landing/mockups"
import { Reveal } from "@/components/landing/reveal"
import { cn } from "@/lib/utils"

const FEATURES = [
  { icon: Users, title: "Hồ sơ nhân sự", text: "Thông tin cá nhân, phòng ban, vị trí và lương của từng nhân viên ở một nơi." },
  { icon: Clock, title: "Chấm công", text: "Dữ liệu từ máy chấm công được tổng hợp thành bảng công; HR đối soát khi cần." },
  { icon: CalendarCheck, title: "Nghỉ phép & đơn từ", text: "Gửi đơn nghỉ phép, nghỉ việc trực tuyến và duyệt chỉ với một cú nhấp." },
  { icon: Wallet, title: "Tính lương", text: "Chốt kỳ lương, tự tính bảo hiểm, thuế TNCN và phát phiếu lương có thể in." },
  { icon: ChartColumn, title: "Báo cáo", text: "Biến động nhân sự, nghỉ việc, chấm công, nghỉ phép và quỹ lương theo tháng." },
  { icon: ShieldCheck, title: "Phân quyền", text: "Vai trò và quyền chi tiết; mỗi người chỉ thấy đúng phần việc của mình." },
]

const SHOWCASE = [
  {
    eyebrow: "CHẤM CÔNG",
    title: "Bảng công rõ ràng, không cần nhập tay",
    text: "Giờ vào – ra được lấy từ máy chấm công của công ty. Nhân viên tự xem công của mình mỗi ngày, bộ phận nhân sự chỉ cần đối soát những trường hợp đặc biệt.",
    points: ["Tự động đối chiếu với ca làm việc", "Phân biệt đi làm, nghỉ phép, nghỉ ốm, ngày lễ", "Nhân viên xem công trên web"],
    mockup: <AttendanceMockup />,
  },
  {
    eyebrow: "NGHỈ PHÉP",
    title: "Đơn nghỉ phép gửi đi, duyệt ngay",
    text: "Mỗi nhân viên có 12 ngày phép năm, tính theo số tháng làm việc. Số ngày còn lại luôn hiển thị, đơn được chuyển thẳng đến người duyệt.",
    points: ["Số ngày phép còn lại luôn được cập nhật", "Duyệt hoặc từ chối kèm lý do", "Lịch nghỉ tự động vào bảng công"],
    mockup: <LeaveMockup />,
  },
  {
    eyebrow: "BẢNG LƯƠNG",
    title: "Lương đúng, đủ và minh bạch",
    text: "Từ lương cơ bản, phụ cấp đến BHXH, BHYT, BHTN và thuế TNCN — hệ thống tính sẵn cho cả kỳ lương. Nhân viên xem và in phiếu lương của mình bất cứ lúc nào.",
    points: ["Chốt kỳ lương theo tháng", "Khấu trừ bảo hiểm và thuế tự động", "Phiếu lương in kèm thông tin công ty"],
    mockup: <PayslipMockup />,
  },
]

const ROLES = [
  { icon: UserRound, title: "Nhân viên", text: "Xem hồ sơ, bảng công, số ngày phép, phiếu lương và gửi đơn từ." },
  { icon: Briefcase, title: "Quản lý", text: "Theo dõi đội nhóm, duyệt đơn nghỉ phép và yêu cầu của nhân viên." },
  { icon: Building2, title: "Nhân sự & kế toán", text: "Quản lý hồ sơ, tổ chức, chấm công, kỳ lương và báo cáo." },
  { icon: ShieldCheck, title: "Quản trị hệ thống", text: "Cổng riêng để quản lý tài khoản, vai trò và quyền truy cập." },
]

export function LandingPage() {
  return (
    <div className="min-h-svh overflow-x-clip bg-[#fbfcfb] text-[#17211d]">
      <header className="sticky top-0 z-40 border-b border-[#dfe5e1]/70 bg-[#fbfcfb]/80 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5 sm:px-8">
          <Link className="brand" href="/">
            <span className="brand-mark !size-9 !text-base">H</span>
            <span>
              <strong>HRM</strong>
              <small>People Workspace</small>
            </span>
          </Link>
          <nav className="flex items-center gap-1 text-sm font-medium text-[#4f5d56] sm:gap-6">
            <a className="hidden hover:text-[#17211d] md:inline" href="#tinh-nang">Tính năng</a>
            <a className="hidden hover:text-[#17211d] md:inline" href="#vai-tro">Dành cho ai</a>
            <Link
              className="inline-flex h-9 items-center gap-1.5 rounded-lg bg-[#147a55] px-4 font-semibold text-white shadow-sm transition-colors hover:bg-[#0d5f42]"
              href="/login"
            >
              Đăng nhập <ArrowRight className="size-4" />
            </Link>
          </nav>
        </div>
      </header>

      <main>
        <section className="relative">
          <div
            aria-hidden
            className="pointer-events-none absolute inset-0 bg-[linear-gradient(#e6ece8_1px,transparent_1px),linear-gradient(90deg,#e6ece8_1px,transparent_1px)] bg-[size:48px_48px] [mask-image:radial-gradient(ellipse_at_top,black_30%,transparent_70%)]"
          />
          <div aria-hidden className="pointer-events-none absolute -top-40 left-1/2 h-[32rem] w-[56rem] -translate-x-1/2 rounded-full bg-[#3fb886]/20 blur-3xl" />

          <div className="relative mx-auto max-w-6xl px-5 pt-16 pb-20 sm:px-8 sm:pt-24">
            <div className="mx-auto max-w-3xl text-center animate-in fade-in slide-in-from-bottom-4 duration-700">
              <span className="inline-flex items-center gap-2 rounded-full border border-[#cfe6da] bg-white/70 px-3 py-1 text-xs font-semibold text-[#0d5f42]">
                <span className="size-1.5 rounded-full bg-[#20a56f]" />
                Nền tảng quản trị nhân sự cho doanh nghiệp Việt
              </span>
              <h1 className="mt-6 text-4xl leading-[1.08] font-semibold tracking-tight sm:text-6xl">
                Quản lý con người,{" "}
                <br className="hidden sm:block" />
                <span className="bg-gradient-to-r from-[#147a55] to-[#3fb886] bg-clip-text text-transparent">
                  nhẹ nhàng hơn mỗi ngày.
                </span>
              </h1>
              <p className="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-[#64706a]">
                Hồ sơ nhân sự, chấm công, nghỉ phép, tính lương và báo cáo — gom về một không gian làm việc
                thống nhất cho cả nhân viên lẫn bộ phận nhân sự.
              </p>
              <div className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
                <Link
                  className="inline-flex h-12 items-center gap-2 rounded-xl bg-[#147a55] px-6 font-semibold text-white shadow-[0_10px_30px_-10px_rgb(20_122_85/0.7)] transition-colors hover:bg-[#0d5f42]"
                  href="/login"
                >
                  Vào hệ thống <ArrowRight className="size-4" />
                </Link>
                <a
                  className="inline-flex h-12 items-center rounded-xl border border-[#dfe5e1] bg-white px-6 font-semibold text-[#35433d] transition-colors hover:border-[#aebbb4]"
                  href="#tinh-nang"
                >
                  Xem tính năng
                </a>
              </div>
            </div>

            <div className="relative mx-auto mt-16 max-w-5xl animate-in fade-in slide-in-from-bottom-6 delay-150 duration-1000 fill-mode-both">
              <DashboardMockup />
              <div className="absolute -bottom-6 -left-4 hidden items-center gap-3 rounded-xl border border-[#dfe5e1] bg-white px-4 py-3 shadow-xl md:flex lg:-left-12">
                <span className="grid size-9 place-items-center rounded-full bg-[#e7f5ee] text-[#147a55]"><CircleCheck className="size-5" /></span>
                <span className="text-sm">
                  <strong className="block">Đơn nghỉ phép đã được duyệt</strong>
                  <span className="text-xs text-[#64706a]">Lê Quốc Huy · vừa xong</span>
                </span>
              </div>
              <div className="absolute -top-6 -right-4 hidden items-center gap-3 rounded-xl border border-[#dfe5e1] bg-white px-4 py-3 shadow-xl md:flex lg:-right-10">
                <span className="grid size-9 place-items-center rounded-full bg-amber-50 text-amber-600"><Wallet className="size-5" /></span>
                <span className="text-sm">
                  <strong className="block">Kỳ lương tháng 09 đã chốt</strong>
                  <span className="text-xs text-[#64706a]">121 phiếu lương sẵn sàng</span>
                </span>
              </div>
            </div>
          </div>
        </section>

        <section id="tinh-nang" className="scroll-mt-16 border-y border-[#dfe5e1] bg-white py-24">
          <div className="mx-auto max-w-6xl px-5 sm:px-8">
            <Reveal className="mx-auto max-w-3xl text-center">
              <p className="eyebrow">TÍNH NĂNG</p>
              <h2 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">Mọi nghiệp vụ nhân sự, trong một hệ thống</h2>
              <p className="mt-4 text-[#64706a]">Không còn bảng tính rời rạc hay giấy tờ chuyền tay.</p>
            </Reveal>
            <div className="mt-14 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {FEATURES.map((feature) => (
                <Reveal key={feature.title}>
                  <article className="group h-full rounded-2xl border border-[#e6ebe8] bg-[#fbfcfb] p-6 transition-all hover:-translate-y-1 hover:border-[#bfe0cf] hover:bg-white hover:shadow-[0_20px_40px_-24px_rgb(12_60_42/0.35)]">
                    <span className="grid size-11 place-items-center rounded-xl bg-[#e7f5ee] text-[#147a55] transition-colors group-hover:bg-[#147a55] group-hover:text-white">
                      <feature.icon className="size-5" />
                    </span>
                    <h3 className="mt-5 text-lg font-semibold">{feature.title}</h3>
                    <p className="mt-2 text-sm leading-relaxed text-[#64706a]">{feature.text}</p>
                  </article>
                </Reveal>
              ))}
            </div>
          </div>
        </section>

        <section className="py-24">
          <div className="mx-auto grid max-w-6xl gap-24 px-5 sm:px-8">
            {SHOWCASE.map((item, index) => (
              <div key={item.eyebrow} className="grid items-center gap-12 lg:grid-cols-2 lg:gap-20">
                <Reveal className={cn(index % 2 === 1 && "lg:order-2")}>
                  <p className="eyebrow">{item.eyebrow}</p>
                  <h2 className="mt-3 text-3xl font-semibold tracking-tight">{item.title}</h2>
                  <p className="mt-4 leading-relaxed text-[#64706a]">{item.text}</p>
                  <ul className="mt-6 grid gap-3">
                    {item.points.map((point) => (
                      <li key={point} className="flex items-center gap-3 text-sm font-medium">
                        <CircleCheck className="size-5 shrink-0 text-[#147a55]" />
                        {point}
                      </li>
                    ))}
                  </ul>
                </Reveal>
                <Reveal className="relative">
                  <div aria-hidden className="absolute inset-6 -z-10 rounded-full bg-[#3fb886]/15 blur-3xl" />
                  <div className="mx-auto max-w-md">{item.mockup}</div>
                </Reveal>
              </div>
            ))}
          </div>
        </section>

        <section id="vai-tro" className="scroll-mt-16 border-t border-[#dfe5e1] bg-white py-24">
          <div className="mx-auto max-w-6xl px-5 sm:px-8">
            <Reveal className="mx-auto max-w-2xl text-center">
              <p className="eyebrow">DÀNH CHO AI</p>
              <h2 className="mt-3 text-3xl font-semibold tracking-tight sm:text-4xl">Mỗi vai trò, một góc nhìn phù hợp</h2>
            </Reveal>
            <div className="mt-14 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {ROLES.map((role) => (
                <Reveal key={role.title}>
                  <article className="h-full rounded-2xl border border-[#e6ebe8] p-6">
                    <role.icon className="size-6 text-[#147a55]" />
                    <h3 className="mt-4 font-semibold">{role.title}</h3>
                    <p className="mt-2 text-sm leading-relaxed text-[#64706a]">{role.text}</p>
                  </article>
                </Reveal>
              ))}
            </div>
          </div>
        </section>

        <section className="px-5 py-24 sm:px-8">
          <Reveal className="relative mx-auto max-w-5xl overflow-hidden rounded-3xl bg-[radial-gradient(circle_at_85%_15%,rgba(119,215,173,0.3),transparent_22rem),linear-gradient(145deg,#0c5a3d,#123d30_70%,#102e26)] px-8 py-16 text-center text-white sm:px-16">
            <div
              aria-hidden
              className="pointer-events-none absolute -right-24 -bottom-40 size-96 rounded-full border border-white/10 shadow-[0_0_0_4rem_rgba(255,255,255,0.03),0_0_0_8rem_rgba(255,255,255,0.02)]"
            />
            <h2 className="relative text-3xl font-semibold tracking-tight sm:text-4xl">Sẵn sàng bắt đầu ngày làm việc?</h2>
            <p className="relative mx-auto mt-4 max-w-xl text-white/75">
              Đăng nhập bằng tài khoản công ty cấp để xem bảng công, gửi đơn và nhận phiếu lương.
            </p>
            <Link
              className="relative mt-8 inline-flex h-12 items-center gap-2 rounded-xl bg-white px-6 font-semibold text-[#0d5f42] transition-colors hover:bg-[#e7f5ee]"
              href="/login"
            >
              Đăng nhập ngay <ArrowRight className="size-4" />
            </Link>
          </Reveal>
        </section>
      </main>

      <footer className="border-t border-[#dfe5e1]">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-3 px-5 py-8 text-sm text-[#64706a] sm:flex-row sm:px-8">
          <p>© {new Date().getFullYear()} HRM Platform. Nền tảng quản trị nguồn nhân lực.</p>
          <Link className="font-medium hover:text-[#17211d]" href="/admin/login">Cổng quản trị hệ thống</Link>
        </div>
      </footer>
    </div>
  )
}
