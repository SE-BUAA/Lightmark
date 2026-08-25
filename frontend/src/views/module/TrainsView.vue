<template>
  <div class="module-page">
    <el-dialog v-model="showChangeCodeDialog" title="改签" width="420px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="订单号">
          <el-input v-model="changeOrderNo" placeholder="请输入订单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showChangeCodeDialog = false">取消</el-button>
        <el-button type="primary" :loading="changePreviewLoading" @click="handlePreviewChange">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-if="showChangeListDialog" v-model="showChangeListDialog" title="选择改签车次" width="860px" destroy-on-close>
      <div v-if="changePreview" class="change-context">
        <el-alert :closable="false" type="info" :title="`${changePreview.startStation} → ${changePreview.endStation}，原 ${changePreview.trainName}，座位 ${changePreview.seatType}`" />
      </div>
      <div class="change-search-bar">
        <div class="date-strip">
          <button
            v-for="d in changeDateOptions"
            :key="d.date"
            :class="['date-chip', { active: changeForm.date === d.date }]"
            @click="changeForm.date = d.date"
          >
            <strong>{{ d.day }}</strong>
            <span>{{ d.weekday }}</span>
          </button>
        </div>
      </div>
      <div class="change-train-list" v-if="changeCandidates.length">
        <div
          v-for="train in changeCandidates"
          :key="train.id"
          class="change-train-row"
          @click="handleChangeTrain(train)"
        >
          <div class="train-info">
            <strong class="train-name">{{ train.name }}</strong>
            <span class="train-time">{{ train.extra?.depart_time || '-' }} → {{ train.extra?.arrive_time || '-' }}</span>
          </div>
          <div class="train-price">
            <span class="price-tag">¥{{ train.price }}</span>
            <el-button size="small" type="primary" :loading="changeSubmitLoading === train.id" @click.stop="handleChangeTrain(train)">
              改签
            </el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="该日期暂无可用车次" />
    </el-dialog>

    <el-dialog v-if="showChangeResultDialog" v-model="showChangeResultDialog" title="改签成功" width="480px" destroy-on-close>
      <el-result icon="success" title="改签成功" :sub-title="changeResult?.message" />
      <el-descriptions v-if="changeResult" :column="1" border>
        <el-descriptions-item label="原订单号">{{ changeResult.oldOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="新订单号">{{ changeResult.newOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="新取票码">{{ changeResult.pickupCode }}</el-descriptions-item>
        <el-descriptions-item label="新票金额">¥{{ changeResult.newPayAmount }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="showChangeResultDialog = false">确定</el-button>
      </template>
    </el-dialog>

    <section class="module-hero">
      <div class="container hero-inner">
        <span class="module-icon">火车票</span>
        <h1 class="section-title">火车票</h1>
        <p class="section-subtitle">按站点、日期、车次类型和座位类型实时筛选，直接挑选合适车次。</p>
        <div class="hero-actions">
          <el-button size="large" @click="openRefundDialog">退票</el-button>
          <el-button size="large" @click="openChangeDialog">改签</el-button>
        </div>
      </div>
    </section>

    <section class="module-content container">
      <el-card class="filter-card" shadow="never">
        <template #header>
          <div class="card-header">
            <div>
              <span>{{ activeMode === 'direct' ? '直达筛选' : '中转筛选' }}</span>
              <strong>{{ routeSummary }}</strong>
            </div>
            <el-button text type="primary" :loading="searchLoading" @click="refreshTrainData">刷新</el-button>
          </div>
        </template>

        <el-tabs v-model="activeMode" class="mode-tabs">
          <el-tab-pane label="直达" name="direct" />
          <el-tab-pane label="中转" name="transfer" />
        </el-tabs>

        <el-form :model="searchForm" label-position="top" class="filter-form">
          <el-form-item label="出发站">
            <el-select v-model="searchForm.startStation" clearable filterable placeholder="请选择出发站">
              <el-option v-for="station in options.startStations" :key="station" :label="station" :value="station" />
            </el-select>
          </el-form-item>
          <el-form-item label="到达站">
            <el-select v-model="searchForm.endStation" clearable filterable placeholder="请选择到达站">
              <el-option v-for="station in options.endStations" :key="station" :label="station" :value="station" />
            </el-select>
          </el-form-item>
          <el-form-item label="列车类型">
            <el-checkbox-group v-model="searchForm.trainTypes">
              <el-checkbox-button v-for="item in trainTypeOptions" :key="item" :label="item" />
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="座位类型">
            <el-checkbox-group v-model="searchForm.seatTypes">
              <el-checkbox-button v-for="item in seatTypeOptions" :key="item" :label="item" />
            </el-checkbox-group>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="date-card" shadow="never">
        <template #header>
          <div class="card-header">
            <div>
              <span>出发日期</span>
              <strong>{{ searchForm.date }}</strong>
            </div>
          </div>
        </template>

        <div class="date-strip">
          <button
            v-for="day in dateCandidates"
            :key="day.date"
            type="button"
            :class="{ active: searchForm.date === day.date, today: day.isToday }"
            @click="selectDate(day.date)"
          >
            <span>{{ day.weekday }}</span>
            <strong>{{ day.label }}</strong>
            <small>{{ day.date }}</small>
          </button>
        </div>
      </el-card>

      <div class="result-toolbar">
        <div>
          <strong>{{ trainResults.length }}</strong>
          <span>{{ activeMode === 'direct' ? '趟可选车次' : '个中转方案' }}</span>
          <el-tag v-if="searchForm.date" effect="plain">{{ searchForm.date }}</el-tag>
        </div>
      </div>

      <el-table v-if="trainResults.length" v-loading="searchLoading" :data="pagedTrains" stripe class="train-table">
        <el-table-column v-if="activeMode === 'direct'" prop="name" label="车次" min-width="110" />
        <el-table-column v-else label="中转车次" min-width="320">
          <template #default="{ row }">
            <div class="transfer-box">
              <div v-for="(segment, index) in row.__segments" :key="`${segmentTrainNo(segment)}-${index}`" class="transfer-line">
                <span class="segment-index">第{{ index + 1 }}程</span>
                <strong>{{ segmentTrainNo(segment) }}</strong>
                <el-tag size="small" effect="plain">{{ segmentTrainType(segment) }}</el-tag>
                <span>{{ segment.extra?.start_station }} - {{ segment.extra?.end_station }}</span>
                <small>{{ segment.extra?.depart_time || '--:--' }} - {{ segment.extra?.arrive_time || '--:--' }}</small>
                <span class="segment-seats">{{ row.__segmentSeatNames[index] || '无座位信息' }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="140">
          <template #default="{ row }">{{ row.extra?.depart_time || '--:--' }} - {{ row.extra?.arrive_time || '--:--' }}</template>
        </el-table-column>
        <el-table-column v-if="activeMode === 'transfer'" label="换乘" min-width="130">
          <template #default="{ row }">
            <div class="transfer-meta">
              <strong>{{ row.extra?.middle_station || '-' }}</strong>
              <span>等待 {{ row.extra?.wait_time || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" min-width="90">
          <template #default="{ row }">{{ row.extra?.train_type }}</template>
        </el-table-column>
        <el-table-column label="可选座位" min-width="260">
          <template #default="{ row }">
            <el-tag v-for="seat in row.__seatNames" :key="seat" class="tag" size="small">{{ seat }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="价格" min-width="100">
          <template #default="{ row }">{{ row.__priceText }}</template>
        </el-table-column>
        <el-table-column label="余票" min-width="80">
          <template #default="{ row }">{{ row.__availableTickets }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" :disabled="row.__availableTickets <= 0" @click="openOrderDialog(row)">
              {{ activeMode === 'direct' ? '购买' : '购买两段' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="trainResults.length > pageSize" class="pagination-bar">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="trainResults.length"
          background
          layout="prev, pager, next, jumper, total"
        />
      </div>
      <el-empty v-else-if="hasSearched && !searchLoading" :description="activeMode === 'direct' ? '未找到符合条件的车次' : '未找到符合条件的中转方案'" />
      <el-empty v-else-if="!hasRouteSelected()" description="请选择车站" />
      <el-empty v-else description="正在加载可用车次" />
    </section>

    <el-dialog v-if="showRefundDialog" v-model="showRefundDialog" title="退票" width="420px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="订单号">
          <el-input v-model="refundOrderNo" placeholder="请输入订单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRefundDialog = false">取消</el-button>
        <el-button type="warning" :loading="refundCodeLoading" @click="handleRefundByOrderNo">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-if="showOrderDialog" v-model="showOrderDialog" title="填写购票人信息" width="520px" destroy-on-close>
      <el-form ref="orderFormRef" :model="orderForm" :rules="orderRules" label-width="90px">
        <el-form-item label="常用出行人">
          <el-select v-model="selectedTravelerKey" placeholder="选择后自动填入" clearable @change="fillTraveler">
            <el-option
              v-for="traveler in travelers"
              :key="travelerKey(traveler)"
              :label="traveler.name"
              :value="travelerKey(traveler)"
            >
              <span>{{ traveler.name }}</span>
              <span class="traveler-phone">{{ traveler.phone || '' }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="姓名" prop="passengerName">
          <el-input v-model="orderForm.passengerName" maxlength="20" placeholder="2-20个汉字" />
        </el-form-item>
        <el-form-item label="手机号" prop="passengerPhone">
          <el-input v-model="orderForm.passengerPhone" maxlength="11" placeholder="中国大陆手机号" />
        </el-form-item>
        <el-form-item label="年龄" prop="passengerAge">
          <el-input-number v-model="orderForm.passengerAge" :min="1" :max="120" controls-position="right" />
        </el-form-item>
        <el-form-item label="学生票">
          <el-checkbox v-model="orderForm.isStudent">我是学生</el-checkbox>
        </el-form-item>
        <el-form-item v-if="!isSelectedTransfer" label="座位类型" prop="seatType">
          <el-select v-model="orderForm.seatType" placeholder="请选择座位类型">
            <el-option
              v-for="seat in currentSeatOptions"
              :key="seat"
              :label="`${seat}（余票 ${selectedTrainSeatCount(seat)} 张）`"
              :value="seat"
            />
          </el-select>
        </el-form-item>
        <template v-else>
          <el-form-item
            v-for="(segment, index) in selectedTransferSegments"
            :key="`${segmentTrainNo(segment)}-${index}`"
            :label="`第${index + 1}程座位`"
            :prop="index === 0 ? 'firstTransferSeatType' : 'secondTransferSeatType'"
          >
            <el-select v-model="orderForm.transferSeatTypes[index]" placeholder="请选择座位类型">
              <el-option
                v-for="seat in availableSeatNames(segment)"
                :key="seat"
                :label="`${segmentTrainNo(segment)} · ${seat}（余票 ${segmentSeatCount(segment, seat)} 张）`"
                :value="seat"
              />
            </el-select>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showOrderDialog = false">取消</el-button>
        <el-button type="primary" :loading="orderLoading" @click="handleSubmitOrder">提交订单</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-if="showPayDialog"
      v-model="showPayDialog"
      :title="trainStore.currentOrders.length > 1 ? '订单支付（共' + trainStore.currentOrders.length + '笔）' : '订单支付'"
      width="520px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <template v-for="(order, idx) in trainStore.currentOrders" :key="order.orderNo">
        <el-descriptions v-if="idx > 0" style="margin-top: 12px" :column="1" border>
          <el-descriptions-item :label="'第' + (idx + 1) + '程订单号'">{{ order.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="金额">¥{{ order.payAmount }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="idx === 0">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="车次">{{ trainStore.selectedTrain?.name }}</el-descriptions-item>
            <el-descriptions-item label="金额">¥{{ order.payAmount }}</el-descriptions-item>
            <el-descriptions-item label="支付方式">
              <el-radio-group v-model="paymentMethod" size="small">
                <el-radio-button label="WECHAT">微信</el-radio-button>
                <el-radio-button label="ALIPAY">支付宝</el-radio-button>
                <el-radio-button label="MOCK_PAY">模拟支付</el-radio-button>
              </el-radio-group>
            </el-descriptions-item>
            <el-descriptions-item label="剩余支付时间">
              <el-text type="danger" size="large">{{ countdownText }}</el-text>
            </el-descriptions-item>
          </el-descriptions>
          <div v-if="trainStore.currentOrders.length > 1" class="order-total-line">
            合计：<strong>¥{{ totalPayAmount }}</strong>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button :loading="cancelLoading" @click="handleCancelOrder">取消订单</el-button>
        <el-button type="primary" :loading="payLoading" @click="handlePayOrder">支付</el-button>
      </template>
    </el-dialog>

    <el-dialog v-if="showTicketDialog" v-model="showTicketDialog" title="支付成功" width="420px" destroy-on-close>
      <div class="ticket-code">
        <el-result icon="success" title="订票成功" sub-title="订单已支付成功" />
        <div class="ticket-actions">
          <el-button @click="showTicketDialog = false">关闭</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addTraveler, getTravelers, type TravelerDTO } from '@/api/user'
import {
  cancelOrder,
  changeTrainOrder,
  createTrainOrder,
  getOrder,
  getTrainOptions,
  payOrder,
  previewTrainChange,
  searchTrainTransfers,
  searchTrains,
  type TrainBatchOrderResponse
} from '@/api/train'
import type { TrainChangePreviewResponse, TrainChangeResponse, TrainOptions, TrainProduct, TrainSegment } from '@/api/train'
import { useTrainStore } from '@/stores/train'

interface DateCandidate {
  date: string
  label: string
  weekday: string
  isToday: boolean
}

type TrainTableRow = TrainProduct & {
  __seatNames: string[]
  __availableTickets: number
  __priceText: string
  __segments: TrainSegment[]
  __segmentSeatNames: string[]
}

const route = useRoute()
const trainStore = useTrainStore()
const trainTypeOptions = ['高铁', '动车', '普速']
const seatTypeOptions = ['商务座', '一等座', '二等座', '硬卧', '软卧', '硬座']
const weekdayLabels = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const showOrderDialog = ref(false)
const showPayDialog = ref(false)
const showTicketDialog = ref(false)
const showRefundDialog = ref(false)
const showChangeCodeDialog = ref(false)
const showChangeListDialog = ref(false)
const showChangeResultDialog = ref(false)
const searchLoading = ref(false)
const orderLoading = ref(false)
const payLoading = ref(false)
const cancelLoading = ref(false)
const refundCodeLoading = ref(false)
const changePreviewLoading = ref(false)
const hasSearched = ref(false)
const refundOrderNo = ref('')
const changeOrderNo = ref('')
const changePreview = ref<TrainChangePreviewResponse | null>(null)
const changeResult = ref<TrainChangeResponse | null>(null)
const changeForm = reactive({ date: '' })
const changeSubmitLoading = ref<string>('')

const changeDateOptions = computed(() => {
  const today = new Date()
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(today)
    d.setDate(d.getDate() + i)
    const weekdayLabels = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    return {
      date: formatDate(d),
      day: String(d.getDate()).padStart(2, '0'),
      weekday: weekdayLabels[d.getDay()]
    }
  })
})

const changeCandidates = computed(() => {
  if (!changePreview.value?.candidates.length) return []
  const selectedDate = changeForm.date
  if (!selectedDate) return changePreview.value.candidates.slice(0, 30)
  return changePreview.value.candidates.filter(c => {
    const extraDate = c.extra?.date
    const ticketDate = (c as any).departureDate || extraDate || ''
    return ticketDate === selectedDate || !ticketDate
  }).slice(0, 30)
})
const paymentMethod = ref<'WECHAT' | 'ALIPAY' | 'MOCK_PAY'>('WECHAT')
const countdownSeconds = ref(0)
const timer = ref<number>()
const pollTimer = ref<number>()
const autoSearchTimer = ref<number>()
const currentPage = ref(1)
const pageSize = 10
const activeMode = ref<'direct' | 'transfer'>('direct')
const isInitializing = ref(true)
const trainResults = shallowRef<TrainProduct[]>([])
const pagedTrains = shallowRef<TrainTableRow[]>([])
let searchRequestId = 0

const options = reactive<TrainOptions>({
  startStations: [],
  endStations: [],
  dates: []
})

const searchForm = reactive({
  startStation: trainStore.searchParams.startStation || '',
  endStation: trainStore.searchParams.endStation || '',
  date: trainStore.searchParams.date || formatDate(new Date()),
  trainTypes: [...(trainStore.searchParams.trainTypes || [])],
  seatTypes: [...(trainStore.searchParams.seatTypes || [])]
})

const orderFormRef = ref<FormInstance>()
const travelers = ref<TravelerDTO[]>([])
const selectedTravelerKey = ref<string>()
const orderForm = reactive({
  passengerName: '',
  passengerPhone: '',
  passengerAge: 25,
  seatType: '',
  transferSeatTypes: ['', ''],
  isStudent: false
})

const orderRules: FormRules = {
  passengerName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { pattern: /^[\u4e00-\u9fa5]{2,20}$/, message: '姓名必须为2-20个汉字', trigger: 'blur' }
  ],
  passengerPhone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的中国大陆手机号', trigger: 'blur' }
  ],
  passengerAge: [{ required: true, type: 'number', message: '年龄必须为1-120之间的正整数', trigger: 'change' }],
  seatType: [{ required: true, message: '请选择座位类型', trigger: 'change' }],
  firstTransferSeatType: [
    {
      validator: (_rule, _value, callback) => {
        if (isSelectedTransfer.value && !orderForm.transferSeatTypes[0]) callback(new Error('请选择第一程座位类型'))
        else callback()
      },
      trigger: 'change'
    }
  ],
  secondTransferSeatType: [
    {
      validator: (_rule, _value, callback) => {
        if (isSelectedTransfer.value && !orderForm.transferSeatTypes[1]) callback(new Error('请选择第二程座位类型'))
        else callback()
      },
      trigger: 'change'
    }
  ]
}

const travelerKey = (traveler: TravelerDTO) => traveler.id || `${traveler.name}-${traveler.id_card || traveler.idCard || ''}-${traveler.phone || ''}`

const fillTraveler = () => {
  const traveler = travelers.value.find(item => travelerKey(item) === selectedTravelerKey.value)
  if (!traveler) return
  orderForm.passengerName = traveler.name
  orderForm.passengerPhone = traveler.phone || ''
}

const saveTravelerIfNeeded = async () => {
  const passengerName = orderForm.passengerName.trim()
  const passengerPhone = orderForm.passengerPhone.trim()
  if (!passengerName || !passengerPhone) return
  const exists = travelers.value.some(item => item.name === passengerName && item.phone === passengerPhone)
  if (exists) return
  try {
    const saved = await addTraveler({
      name: passengerName,
      id_card: `${passengerName}-${passengerPhone}`,
      phone: passengerPhone,
      id_type: 0,
    })
    travelers.value = [saved, ...travelers.value]
  } catch {
    // ignore persistence failure and keep booking flow moving
  }
}

const routeSummary = computed(() => {
  const start = searchForm.startStation || '任意出发站'
  const end = searchForm.endStation || '任意到达站'
  return `${start} - ${end}`
})

const currentSeatOptions = computed(() => {
  return trainStore.selectedTrain ? availableSeatNames(trainStore.selectedTrain) : []
})

const selectedTransferSegments = computed(() => {
  return trainStore.selectedTrain ? transferSegments(trainStore.selectedTrain).slice(0, 2) : []
})

const isSelectedTransfer = computed(() => selectedTransferSegments.value.length >= 2)

const dateCandidates = computed<DateCandidate[]>(() => {
  const today = formatDate(new Date())
  return Array.from({ length: 30 }, (_, index) => {
    const current = new Date()
    current.setDate(current.getDate() + index)
    const date = formatDate(current)
    return {
      date,
      label: `${current.getMonth() + 1}/${current.getDate()}`,
      weekday: date === today ? '今天' : weekdayLabels[current.getDay()],
      isToday: date === today
    }
  })
})

const countdownText = computed(() => {
  const minutes = Math.floor(countdownSeconds.value / 60).toString().padStart(2, '0')
  const seconds = (countdownSeconds.value % 60).toString().padStart(2, '0')
  return `${minutes}:${seconds}`
})

const updatePagedTrains = () => {
  const start = (currentPage.value - 1) * pageSize
  pagedTrains.value = trainResults.value.slice(start, start + pageSize).map(enrichTrainRow)
}

const clearTrainResults = () => {
  currentPage.value = 1
  trainResults.value = []
  updatePagedTrains()
}

const errorMessage = (error: unknown, fallback: string) => {
  return error instanceof Error ? error.message : fallback
}

const availableTickets = (train: TrainProduct) => {
  return Math.max(0, Number(train.stock || 0) - Number(train.soldCount || 0))
}

const availableSeatNames = (train: TrainProduct) => {
  const seats = train.seats || {}
  return Object.keys(seats)
}

const transferSegments = (train: TrainProduct): TrainSegment[] => {
  const segments = train.extra?.segments
  return Array.isArray(segments) ? (segments as TrainSegment[]) : []
}

const firstText = (...values: unknown[]) => {
  for (const value of values) {
    const text = value == null ? '' : String(value).trim()
    if (text) return text
  }
  return ''
}

const segmentTrainNo = (segment: TrainSegment) => {
  return firstText(segment.train_no, segment.extra?.train_no, segment.name, '未知车次')
}

const segmentTrainType = (segment: TrainSegment) => {
  const trainNo = segmentTrainNo(segment)
  if (trainNo && trainNo !== '未知车次') {
    const first = trainNo.charAt(0).toUpperCase()
    if (first === 'G') return '高铁'
    if (first === 'D') return '动车'
    return '普速'
  }
  return firstText(segment.train_type, segment.extra?.train_type, '-')
}

const totalPayAmount = computed(() =>
  trainStore.currentOrders.reduce((sum, o) => sum + Number(o.payAmount || 0), 0)
)

const formatTrainPrices = (train: TrainProduct) => {
  const prices = train.prices || {}
  const displayPrices = availableSeatNames(train)
    .map((seat) => prices[seat])
    .filter((price): price is number => typeof price === 'number' && price > 0)
  if (!displayPrices.length) {
    return train.price ? `¥${train.price}` : '¥0'
  }
  const min = Math.min(...displayPrices)
  const max = Math.max(...displayPrices)
  return min === max ? `¥${min}` : `¥${min} - ¥${max}`
}

const enrichTrainRow = (train: TrainProduct): TrainTableRow => {
  const segments = transferSegments(train)
  return {
    ...train,
    __seatNames: availableSeatNames(train),
    __availableTickets: availableTickets(train),
    __priceText: formatTrainPrices(train),
    __segments: segments,
    __segmentSeatNames: segments.map((segment) => availableSeatNames(segment).join('、'))
  }
}

const selectedTrainSeatCount = (seat: string) => {
  return Number(trainStore.selectedTrain?.seats?.[seat] || 0)
}

const segmentSeatCount = (segment: TrainSegment, seat: string) => {
  return Number(segment.seats?.[seat] || 0)
}

const hasRouteSelected = () => Boolean(searchForm.startStation && searchForm.endStation)

function formatDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const loadOptions = async () => {
  try {
    const data = await getTrainOptions()
    options.startStations = data.startStations || []
    options.endStations = data.endStations || []
    options.dates = data.dates || []
  } catch {
    ElMessage.warning('筛选项加载失败，请稍后重试')
  }
}

const loadTravelers = async () => {
  if (travelers.value.length) return
  try {
    travelers.value = await getTravelers()
  } catch {
    travelers.value = []
  }
}

const refreshTrainData = async () => {
  const requestId = ++searchRequestId
  if (!hasRouteSelected()) {
    trainStore.setSearchParams({ ...searchForm })
    clearTrainResults()
    hasSearched.value = false
    searchLoading.value = false
    return
  }
  searchLoading.value = true
  try {
    trainStore.setSearchParams({ ...searchForm })
    const payload = {
      startStation: searchForm.startStation,
      endStation: searchForm.endStation,
      date: searchForm.date,
      trainTypes: searchForm.trainTypes,
      seatTypes: searchForm.seatTypes
    }
    const trains = activeMode.value === 'transfer' ? await searchTrainTransfers(payload) : await searchTrains(payload)
    if (requestId !== searchRequestId) return
    currentPage.value = 1
    trainResults.value = trains
    updatePagedTrains()
    hasSearched.value = true
  } catch (error: unknown) {
    if (requestId !== searchRequestId) return
    ElMessage.error(errorMessage(error, '查询失败，请稍后重试'))
  } finally {
    if (requestId === searchRequestId) {
      searchLoading.value = false
    }
  }
}

const scheduleRefresh = () => {
  if (isInitializing.value) return
  if (autoSearchTimer.value) window.clearTimeout(autoSearchTimer.value)
  autoSearchTimer.value = window.setTimeout(async () => {
    if (!hasRouteSelected()) {
      clearTrainResults()
      hasSearched.value = false
      return
    }
    await refreshTrainData()
  }, 250)
}

const selectDate = (date: string) => {
  searchForm.date = date
  trainStore.setSearchParams({ ...searchForm })
  currentPage.value = 1
  updatePagedTrains()
  void refreshTrainData()
}

const openRefundDialog = () => {
  refundOrderNo.value = ''
  showRefundDialog.value = true
}

const openChangeDialog = () => {
  changeOrderNo.value = ''
  changePreview.value = null
  changeResult.value = null
  showChangeCodeDialog.value = true
}

const handlePreviewChange = async () => {
  if (!changeOrderNo.value.trim()) {
    ElMessage.warning('请输入订单号')
    return
  }
  changePreviewLoading.value = true
  try {
    const result = await previewTrainChange(changeOrderNo.value.trim())
    changePreview.value = result
    changeForm.date = changeDateOptions.value[0]?.date || ''
    showChangeCodeDialog.value = false
    showChangeListDialog.value = true
  } catch (error: unknown) {
    ElMessage.error(errorMessage(error, '改签查询失败，请稍后重试'))
  } finally {
    changePreviewLoading.value = false
  }
}

const handleChangeTrain = async (train: TrainProduct) => {
  try {
    await ElMessageBox.confirm('确认改签？原订单将不可用。', '改签确认', {
      confirmButtonText: '确认改签',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  const orderNo = changeOrderNo.value.trim()
  changeSubmitLoading.value = train.id
  try {
    const result = await changeTrainOrder(orderNo, train.id)
    changeResult.value = result
    const msg = result.differenceType === 'PAY'
      ? `需补差价：¥${result.differenceAmount}`
      : result.differenceType === 'REFUND'
        ? `已退差价：¥${result.differenceAmount}`
        : '无需补差价'
    ElMessage.success(`${msg}，新订单号：${result.newOrderNo}`)
    showChangeListDialog.value = false
    showChangeResultDialog.value = true
    await refreshTrainData()
  } catch (error: unknown) {
    ElMessage.error(errorMessage(error, '改签失败，请稍后重试'))
  } finally {
    changeSubmitLoading.value = ''
  }
}

const openOrderDialog = (train: TrainProduct) => {
  trainStore.setSelectedTrain(train)
  void loadTravelers()
  selectedTravelerKey.value = undefined
  orderForm.passengerName = ''
  orderForm.passengerPhone = ''
  orderForm.passengerAge = 25
  orderForm.seatType = currentSeatOptions.value[0] || ''
  const segments = transferSegments(train)
  orderForm.transferSeatTypes = [
    segments[0] ? availableSeatNames(segments[0])[0] || '' : '',
    segments[1] ? availableSeatNames(segments[1])[0] || '' : ''
  ]
  orderForm.isStudent = false
  paymentMethod.value = 'WECHAT'
  orderFormRef.value?.clearValidate()
  showOrderDialog.value = true
}

const handleSubmitOrder = async () => {
  const valid = await orderFormRef.value?.validate().catch(() => false)
  if (!valid || !trainStore.selectedTrain) return

  orderLoading.value = true
  try {
    await saveTravelerIfNeeded()
    let orders: TrainOrderResponse[]

    if (isSelectedTransfer.value) {
      // transfer: create two separate orders, one per segment
      const segments = transferSegments(trainStore.selectedTrain)
      const results: TrainOrderResponse[] = []
      for (let i = 0; i < segments.length; i++) {
        const segment = segments[i]
        const order = await createTrainOrder({
          productId: segment.id || trainStore.selectedTrain.id,
          passengerName: orderForm.passengerName,
          passengerPhone: orderForm.passengerPhone,
          passengerAge: orderForm.passengerAge,
          seatType: orderForm.transferSeatTypes[i] || '',
          transferSeatTypes: undefined,
          isStudent: orderForm.isStudent
        })
        results.push(order)
      }
      orders = results
    } else {
      // direct: single order
      const order = await createTrainOrder({
        productId: trainStore.selectedTrain.id,
        passengerName: orderForm.passengerName,
        passengerPhone: orderForm.passengerPhone,
        passengerAge: orderForm.passengerAge,
        seatType: orderForm.seatType,
        transferSeatTypes: undefined,
        isStudent: orderForm.isStudent
      })
      orders = [order]
    }

    trainStore.setCurrentOrders(orders)
    ElMessage.success(orders.length > 1 ? `已创建${orders.length}笔订单，请在10分钟内支付` : '下单成功，请在10分钟内支付')
    showOrderDialog.value = false
    showPayDialog.value = true
    await refreshTrainData()
    startCountdown(orders[0].expireEpochMs || (new Date(orders[0].expireTime).getTime()))
  } catch (error: unknown) {
    ElMessage.error(errorMessage(error, '下单失败，请稍后重试'))
  } finally {
    orderLoading.value = false
  }
}

const startCountdown = (expireEpochMs: number) => {
  clearTimers()
  const update = () => {
    countdownSeconds.value = Math.max(0, Math.floor((expireEpochMs - Date.now()) / 1000))
  }
  update()
  timer.value = window.setInterval(() => {
    update()
    if (countdownSeconds.value <= 0) {
      handleTimeout()
    }
  }, 1000)
  pollTimer.value = window.setInterval(pollOrderStatus, 5000)
}

const updateCountdown = (expireEpochMs: number) => {
  countdownSeconds.value = Math.max(0, Math.floor((expireEpochMs - Date.now()) / 1000))
}

const pollOrderStatus = async () => {
  const orders = trainStore.currentOrders
  if (!orders.length) return
  for (const order of orders) {
    try {
      const result = await getOrder(order.orderNo)
      if (result.status === 2) {
        showPayDialog.value = false
        clearTimers()
        ElMessage.warning('订单已取消，请重新选择车次')
        await refreshTrainData()
        return
      }
    } catch {
      // 轮询失败不打断当前支付流程。
    }
  }
}

const handlePayOrder = async () => {
  const orders = trainStore.currentOrders
  if (!orders.length) return
  payLoading.value = true
  try {
    for (const order of orders) {
      await payOrder(order.orderNo, paymentMethod.value)
    }
    showPayDialog.value = false
    showTicketDialog.value = true
    clearTimers()
  } catch (error: unknown) {
    ElMessage.error(errorMessage(error, '支付失败，请稍后重试'))
    await pollOrderStatus()
  } finally {
    payLoading.value = false
  }
}

const handleCancelOrder = async () => {
  const orders = trainStore.currentOrders
  if (!orders.length) return
  cancelLoading.value = true
  try {
    for (const order of orders) {
      await cancelOrder(order.orderNo)
    }
    ElMessage.success('订单已取消')
    showPayDialog.value = false
    clearTimers()
    await refreshTrainData()
  } catch (error: unknown) {
    ElMessage.error(errorMessage(error, '取消失败，请稍后重试'))
  } finally {
    cancelLoading.value = false
  }
}

const handleRefundByOrderNo = async () => {
  const orderNo = refundOrderNo.value.trim()
  if (!orderNo) {
    ElMessage.warning('请输入订单号')
    return
  }
  refundCodeLoading.value = true
  try {
    const result = await refundTrainOrder(orderNo)
    ElMessage.success(`${result.refundRule}，退款金额：¥${result.refundAmount}`)
    showRefundDialog.value = false
    await refreshTrainData()
  } catch (error: unknown) {
    ElMessage.error(errorMessage(error, '退票失败，请稍后重试'))
  } finally {
    refundCodeLoading.value = false
  }
}

const handleTimeout = async () => {
  clearTimers()
  showPayDialog.value = false
  await ElMessageBox.alert('支付超时，订单已自动取消，请重新选择车次。', '超时提示', {
    confirmButtonText: '确定',
    type: 'warning'
  })
  await refreshTrainData()
}

const clearTimers = () => {
  if (timer.value) window.clearInterval(timer.value)
  if (pollTimer.value) window.clearInterval(pollTimer.value)
  if (autoSearchTimer.value) window.clearTimeout(autoSearchTimer.value)
  timer.value = undefined
  pollTimer.value = undefined
  autoSearchTimer.value = undefined
}

watch(
  () => [activeMode.value, searchForm.startStation, searchForm.endStation, searchForm.date, searchForm.trainTypes.join(','), searchForm.seatTypes.join(',')],
  scheduleRefresh
)

watch(currentPage, updatePagedTrains)

onMounted(async () => {
  await nextTick()
  window.requestAnimationFrame(() => {
    void initializePage()
  })
})

const initializePage = async () => {
  await loadOptions()
  isInitializing.value = false
  await refreshTrainData()
  // Auto-open change dialog when navigated from user center with order number
  const changeNo = route.query.changeOrderNo as string
  if (changeNo) {
    changeOrderNo.value = changeNo
    // Show loading, then auto-open change dialog
    ElMessage.info('正在查询改签信息...')
    setTimeout(() => handlePreviewChange(), 500)
  }
}

onUnmounted(clearTimers)
</script>

<style scoped>
.module-content {
  padding: 32px 0 60px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.filter-card,
.date-card {
  border-radius: 8px;
}

.mode-tabs {
  margin-bottom: 12px;
}

.card-header,
.result-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.card-header > div:first-child,
.result-toolbar > div {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.card-header span,
.result-toolbar span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.card-header strong,
.result-toolbar strong {
  color: var(--el-text-color-primary);
  font-size: 18px;
}

.filter-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 8px 18px;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.filter-form :deep(.el-select) {
  width: 100%;
}

.date-strip {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 2px 2px 8px;
  scroll-snap-type: x proximity;
}

.date-strip button {
  flex: 0 0 112px;
  min-height: 76px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  scroll-snap-align: start;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.date-strip button:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 8px 20px rgba(35, 65, 95, 0.12);
  transform: translateY(-1px);
}

.date-strip button.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.date-strip button.today span {
  color: var(--el-color-primary);
  font-weight: 700;
}

.date-strip button span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.date-strip button strong {
  color: var(--el-color-primary);
  font-size: 18px;
}

.date-strip button small {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.train-table {
  width: 100%;
}

.transfer-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.transfer-line {
  display: grid;
  grid-template-columns: 52px 76px 58px minmax(120px, 1fr) 96px minmax(120px, 1fr);
  align-items: center;
  gap: 8px;
}

.transfer-meta {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.transfer-line strong,
.transfer-meta strong {
  color: var(--el-text-color-primary);
}

.transfer-line span,
.transfer-line small,
.transfer-meta span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.segment-index {
  color: var(--el-color-primary);
  font-weight: 600;
}

.segment-seats {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 4px 0 0;
}

.tag {
  margin: 2px 6px 2px 0;
}

.ticket-code {
  text-align: center;
}

.ticket-actions {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  gap: 12px;
}

.change-alert {
  margin-bottom: 16px;
}

.order-total-line {
  text-align: right;
  margin-top: 12px;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.order-total-line strong {
  color: var(--el-color-danger);
  font-size: 18px;
}

.change-context {
  margin-bottom: 16px;
}

.change-search-bar {
  margin-bottom: 16px;
}

.change-search-bar .date-strip {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 2px 2px 8px;
}

.date-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 80px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  cursor: pointer;
  transition: all 0.2s;
  font: inherit;
}

.date-chip.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.date-chip strong {
  font-size: 18px;
  line-height: 1;
}

.date-chip span {
  font-size: 12px;
  opacity: 0.7;
}

.change-train-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 420px;
  overflow-y: auto;
}

.change-train-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  cursor: pointer;
  transition: all 0.15s;
}

.change-train-row:hover {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.change-train-row .train-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.change-train-row .train-name {
  font-size: 16px;
  font-weight: 600;
}

.change-train-row .train-time {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.change-train-row .train-price {
  display: flex;
  align-items: center;
  gap: 12px;
}

.price-tag {
  font-size: 18px;
  font-weight: 700;
  color: var(--el-color-danger);
}

.traveler-phone {
  float: right;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 768px) {
  .filter-form {
    grid-template-columns: 1fr;
  }

  .date-strip button {
    flex-basis: 92px;
    min-height: 68px;
  }

  .transfer-line {
    grid-template-columns: 1fr;
  }
}
</style>
