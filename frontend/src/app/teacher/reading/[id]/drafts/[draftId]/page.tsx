import DraftEditor from "../../../../_reading/draft-editor";

export default async function Page({ params }: { params: Promise<{ id: string; draftId: string }> }) {
  const { id, draftId } = await params;
  return <DraftEditor key={id + "/" + draftId} kind="reading" id={id} draftId={draftId} />;
}
