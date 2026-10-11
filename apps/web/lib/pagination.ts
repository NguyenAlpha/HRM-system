/**
 * One page of a paged API list (`PagedResult` on the API). The generated schema has a separate copy per
 * item type (`PagedResultRoleResponse`, ...), so the generic shape is written by hand once here.
 */
export interface Page<T> {
  content: T[]
  number: number
  totalElements: number
  totalPages: number
}
