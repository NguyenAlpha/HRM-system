"use client"

import { authorizedRequest } from "@/lib/auth/client"

export interface OrganizationUnitOption {
  id: number
  code: string
  name: string
}

export interface WorkLocationOption {
  id: number
  code: string
  name: string
}

export interface JobPositionOption {
  id: number
  code: string
  title: string
}

function referenceRequest<T>(resource: string): Promise<T> {
  return authorizedRequest<T>("hrm", `/reference/${resource}?active=true`, { cache: "no-store" })
}

export function getOrganizationUnitOptions(): Promise<OrganizationUnitOption[]> {
  return referenceRequest<OrganizationUnitOption[]>("organization-units")
}

export function getWorkLocationOptions(): Promise<WorkLocationOption[]> {
  return referenceRequest<WorkLocationOption[]>("work-locations")
}

export function getJobPositionOptions(): Promise<JobPositionOption[]> {
  return referenceRequest<JobPositionOption[]>("job-positions")
}
