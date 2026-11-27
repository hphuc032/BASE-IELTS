"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { issueMessage, readingRequest, RequestError, type Edition, type Exercise, type ExerciseEdition,
  type Kind, type Paragraph, type Passage, type Question } from "./contract";
import { buttonStyle, emptyQuestion, ModesField, move, primaryStyle, QuestionFields, removeAt, replaceAt,
  TextField, VisibilityField } from "./fields";

type EditorState =
  | { kind: "reading"; input: Passage; etag: string; revision: number }
  | { kind: "exercises"; input: Exercise; etag: string; revision: number; passageId: string };

function passageInput(source: Passage): Passage {
  const { title, summary, instructions, topicId, visibility } = source;
  return { title, summary, instructions, topicId, visibility,
    paragraphs: source.paragraphs.map(({ paragraphId, heading, content, translations }) =>
      ({ paragraphId, heading, content, translations })) };
}

function exerciseInput(source: Exercise): Exercise {
  const { title, summary, visibility, passageVersionId, availableModes, examDurationSeconds } = source;
  return { title, summary, visibility, passageVersionId, availableModes, examDurationSeconds,
    groups: source.groups.map(({ instructions, questions }) => ({ instructions, questions: questions.map(question => {
      const { type, prompt, points, instructions, marking, explanation } = question;
      return { type, prompt, points, instructions, marking, explanation,
        ...(type === "MULTIPLE_CHOICE_SINGLE" && { options: question.options?.map(({ key, content }) => ({ key, content })) }),
        ...((type === "SHORT_ANSWER" || type === "SENTENCE_COMPLETION") && { maxWords: question.maxWords }),
        evidence: question.evidence?.map(({ paragraphId, locator }) => ({ paragraphId, locator })) };
    }) })) };
}

