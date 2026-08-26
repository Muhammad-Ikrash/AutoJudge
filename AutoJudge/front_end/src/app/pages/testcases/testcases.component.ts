import { Component, OnInit, signal, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NavbarComponent } from '../../components/navbar/navbar.component';
import { ApiService } from '../../services/Assignment-apiservice';
import { TestCase } from '../../models/types';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-testcases',
  standalone: true,
  imports: [NavbarComponent, CommonModule, FormsModule],
  templateUrl: './testcases.component.html',
  styleUrl: './testcases.component.scss'
})
export class TestcasesComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);

  assignmentId = signal('');
  testCases = signal<TestCase[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  saving = signal(false);

  // Add test case modal
  showAddModal = signal(false);
  newName = '';
  newWeight = 1;
  inputFile: File | null = null;
  expectedFile: File | null = null;
  addError = signal<string | null>(null);
  addLoading = signal(false);

  // Edit weight inline
  editingId = signal<string | null>(null);
  editWeight = 1;

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.assignmentId.set(id);
    this.load();
  }

  load() {
    this.loading.set(true);
    this.error.set(null);
    this.api.getTestCases(this.assignmentId()).subscribe({
      next: (tcs) => { this.testCases.set(tcs); this.loading.set(false); },
      error: () => { this.error.set('Failed to load test cases. Backend may not be available.'); this.loading.set(false); }
    });
  }

  openAdd() { this.showAddModal.set(true); this.addError.set(null); this.newName = ''; this.newWeight = 1; this.inputFile = null; this.expectedFile = null; }
  closeAdd() { this.showAddModal.set(false); }

  onInputFile(e: Event) { this.inputFile = (e.target as HTMLInputElement).files?.[0] ?? null; }
  onExpectedFile(e: Event) { this.expectedFile = (e.target as HTMLInputElement).files?.[0] ?? null; }

  submitAdd() {
    if (!this.newName || !this.inputFile || !this.expectedFile) {
      this.addError.set('Name, input file and expected output file are required.');
      return;
    }
    const form = new FormData();
    form.append('name', this.newName);
    form.append('weight', String(this.newWeight));
    form.append('input', this.inputFile);
    form.append('expected', this.expectedFile);
    this.addLoading.set(true);
    this.api.addTestCase(this.assignmentId(), form).subscribe({
      next: () => { this.closeAdd(); this.addLoading.set(false); this.load(); },
      error: () => { this.addError.set('Failed to add test case.'); this.addLoading.set(false); }
    });
  }

  startEdit(tc: TestCase) { this.editingId.set(tc.id); this.editWeight = tc.weight; }
  cancelEdit() { this.editingId.set(null); }

  saveWeight(tc: TestCase) {
    this.saving.set(true);
    this.api.updateTestCase(this.assignmentId(), tc.id, this.editWeight).subscribe({
      next: () => { this.editingId.set(null); this.saving.set(false); this.load(); },
      error: () => { this.saving.set(false); }
    });
  }

  deleteTestCase(tc: TestCase) {
    if (!confirm(`Delete test case "${tc.id}"? This cannot be undone.`)) return;
    this.api.deleteTestCase(this.assignmentId(), tc.id).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to delete test case.')
    });
  }
}
