"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { issueMessage, readingRequest, RequestError, type Kind, type Mode } from "./contract";
import { buttonStyle, ModesField, primaryStyle, TextField } from "./fields";

type DraftSummary = { stableId: string; editionId?: string; title: string; slug?: string; state?: "DRAFT" | "FROZEN" };

export default function DraftList({ kind }: { kind: Kind }) {
  const router = useRouter();
  const [rows, setRows] = useState<DraftSummary[] | null>(null);
  const [issue, setIssue] = useState("");
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [title, setTitle] = useState("");
  const [slug, setSlug] = useState("");
  const [passageId, setPassageId] = useState("");
  const [passageVersionId, setPassageVersionId] = useState("");
  const [modes, setModes] = useState<Mode[]>(["LEARNING"]);
  const [duration, setDuration] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const pending = useRef<AbortController | null>(null);
  const exercises = kind === "exercises";

  useEffect(() => {
    let active: AbortController | undefined;
    const channel = new BroadcastChannel("base-ielts-auth-change");
    function load() {
      active?.abort();
      const controller = new AbortController();
      active = controller;
      const params = new URLSearchParams({ page: String(page), size: "20", status: "DRAFT" });
      if (query) params.set("q", query);
      void readingRequest<DraftSummary[]>(kind + "?" + params, { signal: controller.signal }).then(result => {
        if (controller.signal.aborted) return;
        setRows(result.data);
        setTotalPages(result.page?.totalPages ?? 0);
        setIssue("");
      }).catch(error => {
        if (controller.signal.aborted) return;
        setRows(null);
        if (error instanceof RequestError && error.status === 401) window.location.replace("/account/expired");
        setIssue(issueMessage(error));
      });
    }
    channel.onmessage = () => {
      pending.current?.abort();
      setRows(null);
      setTitle("");
      setSlug("");
      setPassageId("");
      setPassageVersionId("");
      setModes(["LEARNING"]);
      setDuration(null);
      load();
    };
    window.addEventListener("focus", load);
    load();
    return () => {
      active?.abort();
      channel.close();
      window.removeEventListener("focus", load);
    };
  }, [kind, page, query]);

  useEffect(() => () => pending.current?.abort(), []);

  async function create(event: FormEvent) {
    event.preventDefault();
    if (pending.current) return;
    const controller = new AbortController();
    pending.current = controller;
    setBusy(true);
    setIssue("");
    try {
      const body = { slug, title, ...(exercises && { passageId: passageId.trim(),
        passageVersionId: passageVersionId.trim(), availableModes: modes, examDurationSeconds: duration }) };
      const { data } = await readingRequest<DraftSummary>(kind, { method: "POST", body, signal: controller.signal });
      if (!controller.signal.aborted && data.editionId) router.push(draftLink(kind, data.stableId, data.editionId));
    } catch (error) {
      if (controller.signal.aborted) return;
      if (error instanceof RequestError && [401, 403].includes(error.status)) {
        setRows(null);
        if (error.status === 401) window.location.replace("/account/expired");
      }
      setIssue(issueMessage(error));
    } finally {
      if (pending.current === controller) pending.current = null;
      setBusy(false);
    }
  }

  return <main className="mx-auto grid max-w-4xl gap-5 px-4 py-8">
    <Link className="underline" prefetch={false} href={exercises ? "/teacher/reading" : "/teacher/exercises"}>
      {exercises ? "Passage drafts" : "Exercise drafts"}
    </Link>
    <h1 className="text-3xl font-semibold">{exercises ? "Exercise drafts" : "Passage drafts"}</h1>
    {issue && <p role="alert">{issue}</p>}
    {!rows && !issue && <p role="status">Loading drafts…</p>}
    {rows && <>
      <form className="flex flex-wrap items-end gap-2" onSubmit={event => {
        event.preventDefault(); setPage(0); setQuery(search.trim());
      }}>
        <TextField label="Search drafts" value={search} set={setSearch} limit={100} />
        <button className={buttonStyle} type="submit">Search</button>
      </form>
      {rows.length === 0 ? <p>No drafts found.</p> : <ul className="grid gap-2">
        {rows.map(row => <li className="rounded border border-slate-300 p-3" key={row.stableId}>
          {row.editionId ? <Link prefetch={false} className="underline"
            href={draftLink(kind, row.stableId, row.editionId)}>{row.title}</Link> : <p>{row.title}</p>}
          {row.slug && <p className="text-sm">{row.slug}</p>}
        </li>)}
      </ul>}
      {totalPages > 1 && <nav aria-label="Draft pages" className="flex items-center gap-3">
        <button className={buttonStyle} disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
        <p>Page {page + 1} of {totalPages}</p>
        <button className={buttonStyle} disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>Next</button>
      </nav>}
      <section className="grid gap-3 rounded border border-slate-300 p-4">
        <h2 className="text-xl font-semibold">New {exercises ? "exercise" : "passage"}</h2>
        <form onSubmit={create}>
          <fieldset disabled={busy} className="grid gap-3">
            <TextField label="Title" value={title} set={setTitle} limit={160} required />
            <TextField label="Slug" value={slug} set={setSlug} limit={80} required />
            {exercises && <>
              <TextField label="Passage ID" value={passageId} set={setPassageId} required />
              <TextField label="Exact Passage edition ID" value={passageVersionId} set={setPassageVersionId} required />
              <ModesField modes={modes} duration={duration} set={(nextModes, nextDuration) => {
                setModes(nextModes); setDuration(nextDuration);
              }} />
            </>}
            <button type="submit" className={primaryStyle}>{busy ? "Creating…" : "Create draft"}</button>
          </fieldset>
        </form>
      </section>
    </>}
  </main>;
}

function draftLink(kind: Kind, stableId: string, editionId: string) {
  return `/teacher/${kind}/${encodeURIComponent(stableId)}/drafts/${encodeURIComponent(editionId)}`;
}
