import {
  defaultLocale,
  isAppLocale,
  LOCALE_COOKIE,
  resolveHtmlLang,
  type AppLocale,
} from "@/src/lib/i18n";
import { resolveLocaleFromLanguageTag } from "@/src/lib/i18n/acceptLanguage";

type GlobalErrorMessages = Pick<
  GlobalErrorCopy,
  "homeLabel" | "message" | "retryLabel" | "title"
>;

// Keep the root error boundary independent from the application message
// catalogs. Importing the eight full locale JSON files here makes them part of
// every route's client graph, even though this boundary needs only four labels.
const messagesByLocale: Record<AppLocale, GlobalErrorMessages> = {
  en: {
    homeLabel: "Back to home",
    message: "Please refresh the page. If the problem persists, contact support.",
    retryLabel: "Retry",
    title: "Something went wrong",
  },
  vi: {
    homeLabel: "Trở về nhà",
    message: "Vui lòng làm mới trang. Nếu sự cố vẫn tiếp diễn, hãy liên hệ với bộ phận hỗ trợ.",
    retryLabel: "Thử lại",
    title: "Đã xảy ra lỗi",
  },
  zh: {
    homeLabel: "返回首页",
    message: "请刷新页面；如果问题持续存在，请联系支持。",
    retryLabel: "重试",
    title: "出现了一些问题",
  },
  "zh-Hant": {
    homeLabel: "返回首頁",
    message: "請刷新頁面。如果問題仍然存在，請聯絡支援人員。",
    retryLabel: "重試",
    title: "出了點問題",
  },
  ja: {
    homeLabel: "ホームへ戻る",
    message: "ページを更新してください。問題が解決しない場合は、サポートにお問い合わせください。",
    retryLabel: "再試行",
    title: "何か問題が発生しました",
  },
  ko: {
    homeLabel: "홈으로 돌아가기",
    message: "페이지를 새로고침하세요. 문제가 지속되면 지원팀에 문의하세요.",
    retryLabel: "재시도",
    title: "문제가 발생했습니다.",
  },
  es: {
    homeLabel: "Volver al inicio",
    message: "Por favor actualice la página. Si el problema persiste, comuníquese con el soporte.",
    retryLabel: "Reintentar",
    title: "algo salió mal",
  },
  "pt-BR": {
    homeLabel: "Voltar ao início",
    message: "Atualize a página. Se o problema persistir, entre em contato com o suporte.",
    retryLabel: "Tentar novamente",
    title: "Algo deu errado",
  },
};

export type GlobalErrorCopy = {
  homeLabel: string;
  htmlLang: string;
  locale: AppLocale;
  message: string;
  retryLabel: string;
  title: string;
};

function parseLocaleCookie(cookie: string | null | undefined): AppLocale | null {
  if (!cookie) return null;

  for (const part of cookie.split(";")) {
    const [name, ...valueParts] = part.trim().split("=");
    if (name !== LOCALE_COOKIE) continue;
    const value = decodeURIComponent(valueParts.join("="));
    return isAppLocale(value) ? value : null;
  }

  return null;
}

export function resolveGlobalErrorLocale(input: {
  cookie?: string | null;
  languages?: readonly string[] | null;
}): AppLocale {
  const cookieLocale = parseLocaleCookie(input.cookie);
  if (cookieLocale) return cookieLocale;

  for (const language of input.languages ?? []) {
    const locale = resolveLocaleFromLanguageTag(language);
    if (locale) return locale;
  }

  return defaultLocale;
}

export function getGlobalErrorCopy(locale: AppLocale): GlobalErrorCopy {
  const messages = messagesByLocale[locale] ?? messagesByLocale[defaultLocale];

  return {
    homeLabel: messages.homeLabel,
    htmlLang: resolveHtmlLang(locale),
    locale,
    message: messages.message,
    retryLabel: messages.retryLabel,
    title: messages.title,
  };
}

export function getBrowserGlobalErrorCopy(): GlobalErrorCopy {
  const cookie = typeof document === "undefined" ? null : document.cookie;
  const languages = typeof navigator === "undefined" ? [] : navigator.languages;
  return getGlobalErrorCopy(resolveGlobalErrorLocale({ cookie, languages }));
}
