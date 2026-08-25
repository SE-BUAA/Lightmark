<template>
  <div class="flight-page" :class="{ 'is-dark-mode': isFlightDark }">
    <main class="flight-main">
      <section class="booking-shell">
        <div class="booking-hero">
          <div>
            <p>Lightmark Air Desk</p>
            <h1>{{ cityName(activeLeg.departureCity) }} → {{ cityName(activeLeg.arrivalCity) }}</h1>
            <span>{{ activeLeg.departureDate }} · {{ cabinLabel(form.cabin) }} · {{ travelerCount }} 位乘机人</span>
          </div>
          <div class="route-metrics" aria-label="当前航线概览">
            <div>
              <strong>{{ visibleFlightCount }}</strong>
              <span>当前航班</span>
            </div>
            <div>
              <strong>{{ lowestCalendarDay ? calendarPriceText(lowestCalendarDay) : "--" }}</strong>
              <span>年内低价</span>
            </div>
            <div>
              <strong>{{ directFlightCount }}</strong>
              <span>直飞可选</span>
            </div>
          </div>
        </div>

        <div class="m3-workflow" aria-label="机票预订流程">
          <div
            v-for="(step, index) in requirementSteps"
            :key="step.title"
            class="workflow-step"
            :class="{ active: index <= activeWorkflowIndex }"
          >
            <span>{{ String(index + 1).padStart(2, "0") }}</span>
            <strong>{{ step.title }}</strong>
            <small>{{ step.desc }}</small>
          </div>
        </div>

        <div class="booking-card flight-search-card">
          <div class="booking-card-head">
            <div>
              <span>搜索条件</span>
              <strong>快速锁定合适航班</strong>
            </div>
            <small>{{ form.directOnly ? "仅直飞" : "含经停" }} · {{ cabinLabel(form.cabin) }}</small>
          </div>

          <div class="ai-command">
            <div class="ai-copy">
              <span>AI 自然语言搜索</span>
              <strong>一句话改写行程条件</strong>
            </div>
            <div class="ai-input-row">
              <el-input
                v-model="aiQuery"
                placeholder="例如：从北京到上海，明天出发，2 位成人，经济舱，只看直飞"
                clearable
                @keyup.enter="applyAiSearch"
              />
              <el-button type="primary" :loading="aiSearchLoading" @click="applyAiSearch">解析并搜索</el-button>
            </div>
            <div class="ai-samples">
              <button v-for="sample in aiSamples" :key="sample" type="button" @click="useAiSample(sample)">
                {{ sample }}
              </button>
            </div>
            <p v-if="aiSearchSummary">{{ aiSearchSummary }}</p>
          </div>

          <div class="trip-toolbar">
            <el-radio-group v-model="form.tripType" @change="handleTripTypeChange">
              <el-radio-button v-for="trip in tripTypes" :key="trip.value" :label="trip.value">
                {{ trip.label }}
              </el-radio-button>
            </el-radio-group>
            <div class="quick-options">
              <el-checkbox v-model="form.directOnly" :disabled="form.tripType === 'transfer'" @change="handleDirectOnlyChange">仅看直飞</el-checkbox>
              <el-checkbox v-model="form.lowPriceFirst" @change="searchFlights">低价优先</el-checkbox>
            </div>
          </div>

          <div class="search-grid">
            <CityPicker
              label="出发地"
              :city-code="form.departureCity"
              :cities="cities"
              :tabs="cityTabs"
              @select="selectDepartureCity"
            />
            <button class="swap-button" type="button" title="交换出发地和目的地" @click="swapCities">⇄</button>
            <CityPicker
              label="目的地"
              :city-code="form.arrivalCity"
              :cities="cities"
              :tabs="cityTabs"
              @select="selectArrivalCity"
            />
            <label class="field-card date-card">
              <span>出发日期</span>
              <el-date-picker v-model="form.departureDate" type="date" value-format="YYYY-MM-DD" @change="handlePrimaryDepartureDateChange" />
            </label>
            <label v-if="form.tripType === 'roundtrip'" class="field-card date-card">
              <span>返程日期</span>
              <el-date-picker v-model="form.returnDate" type="date" value-format="YYYY-MM-DD" @change="handleReturnDateChange" />
            </label>
            <label class="field-card">
              <span>乘机人</span>
              <strong>{{ form.adultCount }} 成人 · {{ form.childCount }} 儿童</strong>
              <div class="counter-row">
                <el-input-number v-model="form.adultCount" :min="1" :max="9" size="small" @change="handlePassengerCountChange" />
                <el-input-number v-model="form.childCount" :min="0" :max="9" size="small" @change="handlePassengerCountChange" />
              </div>
            </label>
            <label class="field-card">
              <span>舱位</span>
              <el-select v-model="form.cabin" @change="handleCabinChange">
                <el-option label="不限舱" value="" />
                <el-option label="经济舱" value="ECONOMY" />
                <el-option label="商务舱" value="BUSINESS" />
                <el-option label="头等舱" value="FIRST" />
              </el-select>
            </label>
          </div>

          <div v-if="form.tripType === 'multi'" class="segment-panel">
            <div v-for="(segment, index) in multiSegments" :key="segment.id" class="segment-row">
              <span>第 {{ index + 1 }} 程</span>
              <CityPicker
                :label="`出发地`"
                :city-code="segment.departureCity"
                :cities="cities"
                :tabs="cityTabs"
                @select="selectSegmentCity(index, 'departureCity', $event)"
              />
              <CityPicker
                :label="`目的地`"
                :city-code="segment.arrivalCity"
                :cities="cities"
                :tabs="cityTabs"
                @select="selectSegmentCity(index, 'arrivalCity', $event)"
              />
              <label class="field-card date-card">
                <span>出发日期</span>
                <el-date-picker v-model="segment.departureDate" type="date" value-format="YYYY-MM-DD" @change="handleSegmentDateChange(index)" />
              </label>
              <el-button v-if="multiSegments.length > 2" text type="danger" @click="removeSegment(index)">删除</el-button>
            </div>
            <el-button text type="primary" :disabled="multiSegments.length >= 4" @click="addSegment">添加航段</el-button>
          </div>

          <div class="booking-actions">
            <div class="assist-options">
              <span class="assist-label">附加服务</span>
              <el-checkbox v-model="orderOptions.insurance">保险</el-checkbox>
              <el-checkbox v-model="orderOptions.extraBaggage">额外行李</el-checkbox>
              <el-checkbox v-model="orderOptions.seatSelection">在线选座</el-checkbox>
            </div>
            <el-button class="search-button" type="primary" size="large" :loading="loading" @click="refreshRouteData">
              搜索航班
            </el-button>
          </div>
        </div>
      </section>

      <section class="special-fares flight-feature-card">
        <div class="section-head compact">
          <div>
            <p>特价机票</p>
            <h2>最近低价与线路规划</h2>
          </div>
          <div class="special-fare-actions">
            <span>{{ specialFareDeals.length > 0 ? `${specialFareDeals.length} 个低价日期` : "等待价格日历" }}</span>
            <button type="button" :disabled="availableCalendarDays.length <= 1" @click="refreshSpecialFares">
              换一批
            </button>
          </div>
        </div>
        <div v-if="specialFareDeals.length > 0" class="special-fare-grid">
          <button
            v-for="deal in specialFareDeals"
            :key="deal.date"
            type="button"
            class="special-fare-card"
            @click="chooseSpecialFare(deal)"
          >
            <span>{{ deal.badge }}</span>
            <strong>{{ calendarPriceText(deal) }}</strong>
            <small>{{ calendarDayLabel(deal.date) }} · {{ lowFareRouteLabel }}</small>
          </button>
        </div>
        <div v-if="lowPriceRoutePlans.length > 0" class="low-route-plans">
          <button
            v-for="plan in lowPriceRoutePlans"
            :key="`${plan.mode}-${plan.date}`"
            type="button"
            @click="applyLowPricePlan(plan)"
          >
            <span>{{ plan.label }}</span>
            <strong>{{ plan.title }}</strong>
            <small>{{ plan.summary }}</small>
          </button>
        </div>
      </section>

      <section class="calendar-strip flight-feature-card">
        <div class="section-head compact">
          <div>
            <p>价格日历</p>
            <h2>近一年价格趋势</h2>
          </div>
          <div class="calendar-actions">
            <span>{{ calendarRangeLabel }}</span>
            <el-date-picker
              v-model="calendarStartDate"
              type="date"
              value-format="YYYY-MM-DD"
              size="small"
              :clearable="false"
              @change="changeCalendarStartDate"
            />
            <el-button text type="primary" :loading="calendarLoading" @click="loadCalendar">刷新</el-button>
            <el-input-number
              v-model="lowPriceTarget"
              :disabled="!lowestCalendarDay"
              :min="1"
              :step="50"
              size="small"
              controls-position="right"
            />
            <el-button type="primary" plain :disabled="!lowestCalendarDay" @click="subscribeLowPrice">低价提醒</el-button>
          </div>
        </div>

        <div v-if="calendarDays.length > 0" class="calendar-advisor">
          <div class="calendar-advisor-copy">
            <span>购票建议</span>
            <strong>{{ calendarAdviceTitle }}</strong>
            <p>{{ calendarAdviceText }}</p>
          </div>
          <div class="calendar-advisor-actions">
            <button type="button" :disabled="!lowestCalendarDay" @click="chooseLowestCalendarDay">选最低价</button>
            <button type="button" @click="focusSelectedCalendarMonth">定位已选日期</button>
          </div>
        </div>

        <div class="calendar-summary flight-summary-grid">
          <div>
            <span>当前选择</span>
            <strong>{{ calendarPriceText(selectedCalendarDay) }}</strong>
            <small>{{ selectedCalendarDeltaText }}</small>
          </div>
          <div>
            <span>近一年最低</span>
            <strong>{{ calendarPriceText(lowestCalendarDay) }}</strong>
            <small>{{ lowestCalendarDay ? calendarDayLabel(lowestCalendarDay.date) : "暂无可售" }}</small>
          </div>
          <div>
            <span>均价参考</span>
            <strong>{{ calendarAveragePrice ? `￥${calendarAveragePrice}` : "--" }}</strong>
            <small>可售日期均价</small>
          </div>
          <div>
            <span>可售天数</span>
            <strong>{{ calendarAvailableCount }}/{{ calendarDays.length }}</strong>
            <small>{{ calendarAvailableCount > 0 ? "可灵活选择" : "暂无余票" }}</small>
          </div>
          <div>
            <span>低价差</span>
            <strong>{{ calendarSavingsText }}</strong>
            <small>{{ lowestCalendarDay ? "最低价对比均价" : "等待价格" }}</small>
          </div>
        </div>

        <div v-if="calendarDays.length > 0" class="calendar-trend" aria-label="近一年价格趋势">
          <button
            v-for="day in calendarDays"
            :key="`trend-${day.date}`"
            type="button"
            class="trend-day"
            :class="[calendarDayToneClass(day), { active: day.date === activeLeg.departureDate, today: isTodayCalendarDay(day), lowest: isLowestCalendarDay(day), disabled: !day.available }]"
            :title="calendarTooltip(day)"
            @click="selectCalendarDay(day)"
          >
            <span class="trend-bar" :style="{ height: trendBarHeight(day) }"></span>
            <small>{{ trendDayLabel(day) }}</small>
          </button>
        </div>

        <el-empty v-else-if="!calendarLoading" description="暂无价格趋势数据" />

        <div v-if="calendarMonths.length > 0" class="calendar-month-navigator">
          <button type="button" class="calendar-nav-button" :disabled="!hasPreviousCalendarMonth" @click="changeCalendarMonth(-1)">
            上一月
          </button>
          <div class="calendar-months" aria-label="价格日历月份">
            <button
              v-for="month in calendarMonths"
              :key="month"
              type="button"
              :class="{ active: activeCalendarMonth === month }"
              @click="selectCalendarMonth(month)"
            >
              {{ calendarMonthLabel(month) }}
            </button>
          </div>
          <button type="button" class="calendar-nav-button" :disabled="!hasNextCalendarMonth" @click="changeCalendarMonth(1)">
            下一月
          </button>
        </div>

        <div
          v-if="calendarMonthPanels.length > 0"
          ref="calendarScrollerRef"
          class="calendar-carousel"
        >
          <section
            v-for="month in calendarMonthPanels"
            :key="month.month"
            class="calendar-month-panel"
            :data-calendar-month="month.month"
          >
            <div class="calendar-month-head">
              <strong>{{ calendarMonthLabel(month.month) }}</strong>
              <span>{{ month.availableCount }} 天可售</span>
            </div>
            <div class="calendar-weekdays">
              <span v-for="weekday in calendarWeekdays" :key="weekday">{{ weekday }}</span>
            </div>
            <div class="calendar-grid">
              <span
                v-for="blank in month.leadingBlankDays"
                :key="`${month.month}-blank-${blank}`"
                class="calendar-blank"
                aria-hidden="true"
              ></span>
              <button
                v-for="day in month.days"
                :key="day.date"
                type="button"
                :class="[calendarDayToneClass(day), { active: day.date === activeLeg.departureDate, today: isTodayCalendarDay(day), lowest: isLowestCalendarDay(day), disabled: !day.available }]"
                @click="selectCalendarDay(day)"
              >
                <span>{{ calendarDateNumber(day.date) }}</span>
                <strong>{{ calendarPriceText(day) }}</strong>
                <small v-if="isLowestCalendarDay(day)">最低价</small>
                <small v-else-if="isTodayCalendarDay(day)">今天</small>
                <small v-else>{{ calendarDeltaText(day) }}</small>
              </button>
            </div>
          </section>
        </div>

        <div v-if="routeLowPriceAlerts.length > 0" class="alert-list flight-mini-card-list">
          <div v-for="alert in routeLowPriceAlerts" :key="`${alert.route}-${alert.cabin}`">
            <span>{{ cityName(alert.departureCity) }} → {{ cityName(alert.arrivalCity) }} · {{ cabinLabel(alert.cabin) }}</span>
            <strong>￥{{ alert.targetPrice }} 以下提醒</strong>
            <small>{{ lowPriceAlertStatus(alert) }}</small>
          </div>
        </div>
      </section>

      <section class="content-grid">
        <div ref="resultsPanelRef" class="results-panel flight-feature-card">
          <div class="section-head compact">
            <div>
              <p>{{ routeLabel }}</p>
              <h2>航班列表</h2>
            </div>
            <el-select v-model="form.sort" class="sort-select" @change="searchFlights">
              <el-option label="价格优先" value="price" />
              <el-option label="起飞时间" value="departureTime" />
              <el-option label="航空公司" value="airline" />
              <el-option label="经停次数" value="stops" />
            </el-select>
          </div>

          <div class="result-summary">
            <span>{{ visibleFlightCount }} 个方案</span>
            <span>{{ directFlightCount }} 个直飞</span>
            <span>按 {{ form.lowPriceFirst ? "低价" : sortLabel }} 排序</span>
            <span>{{ routeQualityLabel }}</span>
          </div>

          <div class="requirement-strip">
            <div v-for="item in requirementCoverage" :key="item.label" :class="{ done: item.done }">
              <span></span>
              {{ item.label }}
            </div>
          </div>

          <div v-if="searchLegs.length > 1" class="leg-tabs">
            <button
              v-for="leg in searchLegs"
              :key="leg.key"
              type="button"
              :class="{ active: activeLegKey === leg.key }"
              @click="activateLeg(leg.key)"
            >
              <strong>{{ leg.title }}</strong>
              <span>{{ cityName(leg.departureCity) }} → {{ cityName(leg.arrivalCity) }} · {{ leg.departureDate }}</span>
            </button>
          </div>

          <el-empty v-if="!loading && flights.length === 0" description="暂无航班，调整日期或航线后重试" />
          <article v-for="flight in flights" :key="flight.id" class="flight-card flight-stack-card" :class="{ selected: selectedFlight?.id === flight.id }">
            <div class="flight-carrier">
              <em>{{ airlineInitial(flight) }}</em>
              <strong>{{ flight.extraInfo.airline || "航空公司" }}</strong>
              <span>{{ flight.extraInfo.flightNo || flight.name }}</span>
              <small>{{ flight.extraInfo.aircraft || "机型待定" }}</small>
            </div>
            <div class="flight-route-main">
              <div class="time-node">
                <strong>{{ flight.extraInfo.departureTime || "--:--" }}</strong>
                <span>{{ cityName(flight.extraInfo.departureCity) }}</span>
                <small>{{ airportLabel(flight, "departure") }}</small>
              </div>
              <div class="route-line">
                <span>{{ flightStopLabel(flight) }}</span>
                <small>{{ flightDurationLabel(flight) }}</small>
              </div>
              <div class="time-node arrival">
                <strong>{{ flight.extraInfo.arrivalTime || "--:--" }}</strong>
                <span>{{ cityName(flight.extraInfo.arrivalCity) }}</span>
                <small>{{ airportLabel(flight, "arrival") }}</small>
              </div>
            </div>
            <div class="flight-price">
              <strong>￥{{ flight.price }}</strong>
              <small>单人票价</small>
              <span :class="{ warning: flight.stock <= 5 }">{{ flight.stock <= 5 ? `仅剩 ${flight.stock} 张` : `余票 ${flight.stock}` }}</span>
              <el-button type="primary" @click="selectFlight(flight)">选择</el-button>
            </div>
          </article>
        </div>

        <aside ref="orderPanelRef" class="order-panel flight-feature-card">
          <div class="section-head compact">
            <div>
              <p>费用核验</p>
              <h2>订单预览</h2>
            </div>
            <el-tag v-if="orderStatus" :type="statusTagType">{{ orderStatus.statusText }}</el-tag>
          </div>

          <template v-if="selectedFlight">
            <div class="detail-block">
              <h3>{{ selectedFlight.name }}</h3>
              <p>{{ selectedFlight.extraInfo.departureDate }} {{ selectedFlight.extraInfo.departureTime }} 起飞</p>
              <p>{{ airportLabel(selectedFlight, "departure") }} → {{ airportLabel(selectedFlight, "arrival") }}</p>
              <div class="detail-tags">
                <span>{{ selectedFlight.extraInfo.airline }} {{ selectedFlight.extraInfo.flightNo }}</span>
                <span>{{ cabinLabel(form.cabin) }}</span>
                <span>{{ selectedFlight.extraInfo.baggage || "20kg 行李" }}</span>
                <span>{{ flightStopLabel(selectedFlight) }}</span>
              </div>
              <p>{{ selectedFlight.extraInfo.refundRule || "以航司实际退改规则为准" }}</p>
            </div>

            <div class="policy-advisor flight-mini-card">
              <div>
                <span>智能退改签解释</span>
                <strong>{{ refundAdvisorTitle }}</strong>
              </div>
              <p>{{ refundAdvisorText }}</p>
              <el-button size="small" plain :disabled="!currentOrderNo" @click="explainRefundPolicy">
                {{ currentOrderNo ? "刷新解释" : "创建订单后可解释" }}
              </el-button>
            </div>

            <el-form label-position="top">
              <div class="passenger-forms">
                <div v-for="(item, index) in passengers" :key="item.key" class="passenger-card flight-mini-card">
                  <div class="passenger-card-head">
                    <strong>乘机人 {{ index + 1 }}</strong>
                    <el-tag size="small" effect="plain">{{ passengerType(index) }}</el-tag>
                  </div>
                  <el-form-item label="姓名">
                    <el-input v-model="item.name" />
                  </el-form-item>
                  <el-form-item label="证件号">
                    <el-input v-model="item.idNo" />
                  </el-form-item>
                  <el-form-item label="联系电话">
                    <el-input v-model="item.phone" />
                  </el-form-item>
                </div>
              </div>
              <el-form-item label="支付方式">
                <el-radio-group v-model="paymentMethod">
                  <el-radio-button v-for="method in paymentMethods" :key="method.value" :label="method.value">
                    {{ method.label }}
                  </el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="积分抵扣">
                <el-input-number v-model="orderOptions.pointsDeduct" :min="0" :step="100" />
              </el-form-item>
            </el-form>

            <div v-if="preview" class="price-lines">
              <div><span>票价</span><strong>￥{{ preview.ticketAmount }}</strong></div>
              <div><span>税费</span><strong>￥{{ preview.taxAmount }}</strong></div>
              <div><span>附加服务</span><strong>￥{{ preview.serviceAmount }}</strong></div>
              <div><span>积分抵扣</span><strong>-￥{{ preview.pointsAmount }}</strong></div>
              <div class="total"><span>应付</span><strong>￥{{ preview.payAmount }}</strong></div>
            </div>

            <div v-if="orderStatus?.ticketNo" class="ticket-info flight-mini-card">
              <span>电子客票号</span>
              <strong>{{ orderStatus.ticketNo }}</strong>
            </div>

            <div class="order-actions">
              <el-button :loading="previewLoading" @click="previewOrder">刷新预览</el-button>
              <el-button type="primary" :loading="orderLoading" :disabled="hasActiveOrder" @click="createOrder">
                {{ hasActiveOrder ? "订单已创建" : "创建订单" }}
              </el-button>
              <el-button v-if="currentOrderNo" type="success" @click="payOrder">模拟支付</el-button>
              <el-button v-if="currentOrderNo" @click="cancelOrder">取消</el-button>
              <el-button v-if="orderStatus?.status === 1" type="primary" @click="openChangeDialog">改签</el-button>
              <el-button v-if="currentOrderNo" type="warning" @click="refundOrder">退款</el-button>
            </div>

            <el-dialog v-model="showChangeDialog" title="机票改签" width="600px" destroy-on-close>
              <el-alert v-if="changeResult" :closable="false" type="success" :title="changeResult.message" />
              <template v-if="!changeResult">
                <p class="change-hint">从以下航班中选择改签目标：</p>
                <el-table :data="changeableFlights" stripe max-height="360">
                  <el-table-column label="航班" min-width="140">
                    <template #default="{ row }">{{ row.name }} / {{ row.extraInfo.airline }}</template>
                  </el-table-column>
                  <el-table-column label="时间" min-width="130">
                    <template #default="{ row }">{{ row.extraInfo.departureTime }} - {{ row.extraInfo.arrivalTime }}</template>
                  </el-table-column>
                  <el-table-column label="价格" width="80">
                    <template #default="{ row }">¥{{ row.price }}</template>
                  </el-table-column>
                  <el-table-column label="操作" width="80">
                    <template #default="{ row }">
                      <el-button size="small" type="primary" :loading="changing" @click="handleChangeFlight(row)">改签</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </template>
              <template #footer v-if="changeResult">
                <el-button type="primary" @click="showChangeDialog = false">确定</el-button>
              </template>
            </el-dialog>
          </template>
          <el-empty v-else description="选择一个航班后可预览订单" />
        </aside>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, nextTick, onMounted, onUnmounted, PropType, reactive, ref } from "vue";
