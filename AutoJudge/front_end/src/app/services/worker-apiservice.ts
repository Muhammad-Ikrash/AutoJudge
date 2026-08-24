import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { WorkerStatus, StartWorkersResponse, StopWorkersResponse, StartSystemResponse } from '../models/types';


@Injectable({ providedIn: 'root' })
export class WorkerAPIService {
  private readonly http = inject(HttpClient);
  private readonly base = 'http://localhost:8080/api';

  // ── Worker endpoints ────────────────────────────────────────────────────

  getWorkerStatus(): Observable<WorkerStatus> {
    return this.http.get<WorkerStatus>(`${this.base}/workers/status`);
  }

  startWorkers(count: number): Observable<StartWorkersResponse> {
    return this.http.post<StartWorkersResponse>(`${this.base}/workers`, null, {
      params: new HttpParams().set('count', count),
    });
  }

  stopWorkers(count: number): Observable<StopWorkersResponse> {
    return this.http.delete<StopWorkersResponse>(`${this.base}/workers`, {
      params: new HttpParams().set('count', count),
    });
  }

  startSystem(workers: number): Observable<StartSystemResponse> {
    return this.http.post<StartSystemResponse>(`${this.base}/system/start`, null, {
      params: new HttpParams().set('workers', workers),
    });
  }

  startResultWorker(): Observable<Record<string, string>> {
    return this.http.post<Record<string, string>>(`${this.base}/result-worker/start`, null);
  }
}
