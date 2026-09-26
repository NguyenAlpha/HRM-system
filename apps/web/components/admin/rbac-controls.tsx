import { ChevronLeft, ChevronRight, TriangleAlert } from "lucide-react"

import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"

export function RbacFeedback({ error }: { error: string | null }) {
  if (!error) return null
  return (
    <Alert variant="destructive" className="animate-in fade-in slide-in-from-top-1">
      <TriangleAlert />
      <AlertDescription>{error}</AlertDescription>
    </Alert>
  )
}

export function Pagination({ page, totalPages, totalElements, disabled, onChange }: {
  page: number
  totalPages: number
  totalElements: number
  disabled: boolean
  onChange: (page: number) => void
}) {
  if (totalElements === 0) return null
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-3 text-sm text-muted-foreground">
      <span>{totalElements} bản ghi · Trang {page + 1}/{Math.max(1, totalPages)}</span>
      <div className="flex items-center gap-1.5">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || page === 0}
          onClick={() => onChange(page - 1)}
        >
          <ChevronLeft /> Trước
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || page + 1 >= totalPages}
          onClick={() => onChange(page + 1)}
        >
          Sau <ChevronRight />
        </Button>
      </div>
    </div>
  )
}
