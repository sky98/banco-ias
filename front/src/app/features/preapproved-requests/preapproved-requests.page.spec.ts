import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
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

  it('shows the rejection reason and remaining quota when quota is insufficient', async () => {
    service.create.mockReturnValue(
      of(
        usage({
          amount: 1200000,
          status: 'REJECTED',
          reason: 'INSUFFICIENT_QUOTA',
          remainingAmount: 1000000,
        }),
      ),
    );

    component.submit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('REJECTED');
    expect(text()).toContain('INSUFFICIENT_QUOTA');
    expect(text()).toContain('1,000,000');
  });

  it('shows a rejected result for a zero amount', async () => {
    service.create.mockReturnValue(
      of(
        usage({
          amount: 0,
          status: 'REJECTED',
          reason: 'INVALID_AMOUNT',
          remainingAmount: 1000000,
        }),
      ),
    );

    component.form.controls.amount.setValue(0);
    component.submit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(service.create).toHaveBeenCalledWith(
      expect.objectContaining({ amount: 0 }),
    );
    expect(text()).toContain('INVALID_AMOUNT');
  });

  it('shows the minimum amount error and does not submit a negative amount', () => {
    component.form.controls.amount.setValue(-1);
    component.submit();
    fixture.detectChanges();

    expect(service.create).not.toHaveBeenCalled();
    expect(text()).toContain('El monto no puede ser negativo.');
  });

  it('shows the required error when the amount is empty', () => {
    component.form.controls.amount.setValue(null as unknown as number);
    component.submit();
    fixture.detectChanges();

    expect(service.create).not.toHaveBeenCalled();
    expect(text()).toContain('El monto es obligatorio.');
  });

  it('shows the max length error for preApprovedId and customerId', () => {
    component.form.controls.preApprovedId.setValue('P'.repeat(41));
    component.form.controls.customerId.setValue('C'.repeat(41));
    component.submit();
    fixture.detectChanges();

    expect(service.create).not.toHaveBeenCalled();
    const messages = text().split('Máximo 40 caracteres.').length - 1;
    expect(messages).toBe(2);
  });

  it('sends a decimal amount unchanged so the backend can reject it', async () => {
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

    component.form.controls.amount.setValue(100.005);
    component.submit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(service.create).toHaveBeenCalledWith(
      expect.objectContaining({ amount: 100.005 }),
    );
    expect(text()).toContain('amount: máximo 2 decimales');
  });

  it('shows a generic message on server errors without an API body', async () => {
    service.create.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, error: null })),
    );

    component.submit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('No fue posible procesar la solicitud.');
  });

  it('shows a generic message when the network fails', async () => {
    service.create.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') })),
    );

    component.submit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('No fue posible procesar la solicitud.');
  });

  it('does not show the original result on a 409 without details', async () => {
    service.create.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              code: 'REFERENCE_CONFLICT',
              message: 'La referencia ya fue procesada con otro payload',
              details: null,
            },
          }),
      ),
    );

    component.submit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('La referencia ya fue procesada con otro payload');
    expect(text()).not.toContain('Referencia ya procesada');
    expect(text()).not.toContain('ORIGINAL');
  });

  it('shows an error when recent requests cannot be loaded', async () => {
    service.listRecent.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    component.loadRecent();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).toContain('No fue posible cargar las solicitudes recientes.');
  });

  it('disables the submit button and shows progress while the request is pending', () => {
    const pending = new Subject<ReturnType<typeof usage>>();
    service.create.mockReturnValue(pending.asObservable());

    component.submit();
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain('Procesando...');

    pending.next(usage());
    pending.complete();
    fixture.detectChanges();

    expect(button.disabled).toBe(false);
  });

  it('clears the previous result and error when a new submit starts', async () => {
    service.create.mockReturnValueOnce(of(usage()));
    component.submit();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text()).toContain('APPROVED');

    service.create.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { code: 'VALIDATION_ERROR', message: 'customerId: obligatorio', details: null },
          }),
      ),
    );
    component.submit();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text()).not.toContain('APPROVED');
    expect(text()).toContain('customerId: obligatorio');
  });

  it('reloads recent requests when the refresh button is clicked', () => {
    const button = fixture.nativeElement.querySelector('.table-card button') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();

    expect(service.listRecent).toHaveBeenCalledTimes(2);
  });

  it('shows a dash when a rejected row has no reason', async () => {
    service.listRecent.mockReturnValue(of([usage({ reason: null })]));

    component.loadRecent();
    await fixture.whenStable();
    fixture.detectChanges();

    const cells = Array.from(fixture.nativeElement.querySelectorAll('tbody td')) as HTMLTableCellElement[];
    expect(cells.some((cell) => cell.textContent?.trim() === '—')).toBe(true);
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
