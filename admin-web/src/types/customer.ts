export interface TagSummary {
  id: string
  tagCode: string
  tagName: string
  tagType: string
}

export interface TagItem extends TagSummary {
  status: 'active' | 'disabled'
  sortOrder: number
  createdAt: string
}

export interface CustomerSummary {
  id: string
  customerNo: string
  name: string
  gender: string
  birthDate?: string
  phoneMasked?: string
  idNoMasked?: string
  sourceCode?: string
  status: 'active' | 'disabled' | 'merged'
  tags: TagSummary[]
  createdAt: string
  updatedAt: string
}

export interface CustomerDetail extends CustomerSummary {
  phone?: string
  idNo?: string
  sensitiveVisible: boolean
  notes?: string
}

export interface CustomerCreatePayload {
  name: string
  gender?: string
  birthDate?: string
  phone?: string
  idNo?: string
  sourceCode?: string
  notes?: string
  tagIds: string[]
  forceCreate?: boolean
}

export interface CustomerUpdatePayload {
  name?: string
  gender?: string
  birthDate?: string
  phone?: string
  idNo?: string
  sourceCode?: string
  notes?: string
  tagIds?: string[]
}

export interface TagCreatePayload {
  tagCode: string
  tagName: string
  tagType?: string
  sortOrder?: number
}

export interface TagUpdatePayload {
  tagName: string
  tagType?: string
  sortOrder?: number
  status?: 'active' | 'disabled'
}

export interface HisSyncResult {
  total: number
  created: number
  linked: number
  updated: number
  skipped: number
}

export interface DuplicateCustomerItem {
  id: string
  customerNo: string
  name: string
  phoneMasked?: string
  birthDate?: string
  createdAt: string
}

export interface CustomerDuplicateGroup {
  reason: 'phone' | 'name_birth'
  items: DuplicateCustomerItem[]
}

export interface CustomerMergeResult {
  targetId: string
  sourceId: string
  mergedTables: Record<string, number>
  removedRows: Record<string, number>
  filledFields?: string[]
}
