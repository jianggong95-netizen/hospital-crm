import type { OrgNode } from '@/types/system'

export interface OrgTreeOption {
  value: string
  label: string
  children?: OrgTreeOption[]
}

export interface FlatOrg {
  id: string
  label: string
  orgCode: string
  status: string
  depth: number
}

export function toOrgOptions(nodes: OrgNode[]): OrgTreeOption[] {
  return nodes.map((node) => ({
    value: node.id,
    label: node.orgName,
    children: node.children?.length ? toOrgOptions(node.children) : undefined
  }))
}

export function flattenOrgs(nodes: OrgNode[], depth = 0): FlatOrg[] {
  const result: FlatOrg[] = []
  for (const node of nodes) {
    result.push({
      id: node.id,
      label: node.orgName,
      orgCode: node.orgCode,
      status: node.status,
      depth
    })
    if (node.children?.length) {
      result.push(...flattenOrgs(node.children, depth + 1))
    }
  }
  return result
}
