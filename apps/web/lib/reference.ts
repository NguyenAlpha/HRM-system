"use client"

import type { components } from "@/lib/api-schema"
import { authorizedRequest } from "@/lib/auth/client"
import type { Portal } from "@/lib/auth/types"

// DTO types come from the API's OpenAPI spec; run `npm run gen:api` after changing them.
type Schemas = components["schemas"]

export type OrganizationUnit = Schemas["OrganizationUnitResponse"]
export type WorkLocation = Schemas["WorkLocationResponse"]
export type JobPosition = Schemas["JobPositionResponse"]
export type WorkShift = Schemas["WorkShiftResponse"]

// The option lists call the same endpoints with ?active=true; dropdowns only read these fields.
export type OrganizationUnitOption = Pick<OrganizationUnit, "id" | "code" | "name">
export type WorkLocationOption = Pick<WorkLocation, "id" | "code" | "name">
export type JobPositionOption = Pick<JobPosition, "id" | "code" | "title">
export type WorkShiftOption = Pick<WorkShift, "id" | "code" | "name">

export type CreateOrganizationUnitInput = Schemas["CreateOrganizationUnitRequest"]
export type UpdateOrganizationUnitInput = Schemas["UpdateOrganizationUnitRequest"]
export type CreateWorkLocationInput = Schemas["CreateWorkLocationRequest"]
export type UpdateWorkLocationInput = Schemas["UpdateWorkLocationRequest"]
export type CreateJobPositionInput = Schemas["CreateJobPositionRequest"]
export type UpdateJobPositionInput = Schemas["UpdateJobPositionRequest"]
export type CreateWorkShiftInput = Schemas["CreateWorkShiftRequest"]
export type UpdateWorkShiftInput = Schemas["UpdateWorkShiftRequest"]

export type OrganizationUnitType = OrganizationUnit["unitType"]
export type LocationType = WorkLocation["locationType"]

// The label records below must list every enum value, so a value added in the API fails typecheck.
export const ORGANIZATION_UNIT_TYPES = ["BOARD", "DEPARTMENT", "TEAM"] as const satisfies readonly OrganizationUnitType[]

export const ORGANIZATION_UNIT_TYPE_LABELS: Record<OrganizationUnitType, string> = {
  BOARD: "Ban",
  DEPARTMENT: "Phòng ban",
  TEAM: "Nhóm / Tổ",
}

export const LOCATION_TYPES = ["HEAD_OFFICE", "BRANCH", "WAREHOUSE"] as const satisfies readonly LocationType[]

export const LOCATION_TYPE_LABELS: Record<LocationType, string> = {
  HEAD_OFFICE: "Trụ sở chính",
  BRANCH: "Chi nhánh",
  WAREHOUSE: "Kho",
}

function referenceRequest<T>(portal: Portal, path: string, init?: RequestInit): Promise<T> {
  return authorizedRequest<T>(portal, `/reference/${path}`, { ...init, cache: "no-store" })
}

function referenceMutation<T>(portal: Portal, path: string, method: "POST" | "PUT" | "DELETE", body?: unknown): Promise<T> {
  return referenceRequest<T>(portal, path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export function getOrganizationUnitOptions(portal: Portal): Promise<OrganizationUnitOption[]> {
  return referenceRequest<OrganizationUnitOption[]>(portal, "organization-units?active=true")
}

export function getWorkLocationOptions(portal: Portal): Promise<WorkLocationOption[]> {
  return referenceRequest<WorkLocationOption[]>(portal, "work-locations?active=true")
}

export function getJobPositionOptions(portal: Portal): Promise<JobPositionOption[]> {
  return referenceRequest<JobPositionOption[]>(portal, "job-positions?active=true")
}

export function getWorkShiftOptions(portal: Portal): Promise<WorkShiftOption[]> {
  return referenceRequest<WorkShiftOption[]>(portal, "work-shifts?active=true")
}

export function listOrganizationUnits(portal: Portal): Promise<OrganizationUnit[]> {
  return referenceRequest<OrganizationUnit[]>(portal, "organization-units")
}

export function listWorkLocations(portal: Portal): Promise<WorkLocation[]> {
  return referenceRequest<WorkLocation[]>(portal, "work-locations")
}

export function listJobPositions(portal: Portal): Promise<JobPosition[]> {
  return referenceRequest<JobPosition[]>(portal, "job-positions")
}

export function createOrganizationUnit(portal: Portal, input: CreateOrganizationUnitInput): Promise<OrganizationUnit> {
  return referenceMutation<OrganizationUnit>(portal, "organization-units", "POST", input)
}

export function updateOrganizationUnit(portal: Portal, id: number, input: UpdateOrganizationUnitInput): Promise<OrganizationUnit> {
  return referenceMutation<OrganizationUnit>(portal, `organization-units/${id}`, "PUT", input)
}

export function deleteOrganizationUnit(portal: Portal, id: number): Promise<null> {
  return referenceMutation<null>(portal, `organization-units/${id}`, "DELETE")
}

export function createWorkLocation(portal: Portal, input: CreateWorkLocationInput): Promise<WorkLocation> {
  return referenceMutation<WorkLocation>(portal, "work-locations", "POST", input)
}

export function updateWorkLocation(portal: Portal, id: number, input: UpdateWorkLocationInput): Promise<WorkLocation> {
  return referenceMutation<WorkLocation>(portal, `work-locations/${id}`, "PUT", input)
}

export function deleteWorkLocation(portal: Portal, id: number): Promise<null> {
  return referenceMutation<null>(portal, `work-locations/${id}`, "DELETE")
}

export function createJobPosition(portal: Portal, input: CreateJobPositionInput): Promise<JobPosition> {
  return referenceMutation<JobPosition>(portal, "job-positions", "POST", input)
}

export function updateJobPosition(portal: Portal, id: number, input: UpdateJobPositionInput): Promise<JobPosition> {
  return referenceMutation<JobPosition>(portal, `job-positions/${id}`, "PUT", input)
}

export function deleteJobPosition(portal: Portal, id: number): Promise<null> {
  return referenceMutation<null>(portal, `job-positions/${id}`, "DELETE")
}

export function listWorkShifts(portal: Portal): Promise<WorkShift[]> {
  return referenceRequest<WorkShift[]>(portal, "work-shifts")
}

export function createWorkShift(portal: Portal, input: CreateWorkShiftInput): Promise<WorkShift> {
  return referenceMutation<WorkShift>(portal, "work-shifts", "POST", input)
}

export function updateWorkShift(portal: Portal, id: number, input: UpdateWorkShiftInput): Promise<WorkShift> {
  return referenceMutation<WorkShift>(portal, `work-shifts/${id}`, "PUT", input)
}

export function deleteWorkShift(portal: Portal, id: number): Promise<null> {
  return referenceMutation<null>(portal, `work-shifts/${id}`, "DELETE")
}
