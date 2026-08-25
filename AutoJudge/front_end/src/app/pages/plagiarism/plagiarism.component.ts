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
  assignmentTitle = signal('Assignment');
  report = signal<PlagiarismReport | null>(null);
  filteredPairs = signal<PlagiarismPair[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  searchId = '';
  threshold = 70;

  ngOnInit() {
    const id = this.route.snapshot.queryParamMap.get('id') ?? 'dsa-a3';
    this.assignmentId.set(id);
    this.searchId = id;
    this.loadReport();
  }

  loadReport() {
    this.loading.set(true);
    this.error.set(null);
    this.api.getPlagiarismReport(this.searchId, this.threshold / 100).subscribe({
      next: (r) => {
        this.report.set(r);
        this.applyFilter(r.pairs);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load plagiarism report. Backend may not be available.');
        this.report.set(null);
        this.filteredPairs.set([]);
        this.loading.set(false);
      }
    });
  }

  private applyFilter(pairs: PlagiarismPair[]) {
    const t = this.threshold / 100;
    this.filteredPairs.set(pairs.filter(p => p.similarity >= t));
  }

  simClass(sim: number): string { return sim >= 0.85 ? 'sim-bar-high' : 'sim-bar-low'; }
  riskLabel(sim: number): string { return sim >= 0.85 ? 'HIGH' : 'MEDIUM'; }
  riskClass(sim: number): string { return sim >= 0.85 ? 'risk-high' : 'risk-medium'; }

  downloadReport() {
    this.api.downloadPlagiarismReport(this.searchId, this.threshold / 100).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = `plagiarism_${this.searchId}.xlsx`;
        a.click(); URL.revokeObjectURL(url);
      },
      error: () => alert('Download failed or backend not available')
    });
  }
}
