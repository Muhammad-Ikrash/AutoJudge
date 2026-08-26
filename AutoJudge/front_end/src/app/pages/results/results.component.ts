import { Component, OnInit, signal, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NavbarComponent } from '../../components/navbar/navbar.component';
import { ApiService } from '../../services/Assignment-apiservice';
import { SubmissionResult } from '../../models/types';
import { CommonModule } from '@angular/common';

type Verdict = 'ACCEPTED' | 'TIME_LIMIT_EXCEEDED' | 'MALICIOUS_CODE' | 'MEMORY_LIMIT_EXCEEDED' | 'RUNTIME_ERROR' | 'GRADING' | string;

@Component({
  selector: 'app-results',
  standalone: true,
  imports: [NavbarComponent, CommonModule, RouterLink],
  templateUrl: './results.component.html',
  styleUrl: './results.component.scss'
})
export class ResultsComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);

  assignmentId = signal('dsa-a3');
  assignmentNum = signal('3');
  course = signal('DSA');
  section = signal('Section 3');
  results = signal<SubmissionResult[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  graded = signal(0);
  total = signal(0);
  accepted = signal(0);
  flagged = signal(0);
  avgScore = signal(0);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.assignmentId.set(id);
    this.loadResults(id);
  }

  private loadResults(id: string) {
    this.loading.set(true);
    this.error.set(null);
    this.api.getResults(id).subscribe({
      next: (data) => {
        this.results.set(data ?? []);
        this.computeStats(data ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load results from backend.');
        this.loading.set(false);
      }
    });
  }

  private computeStats(data: SubmissionResult[]) {
    const graded = data.filter(r => r.verdict !== 'GRADING').length;
    const accepted = data.filter(r => r.verdict === 'ACCEPTED').length;
    const flagged = data.filter(r => r.verdict === 'MALICIOUS_CODE').length;
    const scored = data.filter(r => r.score !== undefined && r.maxScore);
    const avg = scored.length
      ? Math.round(scored.reduce((s, r) => s + (r.score! / r.maxScore!) * 100, 0) / scored.length)
      : 0;
    this.total.set(data.length);
    this.graded.set(graded);
    this.accepted.set(accepted);
    this.flagged.set(flagged);
    this.avgScore.set(avg);
  }

  verdictClass(v: string): string {
    const key = v?.replace(/ /g, '_').toUpperCase();
    return `verdict-${key}`;
  }

  downloadReport() {
    this.api.downloadReport(this.assignmentId()).subscribe({
      next: (blob: any) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `report_${this.assignmentId()}.xlsx`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => alert('Download failed or backend not available')
    });
  }
}
