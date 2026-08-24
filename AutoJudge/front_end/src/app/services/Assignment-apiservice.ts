import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  GradeResponse,
  SubmissionResult,
  PlagiarismReport
} from '../models/types';


@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = 'http://localhost:8080/api';

  // ── Assignment endpoints ────────────────────────────────────────────────

  gradeAssignment(
    id: string,
    path: string,
    plagiarism = false
  ): Observable<GradeResponse> {
    return this.http.post<GradeResponse>(`${this.base}/assignments/${id}/grade`, null, {
      params: new HttpParams()
        .set('path', path)
        .set('plagiarism', plagiarism),
    });
  }

  getResults(id: string): Observable<SubmissionResult[]> {
    return this.http.get<SubmissionResult[]>(`${this.base}/assignments/${id}/results`);
  }

  downloadReport(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/assignments/${id}/report`, {
      responseType: 'blob',
    });
  }

  getPlagiarismReport(id: string, threshold = 0): Observable<PlagiarismReport> {
    return this.http.get<PlagiarismReport>(`${this.base}/assignments/${id}/plagiarism`, {
      params: new HttpParams().set('threshold', threshold),
    });
  }

  downloadPlagiarismReport(id: string, threshold = 0): Observable<Blob> {
    return this.http.get(`${this.base}/assignments/${id}/plagiarism/report`, {
      responseType: 'blob',
      params: new HttpParams().set('threshold', threshold),
    });
  }
}
