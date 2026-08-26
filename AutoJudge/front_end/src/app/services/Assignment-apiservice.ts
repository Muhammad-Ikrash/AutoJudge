import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  GradeResponse,
  SubmissionResult,
  PlagiarismReport,
  AssignmentSummary,
  AssignmentConfig,
  TestCase,
  BatchStatus,
} from '../models/types';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = 'http://localhost:8080/api';

  // ── Assignment list ────────────────────────────────────────────────────
  listAssignments(): Observable<AssignmentSummary[]> {
    return this.http.get<AssignmentSummary[]>(`${this.base}/assignments`);
  }

  getAssignment(id: string): Observable<AssignmentSummary> {
    return this.http.get<AssignmentSummary>(`${this.base}/assignments/${id}`);
  }

  // ── Status / batch progress ────────────────────────────────────────────
  getAssignmentStatus(id: string): Observable<BatchStatus> {
    return this.http.get<BatchStatus>(`${this.base}/assignments/${id}/status`);
  }

  getBatchStatus(batchId: string): Observable<BatchStatus> {
    return this.http.get<BatchStatus>(`${this.base}/batches/${batchId}/status`);
  }

  // ── Grade ──────────────────────────────────────────────────────────────
  gradeAssignment(id: string, path?: string, plagiarism = false): Observable<GradeResponse> {
    let params = new HttpParams().set('plagiarism', plagiarism);
    if (path) params = params.set('path', path);
    return this.http.post<GradeResponse>(`${this.base}/assignments/${id}/grade`, null, { params });
  }

  // ── Results ────────────────────────────────────────────────────────────
  getResults(id: string): Observable<SubmissionResult[]> {
    return this.http.get<SubmissionResult[]>(`${this.base}/assignments/${id}/results`);
  }

  downloadReport(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/assignments/${id}/report`, { responseType: 'blob' });
  }

  // ── Rejudge ────────────────────────────────────────────────────────────
  rejudgeByStudent(id: string, studentId: string): Observable<unknown> {
    return this.http.post(`${this.base}/assignments/${id}/rejudge`, null, {
      params: new HttpParams().set('studentId', studentId),
    });
  }

  rejudgeByTestCase(id: string, testCaseId: string): Observable<unknown> {
    return this.http.post(`${this.base}/assignments/${id}/rejudge`, null, {
      params: new HttpParams().set('testCaseId', testCaseId),
    });
  }

  // ── Test Cases ─────────────────────────────────────────────────────────
  getTestCases(id: string): Observable<TestCase[]> {
    return this.http.get<TestCase[]>(`${this.base}/assignments/${id}/testcases`);
  }

  addTestCase(id: string, form: FormData): Observable<unknown> {
    return this.http.post(`${this.base}/assignments/${id}/testcases`, form);
  }

  updateTestCase(id: string, testCaseId: string, weight: number): Observable<unknown> {
    return this.http.put(`${this.base}/assignments/${id}/testcases/${testCaseId}`, { weight });
  }

  deleteTestCase(id: string, testCaseId: string): Observable<unknown> {
    return this.http.delete(`${this.base}/assignments/${id}/testcases/${testCaseId}`);
  }

  // ── Config ─────────────────────────────────────────────────────────────
  getConfig(id: string): Observable<AssignmentConfig> {
    return this.http.get<AssignmentConfig>(`${this.base}/assignments/${id}/config`);
  }

  saveConfig(id: string, config: AssignmentConfig): Observable<unknown> {
    return this.http.put(`${this.base}/assignments/${id}/config`, config);
  }

  // ── Plagiarism ─────────────────────────────────────────────────────────
  runPlagiarismCheck(id: string): Observable<unknown> {
    return this.http.post(`${this.base}/assignments/${id}/plagiarism`, null);
  }

  getPlagiarismResults(id: string, threshold = 0): Observable<PlagiarismReport> {
    return this.http.get<PlagiarismReport>(`${this.base}/assignments/${id}/plagiarism-results`, {
      params: new HttpParams().set('threshold', threshold),
    });
  }

  /** @deprecated use getPlagiarismResults */
  getPlagiarismReport(id: string, threshold = 0): Observable<PlagiarismReport> {
    return this.getPlagiarismResults(id, threshold);
  }

  downloadPlagiarismReport(id: string, threshold = 0): Observable<Blob> {
    return this.http.get(`${this.base}/assignments/${id}/plagiarism-report`, {
      responseType: 'blob',
      params: new HttpParams().set('threshold', threshold),
    });
  }
}
