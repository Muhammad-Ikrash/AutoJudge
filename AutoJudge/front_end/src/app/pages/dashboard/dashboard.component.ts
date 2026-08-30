import { Component, OnInit, ChangeDetectorRef, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ApiService, AssignmentSummary } from '../../services/api.service';

interface AssignmentRow extends AssignmentSummary {
  status: string;
  batchId?: string;
  polling?: boolean;
  progress?: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  assignments: AssignmentRow[] = [];
  // resultWorkerActive: boolean = false;
  RunningWorkersCount = signal<number>(0);
  showModal = false;
  showWorkerModal = false;
  workers = 3;
  modalSubmitting = false;
  modalError = '';
  WorkerModalError = '';

  gradingForm = {
    assignmentId: '',
    path: '',
    // workers: 4,
    plagiarism: false,
    isNew: false
  };

  private pollIntervals: Record<string, any> = {};

  constructor(private api: ApiService, private cdr: ChangeDetectorRef) { }

  async ngOnInit() {
    await this.loadAssignments();
    const res = await this.api.getWorkerStatus();
    this.RunningWorkersCount.set(res.totalEvaluationWorkers)
  }

  async loadAssignments() {
    try {
      this.cdr.detectChanges();
      const serverAssignments = await this.api.getAssignments();
      const localCache = this.api.getLocalAssignments();
      const mergedMap = new Map<string, AssignmentRow>();

      for (const a of serverAssignments) {
        const local = localCache[a.assignmentId];
        mergedMap.set(a.assignmentId, {
          ...a,
          path: local?.path,
          plagiarismEnabled: local?.plagiarismEnabled,
          status: 'Done'
        });
      }

      for (const [id, local] of Object.entries(localCache)) {
        if (!mergedMap.has(id)) {
          mergedMap.set(id, {
            assignmentId: id,
            submissionCount: 0,
            lastGradedAt: '—',
            path: local.path,
            plagiarismEnabled: local.plagiarismEnabled,
            status: 'Not started'
          });
        }
      }

      this.assignments = Array.from(mergedMap.values());
    } catch (e) {
      console.error('Failed to load assignments', e);
    } finally {
      this.cdr.detectChanges();
    }
  }

  openNewAssignmentModal() {
    // this.gradingForm = { assignmentId: '', path: '', workers: 4, plagiarism: false, isNew: true };
    this.gradingForm = { assignmentId: '', path: '', plagiarism: false, isNew: true };
    this.modalError = '';
    this.showModal = true;
  }

  openWorkerModal() {
    this.showWorkerModal = true;
    this.workers = 3;
    this.WorkerModalError = '';
  }

  closeWorkerModal() {
    this.showWorkerModal = false;
  }

  async submitWorkerCount() {
    this.showWorkerModal = false;
    try {
      const res = await this.api.startSystem(this.workers);
      console.log(res);
      this.RunningWorkersCount.set(res.totalEvaluationWorkers); 
    }
    catch (e: any) {
      console.error('submitWorkerCount error:', e);
      this.WorkerModalError = e.message || 'Unknown error';
    }

  }

  openGradeModal(a: AssignmentRow) {
    this.gradingForm = {
      assignmentId: a.assignmentId,
      path: a.path || '',
      // workers: 3, 
      plagiarism: a.plagiarismEnabled || false,
      isNew: false
    };
    this.modalError = '';
    this.showModal = true;
  }

  closeModal() {
    this.showModal = false;
  }

  async submitGrading() {
    if (!this.gradingForm.assignmentId || !this.gradingForm.path) return;

    this.modalSubmitting = true;
    this.modalError = '';
    this.cdr.detectChanges();

    try {
      // await this.api.startSystem(this.gradingForm.workers);
      const res = await this.api.gradeAssignment(
        this.gradingForm.assignmentId,
        this.gradingForm.path,
        this.gradingForm.plagiarism
      );

      this.showModal = false;
      this.modalSubmitting = false;

      let row = this.assignments.find(a => a.assignmentId === this.gradingForm.assignmentId);
      if (!row) {
        row = {
          assignmentId: this.gradingForm.assignmentId,
          submissionCount: 0,
          lastGradedAt: '—',
          status: 'Grading…',
          batchId: res.batchId,
          path: this.gradingForm.path,
          plagiarismEnabled: this.gradingForm.plagiarism
        };
        this.assignments.push(row);
      } else {
        row.status = 'Grading…';
        row.batchId = res.batchId;
        row.path = this.gradingForm.path;
        row.plagiarismEnabled = this.gradingForm.plagiarism;
      }
      this.cdr.detectChanges();
      this.startPolling(row);
    } catch (e: any) {
      console.error('submitGrading error:', e);
      this.modalError = e.message || 'Unknown error';
      this.modalSubmitting = false;
      this.cdr.detectChanges();
    }
  }

  onFolderPick(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      // Browsers hide the absolute path for security. Extract only the top-level folder name
      // and pre-fill the text input so the user can type the full absolute path around it.
      const relativePath = input.files[0].webkitRelativePath;
      const folderName = relativePath.split('/')[0];
      // Pre-fill with folder name only — user must type the full prefix (e.g. /home/user/projects/)
      if (!this.gradingForm.path || this.gradingForm.path === folderName) {
        this.gradingForm.path = folderName;
      }
      this.cdr.detectChanges();
    }
  }

  saveNewAssignment() {
    if (!this.gradingForm.assignmentId || !this.gradingForm.path) return;
    this.api.saveToLocalCache(this.gradingForm.assignmentId, this.gradingForm.path, this.gradingForm.plagiarism);
    this.showModal = false;
    this.loadAssignments();
  }

  startPolling(row: AssignmentRow) {
    if (!row.batchId || this.pollIntervals[row.assignmentId]) return;

    row.polling = true;
    this.cdr.detectChanges();
    this.pollIntervals[row.assignmentId] = setInterval(async () => {
      try {
        const status = await this.api.getBatchStatus(row.assignmentId, row.batchId!);
        row.progress = `${status.received} / ${status.expected}`;
        this.cdr.detectChanges();

        if (status.complete) {
          clearInterval(this.pollIntervals[row.assignmentId]);
          delete this.pollIntervals[row.assignmentId];
          row.polling = false;
          row.status = 'Done';
          this.cdr.detectChanges();
          await this.loadAssignments();
        }
      } catch (e) {
        console.error('Polling error', e);
      }
    }, 2000);
  }


  deleteAssignmentData(assignmentId: string) {
    this.api.deleteAssignmentData(assignmentId);
  }


  deleteWrapper(assignmentId: string) {
    if (this.assignments.find(a => a.assignmentId === assignmentId)?.submissionCount === 0) {
      this.deleteFromCache(assignmentId);
    }
    else {
      this.deleteAssignmentData(assignmentId);
    }
  }

  deleteFromCache(assignmentId: string) {

    this.api.deleteAssignmentData(assignmentId);
    const cache = this.api.getLocalAssignments();
    delete cache[assignmentId];
    localStorage.setItem('autojudge:known-assignments', JSON.stringify(cache));
    this.assignments = this.assignments.filter(a => a.assignmentId !== assignmentId);
    this.cdr.detectChanges();

  }
}
