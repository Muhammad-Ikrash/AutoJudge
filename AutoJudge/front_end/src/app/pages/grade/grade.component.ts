import { Component, OnInit, OnDestroy, signal, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NavbarComponent } from '../../components/navbar/navbar.component';
import { ApiService } from '../../services/Assignment-apiservice';
import { GradeResponse } from '../../models/types';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-grade',
  standalone: true,
  imports: [NavbarComponent, CommonModule, FormsModule, RouterLink],
  templateUrl: './grade.component.html',
  styleUrl: './grade.component.scss'
})
export class GradeComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);

  assignmentId = signal('');
  assignmentTitle = signal('Assignment');
  enablePlagiarism = false;
  grading = signal(false);
  showStatus = signal(false);
  batchId = signal('');
  completedJobs = signal(0);
  totalJobs = signal(0);
  progressPct = signal(0);
  gradeError = signal<string | null>(null);
  done = signal(false);

  private pollSub?: Subscription;

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.assignmentId.set(id);
    this.assignmentTitle.set(id);
  }

  ngOnDestroy() { this.pollSub?.unsubscribe(); }

  startGrading() {
    this.grading.set(true);
    this.showStatus.set(true);
    this.done.set(false);
    this.completedJobs.set(0);
    this.progressPct.set(0);
    this.gradeError.set(null);

    this.api.gradeAssignment(this.assignmentId(), undefined, this.enablePlagiarism).subscribe({
      next: (r: GradeResponse) => {
        this.totalJobs.set(r.jobsProduced || 0);
        this.batchId.set(r.assignmentId || this.assignmentId());
        this.startPolling();
      },
      error: () => {
        this.grading.set(false);
        this.gradeError.set('Failed to submit grading job. Check that the backend is running.');
      }
    });
  }

  private startPolling() {
    this.pollSub?.unsubscribe();
    this.pollSub = interval(1500).subscribe(() => {
      this.api.getAssignmentStatus(this.assignmentId()).subscribe({
        next: (s) => {
          const completed = s.completed ?? 0;
          const total = s.total > 0 ? s.total : this.totalJobs();
          this.completedJobs.set(completed);
          if (total > 0) {
            this.totalJobs.set(total);
            this.progressPct.set(Math.min(100, Math.round((completed / total) * 100)));
          }
          if (total > 0 && completed >= total) {
            this.grading.set(false);
            this.done.set(true);
            this.pollSub?.unsubscribe();
          }
        },
        error: () => { /* keep polling on transient error */ }
      });
    });
  }
}
