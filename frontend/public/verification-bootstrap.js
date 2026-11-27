(() => {
  let secret = window.location.hash.slice(1);
  history.replaceState(null, "", window.location.pathname);
  if (!/^[A-Za-z0-9_-]{43}$/.test(secret)) secret = "";
  let clear;
  window.addEventListener("pagehide", () => { secret = ""; clear?.(); });
  window.addEventListener("pageshow", event => { if (event.persisted) { secret = ""; clear?.(); } });
  document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("verification");
    const status = document.getElementById("result");
    const submit = form.querySelector("button");
    clear = () => { form.hidden = true; form.reset(); };
    if (!secret) {
      status.textContent = "Reopen the verification link from your email or request a new link.";
      return;
    }
    form.hidden = false;
    form.addEventListener("submit", async event => {
      event.preventDefault();
      if (!secret || submit.disabled) return;
      submit.disabled = true;
      try {
        const csrf = await fetch("/api/v1/auth/csrf?scope=PREAUTH", { cache: "no-store" });
        if (!csrf.ok || !secret) throw new Error();
        const response = await fetch("/api/v1/auth/verify-email", {
          method: "POST",
          cache: "no-store",
          headers: { "Content-Type": "application/json", "X-CSRF-TOKEN": (await csrf.json()).data.token },
          body: JSON.stringify({ token: secret, newPassword: new FormData(form).get("password") }),
        });
        if (response.ok) {
          secret = "";
          clear();
          status.textContent = "Email verified. You can now sign in.";
        } else if (response.status === 403) {
          status.textContent = "The form session expired. Select Verify email again to retry.";
        } else {
          status.textContent = "Verification failed. Check your password or request a fresh link.";
        }
      } catch {
        status.textContent = "Verification is temporarily unavailable. Please try again.";
      } finally {
        submit.disabled = false;
      }
    });
  }, { once: true });
})();
