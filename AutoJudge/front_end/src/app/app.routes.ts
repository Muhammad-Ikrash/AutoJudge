import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { ResultsComponent } from './pages/results/results.component';
import { PlagiarismComponent } from './pages/plagiarism/plagiarism.component';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'assignments/:id/results', component: ResultsComponent },
  { path: 'assignments/:id/plagiarism', component: PlagiarismComponent },
  { path: '**', redirectTo: '' }
];
