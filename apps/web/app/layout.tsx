import type { Metadata } from "next"
import "./globals.css"
import { Inter, Geist_Mono } from "next/font/google";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";

// The fonts expose CSS variables on <html>; globals.css maps them to Tailwind's font tokens in @theme.
// Both are variable fonts, so omitting `weight` still covers every weight.
const inter = Inter({ subsets: ["latin", "vietnamese"], variable: "--font-inter" })
const geistMono = Geist_Mono({ subsets: ["latin"], variable: "--font-geist-mono" })

export const metadata: Metadata = {
  title: {
    default: "HRM",
    template: "%s | HRM",
  },
  description: "Nền tảng quản trị nguồn nhân lực",
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="vi" className={`${inter.variable} ${geistMono.variable} font-sans`} suppressHydrationWarning>
      <body>
        <TooltipProvider delay={200}>{children}</TooltipProvider>
        <Toaster position="top-center" richColors />
      </body>
    </html>
  )
}
