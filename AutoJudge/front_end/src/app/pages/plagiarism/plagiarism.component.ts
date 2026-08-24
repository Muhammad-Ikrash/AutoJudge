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

  assignmentId = signal('dsa-a3');
  assignmentTitle = signal('Assignment 3');
  report = signal<PlagiarismReport | null>(null);
  filteredPairs = signal<PlagiarismPair[]>([]);
  searchId = 'dsa-a3';
  threshold = 70;

  ngOnInit() {
    const id = this.route.snapshot.queryParamMap.get('id') ?? 'dsa-a3';
    this.assignmentId.set(id);
    this.searchId = id;
    this.loadReport();
  }

  loadReport() {
    const mockReport: PlagiarismReport = {
      assignmentId: this.searchId,
      language: 'cpp',
      generatedAt: '14:20',
      pairs: [
        { student1Id: '21i-0512', student2Id: '21i-0447', similarity: 0.91 },
        { student1Id: '21i-0533', student2Id: '21i-0498', similarity: 0.85 },
        { student1Id: '21i-0561', student2Id: '21i-0502', similarity: 0.74 },
        { student1Id: '21i-0214', student2Id: '21i-0219', similarity: 0.68 },
      ]
    };

    this.api.getPlagiarismReport(this.searchId, this.threshold / 100).subscribe({
      next: (r) => {
        this.report.set(r);
        this.applyFilter(r.pairs);
      },
      error: () => {
        this.report.set(mockReport);
        this.applyFilter(mockReport.pairs);
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
