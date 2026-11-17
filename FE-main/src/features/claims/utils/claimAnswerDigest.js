import { describeClaimSummary } from './claimSummaryText.js';

// Questionnaire codes are shared with the detail page. Original answers never enter the AI request.
const TOPICS = {
  PHONE: ['loại/hãng điện thoại', 'ngoại hình thân máy', 'ốp/phụ kiện', 'hình nền/màn hình khóa', 'nhà mạng', 'serial/bằng chứng sở hữu'],
  LAPTOP: ['loại/hãng laptop', 'ngoại hình máy', 'túi đựng máy', 'túi và đồ bên trong', 'phụ kiện đi kèm', 'serial/bằng chứng sở hữu'],
  ELECTRONICS: ['loại thiết bị', 'hãng/dòng máy/màu', 'túi hoặc hộp đựng', 'túi/hộp và đồ bên trong', 'vỏ/bao/ốp', 'dây/đầu nối/dấu riêng', 'serial/bằng chứng sở hữu'],
  PERSONAL: ['loại món đồ', 'thương hiệu và màu sắc', 'chất liệu, kích thước hoặc dung tích', 'nắp, quai, ống hút hoặc hình dán', 'hộp đựng hoặc phụ kiện tai nghe', 'dấu sử dụng hoặc dấu nhận dạng riêng'],
  BAG: ['loại túi', 'hãng/logo', 'màu/họa tiết/chất liệu', 'đồ bên trong túi', 'ngăn/khóa kéo/dấu riêng'],
  BOTTLE: ['loại bình', 'màu/họa tiết', 'chất liệu/dung tích', 'nắp/quai/ống hút', 'tên/hình dán/dấu riêng', 'phụ kiện đi kèm'],
  WALLET: ['loại ví hoặc giấy tờ', 'màu/chất liệu/hãng ví', 'đồ bên trong ví', 'thông tin trên giấy tờ', 'thông tin giấy tờ', 'đặc điểm giấy tờ', 'mã giấy tờ/thẻ'],
  ID_CARD: ['loại giấy tờ/thẻ', 'họ tên trên giấy tờ', 'đơn vị cấp', 'đặc điểm giấy tờ', 'mã giấy tờ/thẻ', 'bao thẻ/dây đeo'],
  KEYS: ['loại chìa khóa', 'số chìa/remote', 'móc/vòng/dây đeo', 'đặc điểm móc/vòng/dây', 'hãng xe', 'chữ khắc trên chìa'],
  CLOTHING: ['loại trang phục', 'hãng/logo', 'màu/họa tiết', 'cỡ trang phục', 'chất liệu', 'nhãn/dấu riêng/vật trong túi'],
  FOOTWEAR: ['loại giày/dép', 'hãng/logo', 'màu/họa tiết', 'cỡ giày/dép', 'chất liệu', 'số chiếc và dấu riêng'],
  ACCESSORY: ['loại phụ kiện', 'hãng/kiểu dáng', 'màu/chất liệu', 'loại kính', 'hộp/phụ kiện đựng', 'đá/hạt trang trí', 'chữ khắc/dấu riêng'],
  EYEWEAR: ['loại kính', 'hãng/logo', 'màu/kiểu gọng và tròng', 'chất liệu gọng', 'hộp/túi/khăn lau', 'chữ khắc/dấu riêng'],
  STATIONERY: ['loại đồ dùng', 'tên sách/môn học/hãng', 'màu/họa tiết', 'tên/lớp/ký hiệu', 'ghi chú/dấu sử dụng', 'đồ trong hộp/túi'],
  CHILDREN: ['loại món đồ', 'hãng/nhãn hiệu', 'màu/hình dáng', 'nhãn/dấu sửa/dấu riêng', 'phụ kiện đi kèm'],
  OTHER: ['loại món đồ', 'màu/hình dáng/kích thước', 'hãng/logo/nhãn', 'vỏ/túi/phụ kiện'],
};
const PRIVATE = new Set(['PHONE:Q6', 'LAPTOP:Q6', 'ELECTRONICS:Q7', 'WALLET:Q4', 'WALLET:Q5', 'WALLET:Q6', 'WALLET:Q7', 'ID_CARD:Q2', 'ID_CARD:Q3', 'ID_CARD:Q4', 'ID_CARD:Q5']);
const CHOICE_ABSENCE = new Map([
  ['PHONE:Q3', 'không dùng ốp hoặc phụ kiện gắn máy'], ['LAPTOP:Q3', 'máy không ở trong túi khi mất'],
  ['LAPTOP:Q5', 'không có bao hoặc phụ kiện đi kèm'], ['ELECTRONICS:Q3', 'thiết bị không ở trong túi/hộp khi mất'],
  ['ELECTRONICS:Q5', 'không có vỏ/bao/ốp bảo vệ'], ['PERSONAL:Q5', 'không có hộp/phụ kiện tai nghe'],
  ['BOTTLE:Q6', 'không có phụ kiện đi kèm'], ['ACCESSORY:Q5', 'không có hộp/phụ kiện đựng'],
  ['ACCESSORY:Q6', 'không có đá/hạt trang trí'], ['EYEWEAR:Q5', 'không có hộp/túi/khăn lau'],
  ['KEYS:Q3', 'không có móc/vòng/dây đeo'], ['CHILDREN:Q5', 'không có phụ kiện đi kèm'],
  ['OTHER:Q4', 'không có vỏ/túi/phụ kiện'], ['ID_CARD:Q6', 'không có bao thẻ/dây đeo'],
]);
const normalize = text => text.toLowerCase().normalize('NFD').replace(/\p{M}/gu, '').replaceAll('đ', 'd')
  .replace(/\bkho+ng\b/gu, 'khong').replace(/\bko\b/gu, 'khong').replace(/\s+/gu, ' ').trim();
