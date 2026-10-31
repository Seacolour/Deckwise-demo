import assert from "node:assert/strict";
import { test } from "vitest";
import {
  getRequestErrorMessage,
  RequestTimeoutError,
} from "@/src/lib/api/requestError.ts";
import { ApiError } from "@/src/lib/types/types.ts";

test("getRequestErrorMessage maps generic HTTP 500 errors to localized server copy", () => {
  assert.equal(
    getRequestErrorMessage(new ApiError("HTTP 500", 500), {
      serverErrorMessage: "Server is having trouble.",
    }),
    "Server is having trouble.",
  );
});

test("getRequestErrorMessage maps unavailable HTTP errors before generic server copy", () => {
  assert.equal(
    getRequestErrorMessage(new ApiError("HTTP 503", 503), {
      serverErrorMessage: "Server is having trouble.",
      serviceUnavailableMessage: "Service is unavailable.",
    }),
    "Service is unavailable.",
  );
});

test("getRequestErrorMessage maps fetch failures to localized network copy", () => {
  assert.equal(
    getRequestErrorMessage(new TypeError("Failed to fetch"), {
      networkErrorMessage: "Network failed.",
    }),
    "Network failed.",
  );
});

test("getRequestErrorMessage keeps non-fetch TypeError messages", () => {
  assert.equal(
    getRequestErrorMessage(new TypeError("Cannot read properties of undefined"), {
      networkErrorMessage: "Network failed.",
    }),
    "Cannot read properties of undefined",
  );
});

test("getRequestErrorMessage keeps timeout copy highest priority", () => {
  assert.equal(
    getRequestErrorMessage(new RequestTimeoutError(1000), {
      networkErrorMessage: "Network failed.",
      timeoutMessage: "Timed out.",
    }),
    "Timed out.",
  );
});
