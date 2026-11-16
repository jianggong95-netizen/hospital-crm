import { api } from './http'
import type { TagCreatePayload, TagItem, TagUpdatePayload } from '@/types/customer'

export function fetchTags(includeDisabled = false): Promise<TagItem[]> {
  return api.get<TagItem[]>('/tags', { includeDisabled })
}

export function createTag(payload: TagCreatePayload): Promise<TagItem> {
  return api.post<TagItem>('/tags', payload)
}

export function updateTag(tagId: string, payload: TagUpdatePayload): Promise<TagItem> {
  return api.put<TagItem>(`/tags/${tagId}`, payload)
}
