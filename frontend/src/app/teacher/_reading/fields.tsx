"use client";

import { useId, type ReactNode } from "react";
import { questionTypes, type Mode, type Paragraph, type Question, type QuestionType, type Visibility } from "./contract";

const inputStyle = "min-h-11 w-full rounded border border-slate-400 bg-white px-3 py-2 text-slate-900";
export const buttonStyle = "min-h-11 rounded border border-slate-400 px-3 py-2 font-medium disabled:opacity-50";
export const primaryStyle = "min-h-11 rounded bg-slate-900 px-4 py-2 font-medium text-white disabled:opacity-50";

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="grid gap-1 text-sm font-medium">{label}{children}</label>;
}

export function TextField({ label, value, set, required, multiline, limit }: {
  label: string; value?: string | null; set: (value: string) => void;
  required?: boolean; multiline?: boolean; limit?: number;
}) {
  const props = {
    className: inputStyle, value: value ?? "", required,
    onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      if (!limit || Array.from(event.target.value).length <= limit) set(event.target.value);
    },
  };
  return <Field label={label}>{multiline ? <textarea {...props} rows={3} /> : <input {...props} />}</Field>;
}

export function VisibilityField({ value, set }: { value: Visibility; set: (value: Visibility) => void }) {
  return <Field label="Visibility"><select value={value} onChange={e => set(e.target.value as Visibility)} className={inputStyle}>
    <option value="PRIVATE">Private</option><option value="PUBLIC">Public</option>
  </select></Field>;
}

export function ModesField({ modes, duration, set }: {
  modes: Mode[]; duration?: number | null;
  set: (modes: Mode[], duration: number | null) => void;
}) {
  const selected = modes.length === 2 ? "both" : modes[0];
  return <>
    <Field label="Available modes"><select className={inputStyle} value={selected} onChange={event => {
      const value = event.target.value;
      set(value === "both" ? ["LEARNING", "EXAM"] : [value as Mode], value === "LEARNING" ? null : duration ?? null);
    }}>
      <option value="LEARNING">Learning</option><option value="EXAM">Exam</option>
      <option value="both">Learning and Exam</option>
    </select></Field>
    {modes.includes("EXAM") && <Field label="Exam duration (seconds)">
      <input className={inputStyle} type="number" min={1} max={86400} step={1} required value={duration ?? ""}
        onChange={e => set(modes, e.target.value === "" ? null : e.target.valueAsNumber)} />
    </Field>}
  </>;
}

export function replaceAt<T>(items: T[], index: number, value: T): T[] {
  return items.map((item, position) => position === index ? value : item);
}

export function removeAt<T>(items: T[], index: number): T[] {
  return items.filter((_, position) => position !== index);
}

export function move<T>(items: T[], index: number, offset: number): T[] {
  const result = [...items];
  [result[index], result[index + offset]] = [result[index + offset], result[index]];
  return result;
}

export function emptyQuestion(type: QuestionType): Question {
  return {
    type, prompt: "", points: 1,
    ...(type === "MULTIPLE_CHOICE_SINGLE" && { options: [{ key: "a", content: "" }, { key: "b", content: "" }] }),
    ...((type === "SHORT_ANSWER" || type === "SENTENCE_COMPLETION") && { maxWords: 1 }),
  };
}

