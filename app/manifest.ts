import type { MetadataRoute } from "next";
import { getLocale, getTranslations } from "next-intl/server";
import { defaultLocale, isAppLocale } from "@/src/lib/i18n";
import { withLocalePrefix } from "@/src/lib/routing/localePrefix";
import { siteMetadata } from "@/src/lib/seo/siteMetadata";

export const dynamic = "force-dynamic";

export default async function manifest(): Promise<MetadataRoute.Manifest> {
    const locale = await getLocale();
    const appLocale = isAppLocale(locale) ? locale : defaultLocale;
    const tMetadata = await getTranslations({ locale: appLocale, namespace: "metadata" });

    return {
        name: siteMetadata.name,
        short_name: siteMetadata.shortName,
        description: tMetadata("siteDescription"),
        start_url: withLocalePrefix("/", appLocale),
        scope: "/",
        display: "standalone",
        background_color: siteMetadata.backgroundColor,
        theme_color: siteMetadata.themeColor,
        icons: [
            {
                src: "/icon.png",
                sizes: "512x512",
                type: "image/png",
            },
            {
                src: "/apple-icon.png",
                sizes: "180x180",
                type: "image/png",
            },
        ],
    };
}