export default function DraftEditor({ kind, id, draftId }: { kind: Kind; id: string; draftId: string }) {
  const path = `${kind}/${encodeURIComponent(id)}/drafts/${encodeURIComponent(draftId)}`;
  const [draft, setDraft] = useState<EditorState | null>(null);
  const [latest, setLatest] = useState<EditorState | null>(null);
  const [issue, setIssue] = useState("");
  const [notice, setNotice] = useState("");
  const [stale, setStale] = useState(false);
  const [preview, setPreview] = useState(false);
  const [busy, setBusy] = useState(false);
  const activeSave = useRef<AbortController | null>(null);
  const denied = useRef(false);
  const comparison = useRef<AbortController | null>(null);
  const form = useRef<HTMLFormElement>(null);

  const clearOnDenial = useCallback((error: unknown) => {
    if (!(error instanceof RequestError) || ![401, 403].includes(error.status)) return false;
    denied.current = true;
    activeSave.current?.abort();
    comparison.current?.abort();
    setDraft(null);
    setLatest(null);
    setPreview(false);
    if (error.status === 401) window.location.replace("/account/expired");
    return true;
  }, []);

  const fetchDraft = useCallback(async (options: { method?: "PUT"; body?: object; etag?: string; signal?: AbortSignal }) => {
    const { data, etag } = await readingRequest<Edition<Passage> | ExerciseEdition>(path, options);
    if (!etag || !/^"[1-9][0-9]*"$/.test(etag)) throw new RequestError(503);
    if (data.stableId !== id || data.editionId !== draftId || data.state !== "DRAFT") throw new RequestError(404);
    if (kind === "reading" && "paragraphs" in data)
      return { kind, input: passageInput(data), etag, revision: data.revision } as const;
    if (kind === "exercises" && "passageId" in data)
      return { kind, input: exerciseInput(data), etag, revision: data.revision, passageId: data.passageId } as const;
    throw new RequestError(503);
  }, [draftId, id, kind, path]);

  useEffect(() => {
    let currentRead: AbortController | undefined;
    const channel = new BroadcastChannel("base-ielts-auth-change");
    function refresh(initial: boolean) {
      currentRead?.abort();
      const controller = new AbortController();
      currentRead = controller;
      void fetchDraft({ signal: controller.signal }).then(value => {
        if (controller.signal.aborted) return;
        denied.current = false;
        setIssue(previous => initial ? "" : previous);
        setDraft(previous => initial || !previous ? value : previous);
      }).catch(error => {
        if (controller.signal.aborted) return;
        if (clearOnDenial(error) || initial) setIssue(issueMessage(error));
      });
    }
    const revalidate = () => refresh(false);
    const authChanged = () => {
      denied.current = true;
      activeSave.current?.abort();
      comparison.current?.abort();
      setDraft(null);
      setLatest(null);
      setPreview(false);
      setNotice("");
      setStale(false);
      refresh(true);
    };
    channel.onmessage = authChanged;
    window.addEventListener("focus", revalidate);
    refresh(true);
    return () => {
      activeSave.current?.abort();
      comparison.current?.abort();
      currentRead?.abort();
      channel.close();
      window.removeEventListener("focus", revalidate);
    };
  }, [clearOnDenial, fetchDraft]);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!draft || activeSave.current || stale) return;
    const controller = new AbortController();
    activeSave.current = controller;
    setBusy(true);
    setIssue("");
    setNotice("");
    try {
      const result = await fetchDraft({ method: "PUT", body: draft.input, etag: draft.etag, signal: controller.signal });
      if (!denied.current && !controller.signal.aborted) {
        setDraft(result);
        setNotice("Draft saved.");
      }
    } catch (error) {
      if (controller.signal.aborted) return;
      clearOnDenial(error);
      if (error instanceof RequestError && error.status === 412) setStale(true);
      if (error instanceof RequestError && error.status === 400) {
        form.current?.querySelector<HTMLElement>("input, textarea, select")?.focus();
      }
      setIssue(issueMessage(error));
    } finally {
      if (activeSave.current === controller) activeSave.current = null;
      setBusy(false);
    }
  }

  async function compare() {
    if (comparison.current) return;
    const controller = new AbortController();
    comparison.current = controller;
    setBusy(true);
    try {
      const result = await fetchDraft({ signal: controller.signal });
      if (!denied.current && !controller.signal.aborted) setLatest(result);
    } catch (error) {
      if (controller.signal.aborted) return;
      clearOnDenial(error);
      setIssue(issueMessage(error));
    } finally {
      comparison.current = null;
      setBusy(false);
    }
  }

  return <main className="mx-auto grid max-w-4xl gap-5 px-4 py-8">
    <Link href={`/teacher/${kind}`} prefetch={false} className="underline">Back to {kind === "reading" ? "passages" : "exercises"}</Link>
    <h1 className="text-3xl font-semibold">{kind === "reading" ? "Passage draft" : "Exercise draft"}</h1>
    {issue && <p role="alert" className="rounded bg-amber-50 p-3 text-slate-900">{issue}</p>}
    <p aria-live="polite">{notice}</p>
    {!draft && !issue && <p role="status">Loading draft…</p>}
    {draft && <>
      <p>Revision {draft.revision}</p>
      {stale && <section className="grid gap-3 rounded border border-slate-300 p-4" aria-label="Revision conflict">
        <button className={buttonStyle} disabled={busy} onClick={compare}>Load latest revision for comparison</button>
        {latest && <>
          <p>Current saved revision {latest.revision}. Your edits are still in the form below.</p>
          <SafePreview draft={latest} />
          <button className={buttonStyle} disabled={busy} onClick={() => {
            if (!window.confirm("Discard your edits and load the latest saved draft?")) return;
            setDraft(latest); setLatest(null); setStale(false); setIssue("");
          }}>Use latest saved draft</button>
        </>}
      </section>}
      <button className={buttonStyle} aria-expanded={preview} onClick={() => setPreview(!preview)}>
        {preview ? "Hide preview" : "Preview unsaved draft"}
      </button>
      {preview && <SafePreview draft={draft} />}
      <form ref={form} onSubmit={save}>
        <fieldset className="grid gap-5" disabled={busy}>
          {draft.kind === "reading"
            ? <PassageFields value={draft.input} set={input => setDraft({ ...draft, input })} />
            : <ExerciseFields value={draft.input} passageId={draft.passageId} clearOnDenial={clearOnDenial}
                set={input => setDraft({ ...draft, input })} />}
          <button className={primaryStyle} disabled={stale} type="submit">{busy ? "Working…" : "Save draft"}</button>
        </fieldset>
      </form>
    </>}
  </main>;
}

