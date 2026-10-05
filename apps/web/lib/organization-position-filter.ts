import type { JobPositionOption, OrganizationUnitOption } from "@/lib/reference"

// Temporary mapping for the default company structure until positions can be assigned to units.
const POSITION_CODES_BY_UNIT: Record<string, readonly string[]> = {
  BOARD: ["DIRECTOR"],
  HR: ["HR_SPECIALIST", "GENERAL_STAFF"],
  ACCOUNTING: ["PAYROLL_ACCOUNTANT", "GENERAL_STAFF"],
  OPERATIONS: [
    "OPERATIONS_MANAGER",
    "BRANCH_MANAGER",
    "WAREHOUSE_SUPERVISOR",
    "TEAM_LEAD",
    "GENERAL_STAFF",
  ],
}

const DEFAULT_POSITION_CODES = new Set(
  Object.values(POSITION_CODES_BY_UNIT).flat(),
)

export function positionsForOrganizationUnit(
  unit: OrganizationUnitOption | undefined,
  positions: JobPositionOption[],
): JobPositionOption[] {
  if (!unit) return []

  const allowedCodes = POSITION_CODES_BY_UNIT[unit.code]
  if (!allowedCodes) return positions

  return positions.filter(
    (position) => !DEFAULT_POSITION_CODES.has(position.code) || allowedCodes.includes(position.code),
  )
}
