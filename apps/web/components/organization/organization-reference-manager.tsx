"use client"

import { useState } from "react"

import { CompanyHolidayManager } from "@/components/organization/company-holiday-manager"
import { JobPositionManager } from "@/components/organization/job-position-manager"
import { OrganizationUnitManager } from "@/components/organization/organization-unit-manager"
import { WorkLocationManager } from "@/components/organization/work-location-manager"
import { WorkShiftManager } from "@/components/organization/work-shift-manager"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { Portal } from "@/lib/auth/types"
import { EMPLOYMENT_TYPES, EMPLOYMENT_TYPE_LABELS } from "@/lib/employee"

export function OrganizationReferenceManager({ portal, onSessionExpired }: { portal: Portal; onSessionExpired: () => void }) {
  const [tab, setTab] = useState("units")
  const { session } = usePortalSession()
  const has = (code: string) => session.account.permissions.some((permission) => permission.code === code)
  // Holidays go through the HRM workforce proxy, so the Admin Console does not list them.
  const canReadHolidays = portal === "hrm" && has("holiday.read")
  const canManage = has("organization.manage")

  return (
    // No card around the tabs: the page header already names the catalogue.
    <Tabs value={tab} onValueChange={setTab}>
      <TabsList>
        <TabsTrigger value="units">Đơn vị tổ chức</TabsTrigger>
        <TabsTrigger value="locations">Địa điểm làm việc</TabsTrigger>
        <TabsTrigger value="positions">Vị trí công việc</TabsTrigger>
        <TabsTrigger value="shifts">Ca làm việc</TabsTrigger>
        {canReadHolidays && <TabsTrigger value="holidays">Ngày lễ</TabsTrigger>}
        <TabsTrigger value="employment-types">Hình thức làm việc</TabsTrigger>
      </TabsList>

      <TabsContent value="units" className="mt-4 grid gap-4">
        <OrganizationUnitManager portal={portal} canManage={canManage} onSessionExpired={onSessionExpired} />
      </TabsContent>

      <TabsContent value="locations" className="mt-4 grid gap-4">
        <WorkLocationManager portal={portal} canManage={canManage} onSessionExpired={onSessionExpired} />
      </TabsContent>

      <TabsContent value="positions" className="mt-4 grid gap-4">
        <JobPositionManager portal={portal} canManage={canManage} onSessionExpired={onSessionExpired} />
      </TabsContent>

      <TabsContent value="shifts" className="mt-4 grid gap-4">
        <WorkShiftManager portal={portal} canManage={canManage} onSessionExpired={onSessionExpired} />
      </TabsContent>

      {canReadHolidays && (
        <TabsContent value="holidays" className="mt-4 grid gap-4">
          <CompanyHolidayManager canManage={canManage} />
        </TabsContent>
      )}

      <TabsContent value="employment-types" className="mt-4">
        <p className="mb-3 text-xs text-muted-foreground">
          Hình thức làm việc là danh mục cố định do hệ thống định nghĩa, không thể chỉnh sửa.
        </p>
        <div className="grid gap-3 sm:grid-cols-3">
          {EMPLOYMENT_TYPES.map((type) => (
            <div key={type} className="rounded-xl border bg-card px-4 py-3">
              <p className="font-medium">{EMPLOYMENT_TYPE_LABELS[type]}</p>
              <p className="mt-0.5 text-xs text-muted-foreground"><code>{type}</code></p>
            </div>
          ))}
        </div>
      </TabsContent>
    </Tabs>
  )
}
