import type { Metadata } from "next"
import "./globals.css"
import { Geist, Geist_Mono } from "next/font/google";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { getBranding } from "@/lib/auth/server";

// The fonts expose CSS variables on <html>; globals.css maps them to Tailwind's font tokens in @theme.
// Both are variable fonts, so omitting `weight` still covers every weight.
const geist = Geist({ subsets: ["latin", "vietnamese"], variable: "--font-geist" })
const geistMono = Geist_Mono({ subsets: ["latin"], variable: "--font-geist-mono" })

// The tab title carries the company's own name.
export async function generateMetadata(): Promise<Metadata> {
  const { name } = await getBranding()
  return {
    title: {
      default: name,
      template: `%s | ${name}`,
    },
    description: "Nền tảng quản trị nguồn nhân lực",
  }
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="vi" className={`${geist.variable} ${geistMono.variable} font-sans`} suppressHydrationWarning>
      <body>
        <TooltipProvider delay={200}>{children}</TooltipProvider>
        <Toaster position="top-center" richColors />
      </body>
    </html>
  )
}
