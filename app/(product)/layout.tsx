import type { Metadata } from "next";
import type { ReactNode } from "react";
import { RuntimeRootDocument } from "@/app/_root/RootDocument";
import { AppRouteProviders } from "@/src/components/_shared/layouts/AppRouteProviders";
import { RouteIntlProvider } from "@/src/components/_shared/layouts/RouteIntlProvider";
import { siteMetadata } from "@/src/lib/seo/siteMetadata";
import "@/src/components/features/markdown-surface/style.css";

export const metadata: Metadata = {
  metadataBase: new URL(siteMetadata.url),
  robots: {
    index: false,
    follow: false,
  },
};

// Authenticated product routes share this root document so navigation between
// `/app` and `/canvas` keeps the product providers and persistent shell mounted.
export default function ProductLayout({ children }: { children: ReactNode }) {
  return (
    <RuntimeRootDocument>
      <RouteIntlProvider scope="product">
        <AppRouteProviders>{children}</AppRouteProviders>
      </RouteIntlProvider>
    </RuntimeRootDocument>
  );
}
