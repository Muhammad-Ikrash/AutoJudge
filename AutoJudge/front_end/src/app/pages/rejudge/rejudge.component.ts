import { Component, OnInit, signal, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NavbarComponent } from '../../components/navbar/navbar.component';
import { ApiService } from '../../services/Assignment-apiservice';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-rejudge',
  standalone: true,
  imports: [NavbarComponent, CommonModule, FormsModule],
  templateUrl: './rejudge.component.html',
  styleUrl: './rejudge.component.scss'
})
export class RejudgeComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);

  assignmentId = signal('dsa-a3');
  assignmentNum = signal('3');
  studentId = '21i-0533';
  testCase = 'testcase_07';
  submitting = signal(false);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id') ?? 'dsa-a3';
    this.assignmentId.set(id);
    const nums: Record<string, string> = {
      'dsa-a3': '3', 'dsa-a4': '4', 'oop-a2': '2', 'pf-a1': '1'
    };
    this.assignmentNum.set(nums[id] ?? id);
  }

  rejudgeStudent() {
    this.submitting.set(true);
    // TODO: call backend rejudge API when implemented
    setTimeout(() => {
      alert(`Rejudging student ${this.studentId} for assignment ${this.assignmentId()}`);
      this.submitting.set(false);
    }, 500);
  }

  rejudgeTestCase() {
    this.submitting.set(true);
    setTimeout(() => {
      alert(`Rejudging test case ${this.testCase} across all students for assignment ${this.assignmentId()}`);
      this.submitting.set(false);
    }, 500);
  }
}
