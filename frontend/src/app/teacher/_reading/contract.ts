export type Mode = "LEARNING" | "EXAM";
export type Visibility = "PRIVATE" | "PUBLIC";
export const questionTypes = {
  MULTIPLE_CHOICE_SINGLE: "Multiple choice",
  TRUE_FALSE_NOT_GIVEN: "True / False / Not Given",
  YES_NO_NOT_GIVEN: "Yes / No / Not Given",
  SHORT_ANSWER: "Short answer",
  SENTENCE_COMPLETION: "Sentence completion",
} as const;
export type QuestionType = keyof typeof questionTypes;
export type Paragraph = {
  paragraphId?: string;
  heading?: string | null;
  content: string;
  translations?: { language: string; text: string }[];
};
export type Passage = {
  title: string;
  summary?: string | null;
  instructions?: string | null;
  topicId?: string | null;
  visibility: Visibility;
  paragraphs: Paragraph[];
};
export type Option = { key: string; content: string };
export type Marking = { correctOptionKey: string } | { value: string }
  | { acceptedAnswers: string[]; caseSensitive: boolean };
export type Question = {
  type: QuestionType;
  prompt: string;
  instructions?: string | null;
  points: number;
  options?: Option[];
  maxWords?: number;
  marking?: Marking | null;
  explanation?: string | null;
  evidence?: { paragraphId: string; locator?: string | null }[];
};
export type Exercise = {
  title: string;
  summary?: string | null;
  visibility: Visibility;
  passageVersionId: string;
  availableModes: Mode[];
  examDurationSeconds?: number | null;
  groups: { instructions?: string | null; questions: Question[] }[];
};
export type Edition<T> = T & {
  stableId: string;
  editionId: string;
  state: "DRAFT" | "FROZEN";
  revision: number;
};
export type ExerciseEdition = Edition<Exercise> & { passageId: string };
export type Kind = "reading" | "exercises";

export class RequestError extends Error {
  constructor(readonly status: number, readonly code?: string) {
    super(code || String(status));
  }
}

export async function readingRequest<T>(path: string, options: {
  method?: "POST" | "PUT";
  body?: object;
  etag?: string;
  signal?: AbortSignal;
} = {}): Promise<{ data: T; etag: string | null; page?: { totalPages: number } }> {
  const headers = new Headers();
  if (options.method) {
    const response = await fetch("/api/v1/auth/csrf?scope=AUTH", {
      cache: "no-store",
      signal: options.signal,
    });
    if (!response.ok) throw new RequestError(response.status);
    headers.set("X-CSRF-TOKEN", (await response.json()).data.token);
    headers.set("Content-Type", "application/json");
  }
  if (options.etag) headers.set("If-Match", options.etag);
  const response = await fetch("/api/v1/teacher/" + path, {
    method: options.method,
    headers,
    body: options.body && JSON.stringify(options.body),
    cache: "no-store",
    signal: options.signal,
  });
  if (!response.ok) {
    const envelope = await response.json().catch(() => null);
    throw new RequestError(response.status, envelope?.error?.code);
  }
  const envelope = await response.json();
  return { data: envelope.data, page: envelope.page, etag: response.headers.get("ETag") };
}

export function issueMessage(error: unknown): string {
  if (error instanceof RequestError) {
    if (error.status === 403) return "Your account does not have access to this authoring operation.";
    if (error.status === 404) return "The requested draft or edition is unavailable.";
    if (error.code === "PARAGRAPH_REFERENCED") return "A question still uses that paragraph as evidence.";
    if (error.status === 412) return "This draft changed since you opened it. Compare your edits with the latest saved draft.";
    if (error.status === 409) return "The draft state has changed or the requested identity already exists.";
    if (error.status === 413) return "The draft exceeds the request size limit.";
    if (error.code === "INVALID_EXAM_DURATION") return "Enter a whole Exam duration from 1 to 86,400 seconds.";
    if (error.status === 400) return "Check the draft fields and try again.";
  }
  return "The authoring service is temporarily unavailable.";
}
