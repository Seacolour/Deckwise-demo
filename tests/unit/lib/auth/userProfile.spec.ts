import assert from "node:assert/strict";
import { test } from "vitest";
import {
  extractPersistedAuthUser,
  resolveUserDisplayName,
} from "@/src/lib/auth/userProfile";

test("extracts the nested auth user payload", () => {
  assert.deepEqual(
    extractPersistedAuthUser({
      userToken: "token",
      user: {
        id: "u_1",
        name: "Mark",
      },
    }),
    {
      id: "u_1",
      name: "Mark",
    },
  );
});

test("resolves display names across legacy field names", () => {
  assert.equal(
    resolveUserDisplayName(
      {
        username: "deckwise-user",
      },
      "Fallback",
    ),
    "deckwise-user",
  );
});

