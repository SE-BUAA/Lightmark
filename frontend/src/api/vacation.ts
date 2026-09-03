import { http } from '@/utils/request'
import type { TrainOrderResponse, TrainOrderDetail } from '@/api/train'

export interface VacationProduct {
  id: string
  name: string
  price: number
  stock: number
  soldCount: number
  categoryTags?: string[]
  extra?: Record<string, unknown>
}

export interface VacationSearchPayload {
  destination?: string
  departCity?: string
  date?: string
  minDays?: number
  maxDays?: number
  minPrice?: number
  maxPrice?: number
  tags: string[]
}

export interface VacationOptions {
  destinations: string[]
  departCities: string[]
  dates: string[]
  tags: string[]
}

export interface VacationOrderPayload {
  productId: string
  travelerName: string
  travelerPhone: string
  travelerCount: number
  cancellationInsurance: boolean
}

export interface VacationRefundResponse {
  orderNo: string
  status: number
  paidAmount: number
  refundAmount: number
  refundRule: string
}

export interface VacationAiDetailResponse {
  productId: string
  content: string
}

export interface VacationAssistantResponse {
  orderNo: string
  destination: string
  date: string
  content: string
}

export function getVacationOptions() {
  return http.get<VacationOptions>('/vacations/options')
}

export function searchVacations(data: VacationSearchPayload) {
  return http.post<VacationProduct[]>('/vacations/search', data)
}

export function generateVacationDetail(productId: string) {
  return http.get<VacationAiDetailResponse>(`/vacations/${productId}/detail-ai`)
}

export function createVacationOrder(data: VacationOrderPayload) {
  return http.post<TrainOrderResponse>('/vacations/order', data)
}

export function payVacationOrder(orderNo: string, paymentMethod?: string) {
  return http.post<TrainOrderResponse>(`/orders/${orderNo}/pay`, { paymentMethod })
}

export function refundVacationOrder(orderNo: string) {
  return http.post<VacationRefundResponse>(`/orders/vacation/${orderNo}/refund`)
}

export function refundVacationOrderByPickupCode(pickupCode: string) {
  return http.post<VacationRefundResponse>('/orders/vacation/refund', undefined, {
    params: { pickupCode }
  })
}

export function getVacationOrder(orderNo: string) {
  return http.get<TrainOrderDetail>(`/orders/${orderNo}`)
}

export function generateVacationAssistant(orderNo: string) {
  return http.get<VacationAssistantResponse>(`/orders/vacation/${orderNo}/assistant`)
}

export function cancelVacationOrder(orderNo: string) {
  return http.post<void>(`/orders/train/${orderNo}/cancel`)
}
