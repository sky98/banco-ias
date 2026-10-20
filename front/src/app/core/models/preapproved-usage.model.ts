export type UsageStatus = 'APPROVED' | 'REJECTED';

export interface CreatePreApprovedUsageRequest {
  requestReference: string;
  preApprovedId: string;
  customerId: string;
  amount: number;
}

export interface PreApprovedUsage {
  requestReference: string;
  preApprovedId: string;
  customerId: string;
  amount: number;
  status: UsageStatus;
  reason: string | null;
  processedAt: string;
  remainingAmount: number;
  replayed: boolean;
}
