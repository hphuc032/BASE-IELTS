"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";

type View = "register" | "pending" | "login" | "profile" | "expired";
type Profile = { displayName: string; email: string };
const api = "/api/v1";

async function request(path: string, method = "GET", scope?: "AUTH" | "PREAUTH", data?: object) {
  const headers = new Headers();
  if (scope) {
    const tokenResponse = await fetch(api + "/auth/csrf?scope=" + scope, { cache: "no-store" });
    if (!tokenResponse.ok) return tokenResponse;
    headers.set("X-CSRF-TOKEN", (await tokenResponse.json()).data.token);
  }
  if (data) headers.set("Content-Type", "application/json");
  return fetch(api + path, { method, headers, cache: "no-store", body: data && JSON.stringify(data) });
}

export default function IdentityForm({ view }: { view: View }) {
  const router = useRouter();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const signingOut = useRef(false);
  useEffect(() => {
    if (view !== "profile") return;
    let active = true;
    async function refresh() {
      try {
        const response = await request("/users/me");
        if (!active || signingOut.current) return;
        if (response.status === 401) window.location.replace("/account/expired");
        else if (response.ok) setProfile((await response.json()).data);
        else setMessage("Your profile is temporarily unavailable.");
      } catch {
        if (active) setMessage("Your profile is temporarily unavailable.");
      }
    }
    const channel = new BroadcastChannel("base-ielts-auth-change");
    channel.onmessage = refresh;
    window.addEventListener("focus", refresh);
    void refresh();
    return () => { active = false; channel.close(); window.removeEventListener("focus", refresh); };
  }, [view]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const email = form.get("email");
    const displayName = form.get("displayName");
    const password = form.get("password");
    setBusy(true);
    setMessage("");
    try {
      const response = view === "profile"
        ? await request("/users/me", "PATCH", "AUTH", { displayName })
        : view === "register"
        ? await request("/auth/register", "POST", "PREAUTH", { email, displayName })
        : view === "pending"
        ? await request("/auth/resend-verification", "POST", "PREAUTH", { email })
        : await request("/auth/login", "POST", "PREAUTH", { email, password });
      if (response.ok) {
        if (view === "login") {
          notifyAuthChange();
          router.push("/account/profile");
        } else if (view === "register") {
          router.push("/account/pending");
        } else if (view === "profile") {
          setProfile((await response.json()).data);
          setMessage("Profile updated.");
        } else {
          setMessage("If verification is available, instructions will be sent.");
        }
      } else if (response.status === 401 && view === "profile") {
        window.location.replace("/account/expired");
      } else if (response.status === 401) {
        setMessage("Email or password is incorrect.");
      } else if (response.status === 429) {
        setMessage("Please wait before trying again.");
      } else {
        setMessage("Could not complete your request. Check your details and try again.");
      }
    } catch {
      setMessage("Service unavailable. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  async function logout() {
    signingOut.current = true;
    setBusy(true);
    try {
      const response = await request("/auth/logout", "POST", "AUTH");
      if (response.ok || response.status === 401) {
        notifyAuthChange();
        window.location.replace("/account/login");
      } else {
        signingOut.current = false;
        setMessage("Sign-out could not be confirmed. Please retry.");
      }
    } catch {
      signingOut.current = false;
      setMessage("Sign-out could not be confirmed. Please retry.");
    } finally {
      setBusy(false);
    }
  }

  const titles = { register: "Create your account", pending: "Check your email", login: "Sign in", profile: "Your profile", expired: "Your session expired" };
  const actions = { register: "Register", pending: "Resend instructions", login: "Sign in", profile: "Save profile", expired: "" };
  return (
    <section className="identity-card">
      <h1>{titles[view]}</h1>
      {view === "expired" ? <Link href="/account/login">Sign in again</Link> : (
        <>
          {view === "pending" && <p>Open the verification link to set your password. You can request another link below.</p>}
          {view === "profile" && profile && <p>{profile.email}</p>}
          {(view !== "profile" || profile) && (
            <form key={profile?.displayName ?? view} onSubmit={submit}>
              {view !== "profile" && <label>Email address<input name="email" type="email" autoComplete="email" required maxLength={254} /></label>}
              {(view === "register" || view === "profile") && <label>Display name<input name="displayName" required maxLength={120} autoComplete="nickname" defaultValue={profile?.displayName} /></label>}
              {view === "login" && <label>Password<input name="password" type="password" autoComplete="current-password" required /></label>}
              <button disabled={busy}>{actions[view]}</button>
            </form>
          )}
          {view === "profile" && <button type="button" onClick={logout} disabled={busy}>Sign out</button>}
          {view === "register" && <Link href="/account/login">Already have an account? Sign in</Link>}
          {view === "login" && <Link href="/account/register">Create an account</Link>}
          <p role="status">{message}</p>
        </>
      )}
    </section>
  );
}

function notifyAuthChange() {
  const channel = new BroadcastChannel("base-ielts-auth-change");
  channel.postMessage("changed");
  channel.close();
}
