"use client";

import { useCallback, useState } from "react";
import { apiFetchWithAuth } from "@/src/lib/api/fetcherWithAuth";
import { buildClientLoginContext } from "@/src/lib/auth/clientLoginContext";
import { saveAuth, writeEnvSignature } from "@/src/stores/auth";

type LoginResult = { user?: Record<string, unknown> | null };

type LoginMessages = {
  invalidEmail: string;
  missingCode: string;
  emailCodeFailed: string;
  emailLoginFailed: string;
};

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function useEmailCodeAuth({
  onDone,
  onError,
  locale,
  messages,
}: {
  onDone: () => void | Promise<void>;
  onError: (message: string) => void;
  locale?: string;
  messages: LoginMessages;
}) {
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [sendingCode, setSendingCode] = useState(false);
  const [codeSent, setCodeSent] = useState(false);
  const [loading, setLoading] = useState(false);

  const changeEmail = useCallback((value: string) => {
    setEmail(value);
    setCode("");
    setCodeSent(false);
  }, []);

  const sendCode = useCallback(async () => {
    const normalizedEmail = email.trim();
    if (!EMAIL_PATTERN.test(normalizedEmail)) {
      onError(messages.invalidEmail);
      return;
    }

    setSendingCode(true);
    try {
      const sent = await apiFetchWithAuth<boolean>("/user/login/email-code", {
        method: "POST",
        body: { email: normalizedEmail },
        disableAuthHeader: true,
        requestPolicy: "mutation",
      });
      if (sent !== true) throw new Error("email code not sent");
      setCodeSent(true);
    } catch {
      onError(messages.emailCodeFailed);
    } finally {
      setSendingCode(false);
    }
  }, [email, messages.emailCodeFailed, messages.invalidEmail, onError]);

  const signIn = useCallback(async () => {
    const normalizedEmail = email.trim();
    if (!EMAIL_PATTERN.test(normalizedEmail)) {
      onError(messages.invalidEmail);
      return;
    }
    if (!code.trim()) {
      onError(messages.missingCode);
      return;
    }

    setLoading(true);
    try {
      const login = await apiFetchWithAuth<LoginResult>("/user/login", {
        method: "POST",
        body: {
          email: normalizedEmail,
          code: code.trim(),
          ...buildClientLoginContext(locale),
        },
        disableAuthHeader: true,
        requestPolicy: "mutation",
      });
      if (!login?.user || typeof login.user !== "object") {
        throw new Error("login response missing user");
      }
      saveAuth({ user: login.user });
      writeEnvSignature();
      await onDone();
    } catch {
      onError(messages.emailLoginFailed);
    } finally {
      setLoading(false);
    }
  }, [code, email, locale, messages.emailLoginFailed, messages.invalidEmail, messages.missingCode, onDone, onError]);

  return {
    email,
    setEmail: changeEmail,
    code,
    setCode,
    codeSent,
    sendingCode,
    loading,
    sendCode,
    signIn,
  };
}
