export function isHiddenAssistantText(text: string): boolean {
  const content = text.trim();
  return content === "__THINKING__" || /^正在思考…?$/.test(content);
}
