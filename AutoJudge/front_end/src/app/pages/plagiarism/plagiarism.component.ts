import { Component, OnInit, signal, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NavbarComponent } from '../../components/navbar/navbar.component';
import { ApiService } from '../../services/Assignment-apiservice';
import { PlagiarismReport, PlagiarismPair } from '../../models/types';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-plagiarism',
  standalone: true,
  imports: [NavbarComponent, CommonModule, FormsModule],
  templateUrl: './plagiarism.component.html',
  styleUrl: './plagiarism.component.scss'
})
export class PlagiarismComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);

  assignmentId = signal('');
  report = signal<PlagiarismReport | null>(null);
  filteredPairs = signal<PlagiarismPair[]>([]);
  loading = signal(false);
  running = signal(false);
  error = signal<string | null>(null);
  runMessage = signal<string | null>(null);
  threshold = 70;

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')
      ?? this.route.snapshot.queryParamMap.get('id')
      ?? '';
    this.assignmentId.set(id);
    if (id) this.loadResults();
  }

  loadResults() {
    this.loading.set(true);
    this.error.set(null);
    this.api.getPlagiarismResults(this.assignmentId(), this.threshold / 100).subscribe({
      next: (r) => {
        this.report.set(r);
        this.applyFilter(r.pairs ?? []);
        this.loading.set(false);
      },
      error: () => {
        // No report yet is not an error — just empty state
        this.report.set(null);
        this.filteredPairs.set([]);
        this.loading.set(false);
      }
    });
  }

  runCheck() {
    this.running.set(true);
    this.runMessage.set(null);
    this.error.set(null);
    this.api.runPlagiarismCheck(this.assignmentId()).subscribe({
      next: () => {
        this.running.set(false);
        this.runMessage.set('Plagiarism analysis complete. Refreshing results…');
        setTimeout(() => { this.runMessage.set(null); this.loadResults(); }, 1500);
      },
      error: () => {
        this.running.set(false);
        this.error.set('Failed to run plagiarism check. Backend may not be available.');
      }
    });
  }

  applyFilter(pairs: PlagiarismPair[]) {
    const t = this.threshold / 100;
    this.filteredPairs.set((pairs ?? []).filter(p => p.similarity >= t)
      .sort((a, b) => b.similarity - a.similarity));
  }

  onThresholdChange() {
    const pairs = this.report()?.pairs ?? [];
    this.applyFilter(pairs);
  }

  totalPairs(): number { return this.report()?.pairs?.length ?? 0; }
  flaggedCount(): number { return this.filteredPairs().length; }
  engine(): string { return this.report() ? `JPlag · ${this.report()!.language ?? 'cpp'}` : '—'; }

  simClass(sim: number): string { return sim >= 0.85 ? 'sim-bar-high' : sim >= 0.7 ? 'sim-bar-med' : 'sim-bar-low'; }
  riskLabel(sim: number): string { return sim >= 0.85 ? 'HIGH' : 'MEDIUM'; }
  riskClass(sim: number): string { return sim >= 0.85 ? 'risk-high' : 'risk-medium'; }

  downloadReport() {
    this.api.downloadPlagiarismReport(this.assignmentId(), this.threshold / 100).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = `plagiarism_${this.assignmentId()}.xlsx`;
        a.click(); URL.revokeObjectURL(url);
      },
      error: () => alert('Download failed or backend not available')
    });
  }
}
