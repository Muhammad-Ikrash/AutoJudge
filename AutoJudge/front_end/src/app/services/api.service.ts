import { Injectable } from '@angular/core';

export interface AssignmentSummary {
  assignmentId: string;
  submissionCount: number;
  lastGradedAt: string;
  path?: string;
  plagiarismEnabled?: boolean;
}

export interface SubmissionResult {
  submissionId: string;
  assignmentId: string;
  studentId: string;
  score: number;
  verdict: string;
  passedTests: number;
  totalTests: number;
  batchId: string;
  testCasesResults?: any[];
}

export interface SimilarityPair {
  submissionA: string;
  submissionB: string;
  similarity: number;
}

export interface PlagiarismReport {
  assignmentId: string;
  threshold: number;
  similarities: SimilarityPair[];
}

export interface BatchProgress {
  batchId: string;
  received: number;
  expected: number;
  complete: boolean;
  verdictCounts: Record<string, number>;
}

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly baseUrl = 'http://localhost:8080'; // Vite/Angular proxies can also be configured
  private readonly CACHE_KEY = 'autojudge:known-assignments';

  private async fetchApi(path: string, options?: RequestInit): Promise<any> {
    const response = await fetch(`${this.baseUrl}${path}`, options);
    if (!response.ok) {
      let errorMsg = `HTTP Error ${response.status}`;
      try {
        const errJson = await response.json();
        errorMsg = errJson.error || errJson.message || errorMsg;
      } catch (e) {
        // Not JSON
      }
      throw new Error(errorMsg);
    }
    // Return blob for report endpoints
    if (path.includes('/report')) {
      return response.blob();
    }
    return response.json();
  }

  // --- API Endpoints ---

  async getAssignments(): Promise<AssignmentSummary[]> {
    return this.fetchApi('/api/assignments');
  }

  async getResults(assignmentId: string): Promise<SubmissionResult[]> {
    return this.fetchApi(`/api/assignments/${assignmentId}/results`);
  }

  async downloadResultsReport(assignmentId: string): Promise<Blob> {
    return this.fetchApi(`/api/assignments/${assignmentId}/report`);
  }

  async getPlagiarism(assignmentId: string, threshold: number): Promise<PlagiarismReport> {
    return this.fetchApi(`/api/assignments/${assignmentId}/plagiarism?threshold=${threshold}`);
  }

  async downloadPlagiarismReport(assignmentId: string, threshold: number): Promise<Blob> {
    return this.fetchApi(`/api/assignments/${assignmentId}/plagiarism/report?threshold=${threshold}`);
  }

  async getBatchStatus(assignmentId: string, batchId: string): Promise<BatchProgress> {
    return this.fetchApi(`/api/assignments/${assignmentId}/batches/${batchId}/status`);
  }

  async gradeAssignment(assignmentId: string, path: string, plagiarism: boolean): Promise<any> {
    const res = await this.fetchApi(`/api/assignments/${assignmentId}/grade?path=${encodeURIComponent(path)}&plagiarism=${plagiarism}`, {
      method: 'POST'
    });
    this.saveToLocalCache(assignmentId, path, plagiarism);
    return res;
  }

  async rejudgeStudent(assignmentId: string, studentId: string, path: string): Promise<any> {
    return this.fetchApi(`/api/assignments/${assignmentId}/students/${studentId}/rejudge?path=${encodeURIComponent(path)}`, {
      method: 'POST'
    });
  }

  async startSystem(workers: number): Promise<any> {
    return this.fetchApi(`/api/system/start?workers=${workers}`, {
      method: 'POST'
    });
  }

  async getWorkerStatus(): Promise<any> {
    return this.fetchApi('/api/workers/status');
  }

  // --- Local Cache ---

  getLocalAssignments(): Record<string, { path: string, plagiarismEnabled: boolean }> {
    const cached = localStorage.getItem(this.CACHE_KEY);
    return cached ? JSON.parse(cached) : {};
  }

  saveToLocalCache(assignmentId: string, path: string, plagiarismEnabled: boolean): void {
    const cache = this.getLocalAssignments();
    cache[assignmentId] = { path, plagiarismEnabled };
    localStorage.setItem(this.CACHE_KEY, JSON.stringify(cache));
  }
}
