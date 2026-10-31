import { expect, test } from "./fixtures";

test.describe("public critical paths", () => {
  test("redirects the public root to login", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveURL(/\/login$/u);
  });

  test.describe("login", () => {
    test("renders the login route", async ({ page }) => {
      const response = await page.goto("/login");

      expect(response?.ok()).toBe(true);
      await expect(page.getByRole("img", { name: "Deckwise Demo" })).toBeVisible();
      await expect(page.locator('input[name="email"]')).toBeVisible();
      await expect(page.locator('input[name="code"]')).toBeVisible();
      await expect(page.getByRole("button", { name: /Send code|发送验证码/iu })).toBeVisible();
      await expect(page.getByRole("button", { name: /Sign in with email|使用邮箱登录/iu })).toBeVisible();
    });
  });

  test("exposes a no-store liveness contract", async ({ request }) => {
    const response = await request.get("/api/health");
    const body = await response.json();

    expect(response.ok()).toBe(true);
    expect(response.headers()["cache-control"]).toContain("no-store");
    expect(body).toMatchObject({ ok: true, publicConfig: { appPlatform: "web" } });
  });

});
