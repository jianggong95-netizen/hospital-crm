import { http, type PageResult } from './request'

export interface CustomerBrief {
  id: string
  customerNo: string
  name: string
  gender: string
  phoneMasked?: string
  status: string
}

/** 按手机号/证件号精确检索患者档案；手机号需完整输入 */
export function searchCustomers(keyword: string): Promise<PageResult<CustomerBrief>> {
  return http.get<PageResult<CustomerBrief>>('/customers', { keyword, page: 1, pageSize: 10 })
}
