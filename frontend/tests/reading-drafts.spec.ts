import { expect, test } from "@playwright/test";

test.use({ baseURL: "http://127.0.0.1:3337" });

const passageId = "7d923ea6-3db0-49fd-833e-a5c33e68d902";
const passageVersionId = "fc91279f-029c-4905-9c02-386c47939f00";
const exerciseId = "b5167699-7450-4d37-98f7-0188516993ed";
const exerciseVersionId = "187deb49-e012-4c85-968b-0a4e73a6a267";
const first = "7024e5f2-f90c-46bb-a008-94bafce167c5";
const second = "09321531-6040-4ef3-95a2-da6093fc2e25";

const passage = {
  stableId: passageId, editionId: passageVersionId, state: "DRAFT", revision: 1,
  title: "Coastal survey", visibility: "PRIVATE",
  paragraphs: [
    { paragraphId: first, position: 1, heading: "Coves", content: "A small cove." },
    { paragraphId: second, position: 2, heading: "Cliffs", content: "A tall cliff." },
  ],
};
const csrf = { data: { token: "fixture-csrf" } };
const noStore = { "Cache-Control": "no-store" };
const staffPath = "/api/v1/teacher";

test("reorders paragraph UUIDs and keeps edits through a revision conflict", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const api = `${staffPath}/reading/${passageId}/drafts/${passageVersionId}`;
  let saved = passage;
  let conflict = false;
  await page.route("**/api/v1/auth/csrf?scope=AUTH", route => route.fulfill({ json: csrf, headers: noStore }));
  await page.route("**" + api, async route => {
    const request = route.request();
    if (request.method() === "PUT") {
      expect(request.headers()["x-csrf-token"]).toBe("fixture-csrf");
      expect(request.headers()["if-match"]).toBe(`"${saved.revision}"`);
      const input = request.postDataJSON();
      expect(input.paragraphs.map((p: { paragraphId: string }) => p.paragraphId)).toEqual([second, first]);
      expect(input.paragraphs.every((p: object) => !("position" in p))).toBe(true);
      if (conflict) {
        saved = { ...saved, revision: saved.revision + 1, title: "Edited elsewhere" };
        return route.fulfill({ status: 412, json: { error: { code: "REVISION_MISMATCH" } }, headers: noStore });
      }
      saved = { ...saved, ...input, revision: saved.revision + 1 };
    }
    return route.fulfill({ json: { data: saved }, headers: { ...noStore, ETag: `"${saved.revision}"` } });
  });
  await page.goto(api.replace(staffPath, "/teacher"));
  const move = page.getByRole("button", { name: "Move paragraph 2 up" });
  await move.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("group", { name: "Paragraph 1" }).getByLabel("Paragraph text")).toHaveValue("A tall cliff.");
  await page.getByRole("button", { name: "Save draft" }).click();
  await expect(page.getByText("Draft saved.")).toBeVisible();
  await expect(page.getByText("Revision 2")).toBeVisible();

  conflict = true;
  await page.getByLabel("Title", { exact: true }).fill("Unsaved local title");
  await page.getByRole("button", { name: "Save draft" }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText("This draft changed");
  await page.getByRole("button", { name: "Load latest revision for comparison" }).click();
  await expect(page.getByText("Edited elsewhere")).toBeVisible();
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Unsaved local title");
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
});

test("loads Evidence from the exact pin, hides private preview data and clears it on denial", async ({ page }) => {
  const api = `${staffPath}/exercises/${exerciseId}/drafts/${exerciseVersionId}`;
  const pinnedApi = `${staffPath}/reading/${passageId}/editions/${passageVersionId}`;
  const question = {
    type: "SHORT_ANSWER", prompt: "Name the feature", points: 1, maxWords: 1,
    marking: { acceptedAnswers: ["private-answer"], caseSensitive: false },
    explanation: "private-explanation", evidence: [{ paragraphId: second }],
  };
  let revoked = false;
  await page.route("**" + pinnedApi, route => route.fulfill({ json: { data: passage }, headers: noStore }));
  await page.route("**" + api, route => {
    if (revoked) return route.fulfill({ status: 403, json: { error: { code: "FORBIDDEN" } }, headers: noStore });
    return route.fulfill({ headers: { ...noStore, ETag: '"1"' }, json: { data: {
      stableId: exerciseId, editionId: exerciseVersionId, state: "DRAFT", revision: 1,
      title: "Coastal questions", visibility: "PRIVATE", passageId, passageVersionId,
      availableModes: ["LEARNING", "EXAM"], examDurationSeconds: 60, groups: [{ questions: [question] }],
    } } });
  });
  await page.goto(api.replace(staffPath, "/teacher"));
  await expect(page.getByText(/Bundle preparation only/)).toBeVisible();
  await expect(page.getByLabel("Evidence paragraph 1")).toHaveValue(second);
  await expect(page.getByLabel("Private explanation")).toHaveValue("private-explanation");
  await page.getByRole("button", { name: "Preview unsaved draft" }).click();
  const preview = page.getByRole("region", { name: "Draft preview" });
  await expect(preview).toContainText("Name the feature");
  await expect(preview).not.toContainText(/private-answer|private-explanation/);

  await page.getByLabel("Available modes").selectOption("LEARNING");
  await expect(page.getByLabel("Exam duration (seconds)")).toHaveCount(0);
  await page.getByLabel("Available modes").selectOption("EXAM");
  await expect(page.getByLabel("Exam duration (seconds)")).toHaveValue("");

  revoked = true;
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(page.getByRole("main").getByRole("alert")).toContainText("does not have access");
  await expect(page.getByLabel("Private explanation")).toHaveCount(0);
  await expect(preview).toHaveCount(0);
});
