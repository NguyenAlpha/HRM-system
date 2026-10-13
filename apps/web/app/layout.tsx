import type { Metadata } from "next"
import "./globals.css"
import { Inter, Geist_Mono, Source_Serif_4 } from "next/font/google";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";

// Loading the fonts registers 'Inter', 'Geist Mono' and 'Source Serif 4'; globals.css names them in @theme.
/* eslint-disable @typescript-eslint/no-unused-vars */
const _inter = Inter({ subsets: ["latin"], weight: ["100", "200", "300", "400", "500", "600", "700", "800", "900"] })
const _geistMono = Geist_Mono({ subsets: ["latin"], weight: ["100", "200", "300", "400", "500", "600", "700", "800", "900"] })
const _sourceSerif4 = Source_Serif_4({ subsets: ["latin"], weight: ["200", "300", "400", "500", "600", "700", "800", "900"] })
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
