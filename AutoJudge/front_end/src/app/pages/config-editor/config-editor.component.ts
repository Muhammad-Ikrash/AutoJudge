import { Component, OnInit, signal, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NavbarComponent } from '../../components/navbar/navbar.component';
import { ApiService } from '../../services/Assignment-apiservice';
import { AssignmentConfig } from '../../models/types';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

const DEFAULT_CONFIG: AssignmentConfig = {
  assignmentId: '',
  resourceLimits: { timeLimitMs: 2000, memoryLimitMb: 256, cpuLimit: 1.0 },
  executionProfile: { autoRemove: true, workingDirectory: '/workspace' },
};

@Component({
  selector: 'app-config-editor',
  standalone: true,
  imports: [NavbarComponent, CommonModule, FormsModule],
  templateUrl: './config-editor.component.html',
  styleUrl: './config-editor.component.scss'
})
export class ConfigEditorComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);

  assignmentId = signal('');
  config = signal<AssignmentConfig>({ ...DEFAULT_CONFIG });
  loading = signal(false);
  saving = signal(false);
  saved = signal(false);
  error = signal<string | null>(null);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.assignmentId.set(id);
    this.load();
  }

  load() {
    this.loading.set(true);
    this.error.set(null);
    this.api.getConfig(this.assignmentId()).subscribe({
      next: (cfg) => { this.config.set(cfg); this.loading.set(false); },
      error: () => { this.error.set('Failed to load config. Backend may not be available.'); this.loading.set(false); }
    });
  }

  save() {
    this.saving.set(true);
    this.api.saveConfig(this.assignmentId(), this.config()).subscribe({
      next: () => { this.saving.set(false); this.saved.set(true); setTimeout(() => this.saved.set(false), 2500); },
      error: () => { this.saving.set(false); this.error.set('Failed to save config.'); }
    });
  }

  reset() {
    const id = this.assignmentId();
    this.config.set({ ...DEFAULT_CONFIG, assignmentId: id });
  }

  // Helpers for nested two-way binding
  get rl() { return this.config().resourceLimits; }
  get ep() { return this.config().executionProfile; }

  setTimeLimitMs(val: number) {
    this.config.update(c => ({ ...c, resourceLimits: { ...c.resourceLimits, timeLimitMs: val } }));
  }
  setMemoryLimitMb(val: number) {
    this.config.update(c => ({ ...c, resourceLimits: { ...c.resourceLimits, memoryLimitMb: val } }));
  }
  setCpuLimit(val: number) {
    this.config.update(c => ({ ...c, resourceLimits: { ...c.resourceLimits, cpuLimit: val } }));
  }
  setAutoRemove(val: boolean) {
    this.config.update(c => ({ ...c, executionProfile: { ...c.executionProfile, autoRemove: val } }));
  }
  setWorkingDir(val: string) {
    this.config.update(c => ({ ...c, executionProfile: { ...c.executionProfile, workingDirectory: val } }));
  }
}
