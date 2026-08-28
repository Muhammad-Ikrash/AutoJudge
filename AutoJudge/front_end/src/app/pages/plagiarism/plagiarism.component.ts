import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService, SimilarityPair } from '../../services/api.service';

@Component({
  selector: 'app-plagiarism',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './plagiarism.component.html',
  styleUrls: ['./plagiarism.component.scss']
})
export class PlagiarismComponent implements OnInit, OnDestroy {
  assignmentId = '';
  pairs: SimilarityPair[] = [];
  loading = true;
  error = '';
  downloading = false;

  thresholdPercent = 0;
  private debounceTimer: any;

  constructor(private api: ApiService, private route: ActivatedRoute) {}

  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      this.assignmentId = params.get('id') || '';
      this.loadPlagiarism();
    });
  }

  ngOnDestroy() {
    clearTimeout(this.debounceTimer);
  }

  onThresholdChange() {
    clearTimeout(this.debounceTimer);
    this.debounceTimer = setTimeout(() => this.loadPlagiarism(), 300);
  }

  async loadPlagiarism() {
    this.loading = true;
    this.error = '';
    try {
      const fraction = this.thresholdPercent / 100;
      const report = await this.api.getPlagiarism(this.assignmentId, fraction);
      this.pairs = (report?.similarities ?? [])
        .sort((a, b) => b.similarity - a.similarity);
    } catch (e: any) {
      this.error = e.message;
    } finally {
      this.loading = false;
    }
  }

  async downloadReport() {
    this.downloading = true;
    try {
      const fraction = this.thresholdPercent / 100;
      const blob = await this.api.downloadPlagiarismReport(this.assignmentId, fraction);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `plagiarism_${this.assignmentId}.xlsx`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (e) {
      alert("Couldn't generate report, try again");
    } finally {
      this.downloading = false;
    }
  }

  getSimilarityClass(similarity: number): string {
    const pct = similarity * 100;
    if (pct >= 80) return 'similarity-high';
    if (pct >= 50) return 'similarity-medium';
    return 'similarity-low';
  }
}
