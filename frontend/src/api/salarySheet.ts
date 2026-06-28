import request from '@/utils/request';

export interface ConstructionPlan {
  id: number;
  name: string;
  unit: string;
  price: number;
}

export interface SubprojectData {
  subproject_id: number;
  space_type_name: string;
  plan_id: number;
  plan_name: string;
  unit: string;
  price: string;
  quantity: number;
  user_quantity: number;
  user_amount: number;
}

/** 施工方案汇总（统一snake_case，与后端一致） */
export interface PlanQuantity {
  total_quantity: number;
  total_amount: number;
  unit?: string;
  price?: string;
  plan_name?: string;
}

export interface ProjectData {
  id: number;
  project_name: string;
  created_at: string;
  salary_distribution: string;
  subprojects: SubprojectData[];
  /** 施工方案汇总映射 */
  plan_quantities: Record<number, PlanQuantity>;
}

export interface AdvanceData {
  id: number;
  user_id: number;
  username: string;
  nickname: string;
  advance_amount: number;
  advance_date: string;
  remark: string;
}

/** 结算响应（统一snake_case，与后端一致） */
export interface SettleResponse {
  settlement_id: number;
  settlement_no: string;
  total_amount: number;
  advance_amount: number;
  actual_amount: number;
}

/** 结算历史（统一snake_case，与后端一致） */
export interface SettlementHistory {
  settlement_id: number;
  settlement_no: string;
  start_month: string;
  end_month: string;
  total_amount: number;
  advance_amount: number;
  actual_amount: number;
  confirmed: boolean;
  confirmed_at: string | null;
  settled_by: number;
  settled_by_username: string;
  settled_by_nickname: string;
  created_at: string;
  projects: ProjectData[];
  advances: AdvanceData[];
  plan_totals?: Record<string, { total_quantity: number; total_amount: number }>;
  grand_total?: number;
  total_advance?: number;
  advances_by_month?: Array<{ month: string; amount: number }>;
  final_total?: number;
}

export const getConstructionPlans = (): Promise<ConstructionPlan[]> => {
  return request.get('/v1/salary-sheet/construction-plans');
};

/** 获取工程数据（含统计汇总） */
export const getProjects = (): Promise<{
  projects: ProjectData[];
  plan_totals: Record<string, { total_quantity: number; total_amount: number }>;
  grand_total: number;
  total_advance: number;
  final_total: number;
  advances: AdvanceData[];
}> => {
  return request.get('/v1/salary-sheet/projects');
};

export const getAdvances = (): Promise<AdvanceData[]> => {
  return request.get('/v1/salary-sheet/advances');
};

export const settle = (data: { projectIds: number[] }): Promise<SettleResponse> => {
  return request.post('/v1/salary-sheet/settle', data);
};

export const getSettledProjects = (): Promise<ProjectData[]> => {
  return request.get('/v1/salary-sheet/settled-projects');
};

export const getSettledAdvances = (): Promise<AdvanceData[]> => {
  return request.get('/v1/salary-sheet/settled-advances');
};

export const getSettlementHistory = (): Promise<SettlementHistory[]> => {
  return request.get('/v1/salary-sheet/settlement-history');
};

/** 计算结算金额结果（统一snake_case，与后端一致） */
export interface CalculateResult {
  plan_totals: Record<string, { total_quantity: number; total_amount: number }>;
  grand_total: number;
  total_advance: number;
  final_total: number;
  advances: AdvanceData[];
}

export const calculate = (data: { projectIds: number[] }): Promise<CalculateResult> => {
  return request.post('/v1/settlements/calculate', data);
};

const salarySheetApi = {
  getConstructionPlans,
  getProjects,
  getAdvances,
  settle,
  getSettledProjects,
  getSettledAdvances,
  getSettlementHistory,
  calculate
};

export default salarySheetApi;
