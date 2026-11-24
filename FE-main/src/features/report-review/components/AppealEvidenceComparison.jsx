import { Scale } from "lucide-react";
import { Gallery } from "./Shared";

function indexedAnswers(answers = []) {
  const occurrences = new Map();
  return answers.map(answer => {
    const question = JSON.stringify([answer.section_title, answer.question_label]);
    const occurrence = (occurrences.get(question) || 0) + 1;
    occurrences.set(question, occurrence);
    return [`${question}:${occurrence}`, answer];
  });
}

export default function AppealEvidenceComparison({ current, approved }) {
  const candidates = [
    { label: "Người gửi tranh chấp", context: current },
    { label: "Người được chấp thuận", context: approved },
  ];
  const [appealAnswers, approvedAnswers] = candidates.map(({ context }) => new Map(indexedAnswers(context.answers)));
  const questions = new Map([...appealAnswers, ...approvedAnswers]);
  const rows = [...questions].map(([key, question]) => {
    const values = [appealAnswers, approvedAnswers].map(answers => answers.get(key)?.answer_text?.trim() || "");
    return { key, question, values };
  }).filter(row => row.values.some(Boolean));
  return <section className="rr-card rr-evidence-comparison" aria-labelledby="rr-evidence-heading">
    <div className="rr-evidence-heading">
      <span className="rr-case-icon"><Scale size={20} aria-hidden="true" /></span>
      <div>
        <h2 id="rr-evidence-heading">Đối chiếu hai hồ sơ nhận đồ</h2>
      </div>
    </div>
    <div className="rr-evidence-grid" role="table" aria-label="Đối chiếu căn cứ nhận đồ">
      <div className="rr-evidence-row rr-evidence-head" role="row">
        <div role="columnheader" className="rr-evidence-question">Nội dung đối chiếu</div>
        {candidates.map(({ label, context }) => {
          const account = context.participants?.find(person => person.id === context.claim?.claimant_id);
          const name = account?.full_name || context.claim?.claimant_name;
          return <div key={label} role="columnheader">
            <strong>{name || label}</strong>
            {name && <small className="rr-evidence-role">{label}</small>}
          </div>;
        })}
      </div>
      {[["description", "Căn cứ nhận đồ"], ["evidence_note", "Thông tin bổ sung"]].map(([key, label]) => (
        (current.claim?.[key] || approved.claim?.[key]) && <div key={key} className="rr-evidence-row" role="row">
          <div className="rr-evidence-question" role="rowheader">{label}</div>
          {candidates.map(({ label: heading, context }) => <div key={heading} role="cell">
            {context.claim?.[key]?.trim() || <span className="rr-muted">Chưa cung cấp</span>}
          </div>)}
        </div>
      ))}
      {rows.map(({key, question, values}) => <div key={key} className="rr-evidence-row" role="row">
        <div className="rr-evidence-question" role="rowheader">
          {question.section_title && <small>{question.section_title}</small>}
          {question.question_label}
        </div>
        {values.map((value, index) => <div key={candidates[index].label} role="cell">
          {value || <span className="rr-muted">Chưa trả lời</span>}
        </div>)}
      </div>)}
      <div className="rr-evidence-row" role="row">
        <div className="rr-evidence-question" role="rowheader">Ảnh bằng chứng</div>
        {candidates.map(({ label, context }) => <div key={label} role="cell">
          {context.claimEvidence?.length ? <Gallery images={context.claimEvidence} /> : <span className="rr-muted">Chưa đính kèm ảnh</span>}
        </div>)}
      </div>
      {candidates.some(({context}) => context.claimSupplements?.length) && <div className="rr-evidence-row" role="row">
        <div className="rr-evidence-question" role="rowheader">Bằng chứng bổ sung</div>
        {candidates.map(({label, context}) => <div key={label} role="cell">
          {context.claimSupplements?.length ? context.claimSupplements.map(entry => <div key={entry.claim_activity_id}>
            <p>{entry.reason}</p><Gallery images={entry.images} />
          </div>) : "Chưa bổ sung"}
        </div>)}
      </div>}
    </div>
  </section>;
}
