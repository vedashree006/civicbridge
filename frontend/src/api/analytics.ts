import { apiClient } from './client';
import { AnalyticsOverview, AnalyticsTrends } from '../types';

export async function getOverview(): Promise<AnalyticsOverview> {
  const { data } = await apiClient.get<AnalyticsOverview>('/admin/analytics/overview');
  return data;
}

export async function getTrends(granularity: 'daily' | 'weekly' | 'monthly'): Promise<AnalyticsTrends> {
  const { data } = await apiClient.get<AnalyticsTrends>('/admin/analytics/trends', { params: { granularity } });
  return data;
}
