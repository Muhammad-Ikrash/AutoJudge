import { TestBed } from '@angular/core/testing';
import { WorkerAPIService } from './worker-apiservice';

describe('WorkerAPIService', () => {
  let service: WorkerAPIService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(WorkerAPIService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
