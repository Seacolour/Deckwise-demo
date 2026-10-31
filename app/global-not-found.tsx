import "./globals.css";

import type { Metadata } from "next";
import { AppErrorFallback } from "@/src/components/_shared/AppErrorFallback";
import { ThemeController } from "@/src/components/_shared/theme/ThemeController";
import { siteMetadata } from "@/src/lib/seo/siteMetadata";
import { createThemeInitScript } from "@/src/lib/theme";

export const metadata: Metadata = {
  description: "The requested Deckwise page does not exist.",
  metadataBase: new URL(siteMetadata.url),
  title: "Page not found | Deckwise",
};

export default function GlobalNotFoundPage() {
  return (
    <html
      lang="en"
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: createThemeInitScript() }} />
      </head>
      <body className="font-sans antialiased">
        <ThemeController />
        <AppErrorFallback
          homeHref="/"
          homeLabel="Back to home"
          message="The page you requested could not be found."
          title="Page not found"
        />
      </body>
    </html>
  );
}
