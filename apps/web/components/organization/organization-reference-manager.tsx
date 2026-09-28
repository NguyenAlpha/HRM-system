"use client"

import { useState } from "react"
import { Briefcase, Building2, Clock, MapPin } from "lucide-react"

import { JobPositionManager } from "@/components/organization/job-position-manager"
import { OrganizationUnitManager } from "@/components/organization/organization-unit-manager"
import { WorkLocationManager } from "@/components/organization/work-location-manager"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { Portal } from "@/lib/auth/types"
import { EMPLOYMENT_TYPES, EMPLOYMENT_TYPE_LABELS } from "@/lib/employee"

export function OrganizationReferenceManager({ portal, onSessionExpired }: { portal: Portal; onSessionExpired: () => void }) {
  const [tab, setTab] = useState("units")

  return (
    <Card>
      <CardHeader>
        <CardTitle>Danh mục tổ chức</CardTitle>
        <CardDescription>Quản lý đơn vị tổ chức, địa điểm làm việc, vị trí công việc và hình thức làm việc.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        <Tabs value={tab} onValueChange={setTab}>
          <TabsList>
            <TabsTrigger value="units"><Building2 /> Đơn vị tổ chức</TabsTrigger>
            <TabsTrigger value="locations"><MapPin /> Địa điểm làm việc</TabsTrigger>
            <TabsTrigger value="positions"><Briefcase /> Vị trí công việc</TabsTrigger>
            <TabsTrigger value="employment-types"><Clock /> Hình thức làm việc</TabsTrigger>
          </TabsList>

          <TabsContent value="units" className="mt-4 grid gap-4">
            <OrganizationUnitManager portal={portal} onSessionExpired={onSessionExpired} />
          </TabsContent>

          <TabsContent value="locations" className="mt-4 grid gap-4">
            <WorkLocationManager portal={portal} onSessionExpired={onSessionExpired} />
          </TabsContent>

          <TabsContent value="positions" className="mt-4 grid gap-4">
            <JobPositionManager portal={portal} onSessionExpired={onSessionExpired} />
          </TabsContent>

          <TabsContent value="employment-types" className="mt-4">
            <p className="mb-3 text-xs text-muted-foreground">
              Hình thức làm việc là danh mục cố định do hệ thống định nghĩa, không thể chỉnh sửa.
            </p>
            <div className="grid gap-3 sm:grid-cols-3">
              {EMPLOYMENT_TYPES.map((type) => (
                <div key={type} className="rounded-xl border px-4 py-3">
                  <p className="font-medium">{EMPLOYMENT_TYPE_LABELS[type]}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground"><code>{type}</code></p>
                </div>
              ))}
            </div>
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  )
}
