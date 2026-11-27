import { expect, test } from "@playwright/test";
import { readFileSync, unlinkSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { execFileSync, spawn } from "node:child_process";

function fixturePsql(database: string): [string, string[]] {
  const fixtureRoot = process.env.BASE_TEST_WSL_FIXTURE_ROOT;
  const args = ["-XAtq", "-v", "ON_ERROR_STOP=1", "-U", "base_ielts_bootstrap", "-d", database];
  if (!fixtureRoot) {
    return ["docker.exe", ["compose", "exec", "-T", "postgres", "psql", ...args]];
  }
  const port = process.env.BASE_TEST_WSL_FIXTURE_PORT;
  if (!/^\/tmp\/base-ielts-pg1712-[A-Za-z0-9]{8}$/.test(fixtureRoot) || !port || !/^[1-9][0-9]{3,4}$/.test(port)) {
    throw new Error("Invalid isolated WSL PostgreSQL fixture.");
  }
  return ["wsl.exe", ["-d", "Ubuntu", "--", fixtureRoot + "/runtime/bin/psql",
    "-h", fixtureRoot + "/socket", "-p", port, ...args]];
}

test("registration, verification, secure profile and sign-out", async ({ page, context, browser }) => {
  const email = process.env.BASE_BROWSER_FIXTURE_EMAIL;
  const token = process.env.BASE_BROWSER_FIXTURE_TOKEN;
  if (!email || !token) throw new Error("Run the isolated browser fixture launcher.");
  const password = "local browser fixture acceptance only";
  await page.goto("/account/register");
  await page.getByLabel("Email address").fill("new-" + email);
  await page.getByLabel("Display name").fill("New learner");
  await page.getByRole("button", { name: "Register" }).click();
  await expect(page.getByRole("heading", { name: "Check your email" })).toBeVisible();
  const navigation = await page.goto("/auth/verify-email#" + token);
  expect(navigation?.headers()["referrer-policy"]).toBe("no-referrer");
  expect(navigation?.headers()["cache-control"]).toBe("no-store");
  expect(Boolean(new URL(page.url()).hash)).toBe(false);
  await page.getByLabel("Choose a password").fill(password);
  await page.getByRole("button", { name: "Verify email", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Email verified");
  expect((await context.cookies()).some(cookie => cookie.name.endsWith("-AUTH"))).toBe(false);
  await page.getByRole("link", { name: "Sign in" }).click();
  await page.getByLabel("Email address").fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByText(email, { exact: true })).toBeVisible();
  const auth = (await context.cookies()).find(cookie => cookie.name === "__Secure-BASEIELTS-AUTH");
  expect(auth?.secure).toBe(true);
  expect(auth?.httpOnly).toBe(true);
  expect(auth?.path).toBe("/api/v1");
  expect(auth?.sameSite).toBe("Lax");
  const tlsFixtures = process.env.BASE_ACCEPTANCE_TLS_DIR;
  if (tlsFixtures) {
    const untrusted = await context.request.get("/api/v1/health", {
      headers: { Host: "untrusted.invalid", "X-Forwarded-Host": "127.0.0.1:3443" },
    });
    expect(untrusted.status()).toBe(421);
    expect((await browser.newContext({ ignoreHTTPSErrors: true }).then(async anonymous => {
      try {
        return (await anonymous.request.get(new URL("/api/v1/users/me", page.url()).href, {
          headers: { Forwarded: "proto=https;host=untrusted.invalid", "X-Forwarded-Proto": "http" },
        })).status();
      } finally {
        await anonymous.close();
      }
    }))).toBe(401);
    const denied = await page.evaluate(async () => (await fetch("/api/v1/users/me", {
      method: "PATCH", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ displayName: "Unauthorized fixture write" }),
    })).status);
    expect(denied).toBe(403);

    writeFileSync(join(tlsFixtures, "drop-profile-response"), "");
    const outcome = await page.evaluate(async () => {
      const csrf = (await (await fetch("/api/v1/auth/csrf?scope=AUTH")).json()).data.token;
      try {
        await fetch("/api/v1/users/me", {
          method: "PATCH", headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf },
          body: JSON.stringify({ displayName: "Committed without a response" }),
        });
        return "received";
      } catch {
        return "disconnected";
      }
    });
    unlinkSync(join(tlsFixtures, "drop-profile-response"));
    expect(outcome).toBe("disconnected");
    expect(readFileSync(join(tlsFixtures, "dropped-profile-responses"), "utf8")).toContain("committed");
    await page.reload();
    await expect(page.getByLabel("Display name")).toHaveValue("Committed without a response");
  }
  await page.getByLabel("Display name").fill("Updated learner");
  await page.getByRole("button", { name: "Save profile" }).click();
  await expect(page.getByRole("status")).toContainText("Profile updated");
  if (process.env.BASE_BROWSER_MIXED_LOAD === "true") {
    const secondaryToken = process.env.BASE_BROWSER_SECOND_TOKEN;
    if (!secondaryToken) throw new Error("The independent login fixture is absent.");
    const secondary = await browser.newContext({ baseURL: new URL(page.url()).origin });
    const secondaryPage = await secondary.newPage();
    await secondaryPage.goto("/auth/verify-email#" + secondaryToken);
    await secondaryPage.getByLabel("Choose a password").fill(password);
    await secondaryPage.getByRole("button", { name: "Verify email", exact: true }).click();
    await expect(secondaryPage.getByRole("status")).toContainText("Email verified");
    const secondaryCsrf = await secondaryPage.evaluate(async () => {
      const response = await fetch("/api/v1/auth/csrf?scope=PREAUTH", { cache: "no-store" });
      if (!response.ok) throw new Error("Could not prepare the independent login.");
      return (await response.json()).data.token as string;
    });
    await page.bringToFront();

    const loadTask = page.evaluate(async () => {
      const deadline = performance.now() + 60_000;
      const profiles: { status: number; elapsed: number }[] = [];
      let capacityDenials = 0;
      async function profile() {
        let next = performance.now();
        while (performance.now() < deadline) {
          const start = performance.now();
          const response = await fetch("/api/v1/users/me", { cache: "no-store" });
          profiles.push({ status: response.status, elapsed: performance.now() - start });
          next += 200;
          await new Promise(resolve => setTimeout(resolve, Math.max(0, next - performance.now())));
        }
      }
      async function anonymous() {
        while (performance.now() < deadline) {
          const response = await fetch("/api/v1/auth/csrf?scope=PREAUTH", {
            cache: "no-store", credentials: "omit",
          });
          if (response.status === 429) capacityDenials++;
          await response.body?.cancel();
        }
      }
      await Promise.all([profile(), ...Array.from({ length: 5 }, anonymous)]);
      const sorted = profiles.map(sample => sample.elapsed).sort((a, b) => a - b);
      return {
        capacityDenials,
        successful: profiles.filter(sample => sample.status === 200).length,
        total: profiles.length,
        p95: sorted[Math.ceil(sorted.length * .95) - 1],
      };
    });
    const loginDuringLoad = (async () => {
      await new Promise(resolve => setTimeout(resolve, 3_000));
      const status = await secondaryPage.evaluate(async ({ email, password, csrf }) => {
        const response = await fetch("/api/v1/auth/login", {
          method: "POST",
          headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": csrf },
          body: JSON.stringify({ email, password }),
        });
        await response.body?.cancel();
        return response.status;
      }, { email: "load-" + email, password, csrf: secondaryCsrf });
      expect(status).toBe(200);
    })();
    const [load] = await Promise.all([loadTask, loginDuringLoad]);
    await secondary.close();
    expect(load.capacityDenials).toBeGreaterThan(0);
    expect(load.total).toBeGreaterThanOrEqual(285);
    expect(load.successful).toBe(load.total);
    expect(load.p95).toBeLessThanOrEqual(500);
  }
  if (tlsFixtures) {
    const oldPid = Number(process.env.BASE_ACCEPTANCE_BACKEND_PID);
    if (!Number.isSafeInteger(oldPid) || oldPid <= 0) throw new Error("Missing fixture process identity.");
    const closedIngress = join(tlsFixtures, "cutover-closed");
    const applicationName = process.env.BASE_ACCEPTANCE_APPLICATION_NAME;
    const fixtureDatabase = process.env.BASE_ACCEPTANCE_DATABASE;
    const graceful = process.env.BASE_ACCEPTANCE_GRACEFUL === "true";
    let interruptedWrite: Promise<number> | undefined;
    if (applicationName && fixtureDatabase) {
      if (!/^m1-cutover-[a-f0-9]{32}$/.test(applicationName) || !/^base_ielts_m1test_[0-9]+$/.test(fixtureDatabase)
          || !/^browser-[a-f0-9]{32}@identity-test\.invalid$/.test(email)) {
        throw new Error("Invalid isolated PostgreSQL fixture identity.");
      }
      const csrf = await page.evaluate(async () =>
        (await (await fetch("/api/v1/auth/csrf?scope=AUTH")).json()).data.token as string);
      const shutdown = graceful ? spawn(join(process.env.JAVA_HOME!, "bin", "java.exe"), [
        "--add-modules", "jdk.attach", "../scripts/FixtureShutdown.java", String(oldPid),
      ], { windowsHide: true }) : undefined;
      const [psqlCommand, psqlArgs] = fixturePsql(fixtureDatabase);
      const observer = spawn(psqlCommand, psqlArgs,
      { cwd: "..", windowsHide: true });
      try {
        if (shutdown) {
          await new Promise<void>((resolve, reject) => {
            let output = "";
            shutdown.stdout.on("data", part => {
              output += part.toString();
              if (output.includes("FIXTURE_READY")) resolve();
            });
            shutdown.once("error", reject);
            shutdown.once("exit", () => reject(new Error("Unable to attach to the fixture JVM.")));
          });
        }
        const atBarrier = new Promise<void>((resolve, reject) => {
          let notices = "";
          observer.stderr.on("data", part => {
            notices += part.toString();
            if (notices.includes("M1_ACTIVE_DATABASE_BARRIER")) {
              try {
                // Close admission and stop precisely while PostgreSQL is inside
                // the fixture's BEFORE UPDATE trigger, before the row can change.
                writeFileSync(closedIngress, "");
                if (shutdown) shutdown.stdin.end("\n");
                else process.kill(oldPid, "SIGKILL");
                resolve();
              } catch (error) {
                reject(error);
              }
            }
          });
          observer.once("error", reject);
          observer.once("exit", () => reject(new Error("Database barrier was not observed.")));
        });
        observer.stdin.end(`
DO $fixture$
BEGIN
    FOR attempt IN 1..200 LOOP
        PERFORM pg_stat_clear_snapshot();
        IF EXISTS (SELECT 1 FROM pg_stat_activity WHERE application_name = '${applicationName}'
                  AND wait_event = 'PgSleep') THEN
            RAISE NOTICE 'M1_ACTIVE_DATABASE_BARRIER';
            RETURN;
        END IF;
        PERFORM pg_sleep(0.02);
    END LOOP;
    RAISE EXCEPTION 'No active fixture transaction was observed';
END
$fixture$;
`);
        interruptedWrite = page.evaluate(async token => {
          try {
            return (await fetch("/api/v1/users/me", {
              method: "PATCH",
              headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": token },
              body: JSON.stringify({ displayName: "Interrupted fixture write" }),
            })).status;
          } catch {
            return 0;
          }
        }, csrf);
        await atBarrier;
      } finally {
        observer.kill();
      }
      const writeStatus = await interruptedWrite;
      if (graceful) expect(writeStatus).toBe(200);
      else expect(writeStatus).not.toBe(200);
      await expect.poll(() => {
        try { process.kill(oldPid, 0); return true; }
        catch { return false; }
      }, { timeout: 12_000 }).toBe(false);
      shutdown?.kill();
      await expect.poll(() => execFileSync(psqlCommand, psqlArgs, {
        cwd: "..", encoding: "utf8", windowsHide: true,
        input: `
SELECT (SELECT count(*) FROM pg_stat_activity WHERE application_name='${applicationName}') || '|' ||
       (SELECT display_name FROM identity.users WHERE canonical_email='${email}');
`,
      }).trim(), { timeout: 8_000 }).toBe(graceful ? "0|Interrupted fixture write" : "0|Updated learner");
    } else {
    writeFileSync(closedIngress, "");
    expect((await context.request.get("/api/v1/users/me")).status()).toBe(503);
    execFileSync("pwsh.exe", ["-NoProfile", "-Command",
      `$p=Get-Process -Id ${oldPid} -ErrorAction Stop; if($p.ProcessName -ne 'java'){throw 'Wrong fixture process'}; Stop-Process -InputObject $p; if(!$p.WaitForExit(8000)){throw 'Fixture process did not exit'}`],
      { windowsHide: true });
    }
    expect((await context.request.get("/api/v1/users/me")).status()).toBe(503);
    const replacement = spawn(join(process.env.JAVA_HOME!, "bin", "java.exe"), [
      "-jar", "../backend/target/identity-foundation-0.1.0-SNAPSHOT.jar", "--spring.profiles.active=dev",
    ], { windowsHide: true, stdio: "ignore" });
    try {
      // Readiness is observed privately. The ingress remains closed until the
      // prior process has exited and the replacement is listening.
      await expect.poll(async () => {
        try {
          return (await fetch("http://127.0.0.1:18083/api/v1/health")).status;
        } catch {
          return 503;
        }
      },
        { timeout: 30_000 }).toBe(200);
      expect((await context.request.get("/api/v1/users/me")).status()).toBe(503);
      unlinkSync(closedIngress);
      expect((await context.request.get("/api/v1/users/me")).status()).toBe(401);
      await page.goto("/account/login");
      await page.getByLabel("Email address").fill(email);
      await page.getByLabel("Password", { exact: true }).fill(password);
      await page.getByRole("button", { name: "Sign in", exact: true }).click();
      await expect(page.getByText(email, { exact: true })).toBeVisible();
      if (interruptedWrite) await expect(page.getByLabel("Display name"))
        .toHaveValue(graceful ? "Interrupted fixture write" : "Updated learner");
      await page.getByRole("button", { name: "Sign out" }).click();
      await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
      // Use a non-loopback hostname so Chromium's localhost Secure-cookie
      // exception cannot make this wire check appear to pass or fail.
      const wire = await browser.newContext({ ignoreHTTPSErrors: true });
      try {
        const wirePage = await wire.newPage();
        await wirePage.goto("https://identity.test:3443/account/login");
        await wirePage.getByLabel("Email address").fill(email);
        await wirePage.getByLabel("Password", { exact: true }).fill(password);
        const loginResponse = wirePage.waitForResponse(response =>
          response.url() === "https://identity.test:3443/api/v1/auth/login" && response.request().method() === "POST");
        await wirePage.getByRole("button", { name: "Sign in", exact: true }).click();
        const issued = await loginResponse;
        expect(issued.status()).toBe(200);
        const cookieHeader = (await issued.headerValue("set-cookie")) ?? "";
        expect(/__Secure-BASEIELTS-AUTH=[^;]+;.*Secure/i.test(cookieHeader)).toBe(true);
        expect((await wire.cookies()).some(cookie =>
          cookie.name === "__Secure-BASEIELTS-AUTH" && cookie.secure && cookie.domain === "identity.test")).toBe(true);
        await expect(wirePage.getByText(email, { exact: true })).toBeVisible();
        const cleartext = await wirePage.goto("http://identity.test:3080/api/v1/users/me");
        expect(cleartext?.status()).toBe(426);
        expect(await cleartext?.headerValue("set-cookie")).toBeNull();
        const wireLog = readFileSync(join(tlsFixtures, "cleartext-wire-observations"), "utf8");
        expect(wireLog.trim().split("\n").every(line => line === "no-auth-cookie")).toBe(true);
      } finally {
        await wire.close();
      }
    } finally {
      replacement.kill();
    }
    return;
  }
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  await page.goto("/account/profile");
  await expect(page.getByRole("heading", { name: "Your session expired" })).toBeVisible();
});
