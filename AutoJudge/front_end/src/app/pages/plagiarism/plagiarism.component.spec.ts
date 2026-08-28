import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PlagiarismComponent } from './plagiarism.component';
import { ApiService } from '../../services/api.service';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

describe('PlagiarismComponent', () => {
  let component: PlagiarismComponent;
  let fixture: ComponentFixture<PlagiarismComponent>;

  beforeEach(async () => {
    const apiSpy = jasmine.createSpyObj('ApiService', ['getPlagiarism']);
    apiSpy.getPlagiarism.and.returnValue(Promise.resolve({ assignmentId: 'a1', threshold: 0, similarities: [] }));

    await TestBed.configureTestingModule({
      imports: [PlagiarismComponent],
      providers: [
        { provide: ApiService, useValue: apiSpy },
        { provide: ActivatedRoute, useValue: { paramMap: of(new Map([['id', 'a1']])) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PlagiarismComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
