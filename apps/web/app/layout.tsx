import type { Metadata } from "next"
import "./globals.css"
import { Inter, Geist_Mono } from "next/font/google";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";

// Loading the fonts registers 'Inter' and 'Geist Mono'; globals.css names them in @theme.
// Both are variable fonts, so omitting `weight` still covers every weight.
/* eslint-disable @typescript-eslint/no-unused-vars */
const _inter = Inter({ subsets: ["latin", "vietnamese"] })
const _geistMono = Geist_Mono({ subsets: ["latin"] })
/* eslint-enable @typescript-eslint/no-unused-vars */

export const metadata: Metadata = {
  title: {
    default: "HRM",
    template: "%s | HRM",
  },
  description: "Nền tảng quản trị nguồn nhân lực",
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="vi" className="font-sans" suppressHydrationWarning>
      <body>
        <TooltipProvider delay={200}>{children}</TooltipProvider>
        <Toaster position="top-center" richColors />
      </body>
    </html>
  )
}
