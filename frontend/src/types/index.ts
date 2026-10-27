export type UserRole = 'CITIZEN' | 'OFFICER' | 'ADMIN';

export interface AuthResponse {
  token: string;
  email: string;
  fullName: string;
  role: UserRole;
}

export interface CaseResponse {
  id: number;
  referenceNo: string;
  title: string;
  description: string;
  status: string;
  category: string;
  citizenEmail: string;
  assignedOfficerEmail: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DashboardSummary {
  totalCases: number;
  casesByStatus: Record<string, number>;
  unreadNotifications: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface AnalyticsOverview {
  generatedAt: string;
  activeWindowSeconds: number;
  redisAvailable: boolean;
  registeredUsers: number;
  authenticatedUsers: number;
  openSessions: number;
  activeSessions: number;
  activeUsers: number;
  recentlyActedUsers: number;
  uniqueAuthenticatedUsersToday: number;
  anonymousVisitorsToday: number;
  today: Record<string, number>;
  last7Days: Record<string, number>;
  last30Days: Record<string, number>;
}

export interface TrendPoint {
  bucketStart: string;
  logins: number;
  logouts: number;
  caseSubmissions: number;
  documentUploads: number;
  aiGuidanceUses: number;
  pageViews: number;
  uniqueActiveUsers: number;
  visitorDays: number;
}

export interface AnalyticsTrends {
  granularity: 'daily' | 'weekly' | 'monthly';
  zone: string;
  points: TrendPoint[];
}
