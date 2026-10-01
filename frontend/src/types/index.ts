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
