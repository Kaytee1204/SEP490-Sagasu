export default function ClaimAnswerSummary({ answers = [] }) {
  if (!answers.length) return null;
  const groups = Object.entries(answers.reduce((result, answer) => {
    (result[answer.sectionTitle] ||= []).push(answer);
    return result;
  }, {}));
  return <section className="claim-panel" aria-label="Câu trả lời claim"><h2>Câu trả lời đã gửi</h2>
    {groups.map(([title, entries]) => <section key={title} className="claim-answer-group"><h3>{title}</h3>
      <dl className="claim-answer-list">{entries.map(answer => <div key={answer.questionCode}>
        <dt>{answer.questionLabel}</dt><dd className="claim-prose">{answer.answerText}</dd>
      </div>)}</dl>
    </section>)}
  </section>;
}
