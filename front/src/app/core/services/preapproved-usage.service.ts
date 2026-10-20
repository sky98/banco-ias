import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreatePreApprovedUsageRequest,
  PreApprovedUsage,
} from '../models/preapproved-usage.model';

@Injectable({ providedIn: 'root' })
export class PreApprovedUsageService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/preapproved-requests';

  create(request: CreatePreApprovedUsageRequest): Observable<PreApprovedUsage> {
    return this.http.post<PreApprovedUsage>(this.baseUrl, request);
  }

  listRecent(limit = 10): Observable<PreApprovedUsage[]> {
    const params = new HttpParams().set('limit', limit);
    return this.http.get<PreApprovedUsage[]>(this.baseUrl, { params });
  }

  getByReference(requestReference: string): Observable<PreApprovedUsage> {
    return this.http.get<PreApprovedUsage>(
      `${this.baseUrl}/${encodeURIComponent(requestReference)}`,
    );
  }
}
