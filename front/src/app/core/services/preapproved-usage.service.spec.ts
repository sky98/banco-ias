import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PreApprovedUsage } from '../models/preapproved-usage.model';
import { PreApprovedUsageService } from './preapproved-usage.service';

describe('PreApprovedUsageService', () => {
  let service: PreApprovedUsageService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PreApprovedUsageService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('creates a usage request', () => {
    const response = usage();

    service
      .create({
        requestReference: 'REF-001',
        preApprovedId: 'PRA-1001',
        customerId: 'USR-10',
        amount: 600000,
      })
      .subscribe((result) => expect(result).toEqual(response));

    const request = httpMock.expectOne('/api/v1/preapproved-requests');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      requestReference: 'REF-001',
      preApprovedId: 'PRA-1001',
      customerId: 'USR-10',
      amount: 600000,
    });
    request.flush(response);
  });

  it('lists recent usages with the requested limit', () => {
    service
      .listRecent(10)
      .subscribe((result) => expect(result).toEqual([usage()]));

    const request = httpMock.expectOne(
      (candidate) =>
        candidate.url === '/api/v1/preapproved-requests' &&
        candidate.params.get('limit') === '10',
    );
    expect(request.request.method).toBe('GET');
    request.flush([usage()]);
  });

  it('gets a usage by reference encoding the path value', () => {
    service
      .getByReference('REF 001')
      .subscribe((result) => expect(result).toEqual(usage()));

    const request = httpMock.expectOne(
      '/api/v1/preapproved-requests/REF%20001',
    );
    expect(request.request.method).toBe('GET');
    request.flush(usage());
  });
});

function usage(): PreApprovedUsage {
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
  };
}
