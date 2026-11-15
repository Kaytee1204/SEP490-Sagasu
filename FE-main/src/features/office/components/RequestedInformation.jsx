export default function RequestedInformation({ claim }) {
  const question = [...(claim.activities || [])].reverse().find(item => item.action === "REQUEST_INFO");
  if (!question?.reason) return null;
  return <div className="of-requested-information">
    <strong>Văn phòng cần làm rõ</strong>
    <p className="of-prose">{question.reason}</p>
  </div>;
}
