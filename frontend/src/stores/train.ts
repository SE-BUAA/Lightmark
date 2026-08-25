import { defineStore } from 'pinia'
import type { TrainOrderResponse, TrainProduct, TrainSearchPayload } from '@/api/train'

interface TrainState {
  searchParams: TrainSearchPayload
  trainsList: TrainProduct[]
  selectedTrain: TrainProduct | null
  currentOrder: TrainOrderResponse | null
  currentOrders: TrainOrderResponse[]
}

export const useTrainStore = defineStore('train', {
  state: (): TrainState => ({
    searchParams: {
      startStation: '',
      endStation: '',
      date: '',
      trainTypes: [],
      seatTypes: []
    },
    trainsList: [],
    selectedTrain: null,
    currentOrder: null,
    currentOrders: []
  }),
  actions: {
    setSearchParams(params: Partial<TrainSearchPayload>) {
      this.searchParams = { ...this.searchParams, ...params }
    },
    setTrainsList(list: TrainProduct[]) {
      this.trainsList = list
    },
    setSelectedTrain(train: TrainProduct | null) {
      this.selectedTrain = train
    },
    setCurrentOrder(order: TrainOrderResponse | null) {
      this.currentOrder = order
      this.currentOrders = order ? [order] : []
    },
    setCurrentOrders(orders: TrainOrderResponse[]) {
      this.currentOrders = orders
      this.currentOrder = orders.length > 0 ? orders[0] : null
    },
    resetOrderFlow() {
      this.selectedTrain = null
      this.currentOrder = null
      this.currentOrders = []
    }
  }
})
