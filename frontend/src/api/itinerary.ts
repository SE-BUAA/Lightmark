import { http } from '@/utils/request'

export interface PageResponse<T> {
  total: number
  page: number
  size: number
  list: T[]
}

export interface TravelPlan {
  id?: string
  user_id?: string
  title: string
  destination: string
  start_date?: string
  end_date?: string
  plan_data?: string
  is_public: number
  create_time?: string
}

export interface GeneratePlanPayload {
  destination: string
  days: number
  startDate?: string
  budget?: string
  preferences?: string
}

export function getMyPlans(params?: Record<string, unknown>) {
  return http.get<PageResponse<TravelPlan>>('/itinerary/my-plans', { params })
}

export function createPlan(data: TravelPlan) {
  return http.post<TravelPlan>('/itinerary/plans', data)
}

export function updatePlan(id: string, data: TravelPlan) {
  return http.put<TravelPlan>(`/itinerary/plans/${id}`, data)
}

export function deletePlan(id: string) {
  return http.delete<boolean>(`/itinerary/plans/${id}`)
}

export function generatePlan(data: GeneratePlanPayload) {
  // AI generation can take longer than ordinary API calls. Keep the global
  // 30s timeout for normal requests, but allow this endpoint up to 60s.
  return http.post<TravelPlan>('/itinerary/ai/generate', data, { timeout: 60000 })
}

export function sharePlan(id: string) {
  return http.get<{ shortLink: string }>(`/itinerary/plans/${id}/share`)
}

export function exportPlan(id: string) {
  return http.get<{ fileUrl: string }>(`/itinerary/plans/${id}/export`)
}