import { ElButton, ElMessage, ElPopover } from "element-plus";
import { http } from "@/utils/request";
import { chinaAirports, chinaProvinceAirportOptions, chinaProvinceTabs, ChinaAirportOption } from "@/data/chinaAirports";

interface PageResponse<T> {
  total: number;
  page: number;
  size: number;
  list: T[];
}

interface ProductDTO {
  id: string;
  product_type: string;
  name: string;
  price: number;
  stock: number;
  sold_count: number;
  status: number;
  extra?: string;
}

interface FlightExtraInfo {
  stops?: number | string;
  departureTime?: string;
  arrivalTime?: string;
  airline?: string;
  refundRule?: string;
  departureCity?: string;
  departure_city?: string;
  departure?: string;
  arrivalCity?: string;
  arrival_city?: string;
  arrival?: string;
  departureAirport?: string;
  departure_airport?: string;
  departureAirportCode?: string;
  arrivalAirport?: string;
  arrival_airport?: string;
  arrivalAirportCode?: string;
  [key: string]: unknown;
}

interface FlightProduct extends ProductDTO {
  extraInfo: FlightExtraInfo;
}

interface CalendarDay {
  date: string;
  lowestPrice: number;
  available: boolean;
}

interface SpecialFareDeal extends CalendarDay {
  badge: string;
  savings: number;
}

interface LowPriceRoutePlan {
  mode: "oneway" | "roundtrip" | "transfer";
  date: string;
  label: string;
  title: string;
  summary: string;
}

interface LowPriceAlert {
  route: string;
  departureCity: string;
  arrivalCity: string;
  cabin: string;
  directOnly: boolean;
  targetPrice: number;
  bestDate: string;
  createdAt: string;
}

interface SearchLeg {
  key: string;
  title: string;
  departureCity: string;
  arrivalCity: string;
  departureDate: string;
}

interface MultiSegment {
  id: number;
  departureCity: string;
  arrivalCity: string;
  departureDate: string;
}

interface PassengerForm {
  key: number;
  type: "ADULT" | "CHILD";
  name: string;
  idType: string;
  idNo: string;
  phone: string;
}

interface PreviewData {
  ticketAmount: number;
  taxAmount: number;
  serviceAmount: number;
  pointsAmount: number;
  payAmount: number;
}

interface OrderDTO {
  order_no: string;
  pay_amount: number;
  status: number;
}

interface OrderStatus {
  status: number;
  statusText: string;
  paymentMethod: string;
  ticketNo?: string;
}

const CityPicker = defineComponent({
  name: "CityPicker",
  props: {
    label: { type: String, required: true },
    cityCode: { type: String, required: true },
    cities: { type: Array as PropType<ChinaAirportOption[]>, required: true },
    tabs: { type: Array as PropType<string[]>, required: true },
  },
  emits: ["select"],
  setup(props, { emit }) {
    const visible = ref(false);
    const activeRegion = ref<ChinaAirportOption["region"]>("domestic");
    const activeTab = ref("热门");
    const keyword = ref("");

    const currentCity = computed(() => props.cities.find((city) => city.code === props.cityCode) || props.cities[0]);
    const filteredCities = computed(() => {
      const query = keyword.value.trim().toLowerCase();
      return props.cities.filter((city) => {
        const isActive = city.active !== false;
        const inRegion = city.region === activeRegion.value;
        const inTab = activeTab.value === "热门" ? city.hot : city.letter === activeTab.value;
        const inSearch =
          !query ||
          city.name.includes(query) ||
          city.airport.includes(query) ||
          city.province.includes(query) ||
          city.code.toLowerCase().includes(query) ||
          city.cityCode.toLowerCase().includes(query) ||
          city.aliases?.some((alias) => alias.toLowerCase().includes(query));
        return isActive && (query ? inSearch : inRegion && inTab);
      });
    });

    function choose(city: ChinaAirportOption) {
      emit("select", city.code);
      visible.value = false;
    }

    return () =>
      h(
        ElPopover,
        {
          visible: visible.value,
          "onUpdate:visible": (value: boolean) => (visible.value = value),
          placement: "bottom-start",
          width: 720,
          trigger: "click",
          popperClass: "flight-city-popper",
        },
        {
          reference: () =>
            h("button", { class: "field-card city-trigger", type: "button" }, [
              h("span", props.label),
              h("strong", currentCity.value.name),
            ]),
          default: () =>
            h("div", { class: "city-picker" }, [
              h("aside", { class: "city-region" }, [
                h("button", { class: { active: activeRegion.value === "domestic" }, type: "button", onClick: () => (activeRegion.value = "domestic") }, "国内"),
                h("button", { class: { active: activeRegion.value === "international" }, type: "button", onClick: () => (activeRegion.value = "international") }, "国际/港澳台"),
              ]),
              h("section", { class: "city-content" }, [
                h("input", {
                  value: keyword.value,
                  placeholder: "搜索省份、直辖市或机场",
                  onInput: (event: Event) => (keyword.value = (event.target as HTMLInputElement).value),
                }),
                h("nav", { class: "city-tabs" }, props.tabs.map((tab) => h("button", { class: { active: activeTab.value === tab }, type: "button", onClick: () => (activeTab.value = tab) }, tab))),
                h("div", { class: "city-list" }, filteredCities.value.map((city) => h("button", { class: { active: city.code === props.cityCode }, type: "button", onClick: () => choose(city) }, [h("strong", city.name), h("span", city.airport)]))),
              ]),
            ]),
        }
      );
  },
});

const tripTypes = [
  { label: "单程", value: "oneway" },
  { label: "往返", value: "roundtrip" },
  { label: "中转", value: "transfer" },
  { label: "多程", value: "multi" },
];
const paymentMethods = [
  { label: "微信", value: "WECHAT" },
  { label: "支付宝", value: "ALIPAY" },
  { label: "积分", value: "POINTS" },
];
const requirementSteps = [
  { title: "多条件搜索", desc: "单程/往返/多程" },
  { title: "排序筛选", desc: "价格/时间/航司/经停" },
  { title: "价格日历", desc: "低价提醒" },
  { title: "服务下单", desc: "舱位/行李/保险" },
  { title: "支付出票", desc: "订单/退改签" },
];
const aiSamples = [
  "从北京到上海，明天出发，经济舱，只看直飞",
  "北京飞上海，2 位成人 1 位儿童，商务舱",
  "7月1日从上海到成都，低价优先",
];
const cityTabs = chinaProvinceTabs;
const cities = chinaProvinceAirportOptions;
const airportOptions = chinaAirports;
const today = todayString();
const defaultReturnDate = addDays(today, 3);
const LOW_PRICE_ALERTS_KEY = "lightmark_flight_low_price_alerts";
const CALENDAR_RANGE_DAYS = 365;
const calendarWeekdays = ["日", "一", "二", "三", "四", "五", "六"];
const MAINLAND_PHONE_PATTERN = /^1[3-9]\d{9}$/;
const ID_CARD_PATTERN = /^\d{17}[\dXx]$/;

// 搜索态使用省/直辖市聚合码，后端会展开为对应机场；结果展示仍保留具体机场。
const form = reactive({
  tripType: "oneway",
  departureCity: provinceCode("北京"),
  arrivalCity: provinceCode("上海"),
  departureDate: today,
  returnDate: defaultReturnDate,
  cabin: "",
  adultCount: 1,
  childCount: 0,
  sort: "price",
  directOnly: false,
  lowPriceFirst: true,
});

const orderOptions = reactive({
  insurance: true,
  extraBaggage: false,
  seatSelection: false,
  pointsDeduct: 0,
});

const multiSegments = ref<MultiSegment[]>([
  { id: 1, departureCity: provinceCode("北京"), arrivalCity: provinceCode("上海"), departureDate: today },
  { id: 2, departureCity: provinceCode("上海"), arrivalCity: provinceCode("北京"), departureDate: defaultReturnDate },
]);
const passengers = ref<PassengerForm[]>([createPassenger(0, "ADULT")]);
const paymentMethod = ref("WECHAT");

const loading = ref(false);
const calendarLoading = ref(false);
const previewLoading = ref(false);
const orderLoading = ref(false);
const resultsByLeg = ref<Record<string, PageResponse<FlightProduct>>>({});
const calendarDays = ref<CalendarDay[]>([]);
const selectedFlight = ref<FlightProduct | null>(null);
const preview = ref<PreviewData | null>(null);
const currentOrderNo = ref("");
const orderStatus = ref<OrderStatus | null>(null);
const activeLegKey = ref("outbound");
const calendarStartDate = ref(today);
const activeCalendarMonth = ref(today.slice(0, 7));
const specialFareRefreshSeed = ref(0);
const calendarScrollerRef = ref<HTMLElement | null>(null);
const resultsPanelRef = ref<HTMLElement | null>(null);
const orderPanelRef = ref<HTMLElement | null>(null);
const lowPriceTarget = ref(0);
const lowPriceAlerts = ref<LowPriceAlert[]>([]);
const aiQuery = ref("");
const aiSearchSummary = ref("");
const aiSearchLoading = ref(false);
const refundAdvisorNote = ref("");
const isFlightDark = ref(false);
let themeObserver: MutationObserver | null = null;
// 异步搜索和日历请求都可能被快速切换条件打断，用序号丢弃过期响应。
let searchRequestSeq = 0;
let calendarRequestSeq = 0;

