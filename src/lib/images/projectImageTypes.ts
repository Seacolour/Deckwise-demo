"use client";

export type ImageFile = {
  url: string;
  fullUrl?: string;
  name: string;
  cacheKey: string;
  sourceId?: string;
  documentId?: string;
  uploadedAt?: number;
  width?: number;
  height?: number;
  aspectRatio?: number;
  uploading?: boolean;
};
