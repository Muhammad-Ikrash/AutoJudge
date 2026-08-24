export interface WorkerStatus {
  totalEvaluationWorkers: number;
  resultWorkerRunning: boolean;
}

export interface StartWorkersResponse {
  started: number;
  totalEvaluationWorkers: number;
}

export interface StopWorkersResponse {
  stopped: number;
  totalEvaluationWorkers: number;
}

export interface StartSystemResponse {
  resultWorkerStarted: boolean;
  evaluationWorkersStarted: number;
  totalEvaluationWorkers: number;
}

export interface GradeResponse {
  status: string;
  jobsProduced: number;
  assignmentId: string;
  message: string;
}

export interface SubmissionResult {
  id?: number;
  studentId: string;
  assignmentId: string;
  testCaseName?: string;
  score?: number;
  maxScore?: number;
  verdict: string;
  gradedAt?: string;
}

export interface PlagiarismPair {
  student1Id: string;
  student2Id: string;
  similarity: number;
}

export interface PlagiarismReport {
  assignmentId: string;
  language: string;
  pairs: PlagiarismPair[];
  generatedAt?: string;
}