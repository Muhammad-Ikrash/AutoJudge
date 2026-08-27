import { Component, OnInit, OnDestroy, signal, inject } from '@angular/core';
import { interval, Subscription } from 'rxjs';
import { NavbarComponent } from '../../components/navbar/navbar.component';
import { WorkerStatus } from '../../models/types';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WorkerAPIService } from '../../services/worker-apiservice';

interface ActiveWorker {
  name: string;
  task: string;
  active: boolean;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [NavbarComponent, CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})

export class DashboardComponent implements OnInit, OnDestroy {
  private api = inject(WorkerAPIService);

  workerStatus = signal<WorkerStatus>({ totalEvaluationWorkers: 0, resultWorkerRunning: false });
  queuedJobs = signal(0);
  lastBatch = signal('—');
  loading = signal(false);
  workerSlider = 4;

  // activeWorkers = signal<ActiveWorker[]>([
  //   { name: 'worker-01', task: 'grading DSA-A3, student 21i-0214', active: true },
  //   { name: 'worker-02', task: 'idle', active: false },
  //   { name: 'worker-03', task: 'grading DSA-A3, student 21i-0219', active: true },
  //   { name: 'worker-04', task: 'idle', active: false },
  // ]);

  private pollSub?: Subscription;

  ngOnInit() {
    this.fetchStatus();
    this.pollSub = interval(5000).subscribe(() => this.fetchStatus());
  }

  ngOnDestroy() {
    this.pollSub?.unsubscribe();
  }

  private fetchStatus() {
    this.api.getWorkerStatus().subscribe({
      next: (s) => {
        this.workerStatus.set(s);
        this.workerSlider = s.totalEvaluationWorkers;
        // this.updateWorkerList(s.totalEvaluationWorkers);
      },
      error: () => {/* keep mock data on error */ }
    });
  }

  // private updateWorkerList(count: number) {
  //   const workers: ActiveWorker[] = [];
  //   for (let i = 1; i <= count; i++) {
  //     const name = `worker-${String(i).padStart(2, '0')}`;
  //     const existing = this.activeWorkers().find(w => w.name === name);
  //     workers.push(existing ?? { name, task: 'idle', active: false });
  //   }
  //   this.activeWorkers.set(workers);
  // }

  incrementWorkers() { this.workerSlider = Math.min(20, this.workerSlider + 1); }
  decrementWorkers() { this.workerSlider = Math.max(0, this.workerSlider - 1); }

  addWorker() {
    this.loading.set(true);
    this.api.startWorkers(1).subscribe({
      next: (r) => { this.workerStatus.update(s => ({ ...s, totalEvaluationWorkers: r.totalEvaluationWorkers })); this.loading.set(false); this.fetchStatus(); },
      error: () => this.loading.set(false)
    });
  }

  removeWorker() {
    this.loading.set(true);
    this.api.stopWorkers(1).subscribe({
      next: (r) => { this.workerStatus.update(s => ({ ...s, totalEvaluationWorkers: r.totalEvaluationWorkers })); this.loading.set(false); this.fetchStatus(); },
      error: () => this.loading.set(false)
    });
  }

  startEverything() {
    this.loading.set(true);
    this.api.startSystem(this.workerSlider).subscribe({
      next: () => { this.loading.set(false); this.fetchStatus(); },
      error: () => this.loading.set(false)
    });
  }
}
