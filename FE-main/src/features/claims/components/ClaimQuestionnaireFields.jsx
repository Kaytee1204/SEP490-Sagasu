import { isQuestionVisible, questionnaireQuestions } from '../questionnaireUtils';
import { CalendarDays, Fingerprint } from 'lucide-react';

function LostDateField({ question, value, onChange }) {
  const precision = value?.precision === 'UNKNOWN' ? 'UNKNOWN' : 'EXACT';
  const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' });
  const labelId = `claim-lost-date-label-${question.code}`;
  return <div className="claim-question claim-lost-date" role="group" aria-labelledby={labelId}>
    <p id={labelId} className="claim-lost-date-label">Bạn có nhớ ngày mất đồ không? <span className="claim-required">*</span></p>
    <div className="claim-lost-date-layout">
      <div className="claim-date-options">
        <label><input type="radio" name={`claim-date-${question.code}`} checked={precision === 'EXACT'}
          onChange={() => onChange({ precision: 'EXACT', date: value?.date || '' })} />Nhớ ngày mất</label>
        <label><input type="radio" name={`claim-date-${question.code}`} checked={precision === 'UNKNOWN'}
          onChange={() => onChange({ precision: 'UNKNOWN' })} />Không nhớ ngày mất</label>
      </div>
      {precision === 'EXACT' && <label className="claim-date-picker"><span className="sr-only">Ngày mất (bắt buộc)</span>
        <input type="date" required max={today} value={value?.date || ''}
          onChange={event => onChange({ precision: 'EXACT', date: event.target.value })} />
      </label>}
    </div>
  </div>;
}

function ChoiceField({ question, value, onChange }) {
  const choice = value?.choice || '';
  const selected = question.options.find(option => option.value === choice);
  const details = Boolean(choice && (selected?.detailsRequired || question.allowDetails));
  return <fieldset className="claim-question"><legend>{question.label}{question.required && <span className="claim-required"> *</span>}</legend>
    {question.options.length <= 4 ? <div className="workflow-options">
      {question.options.map(option => <label key={option.value}>
        <input type="radio" name={`claim-${question.code}`} required={question.required}
          checked={choice === option.value} value={option.value}
          onChange={() => onChange({ choice: option.value, details: '' })} />
        {option.label}
      </label>)}
    </div> : <select required={question.required} value={choice} aria-label={question.choicePrompt || question.label}
      onChange={event => onChange({ choice: event.target.value, details: '' })}>
      <option value="">Chọn câu trả lời</option>
      {question.options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
    </select>}
    {details && <label>{question.inputType === 'CONTENTS' ? 'Liệt kê đồ bên trong' : 'Mô tả thêm'}{selected?.detailsRequired ? ' *' : ' (không bắt buộc)'}
      <textarea rows={2} maxLength={2000} required={Boolean(selected?.detailsRequired)} value={value?.details || ''}
        onChange={event => onChange({ choice, details: event.target.value })} /></label>}
  </fieldset>;
}

function QuestionField({ question, value, onChange }) {
  if (question.inputType === 'LOST_DATE') return <LostDateField question={question} value={value} onChange={onChange} />;
  if (['CHOICE', 'CONTENTS'].includes(question.inputType)) return <ChoiceField question={question} value={value} onChange={onChange} />;
  if (question.code === 'C2') return <label className="claim-location-field"><span>Địa điểm mất đồ <span className="claim-required">*</span></span>
    <input type="text" required={question.required} maxLength={2000} value={value || ''}
      placeholder="Nơi bạn có thể đã làm mất hoặc để quên đồ…" onChange={event => onChange(event.target.value)} />
  </label>;
  return <label className="claim-text-field"><span>{question.label}{question.required && <span className="claim-required"> *</span>}</span><textarea rows={2} required={question.required} maxLength={2000}
    value={value || ''} onChange={event => onChange(event.target.value)} /></label>;
}

export default function ClaimQuestionnaireFields({ questionnaire, answers, onAnswer, description, onDescription, disabled }) {
  const questions = questionnaireQuestions(questionnaire);
  const definitions = Object.fromEntries(questions.map(question => [question.code, question]));
  function render(question) {
    if (!isQuestionVisible(question, answers, definitions)) return null;
    if (question.inputType === 'DESCRIPTION') return <label key={question.code} className="claim-description-field"><span>Căn cứ nhận đồ <span className="claim-required">*</span></span>
      <textarea rows={3} required maxLength={5000} value={description} onChange={event => onDescription(event.target.value)}
        placeholder="Mô tả dấu hiệu riêng hoặc căn cứ cho thấy món đồ này là của bạn…" /></label>;
    if (question.inputType === 'EVIDENCE') return null;
    return <QuestionField key={question.code} question={question} value={answers[question.code]}
      onChange={value => onAnswer(question.code, value)} />;
  }
  return <fieldset className="claim-questionnaire" aria-label="Thông tin xác minh quyền sở hữu" disabled={disabled}>
    <section className="claim-submit-section claim-question-group" aria-label="Thông tin mất đồ">
      <header className="claim-submit-section-heading"><CalendarDays size={19} aria-hidden="true" /><h2>Thông tin mất đồ</h2></header>
      {questionnaire.commonQuestions.map(render)}</section>
    <section className="claim-submit-section claim-question-group" aria-label={`Đặc điểm ${questionnaire.categoryName}`}>
      <header className="claim-submit-section-heading"><Fingerprint size={19} aria-hidden="true" /><h2>Đặc điểm nhận dạng</h2><span>{questionnaire.categoryName}</span></header>
      {questionnaire.categoryQuestions.map(render)}</section>
  </fieldset>;
}
