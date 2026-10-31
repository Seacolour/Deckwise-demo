export const DEFAULT_CODE_BLOCK_LANGUAGE = "python";
export const CODE_BLOCK_LANGUAGE_CLASS_REGEX = /(?:^|\s)language-([\w-]+)/;

export const normalizeCodeBlockLanguage = (language?: string | null) =>
  language?.trim().toLowerCase() ?? "";

export const hasExplicitCodeBlockLanguage = (language?: string | null) =>
  normalizeCodeBlockLanguage(language).length > 0;

export const getCodeBlockLanguageOrDefault = (language?: string | null) => {
  const normalizedLanguage = normalizeCodeBlockLanguage(language);
  return normalizedLanguage || DEFAULT_CODE_BLOCK_LANGUAGE;
};

export const extractCodeBlockLanguageFromClassName = (className?: string | null) =>
  className?.match(CODE_BLOCK_LANGUAGE_CLASS_REGEX)?.[1] ?? undefined;
