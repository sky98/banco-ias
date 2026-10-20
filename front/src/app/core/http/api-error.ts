import { HttpErrorResponse } from '@angular/common/http';

export interface ApiError<T = unknown> {
  code: string;
  message: string;
  details: T | null;
}

export function asApiError(error: unknown): ApiError | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }

  const body = error.error as Partial<ApiError> | null;
  if (!body || typeof body.code !== 'string' || typeof body.message !== 'string') {
    return null;
  }

  return {
    code: body.code,
    message: body.message,
    details: body.details ?? null,
  };
}
