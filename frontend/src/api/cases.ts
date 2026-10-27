import { apiClient } from './client';
import { CaseResponse, PageResponse } from '../types';

export async function listCases(): Promise<PageResponse<CaseResponse>> {
  const { data } = await apiClient.get<PageResponse<CaseResponse>>('/cases');
  return data;
}

export async function getCase(id: number): Promise<CaseResponse> {
  const { data } = await apiClient.get<CaseResponse>(`/cases/${id}`);
  return data;
}

export async function createCase(title: string, description: string, category: string): Promise<CaseResponse> {
  const { data } = await apiClient.post<CaseResponse>('/cases', { title, description, category });
  return data;
}

export async function uploadDocument(caseId: number, file: File) {
  const form = new FormData();
  form.append('file', file);
  const { data } = await apiClient.post(`/cases/${caseId}/documents`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}
