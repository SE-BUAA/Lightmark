import { http } from '@/utils/request'

export interface TrainProduct {
  id: string
  name: string
  price: number
  stock: number
  soldCount: number
  categoryTags?: string[]
  extra?: Record<string, unknown>
  seats?: Record<string, number>
  prices?: Record<string, number>
}

export interface TrainSegment {
  id?: string
  name: string
  train_no?: string
  train_type?: string
  price?: number
  stock?: number
  extra?: Record<string, unknown>
  seats?: Record<string, number>
  prices?: Record<string, number>
}

export interface TrainSearchPayload {
  startStation?: string
  endStation?: string
  date?: string
  month?: string
  trainTypes: string[]
  seatTypes: string[]
}

export interface TrainOptions {
  startStations: string[]
  endStations: string[]
  dates: string[]
}

export interface TrainCalendarPayload {
  startStation?: string
  endStation?: string
  month?: string
  trainTypes: string[]
  seatTypes: string[]
}

export interface TrainCalendarDay {
  date: string
  ticketCount: number
  trainCount: number
}

export interface TrainOrderPayload {
  productId: string
  passengerName: string
  passengerPhone: string
  passengerAge: number
  seatType: string
  transferSeatTypes?: string[]
  isStudent: boolean
}

export interface TrainOrderResponse {
  orderNo: string
  status: number
  payAmount: number
  createTime: string
  expireTime: string
  expireEpochMs?: number
  pickupCode?: string
}

export interface TrainBatchOrderResponse {
  orders: TrainOrderResponse[]
  totalAmount: number
}

export interface TrainRefundResponse {
  orderNo: string
  status: number
  paidAmount: number
  refundAmount: number
  refundRule: string
}

export interface TrainChangePreviewResponse {
  orderNo: string
  trainName: string
  startStation: string
  endStation: string
  seatType: string
  candidates: TrainProduct[]
}

export interface TrainChangeResponse {
  oldOrderNo: string
  newOrderNo: string
  pickupCode: string
  oldPayAmount: number
  newPayAmount: number
  differenceAmount: number
  differenceType: 'PAY' | 'REFUND' | 'NONE'
  message: string
}

export interface TrainOrderDetail {
  orderNo: string
  status: number
  payAmount: number
  pickupCode?: string
  createTime: string
  extraInfo?: string
}

export function getTrainOptions() {
  return http.get<TrainOptions>('/trains/options')
}

export function searchTrains(data: TrainSearchPayload) {
  return http.post<TrainProduct[]>('/trains/search', data)
}

export function searchTrainTransfers(data: TrainSearchPayload) {
  return http.post<TrainProduct[]>('/trains/transfer/search', data)
}

export function getTrainCalendar(data: TrainCalendarPayload) {
  const params = new URLSearchParams()
  if (data.startStation) params.append('startStation', data.startStation)
  if (data.endStation) params.append('endStation', data.endStation)
  if (data.month) params.append('month', data.month)
  data.trainTypes.forEach((item) => params.append('trainTypes', item))
  data.seatTypes.forEach((item) => params.append('seatTypes', item))
  return http.get<TrainCalendarDay[]>('/trains/calendar', { params })
}

export function createTrainOrder(data: TrainOrderPayload) {
  return http.post<TrainOrderResponse>('/orders/train', data)
}

export function payOrder(orderNo: string, paymentMethod?: string) {
  return http.post<TrainOrderResponse>(`/orders/${orderNo}/pay`, { paymentMethod })
}

export function refundTrainOrder(orderNo: string) {
  return http.post<TrainRefundResponse>(`/orders/train/${orderNo}/refund`)
}


export function previewTrainChange(orderNo: string) {
  return http.get<TrainChangePreviewResponse>(`/orders/train/${orderNo}/change`)
}

export function changeTrainOrder(orderNo: string, targetProductId: string) {
  return http.post<TrainChangeResponse>(`/orders/train/${orderNo}/change`, {
    targetProductId
  })
}

export function getOrder(orderNo: string) {
  return http.get<TrainOrderDetail>(`/orders/${orderNo}`)
}

export function cancelOrder(orderNo: string) {
  return http.post<void>(`/orders/train/${orderNo}/cancel`)
}