const clean = text => String(text || '').replace(/\s*\(giờ Việt Nam\)/giu, '').trim();
const join = parts => parts.length < 2 ? parts.join('') : `${parts.slice(0, -1).join(', ')} và ${parts.at(-1)}`;
const privateText = text => /\b(?:cccd|cmnd|cvv|cvc|pin|otp|imei|serial|password|mat khau|ma bao mat|so the)\b|(?:\d[\s.-]*){9,}|https?:\/\//iu.test(normalize(text));
const quote = text => {
  const compact = clean(text).replace(/\s+/gu, ' ');
  return `“${compact.length > 140 ? `${compact.slice(0, 137).replace(/\s+\S*$/u, '')}…` : compact}”`;
};
function state(text) {
  const normalized = normalize(text);
  if (!normalized) return 'MISSING';
  if (normalized === 'khong ap dung') return 'NOT_APPLICABLE';
  if (/^(?:khong|chua) (?:nho|ro|biet)|^khong chac/u.test(normalized)) return 'UNKNOWN';
  if (/^(?:khong(?: co)?|chua co|none|no)[.!]?$/u.test(normalized)) return 'NONE';
  return 'VALUE';
}
function identityText(text) {
  const lines = clean(text).split(/\r?\n/u).map(line => line.trim()).filter(Boolean);
  return lines.length > 1 && /khác/iu.test(lines[0]) ? lines.slice(1).join(' ') : lines.join(' ');
}

/** All stored answers, including optional ones; never substitute the creation date or public post location. */
export function summarizeClaimAnswers(claim, selectedFacts = null) {
  const answers = (claim.answers || []).filter(answer => clean(answer.answerText));
  const common = code => answers.find(answer => answer.templateCode === 'COMMON' && answer.questionCode === code)?.answerText;
  const date = clean(common('C1')), location = clean(common('C2'));
  const sentences = [];
  let context = '';
  if (date) context = state(date) === 'UNKNOWN' ? 'không nhớ rõ thời điểm mất'
    : state(date) === 'VALUE' ? `cho biết mất đồ ${date.replace(/^Ngày /u, 'ngày ').replace(/^Khoảng ngày /u, 'khoảng ngày ')}` : '';
  if (location && !privateText(location)) {
    if (state(location) === 'VALUE') context += context && state(date) === 'VALUE'
      ? ` tại ${quote(location)}` : `${context ? '; ' : ''}nơi mất được khai là ${quote(location)}`;
    else if (state(location) === 'UNKNOWN') context += `${context ? ' và ' : ''}chưa nhớ rõ nơi mất`;
  }
  if (context) sentences.push(context);

  const unknown = [], notStated = [], details = [], negatives = [];
  let identity = '', hasPrivate = Boolean(location && privateText(location));
  for (const answer of answers.filter(answer => answer.templateCode !== 'COMMON')) {
    const text = clean(answer.answerText), key = `${answer.templateCode}:${answer.questionCode}`;
    const topic = (TOPICS[answer.templateCode]?.[Number(answer.questionCode.slice(1)) - 1] || answer.questionLabel || 'đặc điểm món đồ').replaceAll('/', ' hoặc ');
    const answerState = state(text);
    if (answerState === 'NOT_APPLICABLE') continue;
    if (answerState === 'UNKNOWN') { unknown.push(topic); continue; }
    if (answerState === 'NONE') {
      if (CHOICE_ABSENCE.has(key)) negatives.push(CHOICE_ABSENCE.get(key));
      else notStated.push(topic);
      continue;
    }
    if (PRIVATE.has(key) || privateText(text)) { hasPrivate = true; continue; }
    if (answer.questionCode === 'Q1' && !identity) identity = identityText(text);
    else details.push(`${topic} được khai là ${quote(text.replace(/^Có\s*\n/iu, ''))}`);
  }
  if (identity) sentences.push(`món đồ được mô tả là ${quote(identity)}${details.length ? `; ${details.join('; ')}` : ''}`);
  else if (details.length) sentences.push(`các đặc điểm đã khai gồm ${details.join('; ')}`);
  else if (selectedFacts && !answers.some(answer => answer.templateCode !== 'COMMON')) {
    const fallback = describeClaimSummary(selectedFacts);
    if (!fallback.startsWith('chưa có')) sentences.push(fallback);
  }
  const gaps = [];
  if (unknown.length) gaps.push(`chưa làm rõ ${join(unknown)}`);
  if (notStated.length) gaps.push(`chưa nêu ${join(notStated)}`);
  if (gaps.length) sentences.push(join(gaps));
  if (negatives.length) sentences.push(join(negatives));
  const grounds = clean(claim.description || common('C3'));
  if (grounds) {
    if (state(grounds) === 'VALUE' && !privateText(grounds)) sentences.push(`căn cứ nhận đồ được khai là ${quote(grounds)}`);
    else if (state(grounds) !== 'VALUE') sentences.push('chưa nêu căn cứ nhận đồ cụ thể');
    else hasPrivate = true;
  }
  if (Array.isArray(claim.evidence)) sentences.push(claim.evidence.length
    ? `có ${claim.evidence.length} ảnh bằng chứng đính kèm` : 'không đính kèm ảnh bằng chứng');
  if (hasPrivate) sentences.push('thông tin riêng được giữ trong hồ sơ gốc để đối chiếu');
  if (!sentences.length) return 'chưa có câu trả lời phù hợp để tóm tắt';
  return sentences.map((sentence, i) => i ? sentence.charAt(0).toLocaleUpperCase('vi-VN') + sentence.slice(1) : sentence).join('. ');
}
