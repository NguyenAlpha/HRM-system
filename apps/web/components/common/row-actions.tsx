"use client"

import Link from "next/link"
import { Children, type ReactNode } from "react"
import { Menu } from "@base-ui/react/menu"
import { Ellipsis, type LucideIcon } from "lucide-react"

import { Button } from "@/components/ui/button"
import { TableCell } from "@/components/ui/table"

const ITEM_CLASS = "flex cursor-default items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none select-none data-highlighted:bg-accent data-highlighted:text-accent-foreground data-disabled:pointer-events-none data-disabled:opacity-50"
const DESTRUCTIVE_CLASS = "text-destructive data-highlighted:bg-destructive/10 data-highlighted:text-destructive"

/**
 * The "Thao tác" cell every list table ends with: a "…" button that opens the row's actions.
 * Clicks inside the cell do not reach the row, so a clickable row does not open while using the menu.
 * Without children the cell shows a dash.
 */
export function RowActionsCell({ label, children }: { label: string; children?: ReactNode }) {
  // toArray drops the false/null left by conditional items.
  const items = Children.toArray(children).length > 0
  return (
    <TableCell className="text-right" onClick={(event) => event.stopPropagation()}>
      {items ? (
        <Menu.Root>
          <Menu.Trigger
            aria-label={`Mở menu thao tác cho ${label}`}
            title="Thao tác"
            render={<Button type="button" variant="ghost" size="icon-sm" />}
          >
            <Ellipsis />
          </Menu.Trigger>
          <Menu.Portal>
            <Menu.Positioner side="bottom" align="end" sideOffset={4} className="isolate z-50">
              <Menu.Popup className="min-w-44 origin-(--transform-origin) rounded-lg bg-popover p-1 text-popover-foreground shadow-md ring-1 ring-foreground/10 outline-none data-open:animate-in data-open:fade-in-0 data-open:zoom-in-95 data-closed:animate-out data-closed:fade-out-0 data-closed:zoom-out-95">
                {children}
              </Menu.Popup>
            </Menu.Positioner>
          </Menu.Portal>
        </Menu.Root>
      ) : <span className="text-muted-foreground">—</span>}
    </TableCell>
  )
}

export function RowActionItem({ icon: Icon, onClick, destructive, disabled, children }: {
  icon: LucideIcon
  onClick: () => void
  destructive?: boolean
  disabled?: boolean
  children: ReactNode
}) {
  return (
    <Menu.Item onClick={onClick} disabled={disabled} className={destructive ? `${ITEM_CLASS} ${DESTRUCTIVE_CLASS}` : ITEM_CLASS}>
      <Icon className="size-4" /> {children}
    </Menu.Item>
  )
}

export function RowActionLink({ icon: Icon, href, children }: { icon: LucideIcon; href: string; children: ReactNode }) {
  return (
    <Menu.LinkItem closeOnClick render={<Link href={href} />} className={ITEM_CLASS}>
      <Icon className="size-4" /> {children}
    </Menu.LinkItem>
  )
}
