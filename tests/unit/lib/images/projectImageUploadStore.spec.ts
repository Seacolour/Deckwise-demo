import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "vitest";

import {
  clearProjectImageUploads,
  pruneCompletedProjectImageUploads,
  readProjectImageUploads,
  subscribeProjectImageUploads,
  trackProjectImageUploadFailed,
  trackProjectImageUploadStarted,
  trackProjectImageUploadSucceeded,
} from "@/src/lib/images/projectImageUploadStore.ts";

const originalCreateObjectURL = URL.createObjectURL;
const originalRevokeObjectURL = URL.revokeObjectURL;

let objectUrlCounter = 0;
let revokedObjectUrls: string[] = [];

function createFakeImageFile(name: string): File {
  return { name, type: "image/png" } as File;
}

beforeEach(() => {
  objectUrlCounter = 0;
  revokedObjectUrls = [];
  clearProjectImageUploads();

  URL.createObjectURL = (() => {
    objectUrlCounter += 1;
    return `blob:test-${objectUrlCounter}`;
  }) as typeof URL.createObjectURL;
  URL.revokeObjectURL = ((url: string | URL) => {
    revokedObjectUrls.push(String(url));
  }) as typeof URL.revokeObjectURL;
});

afterEach(() => {
  clearProjectImageUploads();
  URL.createObjectURL = originalCreateObjectURL;
  URL.revokeObjectURL = originalRevokeObjectURL;
});

test("empty project image upload snapshots stay referentially stable", () => {
  const first = readProjectImageUploads("empty-project");
  const second = readProjectImageUploads("empty-project");

  assert.equal(first, second);
  assert.equal(first.length, 0);
});

test("started image uploads are readable even before consumers subscribe", () => {
  trackProjectImageUploadStarted({
    projectId: "project-1",
    tempCacheKey: "temp-image-1",
    type: "image",
    name: "Image One",
    rawName: "image-one.png",
    file: createFakeImageFile("image-one.png"),
  });

  const uploads = readProjectImageUploads("project-1");
  assert.equal(uploads.length, 1);
  assert.equal(uploads[0]?.cacheKey, "temp-image-1");
  assert.equal(uploads[0]?.name, "Image One");
  assert.equal(uploads[0]?.url, "blob:test-1");
  assert.equal(uploads[0]?.uploading, true);
});

test("successful uploads promote the temporary entry until the wall prunes it", () => {
  trackProjectImageUploadStarted({
    projectId: "project-2",
    tempCacheKey: "temp-image-2",
    type: "image",
    name: "Image Two",
    rawName: "image-two.png",
    file: createFakeImageFile("image-two.png"),
  });

  trackProjectImageUploadSucceeded({
    projectId: "project-2",
    tempCacheKey: "temp-image-2",
    file: {
      type: "image",
      cacheKey: "final-image-2",
      url: "https://cdn.example.com/image-two.png",
      name: "Image Two",
      documentId: "doc-2",
    },
  });

  const uploads = readProjectImageUploads("project-2");
  assert.equal(uploads.length, 1);
  assert.equal(uploads[0]?.cacheKey, "final-image-2");
  assert.equal(uploads[0]?.url, "https://cdn.example.com/image-two.png");
  assert.equal(uploads[0]?.documentId, "doc-2");
  assert.equal(uploads[0]?.uploading, false);
  assert.deepEqual(revokedObjectUrls, ["blob:test-1"]);

  pruneCompletedProjectImageUploads("project-2", ["final-image-2"]);
  assert.equal(readProjectImageUploads("project-2").length, 0);
});

test("failed uploads remove the temporary entry and revoke its object URL", () => {
  trackProjectImageUploadStarted({
    projectId: "project-3",
    tempCacheKey: "temp-image-3",
    type: "image",
    name: "Image Three",
    rawName: "image-three.png",
    file: createFakeImageFile("image-three.png"),
  });

  trackProjectImageUploadFailed({
    projectId: "project-3",
    tempCacheKey: "temp-image-3",
  });

  assert.equal(readProjectImageUploads("project-3").length, 0);
  assert.deepEqual(revokedObjectUrls, ["blob:test-1"]);
});

test("image upload store subscribers are notified on every state transition", () => {
  let notifications = 0;
  const unsubscribe = subscribeProjectImageUploads(() => {
    notifications += 1;
  });

  trackProjectImageUploadStarted({
    projectId: "project-4",
    tempCacheKey: "temp-image-4",
    type: "image",
    name: "Image Four",
    rawName: "image-four.png",
    file: createFakeImageFile("image-four.png"),
  });
  trackProjectImageUploadSucceeded({
    projectId: "project-4",
    tempCacheKey: "temp-image-4",
    file: {
      type: "image",
      cacheKey: "final-image-4",
      url: "https://cdn.example.com/image-four.png",
      name: "Image Four",
    },
  });
  pruneCompletedProjectImageUploads("project-4", ["final-image-4"]);
  unsubscribe();

  assert.equal(notifications, 3);
});
