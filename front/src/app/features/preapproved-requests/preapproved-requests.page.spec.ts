import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PreApprovedUsage } from '../../core/models/preapproved-usage.model';
import { PreApprovedUsageService } from '../../core/services/preapproved-usage.service';
import { PreApprovedRequestsPage } from './preapproved-requests.page';

interface ServiceMock {
  create: ReturnType<typeof vi.fn>;
  listRecent: ReturnType<typeof vi.fn>;
  getByReference: ReturnType<typeof vi.fn>;
}

describe('PreApprovedRequestsPage', () => {
  let fixture: ComponentFixture<PreApprovedRequestsPage>;
  let component: PreApprovedRequestsPage;
  let service: ServiceMock;

  beforeEach(async () => {
    service = {
      create: vi.fn(),
      listRecent: vi.fn().mockReturnValue(of([])),
      getByReference: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [PreApprovedRequestsPage],
      providers: [{ provide: PreApprovedUsageService, useValue: service }],
    }).compileComponents();

    fixture = TestBed.createComponent(PreApprovedRequestsPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads demo defaults and recent requests on init', async () => {
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.form.getRawValue()).toEqual({
      requestReference: 'REF-001',
      preApprovedId: 'PRA-1001',
      customerId: 'USR-10',
      amount: 600000,
    });
    expect(service.listRecent).toHaveBeenCalledWith(10);
    expect(text()).toContain('No hay solicitudes procesadas todavía.');
  });

  it('shows the result and refreshes recent requests after a successful submit', async () => {
    service.create.mockReturnValue(of(usage()));

    component.submit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(service.create).toHaveBeenCalledWith({
      requestReference: 'REF-001',
      preApprovedId: 'PRA-1001',
      customerId: 'USR-10',
      amount: 600000,
    });
    expect(service.listRecent).toHaveBeenCalledTimes(2);
    expect(text()).toContain('APPROVED');
    expect(text()).toContain('400,000');
  });

  it('shows the replay badge when the backend returns the original result', async () => {
    service.create.mockReturnValue(of({ ...usage(), replayed: true }));

    component.submit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('REPLAY');
  });

  it('shows the original result on a reference conflict', async () => {
    service.create.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              code: 'REFERENCE_CONFLICT',
              message: 'La referencia ya fue procesada con otro payload',
              details: usage(),
            },
          }),
      ),
    );

    component.submit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('Referencia ya procesada');
    expect(text()).toContain('ORIGINAL');
    expect(text()).toContain('600,000');
  });

  it('shows the backend validation message on a bad request', async () => {
    service.create.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              code: 'VALIDATION_ERROR',
              message: 'amount: máximo 2 decimales',
              details: null,
            },
          }),
      ),
    );

    component.submit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('amount: máximo 2 decimales');
  });

  it('renders recent requests in the table', async () => {
    service.listRecent.mockReturnValue(of([usage()]));

    component.loadRecent();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('REF-001');
    expect(text()).toContain('APPROVED');
    expect(text()).toContain('USR-10');
  });

  it('does not submit an invalid form', () => {
    component.form.controls.requestReference.setValue('');
    component.submit();
    fixture.detectChanges();

    expect(service.create).not.toHaveBeenCalled();
    expect(text()).toContain('La referencia es obligatoria.');
  });

  function text(): string {
    return fixture.nativeElement.textContent as string;
  }
});

function usage(overrides: Partial<PreApprovedUsage> = {}): PreApprovedUsage {
  return {
    requestReference: 'REF-001',
    preApprovedId: 'PRA-1001',
    customerId: 'USR-10',
    amount: 600000,
    status: 'APPROVED',
    reason: null,
    processedAt: '2026-10-20T05:00:00Z',
    remainingAmount: 400000,
    replayed: false,
    ...overrides,
  };
}