// 统一把单程、往返、多程转换成航段数组，列表、日历、订单共用这一层抽象。
const searchLegs = computed<SearchLeg[]>(() => {
  if (form.tripType === "roundtrip") {
    return [
      { key: "outbound", title: "去程", departureCity: form.departureCity, arrivalCity: form.arrivalCity, departureDate: form.departureDate },
      { key: "return", title: "返程", departureCity: form.arrivalCity, arrivalCity: form.departureCity, departureDate: form.returnDate },
    ];
  }
  if (form.tripType === "multi") {
    return multiSegments.value.map((segment, index) => ({
      key: `segment-${segment.id}`,
      title: `第 ${index + 1} 程`,
      departureCity: segment.departureCity,
      arrivalCity: segment.arrivalCity,
      departureDate: segment.departureDate,
    }));
  }
  if (form.tripType === "transfer") {
    return [{ key: "outbound", title: "中转", departureCity: form.departureCity, arrivalCity: form.arrivalCity, departureDate: form.departureDate }];
  }
  return [{ key: "outbound", title: "单程", departureCity: form.departureCity, arrivalCity: form.arrivalCity, departureDate: form.departureDate }];
});
const activeLeg = computed<SearchLeg>(() => searchLegs.value.find((leg) => leg.key === activeLegKey.value) || searchLegs.value[0] || {
  key: "outbound",
  title: "单程",
  departureCity: form.departureCity,
  arrivalCity: form.arrivalCity,
  departureDate: form.departureDate,
});
const activeResults = computed(() => resultsByLeg.value[activeLeg.value?.key || "outbound"] || { total: 0, page: 1, size: 20, list: [] });
const flights = computed(() => activeResults.value.list);
const visibleFlightCount = computed(() => activeResults.value.total || flights.value.length);
const directFlightCount = computed(() => flights.value.filter((flight) => Number(flight.extraInfo.stops || 0) === 0).length);
const travelerCount = computed(() => form.adultCount + form.childCount);
const routeLabel = computed(() => {
  const leg = activeLeg.value;
  return `${cityName(leg?.departureCity)} → ${cityName(leg?.arrivalCity)} · ${leg?.departureDate || form.departureDate}`;
});
const sortLabel = computed(() => {
  if (form.sort === "departureTime") return "起飞时间";
  if (form.sort === "airline") return "航空公司";
  if (form.sort === "stops") return "经停次数";
  return "价格";
});
const statusTagType = computed(() => (orderStatus.value?.status === 1 ? "success" : orderStatus.value?.status === 4 ? "warning" : "info"));
const hasActiveOrder = computed(() => Boolean(currentOrderNo.value) && ![2, 4].includes(Number(orderStatus.value?.status)));
const availableCalendarDays = computed(() => calendarDays.value.filter((day) => day.available && Number(day.lowestPrice) > 0));
const lowestCalendarDay = computed(() => {
  return availableCalendarDays.value.reduce<CalendarDay | undefined>((lowest, day) => {
    if (!lowest || Number(day.lowestPrice) < Number(lowest.lowestPrice)) return day;
    return lowest;
  }, undefined);
});
const highestCalendarPrice = computed(() => Math.max(...availableCalendarDays.value.map((day) => Number(day.lowestPrice)), 0));
const lowestCalendarPrice = computed(() => Number(lowestCalendarDay.value?.lowestPrice || 0));
const selectedCalendarDay = computed(() => calendarDays.value.find((day) => day.date === activeLeg.value.departureDate));
const calendarAveragePrice = computed(() => {
  if (availableCalendarDays.value.length === 0) return 0;
  const total = availableCalendarDays.value.reduce((sum, day) => sum + Number(day.lowestPrice), 0);
  return Math.round(total / availableCalendarDays.value.length);
});
const calendarSavingsText = computed(() => {
  if (!lowestCalendarDay.value || calendarAveragePrice.value <= 0) return "--";
  const savings = calendarAveragePrice.value - Number(lowestCalendarDay.value.lowestPrice);
  return savings > 0 ? `省 ￥${Math.round(savings)}` : "接近均价";
});
const lowFareRouteLabel = computed(() => `${cityName(activeLeg.value.departureCity)} → ${cityName(activeLeg.value.arrivalCity)}`);
const specialFareDeals = computed<SpecialFareDeal[]>(() => {
  const average = calendarAveragePrice.value;
  const candidates = availableCalendarDays.value
    .filter((day) => new Date(`${day.date}T00:00:00`) >= new Date(`${today}T00:00:00`))
    .map((day) => ({
      ...day,
      savings: Math.max(0, Math.round(average - Number(day.lowestPrice))),
      badge: isLowestCalendarDay(day) ? "当前最低" : calendarDeltaText(day),
    }))
    .sort((left, right) => {
      const priceDiff = Number(left.lowestPrice) - Number(right.lowestPrice);
      return priceDiff !== 0 ? priceDiff : left.date.localeCompare(right.date);
    })
    .slice(0, 24);
  if (specialFareRefreshSeed.value === 0 || candidates.length <= 6) {
    return candidates.slice(0, 6);
  }
  return candidates
    .map((deal, index) => ({ deal, score: seededRecommendationScore(`${deal.date}-${index}`, specialFareRefreshSeed.value) }))
    .sort((left, right) => left.score - right.score)
    .slice(0, 6)
    .map((item) => item.deal)
    .sort((left, right) => {
      const priceDiff = Number(left.lowestPrice) - Number(right.lowestPrice);
      return priceDiff !== 0 ? priceDiff : left.date.localeCompare(right.date);
    });
});
const lowPriceRoutePlans = computed<LowPriceRoutePlan[]>(() => {
  if (specialFareDeals.value.length === 0) return [];
  const best = specialFareDeals.value[0];
  const next = specialFareDeals.value[1] || best;
  return [
    {
      mode: "oneway",
      date: best.date,
      label: "单程低价",
      title: `${calendarDayLabel(best.date)} 出发`,
      summary: `${lowFareRouteLabel.value} ${calendarPriceText(best)} 起，适合立即锁价。`,
    },
    {
      mode: "roundtrip",
      date: best.date,
      label: "往返建议",
      title: `${calendarDayLabel(best.date)} 去 · ${calendarDayLabel(addDays(best.date, 3))} 回`,
      summary: `去程优先低价日，返程自动规划为 3 天后。`,
    },
    {
      mode: "transfer",
      date: next.date,
      label: "中转省钱",
      title: `${calendarDayLabel(next.date)} 看经停方案`,
      summary: `放开直飞限制，优先比较经停航班的价格空间。`,
    },
  ];
});
const selectedCalendarDeltaText = computed(() => calendarDeltaText(selectedCalendarDay.value) || "未在当前日历范围");
const calendarAdviceTitle = computed(() => {
  if (availableCalendarDays.value.length === 0) return "当前航线暂无可售价格";
  if (selectedCalendarDay.value?.available && isLowestCalendarDay(selectedCalendarDay.value)) return "当前日期已经是最低价";
  if (lowestCalendarDay.value) return `${calendarDayLabel(lowestCalendarDay.value.date)} 更划算`;
  return "换个日期看看";
});
const calendarAdviceText = computed(() => {
  if (availableCalendarDays.value.length === 0) return "可以调整航线、舱位或直飞条件后刷新价格日历。";
  if (!selectedCalendarDay.value?.available) return `已选日期暂无价格，建议选择 ${calendarDayLabel(lowestCalendarDay.value?.date || activeLeg.value.departureDate)}。`;
  const selectedPrice = Number(selectedCalendarDay.value.lowestPrice);
  const lowestPrice = Number(lowestCalendarDay.value?.lowestPrice || 0);
  if (lowestPrice > 0 && selectedPrice > lowestPrice) {
    return `当前选择 ${calendarPriceText(selectedCalendarDay.value)}，最低价 ${calendarPriceText(lowestCalendarDay.value)}，可节省 ￥${Math.round(selectedPrice - lowestPrice)}。`;
  }
  return `当前选择 ${calendarPriceText(selectedCalendarDay.value)}，${calendarDeltaText(selectedCalendarDay.value)}。`;
});
const calendarAvailableCount = computed(() => availableCalendarDays.value.length);
const calendarRangeLabel = computed(() => {
  if (calendarDays.value.length === 0) return "暂无趋势";
  const first = calendarDayLabel(calendarDays.value[0].date);
  const last = calendarDayLabel(calendarDays.value[calendarDays.value.length - 1].date);
  return `${first} - ${last}`;
});
const calendarMonths = computed(() => {
  const months = new Set<string>();
  calendarDays.value.forEach((day) => months.add(day.date.slice(0, 7)));
  return Array.from(months);
});
const activeCalendarMonthIndex = computed(() => calendarMonths.value.indexOf(activeCalendarMonth.value));
const hasPreviousCalendarMonth = computed(() => activeCalendarMonthIndex.value > 0);
const hasNextCalendarMonth = computed(() => activeCalendarMonthIndex.value >= 0 && activeCalendarMonthIndex.value < calendarMonths.value.length - 1);
// 月面板补齐月初空白格，让按钮翻月时保持真实日历的星期对齐。
const calendarMonthPanels = computed(() => calendarMonths.value.map((month) => {
  const days = calendarDays.value.filter((day) => day.date.startsWith(month));
  return {
    month,
    days,
    availableCount: days.filter((day) => day.available).length,
    leadingBlankDays: days.length > 0 ? calendarLeadingBlankDays(days[0].date) : 0,
  };
}));
const routeLowPriceAlerts = computed(() => {
  const leg = activeLeg.value;
  return lowPriceAlerts.value.filter((alert) => alert.route === `${leg.departureCity}-${leg.arrivalCity}`);
});
const activeDepartureMonth = computed(() => activeLeg.value.departureDate.slice(0, 7));
const activeWorkflowIndex = computed(() => {
  if (orderStatus.value?.ticketNo || orderStatus.value?.status === 1) return 4;
  if (currentOrderNo.value || preview.value) return 3;
  if (selectedFlight.value) return 2;
  if (flights.value.length > 0) return 1;
  return 0;
});
const routeQualityLabel = computed(() => {
  if (flights.value.length === 0) return "等待航班数据";
  if (directFlightCount.value === flights.value.length) return "全为直飞方案";
  if (directFlightCount.value > 0) return `${directFlightCount.value} 个直飞可选`;
  return "当前为经停方案";
});
const requirementCoverage = computed(() => [
  { label: "多条件搜索", done: true },
  { label: "排序筛选", done: true },
  { label: "价格日历", done: calendarDays.value.length > 0 },
  { label: "低价提醒", done: routeLowPriceAlerts.value.length > 0 },
  { label: "舱位服务", done: Boolean(form.cabin || orderOptions.insurance || orderOptions.extraBaggage || orderOptions.seatSelection) },
  { label: "乘客下单", done: Boolean(preview.value || currentOrderNo.value) },
  { label: "支付出票", done: Boolean(orderStatus.value?.ticketNo) },
  { label: "退改签解释", done: Boolean(refundAdvisorNote.value || currentOrderNo.value) },
  { label: "自然语言搜索", done: Boolean(aiSearchSummary.value) },
]);
const refundAdvisorTitle = computed(() => {
  if (currentOrderNo.value) return `订单 ${currentOrderNo.value}`;
  if (selectedFlight.value) return "航班政策预览";
  return "选择航班后查看";
});
const refundAdvisorText = computed(() => {
  if (refundAdvisorNote.value) return refundAdvisorNote.value;
  if (!selectedFlight.value) return "选择航班并创建订单后，将结合起飞时间、订单状态和航司规则解释预计退款与手续费。";
  const rule = selectedFlight.value.extraInfo.refundRule || "以航司实际退改规则为准";
  return `${rule}。${currentOrderNo.value ? "可根据订单状态继续刷新退改签解释。" : "创建订单后可生成更精确的订单级解释。"}`;
});

function syncFlightTheme() {
  if (typeof window === "undefined") return;
  const root = document.documentElement;
  const savedTheme = window.localStorage.getItem("lightmark_theme");
  isFlightDark.value = savedTheme === "dark" || root.dataset.theme === "dark" || root.classList.contains("dark");
}

onMounted(() => {
  syncFlightTheme();
  themeObserver = new MutationObserver(syncFlightTheme);
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ["class", "data-theme"] });
  window.addEventListener("storage", syncFlightTheme);
  lowPriceAlerts.value = readLowPriceAlerts();
  searchFlights();
  loadCalendar();
});

onUnmounted(() => {
  themeObserver?.disconnect();
  window.removeEventListener("storage", syncFlightTheme);
});

function useAiSample(sample: string) {
  aiQuery.value = sample;
  applyAiSearch();
}

async function applyAiSearch() {
  const query = aiQuery.value.trim();
  if (!query) {
    ElMessage.warning("请输入自然语言行程需求");
    return;
  }
  aiSearchLoading.value = true;
  try {
    const changes = parseFlightIntent(query);
    Object.assign(form, changes.form);
    if (changes.returnDate) {
      form.returnDate = changes.returnDate;
    }
    if (changes.tripType) {
      form.tripType = changes.tripType;
    }
    syncPassengers();
    activeLegKey.value = "outbound";
    syncCalendarWindowWithActiveLeg();
    aiSearchSummary.value = `已解析：${cityName(form.departureCity)} → ${cityName(form.arrivalCity)}，${form.departureDate}，${travelerCount.value} 位乘机人，${cabinLabel(form.cabin)}${tripTypeSummaryText.value}`;
    await refreshRouteData();
  } finally {
    aiSearchLoading.value = false;
  }
}

// 轻量解析常见中文行程表达，只改写本地搜索条件，不依赖外部 AI 接口。
function parseFlightIntent(query: string) {
  const nextForm: Partial<typeof form> = {};
  const matchedCities = matchCitiesFromText(query);
  if (matchedCities[0]) nextForm.departureCity = matchedCities[0];
  if (matchedCities[1]) nextForm.arrivalCity = matchedCities[1];
  const date = parseChineseDate(query);
  if (date) nextForm.departureDate = date;
  const returnDate = parseReturnDate(query, date || form.departureDate);
  const adultMatch = query.match(/(\d+)\s*(位|个)?成人/);
  const childMatch = query.match(/(\d+)\s*(位|个)?儿童/);
  if (adultMatch) nextForm.adultCount = Math.max(1, Number(adultMatch[1]));
  if (childMatch) nextForm.childCount = Math.max(0, Number(childMatch[1]));
  if (query.includes("商务舱")) nextForm.cabin = "BUSINESS";
  else if (query.includes("头等舱")) nextForm.cabin = "FIRST";
  else if (query.includes("经济舱")) nextForm.cabin = "ECONOMY";
  const wantsTransfer = query.includes("中转") || query.includes("经停");
  if (query.includes("直飞") && !wantsTransfer) nextForm.directOnly = true;
  if (wantsTransfer) nextForm.directOnly = false;
  if (query.includes("低价") || query.includes("便宜")) nextForm.lowPriceFirst = true;
  const tripType = wantsTransfer ? "transfer" : returnDate ? "roundtrip" : query.includes("多程") ? "multi" : undefined;
  return { form: nextForm, returnDate, tripType };
}

function matchCitiesFromText(query: string) {
  const matches = cities
    .filter((city) => query.includes(city.name) || city.aliases?.some((alias) => query.includes(alias)))
    .map((city) => city.code);
  const fromToMatch = query.match(/从(.+?)(到|去|飞)(.+?)(，|,|。|$)/);
  if (fromToMatch) {
    const from = cityCodeFromText(fromToMatch[1]);
    const to = cityCodeFromText(fromToMatch[3]);
    return [from || matches[0], to || matches.find((code) => code !== from)];
  }
  return [matches[0], matches.find((code) => code !== matches[0])];
}

function cityCodeFromText(text: string) {
  return cities.find((city) => text.includes(city.name) || city.aliases?.some((alias) => text.includes(alias)))?.code || "";
}

function parseChineseDate(query: string) {
  const isoMatch = query.match(/(20\d{2}-\d{2}-\d{2})/);
  if (isoMatch) return isoMatch[1];
  if (query.includes("明天")) return addDays(today, 1);
  if (query.includes("后天")) return addDays(today, 2);
  if (query.includes("今天")) return today;
  const match = query.match(/(\d{1,2})月(\d{1,2})(日|号)?/);
  if (!match) return "";
  const now = new Date(`${today}T00:00:00`);
  let date = new Date(now.getFullYear(), Number(match[1]) - 1, Number(match[2]));
  if (date < now) {
    date = new Date(now.getFullYear() + 1, Number(match[1]) - 1, Number(match[2]));
  }
  return formatDate(date);
}

function parseReturnDate(query: string, departureDate: string) {
  if (!query.includes("往返") && !query.includes("返程") && !query.includes("回来") && !query.includes("回")) {
    return "";
  }
  const returnMatch = query.match(/返程(\d{1,2})月(\d{1,2})(日|号)?|(\d{1,2})月(\d{1,2})(日|号)?回/);
  if (returnMatch) {
    const month = Number(returnMatch[1] || returnMatch[4]);
    const day = Number(returnMatch[2] || returnMatch[5]);
    const base = new Date(`${departureDate}T00:00:00`);
    let date = new Date(base.getFullYear(), month - 1, day);
    if (date < base) {
      date = new Date(base.getFullYear() + 1, month - 1, day);
    }
    return formatDate(date);
  }
  return addDays(departureDate, 3);
}

function buildSearchParams(leg: SearchLeg = activeLeg.value) {
  return {
    departureCity: leg.departureCity,
    arrivalCity: leg.arrivalCity,
    departureDate: leg.departureDate,
    adultCount: form.adultCount,
    childCount: form.childCount,
    cabin: form.cabin || undefined,
    directOnly: form.tripType === "transfer" ? false : form.directOnly,
    sort: form.lowPriceFirst ? "price" : form.sort,
    page: 1,
    size: form.tripType === "transfer" ? 100 : 20,
  };
}

async function searchFlights(options: { autoSelectFirst?: boolean; resetOrderOnAutoSelect?: boolean } = {}) {
  const requestId = ++searchRequestSeq;
  loading.value = true;
  try {
    ensureActiveLeg();
    const nextResults: Record<string, PageResponse<FlightProduct>> = {};
    for (const leg of searchLegs.value) {
      const data = await http.get<PageResponse<ProductDTO>>("/flights/search", { params: buildSearchParams(leg) });
      if (requestId !== searchRequestSeq) return;
      const list = data.list.map(normalizeFlight).filter(matchesTripMode);
      nextResults[leg.key] = { ...data, total: form.tripType === "transfer" ? list.length : data.total, list };
    }
    if (requestId !== searchRequestSeq) return;
    resultsByLeg.value = nextResults;
    const activeList = nextResults[activeLegKey.value]?.list || [];
    if (options.autoSelectFirst !== false && activeList.length > 0) {
      await selectFlight(activeList[0], {
        resetOrder: options.resetOrderOnAutoSelect !== false,
        staleGuard: () => requestId !== searchRequestSeq,
      });
    } else {
      if (options.autoSelectFirst !== false) {
        selectedFlight.value = null;
        preview.value = null;
      }
    }
  } finally {
    if (requestId === searchRequestSeq) {
      loading.value = false;
    }
  }
}

async function loadCalendar() {
  const requestId = ++calendarRequestSeq;
  calendarLoading.value = true;
  try {
    const leg = activeLeg.value;
    syncCalendarWindowWithActiveLeg();
    const startDate = calendarStartDate.value || today;
    const data = await http.get<{ days: CalendarDay[] }>("/flights/price-calendar", {
      params: {
        departureCity: leg.departureCity,
        arrivalCity: leg.arrivalCity,
        startDate,
        days: CALENDAR_RANGE_DAYS,
        cabin: form.cabin || undefined,
        directOnly: form.tripType === "transfer" ? false : form.directOnly,
        adultCount: form.adultCount,
        childCount: form.childCount,
      },
    });
    if (requestId !== calendarRequestSeq) return;
    calendarDays.value = data.days;
    activeCalendarMonth.value = activeDepartureMonth.value;
    if (!calendarMonths.value.includes(activeCalendarMonth.value)) {
      activeCalendarMonth.value = calendarMonths.value[0] || calendarStartDate.value.slice(0, 7);
    }
    scrollCalendarMonthIntoView(activeCalendarMonth.value, "auto");
    if (lowestCalendarDay.value && lowPriceTarget.value <= 0) {
      lowPriceTarget.value = Math.round(Number(lowestCalendarDay.value.lowestPrice));
    }
  } finally {
    if (requestId === calendarRequestSeq) {
      calendarLoading.value = false;
    }
  }
}

async function selectFlight(flight: FlightProduct, options: { resetOrder?: boolean; staleGuard?: () => boolean } = { resetOrder: true }) {
  const detail = await http.get<ProductDTO>(`/flights/${flight.id}`);
  if (options.staleGuard?.()) return;
  selectedFlight.value = normalizeFlight(detail);
  if (options.resetOrder) {
    currentOrderNo.value = "";
    orderStatus.value = null;
  }
  await previewOrder({ validatePassengers: false, staleGuard: options.staleGuard });
}

async function previewOrder(options: { validatePassengers?: boolean; staleGuard?: () => boolean } = { validatePassengers: true }) {
  if (!selectedFlight.value) return;
  if (options.validatePassengers !== false && !validatePassengerForms()) {
    return;
  }
  previewLoading.value = true;
  try {
    const nextPreview = await http.post<PreviewData>("/flights/order/preview", buildOrderPayload());
    if (options.staleGuard?.()) return;
    preview.value = nextPreview;
  } finally {
    if (!options.staleGuard?.()) {
      previewLoading.value = false;
    }
  }
}

async function createOrder() {
  if (!selectedFlight.value) return;
  if (hasActiveOrder.value) {
    ElMessage.warning("当前航班已有未完成订单，请先支付、取消或退款后再创建新订单");
    return;
  }
  if (!validatePassengerForms()) {
    return;
  }
  orderLoading.value = true;
  try {
    const order = await http.post<OrderDTO>("/flights/order", buildOrderPayload());
    currentOrderNo.value = order.order_no;
    orderStatus.value = { status: order.status, statusText: "待支付", paymentMethod: "" };
    ElMessage.success(`订单已创建：${order.order_no}`);
  } finally {
    orderLoading.value = false;
  }
}

async function payOrder() {
  if (!currentOrderNo.value) return;
  orderStatus.value = await http.post<OrderStatus>(`/orders/${currentOrderNo.value}/pay`, { paymentMethod: paymentMethod.value });
  ElMessage.success(orderStatus.value.ticketNo ? `支付成功，客票号 ${orderStatus.value.ticketNo}` : "支付成功，已出票");
}

async function cancelOrder() {
  if (!currentOrderNo.value) return;
  await http.post<boolean>(`/orders/${currentOrderNo.value}/cancel`, { reason: "用户取消" });
  await refreshOrderStatus();
  await searchFlights({ autoSelectFirst: false });
}

async function refundOrder() {
  if (!currentOrderNo.value) return;
  const result = await http.post<{ refundAmount: number; status: number; statusText: string }>(`/orders/${currentOrderNo.value}/refund`);
  orderStatus.value = { status: result.status, statusText: result.statusText, paymentMethod: "" };
  ElMessage.success(`退款金额 ￥${result.refundAmount}`);
  await searchFlights({ autoSelectFirst: false });
}

const showChangeDialog = ref(false);
const changing = ref(false);
const changeResult = ref<{ message: string; oldOrderNo: string; newOrderNo: string; oldPayAmount: number; newPayAmount: number; difference: number } | null>(null);

