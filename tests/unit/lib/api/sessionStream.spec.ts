import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  isSseStreamError,
  streamSendMessageRPC,
} from "@/src/lib/api/sessionStream";

describe("streamSendMessageRPC", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.unstubAllGlobals();
  });

  it("raises SSE error events with generic classification", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      streamFromChunks(["event: error\ndata: AI service unavailable\n\n"]),
      {
        headers: { "Content-Type": "text/event-stream" },
        status: 200,
      },
    ));
    vi.stubGlobal("fetch", fetchMock);
    const onMessage = vi.fn();
    const onError = vi.fn();
    const onDone = vi.fn();

    let caught: unknown = null;
    try {
      await streamSendMessageRPC(
        "670",
        { message: "hello", mode: "AGENT" },
        { onMessage, onError, onDone },
      );
    } catch (error) {
      caught = error;
    }

    expect(fetchMock).toHaveBeenCalledWith(
      "/sse/sessions/670/sendRPC",
      expect.objectContaining({
        method: "POST",
        credentials: "include",
        headers: expect.objectContaining({
          Accept: "text/event-stream, application/json",
        }),
      }),
    );
    expect(onMessage).not.toHaveBeenCalled();
    expect(onDone).not.toHaveBeenCalled();
    expect(onError).toHaveBeenCalledTimes(1);
    expect(caught).toBeTruthy();
    expect(caught).toMatchObject({ kind: "streamError", message: "AI service unavailable" });
    expect(onError.mock.calls[0]?.[0]).toBe(caught);
  });

  it("unwraps structured SSE error payloads while preserving their raw data", async () => {
    const rawData = JSON.stringify({
      type: "error",
      seq: 396,
      ts: "2026-07-13T16:06:20.761643912Z",
      payload: "AI 调用失败",
    });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      streamFromChunks([`event: error\ndata: ${rawData}\n\n`]),
      {
        headers: { "Content-Type": "text/event-stream" },
        status: 200,
      },
    )));
    const onError = vi.fn();

    let caught: unknown = null;
    try {
      await streamSendMessageRPC(
        "670",
        { message: "hello", mode: "AGENT" },
        { onMessage: vi.fn(), onError, onDone: vi.fn() },
      );
    } catch (error) {
      caught = error;
    }

    expect(isSseStreamError(caught)).toBe(true);
    expect(caught).toMatchObject({
      eventName: "error",
      kind: "streamError",
      message: "AI 调用失败",
      rawData,
    });
    expect(onError).toHaveBeenCalledWith(caught);
  });

  it("treats structured SSE error payloads as generic stream errors", async () => {
    const rawData = JSON.stringify({
      type: "error",
      payload: { code: 50300, message: "AI service unavailable" },
    });
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      streamFromChunks([`event: error\ndata: ${rawData}\n\n`]),
      {
        headers: { "Content-Type": "text/event-stream" },
        status: 200,
      },
    )));

    let caught: unknown = null;
    try {
      await streamSendMessageRPC(
        "670",
        { message: "hello", mode: "AGENT" },
        { onMessage: vi.fn(), onDone: vi.fn() },
      );
    } catch (error) {
      caught = error;
    }

    expect(caught).toMatchObject({ kind: "streamError", message: "AI service unavailable" });
  });
});

function streamFromChunks(chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    },
  });
}
