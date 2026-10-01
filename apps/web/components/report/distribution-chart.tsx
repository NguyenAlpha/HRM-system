import type { DistributionItem } from "@/lib/report"

export function DistributionChart({ items, emptyLabel = "Chưa có dữ liệu phù hợp." }: {
  items: DistributionItem[]
  emptyLabel?: string
}) {
  const hasData = items.some((item) => item.count > 0)
  if (!hasData) {
    return <p className="rounded-lg bg-muted/50 px-4 py-8 text-center text-sm text-muted-foreground">{emptyLabel}</p>
  }

  return (
    <div className="grid gap-4" role="img" aria-label="Biểu đồ phân bố">
      {items.map((item) => (
        <div className="grid gap-1.5" key={item.key}>
          <div className="flex items-center justify-between gap-4 text-sm">
            <span className="truncate text-muted-foreground">{item.label}</span>
            <strong className="shrink-0 font-medium text-foreground">
              {item.count.toLocaleString("vi-VN")} · {Number(item.percentage).toLocaleString("vi-VN", { maximumFractionDigits: 2 })}%
            </strong>
          </div>
          <div className="h-2.5 overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full bg-[var(--green)] transition-[width] duration-300"
              style={{ width: `${Math.min(100, Math.max(0, Number(item.percentage)))}%` }}
            />
          </div>
        </div>
      ))}
    </div>
  )
}
