import type { Metadata } from "next"
import "./globals.css"
import { Geist } from "next/font/google";
import { cn } from "@/lib/utils";
import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";

const geist = Geist({subsets:['latin'],variable:'--font-sans'});

export const metadata: Metadata = {
  title: {
    default: "HRM",
    template: "%s | HRM",
  },
  description: "Nền tảng quản trị nguồn nhân lực",
}

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="vi" className={cn("font-sans", geist.variable)} suppressHydrationWarning>
      <body>
        <TooltipProvider delay={200}>{children}</TooltipProvider>
        <Toaster position="top-center" richColors />
      </body>
    </html>
  )
}
