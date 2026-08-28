import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResultsComponent } from './results.component';
import { ApiService } from '../../services/api.service';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

describe('ResultsComponent', () => {
  let component: ResultsComponent;
  let fixture: ComponentFixture<ResultsComponent>;

  beforeEach(async () => {
    const apiSpy = jasmine.createSpyObj('ApiService', ['getResults', 'getLocalAssignments']);
    apiSpy.getResults.and.returnValue(Promise.resolve([]));
    apiSpy.getLocalAssignments.and.returnValue({});

    await TestBed.configureTestingModule({
      imports: [ResultsComponent],
      providers: [
        { provide: ApiService, useValue: apiSpy },
        { provide: ActivatedRoute, useValue: { paramMap: of(new Map([['id', 'a1']])) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ResultsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
