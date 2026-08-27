import { Component, OnInit, signal, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NavbarComponent } from '../../components/navbar/navbar.component';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../services/Assignment-apiservice';
import { AssignmentSummary } from '../../models/types';

@Component({
  selector: 'app-assignments',
  standalone: true,
  imports: [NavbarComponent, CommonModule, RouterLink],
  templateUrl: './assignments.component.html',
  styleUrl: './assignments.component.scss'
})
export class AssignmentsComponent implements OnInit {
  private api = inject(ApiService);

  assignments = signal<AssignmentSummary[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  menuOpenId = signal<string | null>(null);

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.error.set(null);
    this.api.listAssignments().subscribe({
      next: (list) => { this.assignments.set(list); this.loading.set(false); },
      error: () => { this.error.set('Failed to load assignments from backend.'); this.loading.set(false); }
    });
  }

  toggleMenu(id: string) {
    this.menuOpenId.set(this.menuOpenId() === id ? null : id);
  }

  closeMenu() { this.menuOpenId.set(null); }

  submissionCount(a: AssignmentSummary): number {
    return a.submissionCount ?? 0;
  }

  language(a: AssignmentSummary): string {
    return (a.config as any)?.resourceLimits ? 'cpp' : '—';
  }

  statusLabel(s: string): string {
    if (s === 'grading') return 'GRADING...';
    if (s === 'idle') return 'IDLE';
    return s.toUpperCase();
  }
}