function SafePreview({ draft }: { draft: EditorState }) {
  return <section className="grid gap-3 whitespace-pre-wrap rounded border border-slate-300 p-4" aria-label="Draft preview">
    <p className="text-sm font-medium">Draft preview</p>
    <h2 className="text-xl font-semibold">{draft.input.title}</h2>
    {draft.input.summary && <p>{draft.input.summary}</p>}
    {draft.kind === "reading" ? <>
      {draft.input.instructions && <p>{draft.input.instructions}</p>}
      {draft.input.paragraphs.map((paragraph, index) => <div key={paragraph.paragraphId ?? index}>
        {paragraph.heading && <h3 className="font-medium">{paragraph.heading}</h3>}
        <p>{paragraph.content}</p>
      </div>)}
    </> : draft.input.groups.map((group, index) => <div key={index} className="grid gap-2">
      {group.instructions && <p>{group.instructions}</p>}
      <ol className="list-decimal pl-5">{group.questions.map((q, position) => <li key={position}>
        <p>{q.prompt}</p>{q.instructions && <p>{q.instructions}</p>}
        {(q.options?.length ?? 0) > 0 && <ul className="list-disc pl-5">
          {q.options?.map((option, optionIndex) => <li key={optionIndex}>{option.content}</li>)}
        </ul>}
      </li>)}</ol>
    </div>)}
  </section>;
}

function PassageFields({ value, set }: { value: Passage; set: (value: Passage) => void }) {
  function update(index: number, paragraph: Paragraph) {
    set({ ...value, paragraphs: replaceAt(value.paragraphs, index, paragraph) });
  }
  return <>
    <TextField label="Title" value={value.title} set={title => set({ ...value, title })} required limit={160} />
    <TextField label="Summary" value={value.summary} set={summary => set({ ...value, summary })} multiline limit={1000} />
    <TextField label="Instructions" value={value.instructions} set={instructions => set({ ...value, instructions })}
      multiline limit={2000} />
    <TextField label="Topic ID" value={value.topicId} set={id => set({ ...value, topicId: id.trim() || null })} />
    <VisibilityField value={value.visibility} set={visibility => set({ ...value, visibility })} />
    {value.paragraphs.map((paragraph, index) => <fieldset key={paragraph.paragraphId ?? index}
      className="grid gap-3 rounded border border-slate-300 p-4">
      <legend className="px-1 font-medium">Paragraph {index + 1}</legend>
      <TextField label="Heading" value={paragraph.heading} set={heading => update(index, { ...paragraph, heading })} limit={160} />
      <TextField label="Paragraph text" value={paragraph.content} set={content => update(index, { ...paragraph, content })}
        multiline required limit={8000} />
      {paragraph.translations?.map((translation, position, translations) => <div key={position} className="grid gap-2">
        <TextField label="Translation language" value={translation.language} required set={language =>
          update(index, { ...paragraph, translations: replaceAt(translations, position, { ...translation, language }) })} />
        <TextField label="Translation text" value={translation.text} required multiline limit={8000} set={text =>
          update(index, { ...paragraph, translations: replaceAt(translations, position, { ...translation, text }) })} />
        <button type="button" className={buttonStyle} onClick={() =>
          update(index, { ...paragraph, translations: removeAt(translations, position) })}>Remove translation</button>
      </div>)}
      <div className="flex flex-wrap gap-2">
        <button className={buttonStyle} type="button" onClick={() =>
          update(index, { ...paragraph, translations: [...(paragraph.translations ?? []), { language: "", text: "" }] })}>
          Add translation
        </button>
        <button className={buttonStyle} type="button" disabled={index === 0}
          onClick={() => set({ ...value, paragraphs: move(value.paragraphs, index, -1) })}>Move paragraph {index + 1} up</button>
        <button className={buttonStyle} type="button" disabled={index === value.paragraphs.length - 1}
          onClick={() => set({ ...value, paragraphs: move(value.paragraphs, index, 1) })}>Move paragraph {index + 1} down</button>
        <button className={buttonStyle} type="button"
          onClick={() => set({ ...value, paragraphs: removeAt(value.paragraphs, index) })}>Remove paragraph {index + 1}</button>
      </div>
    </fieldset>)}
    <button type="button" className={buttonStyle}
      onClick={() => set({ ...value, paragraphs: [...value.paragraphs, { content: "" }] })}>Add paragraph</button>
  </>;
}

