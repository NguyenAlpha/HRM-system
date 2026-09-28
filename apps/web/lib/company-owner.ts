"use client"

import { authorizedRequest } from "@/lib/auth/client"
import type { AccountRoleAssignment } from "@/lib/role-assignment"

export interface CompanyOwnerAccount {
  id: number
  employeeId: number
  username: string
  email: string
  status: string
}

export interface CompanyOwnerInvitation {
  accountId: number
  activationToken: string
  expiresAt: string
}

export interface CompanyOwnerProvisioningResult {
  employeeId: number
  employeeCode: string
  fullName: string
  accountProvisioning: {
    account: CompanyOwnerAccount
    invitation: CompanyOwnerInvitation
  }
  companyOwnerRoleAssignment: AccountRoleAssignment
  directorRoleAssignment: AccountRoleAssignment
}

export interface BootstrapCompanyOwnerInput {
  employee: {
    employeeCode: string
    fullName: string
    workEmail: string
    phone: string | null
    hireDate: string
  }
  account: {
    username: string
  }
  effectiveFrom: string
  ownershipReason: string
  directorAppointmentReason: string
}

export function bootstrapCompanyOwner(input: BootstrapCompanyOwnerInput): Promise<CompanyOwnerProvisioningResult> {
  return authorizedRequest<CompanyOwnerProvisioningResult>("admin", "/company-owner", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
    cache: "no-store",
  })
}
