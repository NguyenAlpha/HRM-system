"use client"

import { authorizedRequest } from "@/lib/auth/client"
import type { Portal } from "@/lib/auth/types"

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

export interface WorkShiftOption {
  id: number
  code: string
  name: string
}

export const ORGANIZATION_UNIT_TYPES = ["BOARD", "DEPARTMENT", "TEAM"] as const
export type OrganizationUnitType = typeof ORGANIZATION_UNIT_TYPES[number]

export const ORGANIZATION_UNIT_TYPE_LABELS: Record<OrganizationUnitType, string> = {
  BOARD: "Ban",
  DEPARTMENT: "Phòng ban",
  TEAM: "Nhóm / Tổ",
}

export const LOCATION_TYPES = ["HEAD_OFFICE", "BRANCH", "WAREHOUSE"] as const
export type LocationType = typeof LOCATION_TYPES[number]

export const LOCATION_TYPE_LABELS: Record<LocationType, string> = {
  HEAD_OFFICE: "Trụ sở chính",
  BRANCH: "Chi nhánh",
  WAREHOUSE: "Kho",
}

export interface OrganizationUnit {
  id: number
  parentUnitId: number | null
  parentUnitName: string | null
  code: string
  name: string
  unitType: OrganizationUnitType
  isActive: boolean
}

export interface WorkLocation {
  id: number
  parentLocationId: number | null
  parentLocationName: string | null
  code: string
  name: string
  locationType: LocationType
  address: string | null
  phone: string | null
  isActive: boolean
}

export interface JobPosition {
  id: number
  code: string
  title: string
  description: string | null
  isManagerial: boolean
  isActive: boolean
}

export interface CreateOrganizationUnitInput {
  parentUnitId: number | null
  code: string
  name: string
  unitType: OrganizationUnitType
}

export interface UpdateOrganizationUnitInput {
  parentUnitId: number | null
  name: string
  isActive: boolean
}

export interface CreateWorkLocationInput {
  parentLocationId: number | null
  code: string
  name: string
  locationType: LocationType
  address: string
  phone: string | null
}

export interface UpdateWorkLocationInput {
  parentLocationId: number | null
  name: string
  address: string
  phone: string | null
  isActive: boolean
}

export interface CreateJobPositionInput {
  code: string
  title: string
  description: string | null
  isManagerial: boolean
}

export interface UpdateJobPositionInput {
  title: string
  description: string | null
  isManagerial: boolean
  isActive: boolean
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
