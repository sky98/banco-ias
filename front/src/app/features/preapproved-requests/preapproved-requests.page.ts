import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { asApiError } from '../../core/http/api-error';
import { PreApprovedUsage } from '../../core/models/preapproved-usage.model';
import { PreApprovedUsageService } from '../../core/services/preapproved-usage.service';

@Component({
  selector: 'app-preapproved-requests',
  imports: [ReactiveFormsModule, DecimalPipe, DatePipe],
  templateUrl: './preapproved-requests.page.html',
  styleUrl: './preapproved-requests.page.css',
})
export class PreApprovedRequestsPage implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly usageService = inject(PreApprovedUsageService);
  private readonly destroyRef = inject(DestroyRef);

  readonly form = this.formBuilder.nonNullable.group({
    requestReference: [
      'REF-001',
      [Validators.required, Validators.maxLength(80)],
    ],
    preApprovedId: [
      'PRA-1001',
      [Validators.required, Validators.maxLength(40)],
    ],
    customerId: [
      'USR-10',
      [Validators.required, Validators.maxLength(40)],
    ],
    amount: [600000, [Validators.required, Validators.min(0)]],
  });

  readonly result = signal<PreApprovedUsage | null>(null);
  readonly recent = signal<PreApprovedUsage[]>([]);
  readonly submitting = signal(false);
  readonly loadingRecent = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly conflictMessage = signal<string | null>(null);
  readonly recentError = signal<string | null>(null);

  ngOnInit(): void {
    this.loadRecent();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);
    this.conflictMessage.set(null);
    this.result.set(null);

    this.usageService
      .create(this.form.getRawValue())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.submitting.set(false)),
      )
      .subscribe({
        next: (usage) => {
          this.result.set(usage);
          this.loadRecent();
        },
        error: (error: unknown) => this.handleError(error),
      });
  }

  loadRecent(): void {
    this.loadingRecent.set(true);
    this.recentError.set(null);

    this.usageService
      .listRecent(10)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loadingRecent.set(false)),
      )
      .subscribe({
        next: (usages) => this.recent.set(usages),
        error: () =>
          this.recentError.set(
            'No fue posible cargar las solicitudes recientes.',
          ),
      });
  }

  private handleError(error: unknown): void {
    const apiError = asApiError(error);

    if (
      error instanceof HttpErrorResponse &&
      error.status === 409 &&
      apiError?.details
    ) {
      this.conflictMessage.set(apiError.message);
      this.result.set(apiError.details as PreApprovedUsage);
      return;
    }

    this.errorMessage.set(
      apiError?.message ?? 'No fue posible procesar la solicitud.',
    );
  }
}
