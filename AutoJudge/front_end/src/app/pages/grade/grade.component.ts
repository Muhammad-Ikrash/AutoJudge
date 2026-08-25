import { Component, OnInit, OnDestroy, signal, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NavbarComponent } from '../../components/navbar/navbar.component';
import { ApiService } from '../../services/Assignment-apiservice';
import { GradeResponse } from '../../models/types';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { interval, Subscription } from 'rxjs';

@Component({
  selector: 'app-grade',
  standalone: true,
  imports: [NavbarComponent, CommonModule, FormsModule],
  templateUrl: './grade.component.html',
  styleUrl: './grade.component.scss'
})
export class GradeComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);

  assignmentId = signal('');
  assignmentTitle = signal('Assignment');
  assignmentPath = '';
  grading = signal(false);
  showStatus = signal(false);
  batchId = signal('');
  completedJobs = signal(0);
  totalJobs = signal(0);
  progressPct = signal(0);
  gradeError = signal<string | null>(null);

  private pollSub?: Subscription;

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.assignmentId.set(id);

    const titles: Record<string, string> = {
      'dsa-a3': 'Assignment 3: Linked Lists',
      'dsa-a4': 'Assignment 4: Graphs',
      'oop-a2': 'Assignment 2: Recursion',
      'pf-a1': 'Assignment 1: Pointers',
    };
    this.assignmentTitle.set(titles[id] ?? id);
    this.assignmentPath = `/data/assignments/dsa/${id}`;
  }

  ngOnDestroy() { this.pollSub?.unsubscribe(); }

  startGrading() {
    if (!this.assignmentPath) return;
    this.grading.set(true);
    this.showStatus.set(true);
    this.completedJobs.set(0);

    this.api.gradeAssignment(this.assignmentId(), this.assignmentPath).subscribe({
      next: (r: GradeResponse) => {
        this.totalJobs.set(r.jobsProduced || 0);
        this.batchId.set(r.assignmentId || this.assignmentId());
        this.gradeError.set(null);
        this.startPolling();
      },
      error: () => {
        this.grading.set(false);
        this.showStatus.set(false);
        this.gradeError.set('Failed to submit grading job. Check that the backend is running and the path is correct.');
      }
    });
  }

  private startPolling() {
    let completed = 0;
    const total = this.totalJobs();
    this.pollSub?.unsubscribe();
    this.pollSub = interval(800).subscribe(() => {
      completed = Math.min(completed + Math.ceil(Math.random() * 3), total);
      this.completedJobs.set(completed);
      this.progressPct.set(Math.round((completed / total) * 100));
      if (completed >= total) {
        this.grading.set(false);
        this.pollSub?.unsubscribe();
      }
    });
  }

  viewConfig() { alert('Config view coming soon'); }
}
