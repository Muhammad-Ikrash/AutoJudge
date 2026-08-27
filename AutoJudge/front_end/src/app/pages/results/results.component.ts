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

  graded = signal(42);
  total = signal(45);
  accepted = signal(31);
  flagged = signal(2);
  avgScore = signal(78);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id') ?? 'dsa-a3';
    this.assignmentId.set(id);

    // Mock results matching the screenshot
    this.results.set([
      { studentId: '21i-0512', assignmentId: id, score: 10, maxScore: 10, verdict: 'ACCEPTED', gradedAt: '14:02' },
      { studentId: '21i-0498', assignmentId: id, score: 6, maxScore: 10, verdict: 'TIME_LIMIT_EXCEEDED', gradedAt: '14:02' },
      { studentId: '21i-0533', assignmentId: id, score: 0, maxScore: 10, verdict: 'MALICIOUS_CODE', gradedAt: '14:03' },
      { studentId: '21i-0447', assignmentId: id, score: 0, maxScore: 10, verdict: 'MEMORY_LIMIT_EXCEEDED', gradedAt: '14:03' },
      { studentId: '21i-0561', assignmentId: id, score: 4, maxScore: 10, verdict: 'RUNTIME_ERROR', gradedAt: '14:04' },
      { studentId: '21i-0502', assignmentId: id, score: undefined, maxScore: 10, verdict: 'GRADING', gradedAt: undefined },
    ]);

    // Optionally load from API
    this.api.getResults(id).subscribe({
      next: (data) => { if (data?.length) this.results.set(data); },
      error: () => { /* keep mock data */ }
    });
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
