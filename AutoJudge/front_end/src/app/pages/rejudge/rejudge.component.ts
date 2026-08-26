import { Component, OnInit, signal, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NavbarComponent } from '../../components/navbar/navbar.component';
import { ApiService } from '../../services/Assignment-apiservice';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-rejudge',
  standalone: true,
  imports: [NavbarComponent, CommonModule, FormsModule, RouterLink],
  templateUrl: './rejudge.component.html',
  styleUrl: './rejudge.component.scss'
})
export class RejudgeComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);

  assignmentId = signal('');
  studentId = '';
  testCase = '';
  submitting = signal(false);
  studentSuccess = signal(false);
  testCaseSuccess = signal(false);
  studentError = signal<string | null>(null);
  testCaseError = signal<string | null>(null);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.assignmentId.set(id);
  }

  rejudgeStudent() {
    if (!this.studentId) return;
    this.submitting.set(true);
    this.studentSuccess.set(false);
    this.studentError.set(null);

    this.api.rejudgeByStudent(this.assignmentId(), this.studentId).subscribe({
      next: () => {
        this.submitting.set(false);
        this.studentSuccess.set(true);
        setTimeout(() => this.studentSuccess.set(false), 3000);
      },
      error: () => {
        this.submitting.set(false);
        this.studentError.set('Failed to trigger rejudge. Backend may not be available.');
      }
    });
  }

  rejudgeTestCase() {
    if (!this.testCase) return;
    this.submitting.set(true);
    this.testCaseSuccess.set(false);
    this.testCaseError.set(null);

    this.api.rejudgeByTestCase(this.assignmentId(), this.testCase).subscribe({
      next: () => {
        this.submitting.set(false);
        this.testCaseSuccess.set(true);
        setTimeout(() => this.testCaseSuccess.set(false), 3000);
      },
      error: () => {
        this.submitting.set(false);
        this.testCaseError.set('Failed to trigger rejudge. Backend may not be available.');
      }
    });
  }
}
