/**
 * 用户中心API模块
 *
 * 对应新版 API 文档第 4 节「用户中心」
 * 所有接口需携带 Authorization: Bearer <token>
 */

import { http } from "@/utils/request";

// ─── 数据类型 ─────────────────────────────────────────

/** 用户DTO — GET /api/user/current 返回 */
export interface UserCurrentDTO {
  id: string;
  phone: string;
  email: string;
  nickname: string;
  avatar: string;
  gender?: number;
  birth_date?: string;
  points: number;
  level: number;
  identity: string;
  roles: string[];
  permissions: string[];
}

/** 用户资料更新请求体 — PUT /api/user/current */
export interface UserUpdateRequest {
  nickname?: string;
  phone?: string | null;
  email?: string | null;
  gender?: number;
  birth_date?: string;
}

/** 出行人DTO */
export interface TravelerDTO {
  id?: string;
  name: string;
  id_card: string;
  idCard?: string;
  phone?: string;
  id_type?: number;
  idType?: number;
  create_time?: string;
}

/** 积分记录DTO */
export interface PointsLogDTO {
  id: string;
  user_id?: string;
  type: number;
  amount: number;
  source?: string;
  order_id?: string;
  create_time?: string;
}

/** 等级升级信息DTO */
export interface UserLevelUpgradeInfoDTO {
  level: number;
  pointsNeeded: number;
  benefits: string[];
}

/** 订单DTO（概要） */
export interface OrderDTO {
  id?: string;
  order_no: string;
  user_id?: string;
  order_type: string;
  total_amount?: number;
  points_deduct?: number;
  pay_amount: number;
  payment_method?: string;
  source?: string;
  status: number;
  pay_deadline?: string;
  pay_time?: string;
  cancel_reason?: string;
  create_time?: string;
  createEpochMs?: number;
  update_time?: string;
  updateEpochMs?: number;
  changed_once?: number;
  original_order_no?: string;
}

export interface RefundResultDTO {
  orderNo: string;
  status: number;
  statusText?: string;
  paidAmount?: number;
  refundAmount?: number;
  refundRule?: string;
}

export interface PayResultDTO {
  orderNo: string;
  status: number;
  payAmount?: number;
  pickupCode?: string;
  expireTime?: string;
  paymentMethod?: string;
}

/** 分页响应 */
export interface PageResponse<T> {
  total: number;
  list: T[];
}

// ─── 个人信息 ─────────────────────────────────────────

/**
 * 获取当前用户信息
 * GET /api/user/current
 */
export const getCurrentUser = (): Promise<UserCurrentDTO> => {
  return http.get<UserCurrentDTO>("/user/current");
};

/**
 * 更新当前用户个人信息
 * PUT /api/user/current
 */
export const updateCurrentUser = (
  data: UserUpdateRequest
): Promise<UserCurrentDTO> => {
  return http.put<UserCurrentDTO>("/user/current", data);
};

/**
 * 更新头像
 * POST /api/user/avatar
 */
export const updateAvatar = (
  avatarUrl: string
): Promise<{ avatarUrl: string }> => {
  return http.post<{ avatarUrl: string }>("/user/avatar", { avatarUrl });
};

export const uploadAvatarFile = (
  file: File
): Promise<{ avatarUrl: string }> => {
  const formData = new FormData();
  formData.append("file", file);
  return http.post<{ avatarUrl: string }>("/user/avatar/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
};

/**
 * 修改密码
 * PUT /api/user/password
 */
export const changePassword = (data: {
  oldPassword: string;
  newPassword: string;
}): Promise<boolean> => {
  return http.put<boolean>("/user/password", data);
};

// ─── 出行人管理 ──────────────────────────────────────

/**
 * 获取出行人列表
 * GET /api/user/travelers
 */
export const getTravelers = (): Promise<TravelerDTO[]> => {
  return http.get<TravelerDTO[]>("/user/travelers").then((list) =>
    (list || []).map((item) => ({
      ...item,
      idCard: item.idCard || item.id_card,
      idType: item.idType ?? item.id_type,
      id_card: item.id_card || item.idCard || "",
      id_type: item.id_type ?? item.idType ?? 0,
    }))
  );
};

/**
 * 新增出行人
 * POST /api/user/travelers
 */
export const addTraveler = (data: TravelerDTO): Promise<TravelerDTO> => {
  return http.post<TravelerDTO>("/user/travelers", {
    ...data,
    id_card: data.id_card || data.idCard || "",
    id_type: data.id_type ?? data.idType ?? 0,
  });
};

/**
 * 修改出行人
 * PUT /api/user/travelers/{id}
 */
export const updateTraveler = (
  id: number | string,
  data: TravelerDTO
): Promise<TravelerDTO> => {
  return http.put<TravelerDTO>(`/user/travelers/${id}`, {
    ...data,
    id_card: data.id_card || data.idCard || "",
    id_type: data.id_type ?? data.idType ?? 0,
  });
};

/**
 * 删除出行人
 * DELETE /api/user/travelers/{id}
 */
export const deleteTraveler = (id: number): Promise<boolean> => {
  return http.delete<boolean>(`/user/travelers/${id}`);
};

// ─── 积分与等级 ──────────────────────────────────────

/**
 * 获取积分明细
 * GET /api/user/points/logs
 */
export const getPointsLogs = (params?: {
  page?: number;
  size?: number;
}): Promise<PageResponse<PointsLogDTO>> => {
  return http.get<PageResponse<PointsLogDTO>>("/user/points/logs", { params });
};

/**
 * 获取等级升级信息
 * GET /api/user/level/upgrade-info
 */
export const getLevelUpgradeInfo = (): Promise<UserLevelUpgradeInfoDTO> => {
  return http.get<UserLevelUpgradeInfoDTO>("/user/level/upgrade-info");
};

// ─── 订单 ────────────────────────────────────────────

/**
 * 获取我的订单
 * GET /api/user/orders
 */
export const getUserOrders = (params?: {
  page?: number;
  size?: number;
}): Promise<PageResponse<OrderDTO>> => {
  return http.get<PageResponse<OrderDTO>>("/user/orders", { params });
};

/**
 * 获取订单详情
 * GET /api/user/orders/{orderNo}
 */
export const getOrderDetail = (orderNo: string): Promise<OrderDTO> => {
  return http.get<OrderDTO>(`/user/orders/${orderNo}`);
};

export const refundUserOrder = (
  order: Pick<OrderDTO, "order_no" | "order_type">
): Promise<RefundResultDTO> => {
  const orderType = String(order.order_type || "").toUpperCase();
  if (orderType === "TRAIN") {
    return http.post<RefundResultDTO>(`/user/orders/${order.order_no}/refund`);
  }
  if (orderType === "VACATION") {
    return http.post<RefundResultDTO>(`/user/orders/${order.order_no}/refund`);
  }
  return http.post<RefundResultDTO>(`/user/orders/${order.order_no}/refund`);
};

export const payUserOrder = (
  orderNo: string,
  paymentMethod: string
): Promise<PayResultDTO> => {
  return http.post<PayResultDTO>(`/orders/${orderNo}/pay`, { paymentMethod });
};
