import { checkin as checkinRequest, checkinStatus as checkinStatusRequest } from "@/src/lib/api/generated/user-account";
import { unwrapGeneratedPayload } from "@/src/lib/api/generatedCompat";
import type { CheckinRequest } from "@/src/lib/types/all/checkinRequest";
import type { CheckinResultDTO } from "@/src/lib/types/all/checkinResultDTO";
import type { CheckinStatusDTO } from "@/src/lib/types/all/checkinStatusDTO";

export async function submitCheckin(body: CheckinRequest): Promise<CheckinResultDTO | null> {
  const response = await checkinRequest(body);
  return unwrapGeneratedPayload<CheckinResultDTO | null>(response);
}

export async function fetchCheckinStatus(): Promise<CheckinStatusDTO | null> {
  const response = await checkinStatusRequest();
  return unwrapGeneratedPayload<CheckinStatusDTO | null>(response);
}
