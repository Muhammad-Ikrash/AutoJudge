import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService, SubmissionResult } from '../../services/api.service';

const VERDICT_SEVERITY: Record<string, number> = {
  'MALICIOUS_CODE': 0,
  'INTERNAL_ERROR': 1,
  'COMPILATION_ERROR': 2,
  'MEMORY_LIMIT_EXCEEDED': 3,
  'TIME_LIMIT_EXCEEDED': 4,
  'PROCESS_LIMIT_EXCEEDED': 5,
  'RUNTIME_ERROR': 6,
  'WRONG_ANSWER': 7,
  'ACCEPTED': 8
};

interface ResultRow extends SubmissionResult {
  expanded?: boolean;
  rejudging?: boolean;
}

@Component({
  selector: 'app-results',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './results.component.html',
  styleUrls: ['./results.component.scss']
})
export class ResultsComponent implements OnInit {
  assignmentId = '';
  results: ResultRow[] = [];
  loading = true;
  error = '';
  downloading = false;

  private pollIntervals: Record<string, any> = {};

  constructor(private api: ApiService, private route: ActivatedRoute, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      this.assignmentId = params.get('id') || '';
      this.loadResults();
    });
  }

  async loadResults() {
    this.loading = true;
    this.error = '';
    this.cdr.detectChanges();
    try {
      const data = await this.api.getResults(this.assignmentId);
      this.results = data.map(r => ({ ...r, expanded: false, rejudging: false }));
      this.results.sort((a, b) => (VERDICT_SEVERITY[a.verdict] ?? 99) - (VERDICT_SEVERITY[b.verdict] ?? 99));
    } catch (e: any) {
      this.error = e.message;
    } finally {
      this.loading = false;
      this.cdr.detectChanges();
    }
  }

  toggleExpand(row: ResultRow) {
    row.expanded = !row.expanded;
    this.cdr.detectChanges();
  }

  async downloadReport() {
    this.downloading = true;
    this.cdr.detectChanges();
    try {
      const blob = await this.api.downloadResultsReport(this.assignmentId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `report_${this.assignmentId}.xlsx`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (e) {
      alert('Cannot generate report, try again');
    } finally {
      this.downloading = false;
      this.cdr.detectChanges();
    }
  }

  async rejudge(row: ResultRow) {
    const local = this.api.getLocalAssignments()[this.assignmentId];
    if (!local || !local.path) {
      alert('Missing assignment path in local cache. Please start a grading run from the dashboard first.');
      return;
    }

    row.rejudging = true;
    this.cdr.detectChanges();
    try {
      const res = await this.api.rejudgeStudent(this.assignmentId, row.studentId, local.path);
      this.startPolling(row, res.batchId);
    } catch (e: any) {
      alert('Failed to queue rejudge: ' + e.message);
      row.rejudging = false;
      this.cdr.detectChanges();
    }
  }

  startPolling(row: ResultRow, batchId: string) {
    if (this.pollIntervals[row.studentId]) return;

    this.pollIntervals[row.studentId] = setInterval(async () => {
      try {
        const status = await this.api.getBatchStatus(this.assignmentId, batchId);
        if (status.complete) {
          clearInterval(this.pollIntervals[row.studentId]);
          delete this.pollIntervals[row.studentId];
          row.rejudging = false;
          await this.loadResults();
        }
      } catch (e) {
        console.error('Polling error', e);
      }
    }, 2000);
  }

  getBadgeClass(verdict: string): string {
    if (verdict === 'ACCEPTED') return 'badge-success';
    if (verdict.includes('ERROR') || verdict === 'MALICIOUS_CODE') return 'badge-danger';
    return 'badge-warning';
  }
}
