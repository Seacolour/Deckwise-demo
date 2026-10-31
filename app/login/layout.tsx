import type { Metadata } from "next";
import type { ReactNode } from "react";
import { RuntimeRootDocument } from "@/app/_root/RootDocument";
import { AppRouteProviders } from "@/src/components/_shared/layouts/AppRouteProviders";
import { RouteIntlProvider } from "@/src/components/_shared/layouts/RouteIntlProvider";

export const metadata: Metadata = {
  title: "Deckwise Demo — Sign in",
  robots: { index: false, follow: false },
};

export default function LoginLayout({ children }: { children: ReactNode }) {
  return (
    <RuntimeRootDocument>
      <RouteIntlProvider scope="login">
        <AppRouteProviders>{children}</AppRouteProviders>
      </RouteIntlProvider>
    </RuntimeRootDocument>
  );
}