function ExerciseFields({ value, set, passageId, clearOnDenial }: {
  value: Exercise; set: (value: Exercise) => void; passageId: string; clearOnDenial: (error: unknown) => boolean;
}) {
  const [edition, setEdition] = useState<Edition<Passage> | null>(null);
  const [editionIssue, setEditionIssue] = useState("");
  const [targetPin, setTargetPin] = useState(value.passageVersionId);
  const questionCount = value.groups.reduce((sum, group) => sum + group.questions.length, 0);
  useEffect(() => {
    const controller = new AbortController();
    const path = `reading/${encodeURIComponent(passageId)}/editions/${encodeURIComponent(value.passageVersionId)}`;
    void readingRequest<Edition<Passage>>(path, { signal: controller.signal }).then(({ data }) => {
      if (data.stableId !== passageId || data.editionId !== value.passageVersionId) throw new RequestError(404);
      if (!controller.signal.aborted) {
        setEdition(data);
        setEditionIssue("");
      }
    }).catch(error => {
      if (!controller.signal.aborted && !clearOnDenial(error)) setEditionIssue(issueMessage(error));
    });
    return () => controller.abort();
  }, [passageId, value.passageVersionId, clearOnDenial]);

  const pinned = edition?.editionId === value.passageVersionId ? edition : null;
  function updateQuestion(group: number, index: number, question: Question) {
    const current = value.groups[group];
    set({ ...value, groups: replaceAt(value.groups, group, { ...current,
      questions: replaceAt(current.questions, index, question) }) });
  }
  return <>
    <TextField label="Title" value={value.title} set={title => set({ ...value, title })} required limit={160} />
    <TextField label="Summary" value={value.summary} set={summary => set({ ...value, summary })} multiline limit={1000} />
    <VisibilityField value={value.visibility} set={visibility => set({ ...value, visibility })} />
    <ModesField modes={value.availableModes} duration={value.examDurationSeconds}
      set={(availableModes, examDurationSeconds) => set({ ...value, availableModes, examDurationSeconds })} />
    <div className="grid gap-2 rounded border border-slate-300 p-4">
      <p>Pinned Passage edition <span className="break-all">{value.passageVersionId}</span></p>
      {pinned && <p>{pinned.title} · Revision {pinned.revision}{pinned.state === "DRAFT" && " · Bundle preparation only"}</p>}
      {editionIssue && <p role="status">{editionIssue}</p>}
      <TextField label="Replacement Passage edition ID" value={targetPin} set={setTargetPin} />
      <button className={buttonStyle} type="button" disabled={!targetPin.trim() || targetPin.trim() === value.passageVersionId}
        onClick={() => {
          if (!window.confirm("Change the pinned edition and clear all Evidence selections?")) return;
          setEdition(null);
          setEditionIssue("");
          set({ ...value, passageVersionId: targetPin.trim(), groups: value.groups.map(group => ({ ...group,
            questions: group.questions.map(q => ({ ...q, evidence: [] })) })) });
        }}>Change pinned edition</button>
    </div>
    {value.groups.map((group, index) => <fieldset key={index} className="grid gap-4 rounded border border-slate-300 p-4">
      <legend className="px-1 font-medium">Group {index + 1}</legend>
      <TextField label="Group instructions" value={group.instructions} multiline limit={2000}
        set={instructions => set({ ...value, groups: replaceAt(value.groups, index, { ...group, instructions }) })} />
      {group.questions.map((question, position) => <div key={position} className="grid gap-3 border-t border-slate-300 pt-4">
        <QuestionFields number={position + 1} question={question} paragraphs={pinned?.paragraphs ?? []}
          set={update => updateQuestion(index, position, update)} />
        <button type="button" className={buttonStyle} onClick={() => set({ ...value,
          groups: replaceAt(value.groups, index, { ...group, questions: removeAt(group.questions, position) }),
        })}>Remove question {position + 1}</button>
      </div>)}
      <button type="button" className={buttonStyle} disabled={questionCount >= 100} onClick={() =>
        set({ ...value, groups: replaceAt(value.groups, index, { ...group,
          questions: [...group.questions, emptyQuestion("MULTIPLE_CHOICE_SINGLE")] }) })}>Add question</button>
      <button type="button" className={buttonStyle} onClick={() =>
        set({ ...value, groups: removeAt(value.groups, index) })}>Remove group {index + 1}</button>
    </fieldset>)}
    <button type="button" className={buttonStyle} disabled={value.groups.length >= 20} onClick={() =>
      set({ ...value, groups: [...value.groups, { questions: [] }] })}>Add group</button>
  </>;
}
