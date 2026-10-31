"use client";

import { EVENTS } from "@/src/lib/config/constants/events";
import { safeDispatchEvent } from "@/src/lib/utils/errorHandler";

export type ProjectEventDetail = {
  forceRefresh?: boolean;
  id: string;
};

export type ProjectMissingDetail = {
  id: string;
  sourceUrl?: string | null;
  code?: number;
  message?: string | null;
};

export function dispatchProjectCreated(id: string) {
  safeDispatchEvent(new CustomEvent<ProjectEventDetail>(EVENTS.PROJECT_CREATED, { detail: { id } }));
}

export function dispatchProjectUpdated(id: string, options: { forceRefresh?: boolean } = {}) {
  safeDispatchEvent(new CustomEvent<ProjectEventDetail>(EVENTS.PROJECT_UPDATED, {
    detail: {
      ...(options.forceRefresh ? { forceRefresh: true } : {}),
      id,
    },
  }));
}

export function dispatchProjectDeleted(id: string) {
  safeDispatchEvent(new CustomEvent<ProjectEventDetail>(EVENTS.PROJECT_DELETED, { detail: { id } }));
}

export function dispatchProjectMissing(detail: ProjectMissingDetail) {
  safeDispatchEvent(
    new CustomEvent<ProjectMissingDetail>(EVENTS.PROJECT_MISSING, { detail }),
  );
}
