"use client";

import { useCallback } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Sparkles } from "lucide-react";
import { Button } from "@/src/components/_shared/ui/button";
import { notify } from "@/src/lib/toast";
import { useEmailCodeAuth } from "./useEmailCodeAuth";
import { useRedirectAfterLogin } from "./useRedirectAfterLogin";

export default function LoginPage() {
  const tLogin = useTranslations("login");
  const locale = useLocale();
  const doRedirect = useRedirectAfterLogin();
  const handleLoginError = useCallback((message: string) => notify.error(message), []);
  const {
    email,
    setEmail,
    code,
    setCode,
    codeSent,
    sendingCode,
    loading,
    sendCode,
    signIn,
  } = useEmailCodeAuth({
    onDone: doRedirect,
    onError: handleLoginError,
    locale,
    messages: {
      invalidEmail: tLogin("invalidEmail"),
      missingCode: tLogin("missingCode"),
      emailCodeFailed: tLogin("emailCodeFailed"),
      emailLoginFailed: tLogin("emailLoginFailed"),
    },
  });

  return (
    <div className="fixed inset-0 flex items-center justify-center overflow-auto bg-background px-6 py-10 text-foreground">
      <div className="relative z-10 w-full max-w-[400px]">
        <div className="mx-auto flex w-full max-w-[360px] flex-col">
          <div className="mx-auto mb-16 flex items-center justify-center gap-3 text-foreground" aria-label="Deckwise Demo" role="img">
            <span className="grid size-[2.75rem] place-items-center rounded-xl bg-blue-600 text-white"><Sparkles size={25} aria-hidden="true" /></span>
            <span className="text-[30px] font-medium leading-none tracking-tight [font-family:var(--font-body-stack)]">
              Deckwise Demo
            </span>
          </div>
          <p className="mx-auto mb-8 w-full max-w-[320px] whitespace-pre-line text-center text-[20px] font-normal leading-[27px]">
            {tLogin("headline")}
          </p>
          <form
            className="flex w-full flex-col gap-5"
            onSubmit={(event) => {
              event.preventDefault();
              void signIn();
            }}
          >
            <label className="flex flex-col gap-2 text-sm" htmlFor="login-email">
              {tLogin("emailLabel")}
              <input
                id="login-email"
                className="h-12 w-full rounded-md border border-input bg-background px-3 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
                type="email"
                name="email"
                autoComplete="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </label>
            <div className="flex items-end gap-3">
              <label className="flex min-w-0 flex-1 flex-col gap-2 text-sm" htmlFor="login-code">
                {tLogin("codeLabel")}
                <input
                  id="login-code"
                  className="h-12 w-full rounded-md border border-input bg-background px-3 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  type="text"
                  name="code"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  required
                  value={code}
                  onChange={(event) => setCode(event.target.value)}
                />
              </label>
              <Button type="button" variant="outline" className="h-12" disabled={sendingCode || loading} onClick={() => void sendCode()}>
                {sendingCode ? tLogin("sendingCode") : tLogin("sendCode")}
              </Button>
            </div>
            <p className="min-h-5 text-sm text-muted-foreground" role="status" aria-live="polite">
              {codeSent ? tLogin("codeSent") : ""}
            </p>
            <Button type="submit" className="h-12 w-full" disabled={loading || sendingCode}>
              {loading ? tLogin("signingIn") : tLogin("signInWithEmail")}
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
}
