import { isQuestionVisible, questionnaireQuestions } from '../questionnaireUtils';

function LostDateField({ question, value, onChange }) {
  const precision = value?.precision || 'EXACT';
  const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' });
  return <fieldset className="claim-question"><legend>{question.label} *</legend>
    <label>Mức độ nhớ thời gian<select value={precision} onChange={event => onChange(event.target.value === 'UNKNOWN'
      ? { precision: 'UNKNOWN' } : { ...value, precision: event.target.value })}>
      <option value="EXACT">Nhớ ngày mất</option><option value="APPROXIMATE">Nhớ gần đúng</option><option value="UNKNOWN">Không nhớ rõ</option>
    </select></label>
    {precision !== 'UNKNOWN' && <div className="claim-question-date">
      <label>Ngày mất *<input type="date" required max={today} value={value?.date || ''}
        onChange={event => onChange({ ...value, precision, date: event.target.value })} /></label>
      <label>Giờ mất (không bắt buộc)<input type="time" value={value?.time || ''}
        onChange={event => onChange({ ...value, precision, time: event.target.value })} /></label>
    </div>}
    <p className="claims-muted">Thời điểm mất đồ theo giờ Việt Nam; có thể chọn không nhớ rõ.</p>
  </fieldset>;
}

function ChoiceField({ question, value, onChange }) {
  const choice = value?.choice || '';
  const selected = question.options.find(option => option.value === choice);
  const details = question.inputType === 'CONTENTS' || selected?.detailsRequired || (choice && question.allowDetails);
  return <fieldset className="claim-question"><legend>{question.label}{question.required ? ' *' : ''}</legend>
    <label>{question.choicePrompt || 'Lựa chọn'}<select required={question.required} value={choice}
      onChange={event => onChange({ choice: event.target.value, details: '' })}>
      <option value="">Chọn câu trả lời</option>
      {question.options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
    </select></label>
    {details && <label>{question.inputType === 'CONTENTS' ? 'Liệt kê đồ bên trong' : 'Mô tả thêm'}{selected?.detailsRequired ? ' *' : ' (không bắt buộc)'}
      <textarea maxLength={2000} required={Boolean(selected?.detailsRequired)} value={value?.details || ''}
        onChange={event => onChange({ choice, details: event.target.value })} /></label>}
  </fieldset>;
}

function QuestionField({ question, value, onChange }) {
  if (question.inputType === 'LOST_DATE') return <LostDateField question={question} value={value} onChange={onChange} />;
  if (['CHOICE', 'CONTENTS'].includes(question.inputType)) return <ChoiceField question={question} value={value} onChange={onChange} />;
  return <label>{question.label}{question.required ? ' *' : ''}<textarea required={question.required} maxLength={2000}
    value={value || ''} onChange={event => onChange(event.target.value)} /></label>;
}

export default function ClaimQuestionnaireFields({ questionnaire, answers, onAnswer, description, onDescription, evidenceField, disabled }) {
  const questions = questionnaireQuestions(questionnaire);
  const definitions = Object.fromEntries(questions.map(question => [question.code, question]));
  function render(question) {
    if (!isQuestionVisible(question, answers, definitions)) return null;
    if (question.inputType === 'DESCRIPTION') return <label key={question.code}>{question.label} *
      <textarea required maxLength={5000} value={description} onChange={event => onDescription(event.target.value)}
        placeholder="Đặc điểm riêng, dấu khắc, chi tiết bên trong hoặc căn cứ khác giúp xác minh quyền sở hữu…" /></label>;
    if (question.inputType === 'EVIDENCE') return <section key={question.code} className="claim-question-evidence">
      <h3>{question.label}</h3>{evidenceField}</section>;
    return <QuestionField key={question.code} question={question} value={answers[question.code]}
      onChange={value => onAnswer(question.code, value)} />;
  }
  return <fieldset className="claim-questionnaire" disabled={disabled}>
    <section className="claim-question-group" aria-label="Câu hỏi chung"><h2>Thông tin mất đồ và căn cứ nhận đồ</h2>
      {questionnaire.commonQuestions.map(render)}</section>
    <section className="claim-question-group" aria-label={`Câu hỏi ${questionnaire.categoryName}`}>
      <h2>Đặc điểm {questionnaire.categoryName.toLowerCase()}</h2>
      <p className="claims-muted">Chỉ những câu hỏi phù hợp với loại đồ bạn chọn mới được hiển thị. Các ô có dấu * cần được trả lời.</p>
      <p className="claims-muted">Không nhập mật khẩu, PIN, OTP, CVV hoặc đầy đủ số thẻ ngân hàng.</p>
      {questionnaire.categoryQuestions.map(render)}</section>
  </fieldset>;
}
