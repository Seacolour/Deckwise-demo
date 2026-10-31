"use client";

import type { ComponentProps } from "react";
import { NextIntlClientProvider } from "next-intl";

export function ExplicitIntlProvider(
  props: ComponentProps<typeof NextIntlClientProvider>,
) {
  return <NextIntlClientProvider {...props} />;
}