export function QuestionFields({ question, set, paragraphs, number }: {
  question: Question; set: (question: Question) => void; paragraphs: Paragraph[]; number: number;
}) {
  const typeId = useId();
  const choice = question.type === "MULTIPLE_CHOICE_SINGLE";
  const text = question.type === "SHORT_ANSWER" || question.type === "SENTENCE_COMPLETION";
  const marking = question.marking;
  const options = question.options ?? [];
  const evidence = question.evidence ?? [];
  const answers = marking && "acceptedAnswers" in marking ? marking : null;

  return <div className="grid gap-3">
    <label htmlFor={typeId} className="text-sm font-medium">Question {number} type</label>
    <select id={typeId} className={inputStyle} value={question.type} onChange={e => {
      const type = e.target.value as QuestionType;
      if (!window.confirm("Change question type and clear its current answer fields?")) return;
      const { prompt, instructions, points, explanation, evidence } = question;
      set({ ...emptyQuestion(type), prompt, instructions, points, explanation, evidence });
    }}>
      {Object.entries(questionTypes).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
    </select>
    <TextField label="Question prompt" value={question.prompt} set={prompt => set({ ...question, prompt })}
      multiline required limit={2000} />
    {question.type === "SENTENCE_COMPLETION" && <p className="text-sm">Use one blank written as ___ in the prompt.</p>}
    <TextField label="Question instructions" value={question.instructions} set={instructions => set({ ...question, instructions })}
      multiline limit={2000} />
    <Field label="Points"><input className={inputStyle} type="number" required min=".01" max="100" step=".01"
      value={Number.isNaN(question.points) ? "" : question.points}
      onChange={e => set({ ...question, points: e.target.valueAsNumber })} /></Field>

    {choice && <>
      {options.map((option, index) => <div key={index} className="grid gap-2 sm:grid-cols-[1fr_2fr_auto] sm:items-end">
        <TextField label={`Option ${index + 1} key`} value={option.key} required limit={24}
          set={key => {
            const update = marking && "correctOptionKey" in marking && marking.correctOptionKey === option.key;
            set({ ...question, options: replaceAt(options, index, { ...option, key }),
              marking: update ? { correctOptionKey: key } : marking });
          }} />
        <TextField label={`Option ${index + 1} text`} value={option.content} limit={1000} required
          set={content => set({ ...question, options: replaceAt(options, index, { ...option, content }) })} />
        <button type="button" className={buttonStyle} disabled={options.length <= 2} onClick={() => {
          const update = marking && "correctOptionKey" in marking && marking.correctOptionKey === option.key;
          set({ ...question, options: removeAt(options, index), marking: update ? null : marking });
        }}>Remove option {index + 1}</button>
      </div>)}
      <button className={buttonStyle} type="button" disabled={options.length >= 6}
        onClick={() => set({ ...question, options: [...options, { key: "", content: "" }] })}>Add option</button>
      <Field label="Correct option"><select className={inputStyle}
        value={marking && "correctOptionKey" in marking ? marking.correctOptionKey : ""}
        onChange={e => set({ ...question, marking: e.target.value ? { correctOptionKey: e.target.value } : null })}>
        <option value="">Unset</option>
        {options.map((option, index) => option.key && <option key={index} value={option.key}>{option.key}</option>)}
      </select></Field>
    </>}

    {!choice && !text && <Field label="Correct classification"><select className={inputStyle}
      value={marking && "value" in marking ? marking.value : ""}
      onChange={e => set({ ...question, marking: e.target.value ? { value: e.target.value } : null })}>
      <option value="">Unset</option>
      {(question.type === "TRUE_FALSE_NOT_GIVEN" ? ["TRUE", "FALSE"] : ["YES", "NO"]).concat("NOT_GIVEN")
        .map(value => <option key={value} value={value}>{value.replaceAll("_", " ")}</option>)}
    </select></Field>}

    {text && <>
      <Field label="Maximum words"><input className={inputStyle} type="number" required min={1} max={50} step={1}
        value={question.maxWords ?? ""} onChange={e => set({ ...question,
          maxWords: e.target.value ? e.target.valueAsNumber : undefined })} /></Field>
      <TextField label="Accepted answers (one per line)" value={answers?.acceptedAnswers.join("\n") ?? ""}
        set={value => set({ ...question, marking: value ? {
          acceptedAnswers: value.split("\n"), caseSensitive: answers?.caseSensitive ?? false,
        } : null })} multiline />
      <label className="flex min-h-11 items-center gap-2"><input type="checkbox" checked={answers?.caseSensitive ?? false}
        disabled={!answers} onChange={e => answers && set({ ...question, marking: { ...answers, caseSensitive: e.target.checked } })} />
        Match letter case</label>
    </>}

    <TextField label="Private explanation" value={question.explanation} set={explanation => set({ ...question, explanation })}
      multiline limit={4000} />
    {evidence.map((item, index) => <div key={index} className="grid gap-2 rounded border border-slate-300 p-3">
      <Field label={`Evidence paragraph ${index + 1}`}><select className={inputStyle} required value={item.paragraphId}
        onChange={e => set({ ...question, evidence: replaceAt(evidence, index, { ...item, paragraphId: e.target.value }) })}>
        <option value="">Choose paragraph</option>
        {paragraphs.map((paragraph, position) => <option key={paragraph.paragraphId} value={paragraph.paragraphId}>
          {position + 1}. {paragraph.heading || paragraph.content.slice(0, 90)}
        </option>)}
      </select></Field>
      <TextField label="Evidence locator" value={item.locator} limit={240}
        set={locator => set({ ...question, evidence: replaceAt(evidence, index, { ...item, locator }) })} />
      <button type="button" className={buttonStyle}
        onClick={() => set({ ...question, evidence: removeAt(evidence, index) })}>Remove evidence {index + 1}</button>
    </div>)}
    <button type="button" className={buttonStyle} disabled={!paragraphs.length}
      onClick={() => set({ ...question, evidence: [...evidence, { paragraphId: "" }] })}>Add evidence</button>
  </div>;
}