const changeableFlights = computed(() => {
  return flights.value.filter(f => f.id !== selectedFlight.value?.id);
});

function openChangeDialog() {
  changeResult.value = null;
  showChangeDialog.value = true;
}

async function handleChangeFlight(target: FlightProduct) {
  if (!currentOrderNo.value) return;
  changing.value = true;
  try {
    const result = await http.post<typeof changeResult.value>(`/orders/${currentOrderNo.value}/change`, { productId: target.id });
    changeResult.value = result;
    ElMessage.success(result?.message || "改签成功");
    await refreshOrderStatus();
  } catch {
    ElMessage.error("改签失败，请稍后重试");
  } finally {
    changing.value = false;
  }
}

function explainRefundPolicy() {
  if (!selectedFlight.value) return;
  const rule = selectedFlight.value.extraInfo.refundRule || "以航司实际退改规则为准";
  const status = orderStatus.value?.statusText || (currentOrderNo.value ? "待支付" : "未下单");
  const amount = preview.value?.payAmount ? `当前应付 ￥${preview.value.payAmount}` : "暂无订单金额";
  refundAdvisorNote.value = `订单状态：${status}。${amount}。退改规则：${rule}。建议在支付前确认乘机人信息；已支付订单如需退款，系统会按航司规则计算手续费并返回预计退款金额。`;
  ElMessage.success("已生成退改签解释");
}

async function refreshOrderStatus() {
  if (!currentOrderNo.value) return;
  orderStatus.value = await http.get<OrderStatus>(`/orders/${currentOrderNo.value}/status`);
}

function buildOrderPayload() {
  return {
    productId: selectedFlight.value?.id,
    cabin: orderCabin(),
    adultCount: form.adultCount,
    childCount: form.childCount,
    passengers: normalizedPassengers(),
    insurance: orderOptions.insurance,
    extraBaggage: orderOptions.extraBaggage,
    seatSelection: orderOptions.seatSelection,
    pointsDeduct: orderOptions.pointsDeduct,
  };
}

// 下单前统一裁剪空白，避免前端校验通过但后端保存带空格的乘机人信息。
function normalizedPassengers() {
  return passengers.value.map(({ ...item }) => ({
    ...item,
    name: item.name.trim(),
    idNo: item.idNo.trim(),
    phone: item.phone.trim(),
  }));
}

// 航班扩展字段历史上有 camelCase 和 snake_case 两种命名，这里统一成页面使用的字段。
function normalizeFlight(product: ProductDTO): FlightProduct {
  let extraInfo: FlightExtraInfo = {};
  try {
    extraInfo = product.extra ? JSON.parse(product.extra) : {};
  } catch {
    extraInfo = {};
  }
  extraInfo.departureCity = extraInfo.departureCity || extraInfo.departure_city || extraInfo.departure;
  extraInfo.arrivalCity = extraInfo.arrivalCity || extraInfo.arrival_city || extraInfo.arrival;
  extraInfo.departureAirport = extraInfo.departureAirport || extraInfo.departure_airport || extraInfo.departureAirportCode;
  extraInfo.arrivalAirport = extraInfo.arrivalAirport || extraInfo.arrival_airport || extraInfo.arrivalAirportCode;
  return { ...product, extraInfo };
}

function matchesTripMode(flight: FlightProduct) {
  if (form.tripType !== "transfer") return true;
  return Number(flight.extraInfo.stops || 0) > 0;
}

function chooseSpecialFare(deal: CalendarDay) {
  selectCalendarDay(deal);
}

function refreshSpecialFares() {
  if (availableCalendarDays.value.length <= 1) {
    ElMessage.info("当前低价日期较少，暂无更多推荐");
    return;
  }
  specialFareRefreshSeed.value += 1;
  ElMessage.success("已为你换一批低价机票");
}

async function applyLowPricePlan(plan: LowPriceRoutePlan) {
  form.tripType = plan.mode;
  if (plan.mode === "transfer") {
    form.directOnly = false;
  }
  if (plan.mode === "roundtrip") {
    form.returnDate = addDays(plan.date, 3);
  }
  activeLegKey.value = "outbound";
  applyDateToActiveLeg(plan.date);
  calendarStartDate.value = plan.date;
  activeCalendarMonth.value = plan.date.slice(0, 7);
  resetSelection();
  await refreshRouteData({ keepSelectionReset: false });
  if (selectedFlight.value) {
    scrollOrderPanelIntoView();
  } else {
    scrollResultsPanelIntoView();
  }
}

async function selectCalendarDay(day: CalendarDay) {
  if (!day.available) return;
  applyDateToActiveLeg(day.date);
  calendarStartDate.value = day.date;
  activeCalendarMonth.value = day.date.slice(0, 7);
  resetSelection();
  await refreshRouteData({ keepSelectionReset: false });
  if (selectedFlight.value) {
    scrollOrderPanelIntoView();
    ElMessage.success("已为当天选择可购航班，可继续填写乘机人并下单");
  } else {
    scrollResultsPanelIntoView();
    ElMessage.warning("当天暂无符合条件的可购航班，请调整航线或筛选条件");
  }
}

function calendarPriceText(day?: CalendarDay) {
  if (!day || !day.available || Number(day.lowestPrice) <= 0) return "无票";
  return `￥${Math.round(Number(day.lowestPrice))}`;
}

function calendarDeltaText(day?: CalendarDay) {
  if (!day?.available || Number(day.lowestPrice) <= 0 || calendarAveragePrice.value <= 0) return "暂无价格";
  const delta = Math.round(Number(day.lowestPrice) - calendarAveragePrice.value);
  if (delta <= -20) return `省 ￥${Math.abs(delta)}`;
  if (delta >= 20) return `高 ￥${delta}`;
  return "接近均价";
}

function calendarDayToneClass(day?: CalendarDay) {
  if (!day?.available || Number(day.lowestPrice) <= 0) return "is-unavailable";
  if (isLowestCalendarDay(day)) return "is-best";
  if (calendarAveragePrice.value <= 0) return "is-neutral";
  const price = Number(day.lowestPrice);
  if (price <= calendarAveragePrice.value * 0.92) return "is-cheap";
  if (price >= calendarAveragePrice.value * 1.12) return "is-high";
  return "is-neutral";
}

