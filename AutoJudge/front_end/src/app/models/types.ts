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

export interface BatchStatus {
  completed: number;
  total: number;
}

export interface AssignmentConfig {
  assignmentId: string;
  resourceLimits: {
    timeLimitMs: number;
    memoryLimitMb: number;
    cpuLimit: number;
  };
  executionProfile: {
    autoRemove: boolean;
    workingDirectory: string;
  };
}

export interface AssignmentSummary {
  id: string;
  submissionCount: number;
  status: string;
  config: AssignmentConfig | Record<string, never>;
}

export interface TestCase {
  id: string;
  inputFile: string;
  outputFile: string;
  weight: number;
}

export interface SubmissionResult {
  id?: number;
  studentId: string;
  assignmentId: string;
  submissionId?: string;
  testCaseName?: string;
  score?: number;
  maxScore?: number;
  passedTests?: number;
  totalTests?: number;
  verdict: string;
  gradedAt?: string;
  testCasesResults?: { testCaseId: string; verdict: string }[];
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