import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { AssignmentsComponent } from './pages/assignments/assignments.component';
import { GradeComponent } from './pages/grade/grade.component';
import { ResultsComponent } from './pages/results/results.component';
import { RejudgeComponent } from './pages/rejudge/rejudge.component';
import { PlagiarismComponent } from './pages/plagiarism/plagiarism.component';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'assignments', component: AssignmentsComponent },
  { path: 'assignments/:id/grade', component: GradeComponent },
  { path: 'assignments/:id/results', component: ResultsComponent },
  { path: 'assignments/:id/rejudge', component: RejudgeComponent },
  { path: 'results', component: ResultsComponent },
  { path: 'plagiarism', component: PlagiarismComponent },
  { path: '**', redirectTo: 'dashboard' },
];