function calendarDayLabel(date: string) {
  const parsed = new Date(`${date}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) return date.slice(5);
  const weekdays = ["日", "一", "二", "三", "四", "五", "六"];
  return `${date.slice(5)} 周${weekdays[parsed.getDay()]}`;
}

function calendarDateNumber(date: string) {
  return String(Number(date.slice(8)));
}

function calendarLeadingBlankDays(date: string) {
  const parsed = new Date(`${date}T00:00:00`);
  return Number.isNaN(parsed.getTime()) ? 0 : parsed.getDay();
}

function isTodayCalendarDay(day?: CalendarDay) {
  return day?.date === today;
}

function isLowestCalendarDay(day?: CalendarDay) {
  if (!day?.available || !lowestCalendarDay.value) return false;
  return day.date === lowestCalendarDay.value.date;
}

function seededRecommendationScore(value: string, seed: number) {
  let hash = seed * 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function trendBarHeight(day: CalendarDay) {
  if (!day.available || Number(day.lowestPrice) <= 0) return "12%";
  const min = lowestCalendarPrice.value;
  const max = highestCalendarPrice.value;
  if (max <= min) return "72%";
  const ratio = (Number(day.lowestPrice) - min) / (max - min);
  return `${Math.round(36 + ratio * 54)}%`;
}

function calendarTooltip(day: CalendarDay) {
  return `${calendarDayLabel(day.date)} · ${calendarPriceText(day)}`;
}

function flightStopLabel(flight: FlightProduct) {
  const stops = Number(flight.extraInfo.stops || 0);
  return stops > 0 ? `${stops} 次经停` : "直飞";
}

function flightDurationLabel(flight: FlightProduct) {
  const departure = flight.extraInfo.departureTime;
  const arrival = flight.extraInfo.arrivalTime;
  if (!departure || !arrival) return "时长待定";
  const [departureHour, departureMinute] = String(departure).split(":").map(Number);
  const [arrivalHour, arrivalMinute] = String(arrival).split(":").map(Number);
  if ([departureHour, departureMinute, arrivalHour, arrivalMinute].some((value) => Number.isNaN(value))) {
    return "时长待定";
  }
  let minutes = arrivalHour * 60 + arrivalMinute - (departureHour * 60 + departureMinute);
  if (minutes <= 0) {
    minutes += 24 * 60;
  }
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}

function airlineInitial(flight: FlightProduct) {
  const airline = String(flight.extraInfo.airline || flight.name || "航");
  return airline.slice(0, 1);
}

function trendDayLabel(day: CalendarDay) {
  if (day.date.endsWith("-01")) return day.date.slice(5);
  return day.date.slice(8);
}

function calendarMonthLabel(month: string) {
  const [year, value] = month.split("-");
  return `${year}.${value}`;
}

function selectCalendarMonth(month: string) {
  activeCalendarMonth.value = month;
  scrollCalendarMonthIntoView(month);
}

function changeCalendarMonth(direction: -1 | 1) {
  const index = activeCalendarMonthIndex.value;
  if (index < 0) return;
  const nextMonth = calendarMonths.value[index + direction];
  if (!nextMonth) return;
  selectCalendarMonth(nextMonth);
}

function chooseLowestCalendarDay() {
  if (!lowestCalendarDay.value) return;
  selectCalendarDay(lowestCalendarDay.value);
}

function focusSelectedCalendarMonth() {
  activeCalendarMonth.value = activeDepartureMonth.value;
  scrollCalendarMonthIntoView(activeDepartureMonth.value);
}

function scrollCalendarMonthIntoView(month: string, behavior: "auto" | "smooth" = "smooth") {
  nextTick(() => {
    const scroller = calendarScrollerRef.value;
    const panel = scroller?.querySelector<HTMLElement>(`[data-calendar-month="${month}"]`);
    if (!scroller || !panel) return;
    scroller.scrollTo({ left: panel.offsetLeft, behavior });
  });
}

function changeCalendarStartDate() {
  if (!calendarStartDate.value) {
    calendarStartDate.value = today;
  }
  activeCalendarMonth.value = calendarStartDate.value.slice(0, 7);
  loadCalendar();
}

function subscribeLowPrice() {
  const lowest = lowestCalendarDay.value;
  if (!lowest) return;
  const leg = activeLeg.value;
  const alert = {
    route: `${leg.departureCity}-${leg.arrivalCity}`,
    departureCity: leg.departureCity,
    arrivalCity: leg.arrivalCity,
    cabin: form.cabin || "ANY",
    directOnly: form.directOnly,
    targetPrice: lowPriceTarget.value > 0 ? lowPriceTarget.value : Math.round(Number(lowest.lowestPrice)),
    bestDate: lowest.date,
    createdAt: new Date().toISOString(),
  };
  const alerts = readLowPriceAlerts().filter((item) => item.route !== alert.route || item.cabin !== alert.cabin || item.directOnly !== alert.directOnly);
  alerts.unshift(alert);
  lowPriceAlerts.value = alerts.slice(0, 20);
  localStorage.setItem(LOW_PRICE_ALERTS_KEY, JSON.stringify(lowPriceAlerts.value));
  ElMessage.success(`已订阅 ${cityName(leg.departureCity)} 至 ${cityName(leg.arrivalCity)} ￥${alert.targetPrice} 以下低价提醒`);
}

function readLowPriceAlerts(): LowPriceAlert[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(LOW_PRICE_ALERTS_KEY) || "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function lowPriceAlertStatus(alert: LowPriceAlert) {
  const matched = availableCalendarDays.value.find((day) => Number(day.lowestPrice) <= Number(alert.targetPrice));
  if (!matched) {
    return `当前近一年最低 ${calendarPriceText(lowestCalendarDay.value)}`;
  }
  return `已触发：${calendarDayLabel(matched.date)} ${calendarPriceText(matched)}`;
}

function cabinLabel(cabin: string) {
  if (!cabin || cabin === "ANY") return "不限舱";
  if (cabin === "BUSINESS") return "商务舱";
  if (cabin === "FIRST") return "头等舱";
  return "经济舱";
}

function orderCabin() {
  return form.cabin || "ECONOMY";
}

function createPassenger(index: number, type: "ADULT" | "CHILD"): PassengerForm {
  return {
    key: Date.now() + index,
    type,
    name: "",
    idType: "ID_CARD",
    idNo: "",
    phone: "",
  };
}

function validatePassengerForms() {
  const expectedCount = Math.max(1, form.adultCount + form.childCount);
  if (passengers.value.length < expectedCount) {
    syncPassengers();
  }
  for (let index = 0; index < expectedCount; index += 1) {
    const passenger = passengers.value[index];
    if (!passenger) {
      ElMessage.warning(`请补充乘机人 ${index + 1} 信息`);
      return false;
    }
    if (!passenger.name.trim()) {
      ElMessage.warning(`请输入乘机人 ${index + 1} 姓名`);
      return false;
    }
    if (!passenger.idNo.trim()) {
      ElMessage.warning(`请输入乘机人 ${index + 1} 证件号`);
      return false;
    }
    if (!ID_CARD_PATTERN.test(passenger.idNo.trim())) {
      ElMessage.warning(`乘机人 ${index + 1} 身份证号需为 18 位`);
      return false;
    }
    if (!passenger.phone.trim()) {
      ElMessage.warning(`请输入乘机人 ${index + 1} 联系电话`);
      return false;
    }
    if (!MAINLAND_PHONE_PATTERN.test(passenger.phone.trim())) {
      ElMessage.warning(`乘机人 ${index + 1} 手机号需为 11 位大陆手机号`);
      return false;
    }
  }
  return true;
}

// 乘机人数量跟成人/儿童计数联动；新增乘机人保持空值，逼迫用户真实填写。
function syncPassengers() {
  const targetCount = Math.max(1, form.adultCount + form.childCount);
  const next = passengers.value.slice(0, targetCount);
  while (next.length < targetCount) {
    next.push(createPassenger(next.length, next.length < form.adultCount ? "ADULT" : "CHILD"));
  }
  next.forEach((item, index) => {
    item.type = index < form.adultCount ? "ADULT" : "CHILD";
  });
  passengers.value = next;
}

function handlePassengerCountChange() {
  syncPassengers();
  refreshRouteData();
}

function handleDirectOnlyChange() {
  syncCalendarWindowWithActiveLeg();
  refreshRouteData();
}

function handleCabinChange() {
  syncCalendarWindowWithActiveLeg();
  refreshRouteData();
}

function handlePrimaryDepartureDateChange() {
  activeLegKey.value = "outbound";
  syncCalendarWindowWithActiveLeg();
  refreshRouteData();
}

function handleReturnDateChange() {
  activeLegKey.value = "return";
  syncCalendarWindowWithActiveLeg();
  refreshRouteData();
}

function handleSegmentDateChange(index: number) {
  const segment = multiSegments.value[index];
  if (segment) {
    activeLegKey.value = `segment-${segment.id}`;
  }
  syncCalendarWindowWithActiveLeg();
  refreshRouteData();
}

function passengerType(index: number) {
  return index < form.adultCount ? "成人" : "儿童";
}

function handleTripTypeChange() {
  if (form.tripType === "transfer") {
    form.directOnly = false;
  }
  if (form.tripType === "multi") {
    multiSegments.value = [
      { id: 1, departureCity: form.departureCity, arrivalCity: form.arrivalCity, departureDate: form.departureDate },
      { id: 2, departureCity: form.arrivalCity, arrivalCity: form.departureCity, departureDate: form.returnDate },
    ];
    activeLegKey.value = "segment-1";
  } else {
    activeLegKey.value = "outbound";
  }
  refreshRouteData();
}

function ensureActiveLeg() {
  if (!searchLegs.value.some((leg) => leg.key === activeLegKey.value)) {
    activeLegKey.value = searchLegs.value[0]?.key || "outbound";
  }
}

function activateLeg(key: string) {
  activeLegKey.value = key;
  syncCalendarWindowWithActiveLeg();
  resetSelection();
  loadCalendar();
  const list = resultsByLeg.value[key]?.list || [];
  if (list.length > 0) {
    void selectFlight(list[0]);
  }
}

function selectSegmentCity(index: number, field: "departureCity" | "arrivalCity", code: string) {
  const segment = multiSegments.value[index];
  if (!segment || segment[field] === code) return;
  segment[field] = code;
  refreshRouteData();
}

function addSegment() {
  const lastSegment = multiSegments.value[multiSegments.value.length - 1];
  const nextId = Math.max(...multiSegments.value.map((segment) => segment.id), 0) + 1;
  multiSegments.value.push({
    id: nextId,
    departureCity: lastSegment?.arrivalCity || form.arrivalCity,
    arrivalCity: form.departureCity,
    departureDate: addDays(lastSegment?.departureDate || form.departureDate, 1),
  });
  refreshRouteData();
}

function removeSegment(index: number) {
  if (multiSegments.value.length <= 2) return;
  multiSegments.value.splice(index, 1);
  refreshRouteData();
}

function applyDateToActiveLeg(date: string) {
  const leg = activeLeg.value;
  if (!leg) return;
  if (form.tripType === "roundtrip" && leg.key === "return") {
    form.returnDate = date;
    return;
  }
  if (form.tripType === "multi") {
    const segment = multiSegments.value.find((item) => `segment-${item.id}` === leg.key);
    if (segment) {
      segment.departureDate = date;
    }
    return;
  }
  form.departureDate = date;
}

function syncCalendarWindowWithActiveLeg() {
  const departureDate = activeLeg.value?.departureDate || today;
  calendarStartDate.value = departureDate;
  activeCalendarMonth.value = departureDate.slice(0, 7);
}

function todayString() {
  return formatDate(new Date());
}

function addDays(dateText: string, days: number) {
  const date = new Date(`${dateText}T00:00:00`);
  date.setDate(date.getDate() + days);
  return formatDate(date);
}

function formatDate(date: Date) {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function selectDepartureCity(code: string) {
  if (form.departureCity === code) return;
  form.departureCity = code;
  activeLegKey.value = "outbound";
  syncCalendarWindowWithActiveLeg();
  refreshRouteData();
}

function selectArrivalCity(code: string) {
  if (form.arrivalCity === code) return;
  form.arrivalCity = code;
  activeLegKey.value = "outbound";
  syncCalendarWindowWithActiveLeg();
  refreshRouteData();
}

function swapCities() {
  const nextDeparture = form.arrivalCity;
  form.arrivalCity = form.departureCity;
  form.departureCity = nextDeparture;
  activeLegKey.value = "outbound";
  syncCalendarWindowWithActiveLeg();
  refreshRouteData();
}

async function refreshRouteData(options: { keepSelectionReset?: boolean } = {}) {
  if (options.keepSelectionReset !== false) {
    resetSelection();
  }
  await Promise.all([searchFlights(), loadCalendar()]);
}

function scrollOrderPanelIntoView() {
  nextTick(() => {
    orderPanelRef.value?.scrollIntoView({ behavior: "smooth", block: "start" });
  });
}

function scrollResultsPanelIntoView() {
  nextTick(() => {
    resultsPanelRef.value?.scrollIntoView({ behavior: "smooth", block: "start" });
  });
}

function resetSelection() {
  selectedFlight.value = null;
  preview.value = null;
  currentOrderNo.value = "";
  orderStatus.value = null;
  refundAdvisorNote.value = "";
}

function provinceCode(province: string) {
  return cities.find((city) => city.name === province)?.code || "";
}

function cityName(code?: string) {
  const normalized = code || "";
  if (normalized.includes(",")) {
    return cities.find((city) => city.code === normalized)?.name || normalized.split(",")[0] || "-";
  }
  const airport = airportOptions.find((city) => city.code === normalized || city.cityCode === normalized);
  if (airport) {
    return airport.name;
  }
  return cities.find((city) => city.code.split(",").includes(normalized))?.name || code || "-";
}

function airportLabel(flight: FlightProduct, direction: "departure" | "arrival") {
  const airportCode = direction === "departure" ? flight.extraInfo.departureAirport : flight.extraInfo.arrivalAirport;
  const cityCode = direction === "departure" ? flight.extraInfo.departureCity : flight.extraInfo.arrivalCity;
  const airport = airportOptions.find((city) => city.code === airportCode);
  if (airport) {
    return `${airport.airport} (${airport.code})`;
  }
  return cityName(cityCode);
}
</script>

<style scoped>
.flight-page {
  --flight-bg: #f3f7f5;
  --flight-card: #ffffff;
  --flight-card-strong: #f7faf8;
  --flight-text: #16201f;
  --flight-muted: #627270;
  --flight-border: #d8e3df;
  --flight-primary: #0e7490;
  --flight-primary-strong: #0f5f76;
  --flight-price: #d65f1f;
  --flight-warm: #c4933e;
  --flight-shadow: 0 18px 44px rgba(33, 63, 59, 0.11);
  --search-card-bg: #ffffff;
  --search-card-head-bg: linear-gradient(90deg, color-mix(in srgb, var(--flight-primary) 7%, transparent), transparent 54%), #ffffff;
  --search-card-actions-bg: linear-gradient(90deg, color-mix(in srgb, var(--flight-primary) 6%, transparent), transparent), #ffffff;
  --search-card-surface: linear-gradient(180deg, #fbfdfc, #f7faf8);
  --search-card-surface-solid: #f7faf8;
  --search-card-border: #d8e3df;
  --search-card-shadow: 0 18px 44px rgba(33, 63, 59, 0.11);
  --search-checked-text: #ffffff;
  background:
    radial-gradient(circle at 12% 4%, rgba(14, 116, 144, 0.13), transparent 26%),
    linear-gradient(180deg, #f8fbf8 0%, var(--flight-bg) 42%, #edf3f0 100%);
  color: var(--flight-text);
  min-height: 100vh;
}

.flight-page.is-dark-mode {
  --flight-bg: #000000;
  --flight-card: #050505;
  --flight-card-strong: #0b0b0b;
  --flight-text: #f4f8ff;
  --flight-muted: #93a4b8;
  --flight-border: rgba(255, 255, 255, 0.18);
  --flight-primary: #5ed4e8;
  --flight-primary-strong: #2aa9c3;
  --flight-price: #f4b860;
  --flight-warm: #d6a85f;
  --flight-shadow: 0 22px 60px rgba(0, 0, 0, 0.56);
  --search-card-bg: #050505;
  --search-card-head-bg: #050505;
  --search-card-actions-bg: #050505;
  --search-card-surface: #0b0b0b;
  --search-card-surface-solid: #0b0b0b;
  --search-card-border: rgba(255, 255, 255, 0.18);
  --search-card-shadow: 0 24px 70px rgba(0, 0, 0, 0.56), 0 0 0 1px rgba(94, 212, 232, 0.08) inset;
  --search-checked-text: #041018;
  background: #000000 !important;
  background-image: none !important;
}

.flight-page.is-dark-mode :deep(.el-input__wrapper),
.flight-page.is-dark-mode :deep(.el-select__wrapper) {
  background: #0b0b0b !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.18) inset !important;
}

:global([data-theme="dark"]) .flight-page,
:global(.dark) .flight-page {
  --flight-bg: #050a12;
  --flight-card: #0d1624;
  --flight-card-strong: #121e2f;
  --flight-text: #f4f8ff;
  --flight-muted: #93a4b8;
  --flight-border: rgba(148, 163, 184, 0.24);
  --flight-primary: #5ed4e8;
  --flight-primary-strong: #2aa9c3;
  --flight-price: #f4b860;
  --flight-warm: #d6a85f;
  --flight-shadow: 0 22px 60px rgba(0, 0, 0, 0.48);
  --search-card-bg: #050505;
  --search-card-head-bg: #050505;
  --search-card-actions-bg: #050505;
  --search-card-surface: #0b0b0b;
  --search-card-surface-solid: #0b0b0b;
  --search-card-border: rgba(255, 255, 255, 0.18);
  --search-card-shadow: 0 24px 70px rgba(0, 0, 0, 0.46), 0 0 0 1px rgba(94, 212, 232, 0.05) inset;
  --search-checked-text: #041018;
  background:
    radial-gradient(circle at 18% 0%, rgba(94, 212, 232, 0.14), transparent 28%),
    radial-gradient(circle at 88% 12%, rgba(244, 184, 96, 0.1), transparent 24%),
    linear-gradient(180deg, #050a12 0%, #09111d 42%, #050a12 100%);
}

.flight-main {
  margin: 0 auto;
  max-width: 1180px;
  padding: 28px 24px 72px;
}

.booking-shell {
  background:
    linear-gradient(120deg, rgba(8, 37, 45, 0.94), rgba(14, 116, 144, 0.9)),
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.08) 0 1px, transparent 1px 72px);
  border-radius: 8px;
  box-shadow: var(--flight-shadow);
  overflow: hidden;
  padding: 22px 0 20px;
}

:global([data-theme="dark"]) .booking-shell,
:global(.dark) .booking-shell {
  background:
    linear-gradient(120deg, rgba(4, 16, 24, 0.96), rgba(9, 51, 64, 0.92)),
    repeating-linear-gradient(90deg, rgba(94, 212, 232, 0.14) 0 1px, transparent 1px 72px);
  border: 1px solid rgba(94, 212, 232, 0.16);
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(94, 212, 232, 0.08) inset;
}

.booking-hero {
  align-items: end;
  color: #fff;
  display: grid;
  gap: 18px;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.72fr);
  padding: 0 24px 22px;
}

.booking-hero p {
  color: rgba(255, 255, 255, 0.66);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  margin: 0 0 8px;
  text-transform: uppercase;
}

.booking-hero h1 {
  font-family: var(--font-heading);
  font-size: clamp(30px, 4vw, 52px);
  font-weight: 700;
  line-height: 1.05;
  margin: 0;
}

.booking-hero > div > span {
  color: rgba(255, 255, 255, 0.74);
  display: block;
  font-size: 15px;
  font-weight: 700;
  margin-top: 12px;
}

.route-metrics {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.route-metrics div {
  background: rgba(255, 255, 255, 0.11);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 8px;
  padding: 12px;
}

.route-metrics strong,
.route-metrics span {
  display: block;
}

.route-metrics strong {
  font-size: 24px;
  line-height: 1.1;
}

.route-metrics span {
  color: rgba(255, 255, 255, 0.68);
  font-size: 12px;
  font-weight: 800;
  margin-top: 6px;
}

.m3-workflow {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  padding: 0 24px 18px;
}

.workflow-step {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.68);
  min-width: 0;
  padding: 12px;
}

.workflow-step span,
.workflow-step strong,
.workflow-step small {
  display: block;
}

.workflow-step span {
  color: rgba(255, 255, 255, 0.48);
  font-size: 11px;
  font-weight: 900;
}

.workflow-step strong {
  color: rgba(255, 255, 255, 0.86);
  font-size: 14px;
  line-height: 1.2;
  margin-top: 6px;
}

.workflow-step small {
  font-size: 11px;
  font-weight: 800;
  line-height: 1.3;
  margin-top: 5px;
}

.workflow-step.active {
  background: rgba(94, 212, 232, 0.14);
  border-color: rgba(94, 212, 232, 0.34);
}

:global([data-theme="dark"]) .route-metrics div,
:global(.dark) .route-metrics div {
  background: rgba(94, 212, 232, 0.08);
  border-color: rgba(94, 212, 232, 0.18);
}

.booking-card,
.calendar-strip,
.results-panel,
.order-panel {
  background: var(--flight-card);
  border: 1px solid var(--flight-border);
  border-radius: 8px;
  box-shadow: var(--flight-shadow);
}

:global([data-theme="dark"]) .booking-card,
:global([data-theme="dark"]) .calendar-strip,
:global([data-theme="dark"]) .results-panel,
:global([data-theme="dark"]) .order-panel,
:global(.dark) .booking-card,
:global(.dark) .calendar-strip,
:global(.dark) .results-panel,
:global(.dark) .order-panel {
  background: linear-gradient(180deg, rgba(13, 22, 36, 0.96), rgba(9, 17, 29, 0.96));
  box-shadow: var(--flight-shadow), 0 0 0 1px rgba(94, 212, 232, 0.05) inset;
}

.booking-card {
  background: var(--search-card-bg);
  border-color: var(--search-card-border);
  box-shadow: var(--search-card-shadow);
  margin: 0 20px;
  overflow: hidden;
  padding: 0;
  position: relative;
}

.booking-card::before {
  background: linear-gradient(90deg, var(--flight-primary), var(--flight-warm), var(--flight-price));
  content: "";
  height: 3px;
  inset: 0 0 auto;
  position: absolute;
}

.booking-card-head {
  align-items: center;
  background: var(--search-card-head-bg);
  border-bottom: 1px solid var(--search-card-border);
  display: flex;
  gap: 14px;
  justify-content: space-between;
  padding: 20px 22px 16px;
}

.booking-card-head span,
.assist-label {
  color: var(--flight-muted);
  display: block;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.booking-card-head span,
.assist-label {
  text-transform: uppercase;
}

.booking-card-head strong {
  color: var(--flight-text);
  display: block;
  font-family: var(--font-heading);
  font-size: 24px;
  line-height: 1.15;
  margin-top: 4px;
}

.booking-card-head small {
  background: var(--search-card-surface);
  border: 1px solid var(--search-card-border);
  border-radius: 999px;
  color: var(--flight-primary);
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 900;
  padding: 7px 11px;
}

.ai-command {
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--flight-primary) 8%, transparent), transparent 70%),
    var(--search-card-surface-solid);
  border-bottom: 1px solid var(--search-card-border);
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(160px, 0.34fr) minmax(360px, 1fr);
  padding: 16px 22px;
}

.ai-copy span,
.ai-copy strong,
.ai-command p {
  display: block;
}

.ai-copy span {
  color: var(--flight-muted);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.ai-copy strong {
  color: var(--flight-text);
  font-size: 17px;
  line-height: 1.25;
  margin-top: 5px;
}

.ai-input-row {
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) auto;
}

.ai-input-row :deep(.el-button) {
  border-radius: 8px;
  font-weight: 900;
  min-width: 126px;
}

.ai-samples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  grid-column: 2;
}

.ai-samples button {
  background: var(--flight-card);
  border: 1px solid var(--flight-border);
  border-radius: 999px;
  color: var(--flight-muted);
  font-size: 12px;
  font-weight: 800;
  padding: 6px 10px;
}

.ai-samples button:hover {
  border-color: var(--flight-primary);
  color: var(--flight-primary);
}

.ai-command p {
  color: var(--flight-primary);
  font-size: 12px;
  font-weight: 800;
  grid-column: 2;
  margin: 0;
}

:global([data-theme="dark"]) .booking-card-head,
:global(.dark) .booking-card-head,
:global([data-theme="dark"]) .booking-actions,
:global(.dark) .booking-actions {
  background:
    linear-gradient(90deg, rgba(94, 212, 232, 0.08), transparent 60%),
    rgba(13, 22, 36, 0.72);
}

:global([data-theme="dark"]) .booking-card-head small,
:global(.dark) .booking-card-head small,
:global([data-theme="dark"]) .quick-options,
:global(.dark) .quick-options,
:global([data-theme="dark"]) .assist-options :deep(.el-checkbox),
:global(.dark) .assist-options :deep(.el-checkbox) {
  background: rgba(18, 30, 47, 0.92);
}

.trip-toolbar,
.booking-actions,
.section-head,
.assist-options {
  align-items: center;
  display: flex;
}

.trip-toolbar,
.booking-actions,
.section-head {
  justify-content: space-between;
}

.trip-toolbar {
  gap: 14px;
  padding: 16px 22px 0;
}

.quick-options,
.assist-options {
  gap: 18px;
}

.trip-toolbar :deep(.el-radio-group) {
  background: var(--search-card-surface);
  border: 1px solid var(--search-card-border);
  border-radius: 8px;
  padding: 4px;
}

.trip-toolbar :deep(.el-radio-button__inner) {
  background: transparent;
  border: 0;
  border-radius: 6px;
  box-shadow: none;
  font-weight: 900;
  padding: 9px 18px;
}

.trip-toolbar :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--flight-primary);
  box-shadow: 0 8px 22px color-mix(in srgb, var(--flight-primary) 24%, transparent);
  color: var(--search-checked-text);
}

:global([data-theme="dark"]) .trip-toolbar :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner),
:global(.dark) .trip-toolbar :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner),
:global([data-theme="dark"]) .search-button,
:global(.dark) .search-button {
  color: #041018;
}

.quick-options {
  align-items: center;
  background: var(--search-card-surface);
  border: 1px solid var(--search-card-border);
  border-radius: 8px;
  display: flex;
  flex-wrap: wrap;
  padding: 8px 12px;
}

.quick-options :deep(.el-checkbox) {
  height: 24px;
  margin-right: 0;
}

.search-grid {
  align-items: stretch;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(180px, 1.05fr) 42px minmax(180px, 1.05fr) minmax(156px, 0.9fr) minmax(156px, 0.9fr) minmax(188px, 1.1fr) minmax(152px, 0.82fr);
  padding: 16px 22px 0;
}

.field-card {
  background: var(--search-card-surface);
  border: 1px solid var(--search-card-border);
  border-radius: 8px;
  color: var(--flight-text);
  display: flex;
  flex-direction: column;
  gap: 7px;
  justify-content: center;
  min-height: 88px;
  min-width: 0;
  overflow: hidden;
  padding: 13px 14px;
  position: relative;
  text-align: left;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.field-card::before {
  background: var(--flight-primary);
  content: "";
  height: 28px;
  left: 0;
  opacity: 0.72;
  position: absolute;
  top: 16px;
  width: 3px;
}

:global(.flight-page .city-trigger) {
  background: var(--search-card-surface);
  border: 1px solid var(--search-card-border);
  border-radius: 8px;
  color: var(--flight-text);
  display: flex;
  flex-direction: column;
  gap: 7px;
  justify-content: center;
  min-height: 88px;
  min-width: 0;
  overflow: hidden;
  padding: 13px 14px;
  text-align: left;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
  width: 100%;
}

:global([data-theme="dark"] .flight-page .city-trigger),
:global(.dark .flight-page .city-trigger),
:global([data-theme="dark"]) .field-card,
:global(.dark) .field-card {
  background: rgba(18, 30, 47, 0.86);
}

.field-card span {
  color: var(--flight-muted);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

:global(.flight-page .city-trigger span) {
  color: var(--flight-muted);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.field-card strong {
  font-size: 19px;
  line-height: 1.3;
  white-space: nowrap;
}

:global(.flight-page .city-trigger strong) {
  color: var(--flight-text);
  font-size: 22px;
  line-height: 1.2;
  white-space: nowrap;
}

.city-trigger:hover,
.swap-button:hover,
.calendar-grid button:hover {
  border-color: var(--flight-primary);
}

:global(.flight-page .city-trigger:hover),
.field-card:hover {
  box-shadow: 0 12px 28px color-mix(in srgb, var(--flight-primary) 12%, transparent);
  transform: translateY(-1px);
}

:global(.flight-page .city-trigger:hover) {
  border-color: var(--flight-primary);
}

.swap-button {
  align-self: center;
  background: var(--search-card-surface-solid);
  border: 1px solid var(--search-card-border);
  border-radius: 50%;
  color: var(--flight-primary);
  font-size: 18px;
  font-weight: 900;
  height: 40px;
  justify-self: center;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
  width: 40px;
}

.swap-button:hover {
  box-shadow: 0 10px 26px color-mix(in srgb, var(--flight-primary) 18%, transparent);
  transform: rotate(180deg);
}

:global([data-theme="dark"]) .swap-button,
:global(.dark) .swap-button {
  background: rgba(18, 30, 47, 0.92);
  box-shadow: 0 0 0 1px rgba(94, 212, 232, 0.08) inset;
}

.date-card :deep(.el-input__wrapper),
.field-card :deep(.el-input__wrapper) {
  background: transparent;
  box-shadow: none;
  padding: 0;
}

.date-card :deep(.el-date-editor.el-input),
.field-card :deep(.el-select),
.field-card :deep(.el-select__wrapper) {
  width: 100%;
}

.date-card :deep(.el-input__inner),
.field-card :deep(.el-input__inner) {
  color: var(--flight-text);
  font-weight: 700;
}

.counter-row {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 8px;
}

.counter-row :deep(.el-input-number) {
  width: 100%;
}

.booking-actions {
  background: var(--search-card-actions-bg);
  border-top: 1px solid var(--search-card-border);
  gap: 14px;
  margin-top: 18px;
  padding: 16px 22px 20px;
}

.search-button {
  background: linear-gradient(135deg, var(--flight-primary), var(--flight-primary-strong));
  border: 0;
  border-radius: 8px;
  box-shadow: 0 12px 30px color-mix(in srgb, var(--flight-primary) 24%, transparent);
  color: var(--search-checked-text);
  font-weight: 900;
  min-height: 46px;
  min-width: 178px;
}

.assist-options {
  align-items: center;
  flex-wrap: wrap;
}

.assist-label {
  margin-right: 2px;
}

.assist-options :deep(.el-checkbox) {
  background: var(--search-card-surface);
  border: 1px solid var(--search-card-border);
  border-radius: 999px;
  height: 34px;
  margin-right: 0;
  padding: 0 12px;
}

.assist-options :deep(.el-checkbox.is-checked) {
  border-color: var(--flight-primary);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--flight-primary) 20%, transparent) inset;
}

.segment-panel {
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 8px;
  display: grid;
  gap: 12px;
  margin-top: 14px;
  padding: 12px;
}

.segment-row {
  align-items: center;
  display: grid;
  gap: 10px;
  grid-template-columns: 74px minmax(150px, 1fr) minmax(150px, 1fr) minmax(150px, 1fr) auto;
}

.segment-row > span {
  color: var(--flight-muted);
  font-weight: 700;
}

.special-fares,
.calendar-strip,
.content-grid {
  margin-top: 18px;
}

.results-panel,
.order-panel {
  padding: 18px;
}

.calendar-strip {
  padding: 18px;
}

.special-fares {
  padding: 18px;
}

.special-fare-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.special-fare-actions span {
  color: var(--flight-muted);
  font-weight: 700;
}

.special-fare-actions button {
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 6px;
  color: var(--flight-text);
  font-size: 12px;
  font-weight: 900;
  min-height: 32px;
  padding: 7px 12px;
  transition: border-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.special-fare-actions button:hover:not(:disabled) {
  border-color: var(--flight-primary);
  color: var(--flight-primary);
  transform: translateY(-1px);
}

.special-fare-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.special-fare-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin-top: 14px;
}

.special-fare-card,
.low-route-plans button {
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 8px;
  color: var(--flight-text);
  min-width: 0;
  text-align: left;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.special-fare-card {
  display: grid;
  gap: 7px;
  min-height: 112px;
  padding: 14px;
}

.special-fare-card:hover,
.low-route-plans button:hover {
  border-color: var(--flight-primary);
  box-shadow: 0 14px 30px color-mix(in srgb, var(--flight-primary) 13%, transparent);
  transform: translateY(-1px);
}

.special-fare-card span,
.low-route-plans span {
  color: var(--flight-muted);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.special-fare-card strong {
  color: var(--flight-primary);
  font-size: 24px;
  line-height: 1;
}

.special-fare-card small,
.low-route-plans small {
  color: var(--flight-muted);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.45;
}

.low-route-plans {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 12px;
}

.low-route-plans button {
  display: grid;
  gap: 7px;
  min-height: 104px;
  padding: 14px;
}

.low-route-plans strong {
  color: var(--flight-text);
  font-size: 16px;
  line-height: 1.3;
}

.flight-page.is-dark-mode .special-fare-card,
.flight-page.is-dark-mode .low-route-plans button,
.flight-page.is-dark-mode .special-fare-actions button,
:global([data-theme="dark"]) .flight-page .special-fare-card,
:global([data-theme="dark"]) .flight-page .low-route-plans button,
:global([data-theme="dark"]) .flight-page .special-fare-actions button,
:global(.dark) .flight-page .special-fare-card,
:global(.dark) .flight-page .low-route-plans button,
:global(.dark) .flight-page .special-fare-actions button {
  background: #050505;
  border-color: var(--border-light);
}

.section-head p {
  color: var(--flight-muted);
  font-size: 13px;
  margin: 0 0 4px;
}

.section-head h2 {
  font-size: 22px;
  margin: 0;
}

.section-head.compact h2 {
  font-size: 20px;
}

.section-head > span {
  color: var(--flight-muted);
  font-weight: 700;
}

.calendar-actions {
  align-items: center;
  color: var(--flight-muted);
  display: flex;
  font-size: 13px;
  font-weight: 700;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.calendar-advisor {
  align-items: center;
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--flight-primary) 10%, transparent), transparent 72%),
    var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 8px;
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(0, 1fr) auto;
  margin-top: 14px;
  padding: 14px;
}

.calendar-advisor-copy span,
.calendar-advisor-copy strong,
.calendar-advisor-copy p {
  display: block;
}

.calendar-advisor-copy span {
  color: var(--flight-muted);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.calendar-advisor-copy strong {
  color: var(--flight-text);
  font-size: 20px;
  line-height: 1.2;
  margin-top: 4px;
}

.calendar-advisor-copy p {
  color: var(--flight-muted);
  font-size: 13px;
  line-height: 1.55;
  margin: 6px 0 0;
}

.calendar-advisor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.calendar-advisor-actions button {
  background: var(--flight-card);
  border: 1px solid var(--flight-border);
  border-radius: 999px;
  color: var(--flight-text);
  font-size: 12px;
  font-weight: 900;
  min-height: 34px;
  padding: 0 13px;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, color 0.18s ease;
}

.calendar-advisor-actions button:first-child {
  background: var(--flight-primary);
  border-color: var(--flight-primary);
  color: var(--search-checked-text);
}

.calendar-advisor-actions button:hover:not(:disabled) {
  border-color: var(--flight-primary);
  box-shadow: 0 10px 24px color-mix(in srgb, var(--flight-primary) 18%, transparent);
  color: var(--flight-primary);
}

.calendar-advisor-actions button:first-child:hover:not(:disabled) {
  color: var(--search-checked-text);
}

.calendar-advisor-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.calendar-summary {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(5, 1fr);
  margin-top: 14px;
}

.calendar-summary div {
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 6px;
  min-height: 72px;
  padding: 12px;
}

.calendar-summary span,
.calendar-summary strong,
.calendar-summary small {
  display: block;
}

.calendar-summary span {
  color: var(--flight-muted);
  font-size: 12px;
}

.calendar-summary strong {
  color: var(--flight-text);
  font-size: 22px;
  line-height: 1.2;
  margin-top: 8px;
}

.calendar-summary small {
  color: var(--flight-muted);
  font-size: 11px;
  font-weight: 800;
  line-height: 1.25;
  margin-top: 6px;
}

:global([data-theme="dark"]) .calendar-summary div,
:global(.dark) .calendar-summary div {
  background: rgba(18, 30, 47, 0.86);
}

:global([data-theme="dark"]) .calendar-summary strong,
:global(.dark) .calendar-summary strong {
  text-shadow: 0 0 18px rgba(94, 212, 232, 0.12);
}

.calendar-trend {
  align-items: end;
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 8px;
  display: grid;
  gap: 6px;
  grid-auto-columns: 14px;
  grid-auto-flow: column;
  height: 156px;
  margin-top: 14px;
  overflow-x: auto;
  padding: 12px 12px 8px;
}

.trend-day {
  align-items: center;
  color: var(--flight-muted);
  display: flex;
  flex-direction: column;
  gap: 6px;
  height: 100%;
  justify-content: flex-end;
  min-width: 14px;
}

.trend-bar {
  background: linear-gradient(180deg, var(--flight-primary), var(--flight-primary-strong));
  border-radius: 999px 999px 4px 4px;
  display: block;
  min-height: 12px;
  opacity: 0.82;
  width: 100%;
}

:global([data-theme="dark"]) .calendar-trend,
:global(.dark) .calendar-trend {
  background: rgba(18, 30, 47, 0.86);
}

:global([data-theme="dark"]) .trend-bar,
:global(.dark) .trend-bar {
  box-shadow: 0 0 14px rgba(94, 212, 232, 0.2);
}

.trend-day.lowest .trend-bar {
  background: linear-gradient(180deg, #ffcf7a, var(--flight-price));
  opacity: 1;
}

.trend-day.is-cheap .trend-bar,
.trend-day.is-best .trend-bar {
  background: linear-gradient(180deg, var(--flight-price), var(--flight-primary));
  opacity: 1;
}

.trend-day.is-high .trend-bar {
  opacity: 0.48;
}

.trend-day.active .trend-bar {
  box-shadow: 0 0 0 2px var(--flight-card-strong), 0 0 0 4px var(--flight-primary);
}

.trend-day.today small {
  color: var(--flight-primary);
  font-weight: 700;
}

.trend-day.disabled {
  cursor: not-allowed;
}

.trend-day.disabled .trend-bar {
  background: var(--flight-border);
  opacity: 0.75;
}

.trend-day small {
  font-size: 10px;
  line-height: 1;
  writing-mode: vertical-rl;
}

.calendar-month-navigator {
  align-items: center;
  display: flex;
  gap: 8px;
  margin-top: 14px;
}

.calendar-months {
  display: flex;
  flex: 1;
  gap: 8px;
  min-width: 0;
  overflow-x: auto;
  padding-bottom: 2px;
}

.calendar-nav-button {
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 6px;
  color: var(--flight-text);
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 800;
  min-height: 34px;
  padding: 7px 12px;
}

.calendar-nav-button:hover:not(:disabled) {
  border-color: var(--flight-primary);
  color: var(--flight-primary);
}

.calendar-nav-button:disabled {
  cursor: not-allowed;
  opacity: 0.42;
}

.calendar-months button {
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 999px;
  color: var(--flight-muted);
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 700;
  padding: 7px 12px;
}

.calendar-months button.active {
  background: color-mix(in srgb, var(--flight-primary) 14%, var(--flight-card-strong));
  border-color: var(--flight-primary);
  color: var(--flight-primary);
}

.calendar-carousel {
  display: flex;
  gap: 14px;
  margin-top: 14px;
  overflow-x: auto;
  overscroll-behavior-x: contain;
  padding-bottom: 4px;
  scroll-snap-type: x mandatory;
  scrollbar-color: var(--flight-border) transparent;
  scrollbar-width: thin;
}

.calendar-month-panel {
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 8px;
  flex: 0 0 100%;
  min-width: 0;
  padding: 14px;
  scroll-snap-align: start;
}

:global([data-theme="dark"]) .calendar-month-panel,
:global(.dark) .calendar-month-panel {
  background: rgba(18, 30, 47, 0.86);
}

.calendar-month-head {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.calendar-month-head strong {
  color: var(--flight-text);
  font-size: 18px;
}

.calendar-month-head span {
  color: var(--flight-muted);
  font-size: 12px;
  font-weight: 700;
}

.calendar-weekdays {
  color: var(--flight-muted);
  display: grid;
  font-size: 12px;
  font-weight: 700;
  gap: 10px;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  margin-bottom: 8px;
  text-align: center;
}

.calendar-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}

.calendar-grid button {
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 6px;
  color: var(--flight-text);
  min-height: 76px;
  padding: 8px 6px;
  text-align: center;
}

:global([data-theme="dark"]) .calendar-grid button,
:global(.dark) .calendar-grid button {
  background: rgba(13, 22, 36, 0.82);
}

.calendar-blank {
  min-height: 76px;
}

.calendar-grid button.active {
  background: color-mix(in srgb, var(--flight-primary) 12%, var(--flight-card-strong));
  border-color: var(--flight-primary);
  color: var(--flight-primary);
}

.calendar-grid button.is-cheap {
  background: color-mix(in srgb, var(--flight-price) 8%, var(--flight-card-strong));
  border-color: color-mix(in srgb, var(--flight-price) 42%, var(--flight-border));
}

.calendar-grid button.is-best {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--flight-price) 14%, transparent), transparent),
    var(--flight-card-strong);
  border-color: var(--flight-price);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--flight-price) 20%, transparent) inset;
}

.calendar-grid button.is-high {
  opacity: 0.72;
}

.calendar-grid button.lowest {
  border-color: var(--flight-price);
}

.calendar-grid button.today {
  box-shadow: 0 0 0 1px var(--flight-primary) inset;
}

.calendar-grid button.disabled {
  color: var(--flight-muted);
  cursor: not-allowed;
  opacity: 0.68;
}

.calendar-grid button span,
.calendar-grid strong,
.calendar-grid small {
  display: block;
}

.calendar-grid button span {
  color: var(--flight-muted);
  font-size: 12px;
}

.calendar-grid strong {
  font-size: 17px;
  line-height: 1.2;
  margin-top: 6px;
}

.calendar-grid small {
  color: var(--flight-price);
  font-size: 11px;
  margin-top: 4px;
  min-height: 15px;
}

.alert-list {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  margin-top: 14px;
}

.alert-list div {
  background: color-mix(in srgb, var(--flight-primary) 8%, var(--flight-card-strong));
  border: 1px solid var(--flight-border);
  border-radius: 8px;
  padding: 12px;
}

.alert-list span,
.alert-list strong,
.alert-list small {
  display: block;
}

.alert-list span,
.alert-list small {
  color: var(--flight-muted);
  font-size: 12px;
}

.alert-list strong {
  color: var(--flight-text);
  font-size: 17px;
  margin: 5px 0;
}

.content-grid {
  display: grid;
  gap: 18px;
  grid-template-columns: minmax(0, 1fr) 360px;
}

.sort-select {
  width: 132px;
}

.result-summary {
  border-bottom: 1px solid var(--flight-border);
  color: var(--flight-muted);
  display: flex;
  flex-wrap: wrap;
  font-size: 12px;
  font-weight: 800;
  gap: 8px;
  margin-top: 14px;
  padding-bottom: 12px;
}

.result-summary span {
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 999px;
  padding: 5px 9px;
}

.requirement-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.requirement-strip div {
  align-items: center;
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 999px;
  color: var(--flight-muted);
  display: inline-flex;
  font-size: 12px;
  font-weight: 800;
  gap: 6px;
  padding: 6px 9px;
}

.requirement-strip span {
  background: var(--flight-muted);
  border-radius: 50%;
  height: 7px;
  opacity: 0.45;
  width: 7px;
}

.requirement-strip div.done {
  border-color: color-mix(in srgb, var(--flight-primary) 45%, var(--flight-border));
  color: var(--flight-primary);
}

.requirement-strip div.done span {
  background: var(--flight-primary);
  opacity: 1;
}

.leg-tabs {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  margin-top: 14px;
}

.leg-tabs button {
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 8px;
  color: var(--flight-text);
  min-height: 68px;
  padding: 10px 12px;
  text-align: left;
}

.leg-tabs button.active {
  border-color: var(--flight-primary);
  box-shadow: 0 0 0 1px var(--flight-primary) inset;
}

.leg-tabs strong,
.leg-tabs span {
  display: block;
}

.leg-tabs span {
  color: var(--flight-muted);
  font-size: 12px;
  margin-top: 4px;
}

.flight-card {
  align-items: stretch;
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 8px;
  display: grid;
  gap: 16px;
  grid-template-columns: 136px minmax(280px, 1fr) minmax(96px, 112px);
  margin-top: 12px;
  min-width: 0;
  padding: 14px;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.flight-card:hover {
  border-color: color-mix(in srgb, var(--flight-primary) 45%, var(--flight-border));
  box-shadow: 0 12px 28px rgba(21, 87, 151, 0.12);
  transform: translateY(-1px);
}

:global([data-theme="dark"]) .flight-card,
:global(.dark) .flight-card,
:global([data-theme="dark"]) .passenger-card,
:global(.dark) .passenger-card {
  background: rgba(18, 30, 47, 0.86);
}

:global([data-theme="dark"]) .flight-card:hover,
:global(.dark) .flight-card:hover {
  box-shadow: 0 18px 42px rgba(0, 0, 0, 0.42), 0 0 0 1px rgba(94, 212, 232, 0.18) inset;
}

.flight-card.selected {
  border-color: var(--flight-primary);
  box-shadow: 0 0 0 1px var(--flight-primary) inset, var(--flight-shadow);
}

.flight-carrier,
.flight-route-main,
.flight-price {
  min-width: 0;
}

.flight-carrier {
  border-right: 1px solid var(--flight-border);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding-right: 12px;
}

.flight-carrier em {
  align-items: center;
  background: linear-gradient(135deg, var(--flight-primary), var(--flight-primary-strong));
  border-radius: 8px;
  color: #fff;
  display: inline-flex;
  font-size: 16px;
  font-style: normal;
  font-weight: 900;
  height: 36px;
  justify-content: center;
  margin-bottom: 10px;
  width: 36px;
}

:global([data-theme="dark"]) .flight-carrier em,
:global(.dark) .flight-carrier em {
  color: #041018;
  box-shadow: 0 0 22px rgba(94, 212, 232, 0.2);
}

.flight-carrier strong,
.time-node strong,
.flight-price strong {
  display: block;
}

.flight-carrier strong {
  color: var(--flight-text);
  font-size: 16px;
  line-height: 1.25;
}

.flight-carrier span {
  color: var(--flight-primary);
  font-size: 13px;
  font-weight: 800;
  margin-top: 7px;
}

.flight-carrier small {
  color: var(--flight-muted);
  font-size: 12px;
  margin-top: 4px;
}

.flight-route-main {
  align-items: center;
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(96px, 1fr) minmax(96px, 0.8fr) minmax(96px, 1fr);
}

.time-node {
  min-width: 0;
}

.time-node strong {
  font-size: 24px;
  line-height: 1.1;
}

.time-node span,
.flight-price span,
.detail-block p {
  color: var(--flight-muted);
}

.time-node span {
  display: block;
  font-size: 14px;
  font-weight: 800;
  margin-top: 8px;
}

.time-node small {
  color: var(--flight-text);
  display: block;
  font-size: 12px;
  line-height: 1.35;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time-node.arrival {
  text-align: right;
}

.route-line {
  align-items: center;
  color: var(--flight-muted);
  display: flex;
  flex-direction: column;
  font-size: 12px;
  font-weight: 800;
  gap: 8px;
  justify-content: center;
}

.route-line::before,
.route-line::after {
  background: var(--flight-border);
  content: "";
  height: 1px;
  min-width: 18px;
  width: 100%;
}

.route-line span {
  background: var(--flight-card);
  border: 1px solid var(--flight-border);
  border-radius: 999px;
  color: var(--flight-primary);
  padding: 4px 9px;
}

:global([data-theme="dark"]) .route-line span,
:global(.dark) .route-line span {
  background: rgba(5, 10, 18, 0.9);
}

.route-line small {
  color: var(--flight-muted);
  font-size: 11px;
}

.detail-block h3 {
  margin: 0 0 6px;
}

.detail-block p {
  display: block;
  margin: 4px 0;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0;
}

.detail-tags span {
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 999px;
  color: var(--flight-muted);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.3;
  padding: 5px 9px;
}

.flight-price {
  align-items: flex-end;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  overflow: hidden;
  text-align: right;
}

.flight-price strong {
  font-size: 26px;
  line-height: 1.1;
  max-width: 100%;
  overflow-wrap: anywhere;
}

.flight-price small {
  color: var(--flight-muted);
  font-size: 11px;
  font-weight: 800;
  margin-top: 4px;
}

.flight-price strong,
.price-lines .total {
  color: var(--flight-price);
}

.flight-price span {
  display: block;
  font-size: 12px;
  font-weight: 800;
  margin: 8px 0 12px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flight-price span.warning {
  color: var(--flight-price);
}

.flight-price :deep(.el-button) {
  max-width: 100%;
  min-width: 76px;
}

.detail-block {
  border-bottom: 1px solid var(--flight-border);
  margin: 12px 0 16px;
  padding-bottom: 12px;
}

.policy-advisor {
  background: color-mix(in srgb, var(--flight-primary) 8%, var(--flight-card-strong));
  border: 1px solid var(--flight-border);
  border-radius: 8px;
  display: grid;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px;
}

.policy-advisor span,
.policy-advisor strong {
  display: block;
}

.policy-advisor span {
  color: var(--flight-muted);
  font-size: 11px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.policy-advisor strong {
  color: var(--flight-text);
  font-size: 16px;
  margin-top: 3px;
}

.policy-advisor p {
  color: var(--flight-muted);
  font-size: 13px;
  line-height: 1.55;
  margin: 0;
}

.policy-advisor :deep(.el-button) {
  justify-self: start;
}

.passenger-forms {
  display: grid;
  gap: 12px;
}

.passenger-card {
  background: var(--flight-card-strong);
  border: 1px solid var(--flight-border);
  border-radius: 8px;
  padding: 12px;
}

.passenger-card-head {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.passenger-card-head strong {
  color: var(--flight-text);
}

.price-lines {
  border-top: 1px solid var(--flight-border);
  margin-top: 10px;
  padding-top: 12px;
}

.price-lines div {
  display: flex;
  justify-content: space-between;
  margin: 9px 0;
}

.price-lines .total {
  font-size: 20px;
}

.ticket-info {
  background: color-mix(in srgb, var(--flight-primary) 12%, var(--flight-card-strong));
  border: 1px solid var(--flight-primary);
  border-radius: 8px;
  margin-top: 12px;
  padding: 12px;
}

:global([data-theme="dark"]) .ticket-info,
:global(.dark) .ticket-info {
  background: rgba(94, 212, 232, 0.08);
  box-shadow: 0 0 0 1px rgba(94, 212, 232, 0.08) inset;
}

.ticket-info span,
.ticket-info strong {
  display: block;
}

.ticket-info span {
  color: var(--flight-muted);
  font-size: 12px;
}

.ticket-info strong {
  color: var(--flight-text);
  font-size: 18px;
  margin-top: 4px;
}

.order-actions {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, 1fr);
  margin-top: 18px;
}

.change-hint {
  color: var(--text-secondary);
  font-size: 14px;
  margin-bottom: 12px;
}

.order-panel {
  align-self: start;
  max-height: calc(100vh - 96px);
  overflow: auto;
  position: sticky;
  top: 82px;
}

:deep(.el-radio-button__inner),
:deep(.el-checkbox__label),
:deep(.el-form-item__label) {
  color: var(--flight-text);
}

:deep(.el-input__wrapper),
:deep(.el-select__wrapper) {
  background: var(--flight-card-strong);
  box-shadow: 0 0 0 1px var(--flight-border) inset;
}

:deep(.el-input__inner),
:deep(.el-select__placeholder),
:deep(.el-select__selected-item) {
  color: var(--flight-text);
}

/* Night card system */
:global([data-theme="dark"]) .booking-card,
:global([data-theme="dark"]) .calendar-strip,
:global([data-theme="dark"]) .results-panel,
:global([data-theme="dark"]) .order-panel,
:global(.dark) .booking-card,
:global(.dark) .calendar-strip,
:global(.dark) .results-panel,
:global(.dark) .order-panel {
  background:
    linear-gradient(180deg, rgba(13, 22, 36, 0.98), rgba(7, 13, 24, 0.96)),
    radial-gradient(circle at 12% 0%, rgba(94, 212, 232, 0.08), transparent 34%);
  border-color: rgba(148, 163, 184, 0.2);
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.46), 0 0 0 1px rgba(94, 212, 232, 0.05) inset;
}

:global([data-theme="dark"]) .booking-card-head,
:global([data-theme="dark"]) .booking-actions,
:global([data-theme="dark"]) .ai-command,
:global(.dark) .booking-card-head,
:global(.dark) .booking-actions,
:global(.dark) .ai-command {
  background:
    linear-gradient(90deg, rgba(94, 212, 232, 0.1), rgba(244, 184, 96, 0.04), transparent),
    rgba(9, 17, 29, 0.82);
  border-color: rgba(148, 163, 184, 0.18);
}

:global([data-theme="dark"]) .field-card,
:global([data-theme="dark"]) .quick-options,
:global([data-theme="dark"]) .segment-panel,
:global([data-theme="dark"]) .calendar-summary div,
:global([data-theme="dark"]) .calendar-trend,
:global([data-theme="dark"]) .calendar-month-panel,
:global([data-theme="dark"]) .alert-list div,
:global([data-theme="dark"]) .result-summary span,
:global([data-theme="dark"]) .requirement-strip div,
:global([data-theme="dark"]) .leg-tabs button,
:global([data-theme="dark"]) .flight-card,
:global([data-theme="dark"]) .passenger-card,
:global([data-theme="dark"]) .policy-advisor,
:global([data-theme="dark"]) .ai-samples button,
:global([data-theme="dark"]) .detail-tags span,
:global([data-theme="dark"]) .ticket-info,
:global(.dark) .field-card,
:global(.dark) .quick-options,
:global(.dark) .segment-panel,
:global(.dark) .calendar-summary div,
:global(.dark) .calendar-trend,
:global(.dark) .calendar-month-panel,
:global(.dark) .alert-list div,
:global(.dark) .result-summary span,
:global(.dark) .requirement-strip div,
:global(.dark) .leg-tabs button,
:global(.dark) .flight-card,
:global(.dark) .passenger-card,
:global(.dark) .policy-advisor,
:global(.dark) .ai-samples button,
:global(.dark) .detail-tags span,
:global(.dark) .ticket-info {
  background:
    linear-gradient(180deg, rgba(18, 30, 47, 0.94), rgba(12, 22, 36, 0.92));
  border-color: rgba(148, 163, 184, 0.2);
  box-shadow: 0 0 0 1px rgba(94, 212, 232, 0.04) inset;
}

:global([data-theme="dark"] .flight-page .city-trigger),
:global(.dark .flight-page .city-trigger),
:global([data-theme="dark"]) .trip-toolbar :deep(.el-radio-group),
:global([data-theme="dark"]) .assist-options :deep(.el-checkbox),
:global(.dark) .trip-toolbar :deep(.el-radio-group),
:global(.dark) .assist-options :deep(.el-checkbox) {
  background:
    linear-gradient(180deg, rgba(18, 30, 47, 0.94), rgba(12, 22, 36, 0.92));
  border-color: rgba(148, 163, 184, 0.2);
}

:global([data-theme="dark"]) .field-card:hover,
:global([data-theme="dark"] .flight-page .city-trigger:hover),
:global([data-theme="dark"]) .leg-tabs button:hover,
:global([data-theme="dark"]) .calendar-grid button:hover,
:global(.dark) .field-card:hover,
:global(.dark .flight-page .city-trigger:hover),
:global(.dark) .leg-tabs button:hover,
:global(.dark) .calendar-grid button:hover {
  border-color: rgba(94, 212, 232, 0.72);
  box-shadow: 0 14px 36px rgba(0, 0, 0, 0.34), 0 0 0 1px rgba(94, 212, 232, 0.18) inset;
}

:global([data-theme="dark"]) .calendar-grid button,
:global(.dark) .calendar-grid button {
  background: rgba(9, 17, 29, 0.82);
  border-color: rgba(148, 163, 184, 0.18);
}

:global([data-theme="dark"]) .calendar-grid button.active,
:global([data-theme="dark"]) .leg-tabs button.active,
:global([data-theme="dark"]) .flight-card.selected,
:global(.dark) .calendar-grid button.active,
:global(.dark) .leg-tabs button.active,
:global(.dark) .flight-card.selected {
  background:
    linear-gradient(180deg, rgba(94, 212, 232, 0.14), rgba(18, 30, 47, 0.96));
  border-color: rgba(94, 212, 232, 0.76);
  box-shadow: 0 0 0 1px rgba(94, 212, 232, 0.22) inset, 0 18px 44px rgba(0, 0, 0, 0.32);
}

:global([data-theme="dark"]) .calendar-grid button.lowest,
:global(.dark) .calendar-grid button.lowest {
  border-color: rgba(244, 184, 96, 0.82);
  box-shadow: 0 0 0 1px rgba(244, 184, 96, 0.18) inset;
}

:global([data-theme="dark"]) .route-line span,
:global([data-theme="dark"]) .booking-card-head small,
:global(.dark) .route-line span,
:global(.dark) .booking-card-head small {
  background: rgba(5, 10, 18, 0.72);
  border-color: rgba(94, 212, 232, 0.24);
}

:global([data-theme="dark"]) .price-lines,
:global([data-theme="dark"]) .detail-block,
:global(.dark) .price-lines,
:global(.dark) .detail-block {
  border-color: rgba(148, 163, 184, 0.18);
}

:global([data-theme="dark"]) .price-lines div,
:global(.dark) .price-lines div {
  color: #d6e1f0;
}

:global([data-theme="dark"]) .flight-carrier,
:global(.dark) .flight-carrier {
  border-color: rgba(148, 163, 184, 0.18);
}

:global([data-theme="dark"]) .flight-price,
:global(.dark) .flight-price {
  color: #d6e1f0;
}

:global([data-theme="dark"]) .flight-page :deep(.el-input__wrapper),
:global([data-theme="dark"]) .flight-page :deep(.el-select__wrapper),
:global(.dark) .flight-page :deep(.el-input__wrapper),
:global(.dark) .flight-page :deep(.el-select__wrapper) {
  background: rgba(5, 10, 18, 0.36);
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.18) inset;
}

/* Home card type mapping for the flight module */
.flight-search-card {
  background: var(--white, #ffffff);
  border: 0;
  border-radius: var(--radius-lg, 16px);
  box-shadow: var(--shadow-lg, 0 20px 50px rgba(15, 23, 42, 0.14));
  overflow: hidden;
}

.flight-page.is-dark-mode .flight-search-card,
:global(html[data-theme="dark"]) .flight-page .flight-search-card,
:global(html.dark) .flight-page .flight-search-card,
:global([data-theme="dark"]) .flight-page .flight-search-card,
:global(.dark) .flight-page .flight-search-card {
  background: #050505 !important;
  border: 1px solid rgba(255, 255, 255, 0.18) !important;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.56), 0 0 0 1px rgba(94, 212, 232, 0.08) inset !important;
}

.flight-search-card::before {
  display: none;
}

.flight-search-card .booking-card-head {
  border-bottom-color: var(--slate-200, #e2e8f0);
}

.flight-feature-card {
  background: var(--white, #ffffff);
  border: 1px solid var(--slate-100, #f1f5f9);
  border-radius: var(--radius-md, 8px);
  box-shadow: none;
  transition: border-color var(--duration-normal, 0.24s) var(--ease-out, ease), box-shadow var(--duration-normal, 0.24s) var(--ease-out, ease), transform var(--duration-normal, 0.24s) var(--ease-out, ease);
}

.flight-feature-card:hover {
  border-color: var(--gold-200, #ead4a6);
  box-shadow: var(--shadow-md, 0 14px 34px rgba(15, 23, 42, 0.12));
  transform: translateY(-6px);
}

.order-panel.flight-feature-card:hover {
  transform: none;
}

.flight-stack-card {
  background: color-mix(in srgb, var(--white, #ffffff) 92%, var(--cream-100, #f7f1e8));
  border: 1px solid var(--slate-100, #f1f5f9);
  border-radius: var(--radius-md, 8px);
  box-shadow: none;
  transition: border-color var(--duration-normal, 0.24s) var(--ease-out, ease), box-shadow var(--duration-normal, 0.24s) var(--ease-out, ease), transform var(--duration-normal, 0.24s) var(--ease-out, ease);
}

.flight-stack-card:hover {
  border-color: var(--gold-200, #ead4a6);
  box-shadow: var(--shadow-md, 0 14px 34px rgba(15, 23, 42, 0.12));
  transform: translateY(-6px);
}

.flight-stack-card.selected {
  border-color: var(--gold-500, #c9953d);
  box-shadow: 0 0 0 1px var(--gold-500, #c9953d) inset, var(--shadow-md, 0 14px 34px rgba(15, 23, 42, 0.12));
}

.flight-mini-card,
.flight-summary-grid > div,
.flight-mini-card-list > div {
  background: var(--white, #ffffff);
  border: 1px solid var(--slate-100, #f1f5f9);
  border-radius: var(--radius-sm, 6px);
  box-shadow: none;
}

.flight-page.is-dark-mode .flight-feature-card,
.flight-page.is-dark-mode .flight-stack-card,
.flight-page.is-dark-mode .flight-mini-card,
.flight-page.is-dark-mode .flight-summary-grid > div,
.flight-page.is-dark-mode .flight-mini-card-list > div,
:global(html[data-theme="dark"]) .flight-page .flight-feature-card,
:global(html[data-theme="dark"]) .flight-page .flight-stack-card,
:global(html[data-theme="dark"]) .flight-page .flight-mini-card,
:global(html[data-theme="dark"]) .flight-page .flight-summary-grid > div,
:global(html[data-theme="dark"]) .flight-page .flight-mini-card-list > div,
:global(html.dark) .flight-page .flight-feature-card,
:global(html.dark) .flight-page .flight-stack-card,
:global(html.dark) .flight-page .flight-mini-card,
:global(html.dark) .flight-page .flight-summary-grid > div,
:global(html.dark) .flight-page .flight-mini-card-list > div,
:global([data-theme="dark"]) .flight-page .flight-feature-card,
:global([data-theme="dark"]) .flight-page .flight-stack-card,
:global([data-theme="dark"]) .flight-page .flight-mini-card,
:global([data-theme="dark"]) .flight-page .flight-summary-grid > div,
:global([data-theme="dark"]) .flight-page .flight-mini-card-list > div,
:global(.dark) .flight-page .flight-feature-card,
:global(.dark) .flight-page .flight-stack-card,
:global(.dark) .flight-page .flight-mini-card,
:global(.dark) .flight-page .flight-summary-grid > div,
:global(.dark) .flight-page .flight-mini-card-list > div {
  background: #050505 !important;
  border-color: rgba(255, 255, 255, 0.18) !important;
  box-shadow: 0 0 0 1px rgba(94, 212, 232, 0.05) inset !important;
}

.flight-page.is-dark-mode .flight-feature-card:hover,
.flight-page.is-dark-mode .flight-stack-card:hover,
:global(html[data-theme="dark"]) .flight-page .flight-feature-card:hover,
:global(html[data-theme="dark"]) .flight-page .flight-stack-card:hover,
:global(html.dark) .flight-page .flight-feature-card:hover,
:global(html.dark) .flight-page .flight-stack-card:hover,
:global([data-theme="dark"]) .flight-page .flight-feature-card:hover,
:global([data-theme="dark"]) .flight-page .flight-stack-card:hover,
:global(.dark) .flight-page .flight-feature-card:hover,
:global(.dark) .flight-page .flight-stack-card:hover {
  border-color: rgba(244, 184, 96, 0.64) !important;
  box-shadow: 0 18px 44px rgba(0, 0, 0, 0.44), 0 0 0 1px rgba(244, 184, 96, 0.14) inset !important;
}

/* Search card dark lock */
:global(html[data-theme="dark"]) .flight-page {
  --search-card-bg: #050505;
  --search-card-head-bg: #050505;
  --search-card-actions-bg: #050505;
  --search-card-surface: #0b0b0b;
  --search-card-surface-solid: #0b0b0b;
  --search-card-border: rgba(255, 255, 255, 0.18);
  --search-card-shadow: 0 24px 70px rgba(0, 0, 0, 0.56), 0 0 0 1px rgba(94, 212, 232, 0.08) inset;
  --search-checked-text: #041018;
}

:global(html.dark) .flight-page {
  --search-card-bg: #050505;
  --search-card-head-bg: #050505;
  --search-card-actions-bg: #050505;
  --search-card-surface: #0b0b0b;
  --search-card-surface-solid: #0b0b0b;
  --search-card-border: rgba(255, 255, 255, 0.18);
  --search-card-shadow: 0 24px 70px rgba(0, 0, 0, 0.56), 0 0 0 1px rgba(94, 212, 232, 0.08) inset;
  --search-checked-text: #041018;
}

:global(html[data-theme="dark"]) .flight-page .booking-card,
:global(html.dark) .flight-page .booking-card,
:global([data-theme="dark"]) .flight-page .booking-card,
:global(.dark) .flight-page .booking-card {
  background: #050505 !important;
  border-color: rgba(255, 255, 255, 0.18) !important;
  box-shadow: var(--search-card-shadow) !important;
}

:global(html[data-theme="dark"]) .flight-page .booking-card-head,
:global(html[data-theme="dark"]) .flight-page .ai-command,
:global(html[data-theme="dark"]) .flight-page .booking-actions,
:global(html.dark) .flight-page .booking-card-head,
:global(html.dark) .flight-page .ai-command,
:global(html.dark) .flight-page .booking-actions,
:global([data-theme="dark"]) .flight-page .booking-card-head,
:global([data-theme="dark"]) .flight-page .ai-command,
:global([data-theme="dark"]) .flight-page .booking-actions,
:global(.dark) .flight-page .booking-card-head,
:global(.dark) .flight-page .ai-command,
:global(.dark) .flight-page .booking-actions {
  background: #050505 !important;
  border-color: rgba(255, 255, 255, 0.18) !important;
}

:global(html[data-theme="dark"]) .flight-page .booking-actions,
:global(html.dark) .flight-page .booking-actions,
:global([data-theme="dark"]) .flight-page .booking-actions,
:global(.dark) .flight-page .booking-actions {
  background: #050505 !important;
}

:global(html[data-theme="dark"]) .flight-page .booking-card-head small,
:global(html[data-theme="dark"]) .flight-page .quick-options,
:global(html[data-theme="dark"]) .flight-page .field-card,
:global(html[data-theme="dark"] .flight-page .city-trigger),
:global(html[data-theme="dark"]) .flight-page .swap-button,
:global(html.dark) .flight-page .booking-card-head small,
:global(html.dark) .flight-page .quick-options,
:global(html.dark) .flight-page .field-card,
:global(html.dark .flight-page .city-trigger),
:global(html.dark) .flight-page .swap-button,
:global([data-theme="dark"]) .flight-page .quick-options,
:global([data-theme="dark"]) .flight-page .field-card,
:global([data-theme="dark"] .flight-page .city-trigger),
:global([data-theme="dark"]) .flight-page .swap-button,
:global(.dark) .flight-page .quick-options,
:global(.dark) .flight-page .field-card,
:global(.dark .flight-page .city-trigger),
:global(.dark) .flight-page .swap-button {
  background: #0b0b0b !important;
  border-color: rgba(255, 255, 255, 0.18) !important;
}

:global(html[data-theme="dark"]) .flight-page .trip-toolbar :deep(.el-radio-group),
:global(html[data-theme="dark"]) .flight-page .assist-options :deep(.el-checkbox),
:global(html.dark) .flight-page .trip-toolbar :deep(.el-radio-group),
:global(html.dark) .flight-page .assist-options :deep(.el-checkbox),
:global([data-theme="dark"]) .flight-page .trip-toolbar :deep(.el-radio-group),
:global([data-theme="dark"]) .flight-page .assist-options :deep(.el-checkbox),
:global(.dark) .flight-page .trip-toolbar :deep(.el-radio-group),
:global(.dark) .flight-page .assist-options :deep(.el-checkbox) {
  background: #0b0b0b !important;
  border-color: rgba(255, 255, 255, 0.18) !important;
}

:global([data-theme="dark"]) .flight-page .trip-toolbar :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner),
:global([data-theme="dark"]) .flight-page .search-button,
:global(.dark) .flight-page .trip-toolbar :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner),
:global(.dark) .flight-page .search-button {
  color: var(--search-checked-text);
}

/* Flight module dark background lock */
.flight-page.is-dark-mode,
:global(html[data-theme="dark"]) .flight-page,
:global(html.dark) .flight-page,
:global([data-theme="dark"]) .flight-page,
:global(.dark) .flight-page {
  background: #000000 !important;
  background-image: none !important;
}

.flight-page.is-dark-mode .booking-shell,
:global(html[data-theme="dark"]) .flight-page .booking-shell,
:global(html.dark) .flight-page .booking-shell,
:global([data-theme="dark"]) .flight-page .booking-shell,
:global(.dark) .flight-page .booking-shell {
  background: #050505 !important;
  background-image: none !important;
  border-color: rgba(255, 255, 255, 0.18) !important;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.56) !important;
}

.flight-page.is-dark-mode .route-metrics div,
.flight-page.is-dark-mode .workflow-step,
:global(html[data-theme="dark"]) .flight-page .route-metrics div,
:global(html[data-theme="dark"]) .flight-page .workflow-step,
:global(html.dark) .flight-page .route-metrics div,
:global(html.dark) .flight-page .workflow-step,
:global([data-theme="dark"]) .flight-page .route-metrics div,
:global([data-theme="dark"]) .flight-page .workflow-step,
:global(.dark) .flight-page .route-metrics div,
:global(.dark) .flight-page .workflow-step {
  background: #0b0b0b !important;
  border-color: rgba(255, 255, 255, 0.18) !important;
}

/* Sync flight dark palette with the main page */
.flight-page.is-dark-mode,
:global(html[data-theme="dark"]) .flight-page,
:global(html.dark) .flight-page,
:global([data-theme="dark"]) .flight-page,
:global(.dark) .flight-page {
  --flight-bg: var(--bg-body);
  --flight-card: var(--bg-card);
  --flight-card-strong: var(--bg-elevated);
  --flight-text: var(--text-primary);
  --flight-muted: var(--text-secondary);
  --flight-border: var(--border-light);
  --flight-primary: var(--accent);
  --flight-primary-strong: var(--accent-hover);
  --flight-price: var(--gold-400);
  --flight-warm: var(--gold-500);
  --flight-shadow: var(--shadow-lg);
  --search-card-bg: var(--bg-card);
  --search-card-head-bg: var(--bg-card);
  --search-card-actions-bg: var(--bg-card);
  --search-card-surface: var(--bg-elevated);
  --search-card-surface-solid: var(--bg-elevated);
  --search-card-border: var(--border-light);
  --search-card-shadow: var(--shadow-lg);
  --search-checked-text: var(--white);
  background: var(--bg-body) !important;
  background-image: none !important;
}

.flight-page.is-dark-mode .booking-shell,
:global(html[data-theme="dark"]) .flight-page .booking-shell,
:global(html.dark) .flight-page .booking-shell,
:global([data-theme="dark"]) .flight-page .booking-shell,
:global(.dark) .flight-page .booking-shell {
  background: var(--navy-900) !important;
  background-image: none !important;
  border-color: var(--border-light) !important;
  box-shadow: var(--shadow-lg) !important;
}

.flight-page.is-dark-mode .flight-search-card,
.flight-page.is-dark-mode .booking-card,
.flight-page.is-dark-mode .flight-feature-card,
.flight-page.is-dark-mode .order-panel,
.flight-page.is-dark-mode .results-panel,
.flight-page.is-dark-mode .calendar-strip,
:global(html[data-theme="dark"]) .flight-page .flight-search-card,
:global(html[data-theme="dark"]) .flight-page .booking-card,
:global(html[data-theme="dark"]) .flight-page .flight-feature-card,
:global(html[data-theme="dark"]) .flight-page .order-panel,
:global(html[data-theme="dark"]) .flight-page .results-panel,
:global(html[data-theme="dark"]) .flight-page .calendar-strip,
:global(html.dark) .flight-page .flight-search-card,
:global(html.dark) .flight-page .booking-card,
:global(html.dark) .flight-page .flight-feature-card,
:global(html.dark) .flight-page .order-panel,
:global(html.dark) .flight-page .results-panel,
:global(html.dark) .flight-page .calendar-strip,
:global([data-theme="dark"]) .flight-page .flight-search-card,
:global([data-theme="dark"]) .flight-page .booking-card,
:global([data-theme="dark"]) .flight-page .flight-feature-card,
:global([data-theme="dark"]) .flight-page .order-panel,
:global([data-theme="dark"]) .flight-page .results-panel,
:global([data-theme="dark"]) .flight-page .calendar-strip,
:global(.dark) .flight-page .flight-search-card,
:global(.dark) .flight-page .booking-card,
:global(.dark) .flight-page .flight-feature-card,
:global(.dark) .flight-page .order-panel,
:global(.dark) .flight-page .results-panel,
:global(.dark) .flight-page .calendar-strip {
  background: var(--bg-card) !important;
  border-color: var(--border-light) !important;
  box-shadow: var(--shadow-lg) !important;
}

.flight-page.is-dark-mode .booking-card-head,
.flight-page.is-dark-mode .ai-command,
.flight-page.is-dark-mode .booking-actions,
.flight-page.is-dark-mode .flight-mini-card,
.flight-page.is-dark-mode .flight-stack-card,
.flight-page.is-dark-mode .flight-summary-grid > div,
.flight-page.is-dark-mode .flight-mini-card-list > div,
.flight-page.is-dark-mode .field-card,
.flight-page.is-dark-mode .quick-options,
.flight-page.is-dark-mode .segment-panel,
.flight-page.is-dark-mode .calendar-trend,
.flight-page.is-dark-mode .calendar-month-panel,
.flight-page.is-dark-mode .calendar-grid button,
.flight-page.is-dark-mode .result-summary span,
.flight-page.is-dark-mode .requirement-strip div,
.flight-page.is-dark-mode .leg-tabs button,
.flight-page.is-dark-mode .detail-tags span,
.flight-page.is-dark-mode .ai-samples button,
.flight-page.is-dark-mode .route-metrics div,
.flight-page.is-dark-mode .workflow-step,
:global(html[data-theme="dark"]) .flight-page .booking-card-head,
:global(html[data-theme="dark"]) .flight-page .ai-command,
:global(html[data-theme="dark"]) .flight-page .booking-actions,
:global(html[data-theme="dark"]) .flight-page .flight-mini-card,
:global(html[data-theme="dark"]) .flight-page .flight-stack-card,
:global(html[data-theme="dark"]) .flight-page .flight-summary-grid > div,
:global(html[data-theme="dark"]) .flight-page .flight-mini-card-list > div,
:global(html[data-theme="dark"]) .flight-page .field-card,
:global(html[data-theme="dark"]) .flight-page .quick-options,
:global(html[data-theme="dark"]) .flight-page .segment-panel,
:global(html[data-theme="dark"]) .flight-page .calendar-trend,
:global(html[data-theme="dark"]) .flight-page .calendar-month-panel,
:global(html[data-theme="dark"]) .flight-page .calendar-grid button,
:global(html[data-theme="dark"]) .flight-page .result-summary span,
:global(html[data-theme="dark"]) .flight-page .requirement-strip div,
:global(html[data-theme="dark"]) .flight-page .leg-tabs button,
:global(html[data-theme="dark"]) .flight-page .detail-tags span,
:global(html[data-theme="dark"]) .flight-page .ai-samples button,
:global(html[data-theme="dark"]) .flight-page .route-metrics div,
:global(html[data-theme="dark"]) .flight-page .workflow-step,
:global(html.dark) .flight-page .booking-card-head,
:global(html.dark) .flight-page .ai-command,
:global(html.dark) .flight-page .booking-actions,
:global(html.dark) .flight-page .flight-mini-card,
:global(html.dark) .flight-page .flight-stack-card,
:global(html.dark) .flight-page .flight-summary-grid > div,
:global(html.dark) .flight-page .flight-mini-card-list > div,
:global(html.dark) .flight-page .field-card,
:global(html.dark) .flight-page .quick-options,
:global(html.dark) .flight-page .segment-panel,
:global(html.dark) .flight-page .calendar-trend,
:global(html.dark) .flight-page .calendar-month-panel,
:global(html.dark) .flight-page .calendar-grid button,
:global(html.dark) .flight-page .result-summary span,
:global(html.dark) .flight-page .requirement-strip div,
:global(html.dark) .flight-page .leg-tabs button,
:global(html.dark) .flight-page .detail-tags span,
:global(html.dark) .flight-page .ai-samples button,
:global(html.dark) .flight-page .route-metrics div,
:global(html.dark) .flight-page .workflow-step,
:global([data-theme="dark"]) .flight-page .booking-card-head,
:global([data-theme="dark"]) .flight-page .ai-command,
:global([data-theme="dark"]) .flight-page .booking-actions,
:global([data-theme="dark"]) .flight-page .flight-mini-card,
:global([data-theme="dark"]) .flight-page .flight-stack-card,
:global([data-theme="dark"]) .flight-page .flight-summary-grid > div,
:global([data-theme="dark"]) .flight-page .flight-mini-card-list > div,
:global([data-theme="dark"]) .flight-page .field-card,
:global([data-theme="dark"]) .flight-page .quick-options,
:global([data-theme="dark"]) .flight-page .segment-panel,
:global([data-theme="dark"]) .flight-page .calendar-trend,
:global([data-theme="dark"]) .flight-page .calendar-month-panel,
:global([data-theme="dark"]) .flight-page .calendar-grid button,
:global([data-theme="dark"]) .flight-page .result-summary span,
:global([data-theme="dark"]) .flight-page .requirement-strip div,
:global([data-theme="dark"]) .flight-page .leg-tabs button,
:global([data-theme="dark"]) .flight-page .detail-tags span,
:global([data-theme="dark"]) .flight-page .ai-samples button,
:global([data-theme="dark"]) .flight-page .route-metrics div,
:global([data-theme="dark"]) .flight-page .workflow-step,
:global(.dark) .flight-page .booking-card-head,
:global(.dark) .flight-page .ai-command,
:global(.dark) .flight-page .booking-actions,
:global(.dark) .flight-page .flight-mini-card,
:global(.dark) .flight-page .flight-stack-card,
:global(.dark) .flight-page .flight-summary-grid > div,
:global(.dark) .flight-page .flight-mini-card-list > div,
:global(.dark) .flight-page .field-card,
:global(.dark) .flight-page .quick-options,
:global(.dark) .flight-page .segment-panel,
:global(.dark) .flight-page .calendar-trend,
:global(.dark) .flight-page .calendar-month-panel,
:global(.dark) .flight-page .calendar-grid button,
:global(.dark) .flight-page .result-summary span,
:global(.dark) .flight-page .requirement-strip div,
:global(.dark) .flight-page .leg-tabs button,
:global(.dark) .flight-page .detail-tags span,
:global(.dark) .flight-page .ai-samples button,
:global(.dark) .flight-page .route-metrics div,
:global(.dark) .flight-page .workflow-step {
  background: var(--bg-elevated) !important;
  border-color: var(--border-light) !important;
  box-shadow: none !important;
}

.flight-page.is-dark-mode :deep(.el-input__wrapper),
.flight-page.is-dark-mode :deep(.el-select__wrapper),
:global(html[data-theme="dark"]) .flight-page :deep(.el-input__wrapper),
:global(html[data-theme="dark"]) .flight-page :deep(.el-select__wrapper),
:global(html.dark) .flight-page :deep(.el-input__wrapper),
:global(html.dark) .flight-page :deep(.el-select__wrapper),
:global([data-theme="dark"]) .flight-page :deep(.el-input__wrapper),
:global([data-theme="dark"]) .flight-page :deep(.el-select__wrapper),
:global(.dark) .flight-page :deep(.el-input__wrapper),
:global(.dark) .flight-page :deep(.el-select__wrapper) {
  background: #050505 !important;
  box-shadow: 0 0 0 1px var(--border-light) inset !important;
}

.flight-page.is-dark-mode .calendar-advisor,
:global(html[data-theme="dark"]) .flight-page .calendar-advisor,
:global(html.dark) .flight-page .calendar-advisor,
:global([data-theme="dark"]) .flight-page .calendar-advisor,
:global(.dark) .flight-page .calendar-advisor {
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--accent) 10%, transparent), transparent 72%),
    var(--bg-elevated) !important;
  border-color: var(--border-light) !important;
}

.flight-page.is-dark-mode .calendar-grid button.is-cheap,
:global(html[data-theme="dark"]) .flight-page .calendar-grid button.is-cheap,
:global(html.dark) .flight-page .calendar-grid button.is-cheap,
:global([data-theme="dark"]) .flight-page .calendar-grid button.is-cheap,
:global(.dark) .flight-page .calendar-grid button.is-cheap {
  background: color-mix(in srgb, var(--gold-500) 10%, var(--bg-elevated)) !important;
  border-color: color-mix(in srgb, var(--gold-500) 50%, var(--border-light)) !important;
}

.flight-page.is-dark-mode .calendar-grid button.is-best,
:global(html[data-theme="dark"]) .flight-page .calendar-grid button.is-best,
:global(html.dark) .flight-page .calendar-grid button.is-best,
:global([data-theme="dark"]) .flight-page .calendar-grid button.is-best,
:global(.dark) .flight-page .calendar-grid button.is-best {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--gold-500) 16%, transparent), transparent),
    var(--bg-elevated) !important;
  border-color: var(--gold-500) !important;
}

.flight-page.is-dark-mode .calendar-grid button.active,
:global(html[data-theme="dark"]) .flight-page .calendar-grid button.active,
:global(html.dark) .flight-page .calendar-grid button.active,
:global([data-theme="dark"]) .flight-page .calendar-grid button.active,
:global(.dark) .flight-page .calendar-grid button.active {
  border-color: var(--accent) !important;
  box-shadow: 0 0 0 1px var(--accent) inset !important;
}

@media (max-width: 1280px) and (min-width: 1101px) {
  .content-grid {
    grid-template-columns: minmax(0, 1fr) 320px;
  }

  .flight-card {
    grid-template-columns: 118px minmax(220px, 1fr) minmax(88px, 104px);
    gap: 12px;
  }

  .flight-price strong {
    font-size: 23px;
  }
}

@media (max-width: 1100px) {
  .booking-hero {
    align-items: stretch;
    grid-template-columns: 1fr;
  }

  .route-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .special-fare-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .m3-workflow {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ai-command {
    grid-template-columns: 1fr;
  }

  .ai-samples,
  .ai-command p {
    grid-column: auto;
  }

  .search-grid,
  .content-grid,
  .flight-card,
  .segment-row {
    grid-template-columns: 1fr;
  }

  .flight-card {
    gap: 14px;
  }

  .flight-carrier {
    border-right: 0;
    border-bottom: 1px solid var(--flight-border);
    padding: 0 0 12px;
  }

  .flight-route-main {
    grid-template-columns: minmax(0, 1fr) minmax(82px, 0.7fr) minmax(0, 1fr);
  }

  .flight-price {
    align-items: stretch;
    border-top: 1px solid var(--flight-border);
    padding-top: 12px;
    text-align: left;
  }

  .calendar-summary {
    grid-template-columns: repeat(2, 1fr);
  }

  .calendar-advisor {
    align-items: stretch;
    grid-template-columns: 1fr;
  }

  .calendar-advisor-actions {
    justify-content: flex-start;
  }

  .order-panel {
    max-height: none;
    position: static;
  }

  .calendar-month-panel {
    padding: 10px;
  }

  .calendar-weekdays,
  .calendar-grid {
    gap: 6px;
    grid-template-columns: repeat(7, minmax(0, 1fr));
  }

  .calendar-grid button,
  .calendar-blank {
    min-height: 62px;
  }

  .calendar-grid button {
    padding: 6px 2px;
  }

  .calendar-grid button span,
  .calendar-grid small {
    font-size: 10px;
  }

  .calendar-grid strong {
    font-size: 13px;
  }

  .trip-toolbar,
  .booking-actions {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .quick-options,
  .assist-options,
  .search-button {
    width: 100%;
  }

  .search-button {
    justify-content: center;
  }
}

@media (max-width: 640px) {
  .flight-main {
    padding: 16px 12px 48px;
  }

  .booking-shell {
    padding-top: 18px;
  }

  .booking-card {
    margin: 0 12px;
    padding: 0;
  }

  .booking-card-head,
  .ai-command,
  .trip-toolbar,
  .search-grid,
  .booking-actions {
    padding-left: 14px;
    padding-right: 14px;
  }

  .booking-card-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .booking-card-head small {
    white-space: normal;
  }

  .booking-hero {
    padding: 0 16px 18px;
  }

  .route-metrics,
  .special-fare-grid,
  .low-route-plans,
  .m3-workflow,
  .calendar-summary,
  .order-actions {
    grid-template-columns: 1fr;
  }

  .ai-input-row {
    grid-template-columns: 1fr;
  }

  .flight-route-main {
    gap: 10px;
    grid-template-columns: 1fr;
  }

  .time-node.arrival {
    text-align: left;
  }

  .route-line {
    align-items: flex-start;
  }
}
</style>
