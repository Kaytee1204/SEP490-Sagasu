import { test } from 'node:test';
import assert from 'node:assert/strict';
import { summarizeClaimAnswers } from './claimAnswerDigest.js';

const answer = (templateCode, questionCode, answerText, extra = {}) => ({ templateCode, questionCode, answerText, ...extra });
test('summarizes the same full answer set as the detail page, including optional uncertain answers', () => {
  const result = summarizeClaimAnswers({ answers: [
    answer('COMMON', 'C1', 'Ngày 09/11/2026 (giờ Việt Nam)'), answer('COMMON', 'C2', 'Khu H'),
    answer('PERSONAL', 'Q1', 'Đồ cá nhân khác\nđiện thoại ximi xanh trắng'), answer('PERSONAL', 'Q2', 'Ximi'),
    answer('PERSONAL', 'Q3', 'khoong rõ', { required: false }), answer('PERSONAL', 'Q6', 'khoong', { required: false }),
  ], description: 'Vỏ có dấu xước gần camera', evidence: [] });
  for (const expected of ['ngày 09/11/2026', 'tại “Khu H”', 'điện thoại ximi xanh trắng', 'Ximi',
    'Chưa làm rõ chất liệu, kích thước hoặc dung tích', 'chưa nêu dấu sử dụng', 'Vỏ có dấu xước gần camera', 'không đính kèm ảnh'])
    assert.ok(result.toLocaleLowerCase('vi-VN').includes(expected.toLocaleLowerCase('vi-VN')), expected);
  assert.doesNotMatch(result, /giờ Việt Nam|Đồ cá nhân khác/u);
});
test('does not substitute submission time, public post location or normalized AI tokens for original answers', () => {
  const result = summarizeClaimAnswers({ createdAt: '2026-11-17', locationText: 'PUBLIC LOCATION',
    answers: [answer('COMMON', 'C1', 'Không nhớ rõ thời điểm mất'), answer('COMMON', 'C2', 'Không nhớ'),
      answer('PHONE', 'Q1', 'Sony Xperia 10 VI')], evidence: [{ id: 1 }, { id: 2 }] },
  { cells: [{ criterion: 'identity', points: ['Samsung'] }] });
  assert.match(result, /không nhớ rõ thời điểm mất/u);
  assert.match(result, /Sony Xperia 10 VI/u); assert.match(result, /2 ảnh/u);
  assert.doesNotMatch(result, /2026-11-17|PUBLIC LOCATION|Samsung/u);
});
test('distinguishes a negative choice from an undescribed feature', () => {
  const result = summarizeClaimAnswers({ answers: [answer('PHONE', 'Q3', 'Không'), answer('PERSONAL', 'Q4', 'không có')] });
  assert.match(result, /không dùng ốp hoặc phụ kiện gắn máy/iu);
  assert.match(result, /chưa nêu nắp, quai, ống hút/u);
  assert.doesNotMatch(result, /không có phụ kiện đi kèm/u);
});
test('does not render credentials or identity numbers from any answer or ownership grounds', () => {
  const result = summarizeClaimAnswers({ answers: [answer('ID_CARD', 'Q2', 'Nguyễn Văn Bí Mật'),
    answer('ID_CARD', 'Q5', '001122334455'), answer('OTHER', 'Q2', 'CVV 123'),
    answer('COMMON', 'C2', 'CCCD 001122334455')], description: 'mật khẩu 123456', evidence: [] });
  assert.doesNotMatch(result, /Nguyễn|001122334455|123456|CVV 123/u);
  assert.match(result, /thông tin riêng được giữ trong hồ sơ gốc/iu);
});
test('preserves non-sensitive distinctive evidence that is intentionally not sent to Gemini', () => {
  const result = summarizeClaimAnswers({ answers: [answer('BAG', 'Q4', 'Một cuốn sách bìa xanh'),
    answer('BAG', 'Q5', 'Khóa kéo bên trái bị sứt một răng')] });
  assert.match(result, /Một cuốn sách bìa xanh/u);
  assert.match(result, /Khóa kéo bên trái bị sứt một răng/u);
});
test('does not invent hidden branches or confuse not applicable with unknown', () => {
  const result = summarizeClaimAnswers({ answers: [answer('PERSONAL', 'Q1', 'Ô'), answer('PERSONAL', 'Q3', 'Không áp dụng')] });
  assert.doesNotMatch(result, /nắp|quai|ống hút|không nhớ|Chưa làm rõ/u);
});
test('keeps approximate dates approximate and marks shortened source excerpts', () => {
  const result = summarizeClaimAnswers({ answers: [answer('COMMON', 'C1', 'Khoảng ngày 08/11/2026, lúc 14:00 (giờ Việt Nam)'),
    answer('OTHER', 'Q2', 'màu xanh '.repeat(50))] });
  assert.match(result, /khoảng ngày 08\/11\/2026, lúc 14:00/u);
  assert.match(result, /…/u); assert.ok(result.length < 350);
});
test('falls back for legacy dossiers but does not declare missing evidence when it was not loaded', () => {
  const result = summarizeClaimAnswers({ answers: [] }, { cells: [{ criterion: 'identity', points: ['Bình nước'] }] });
  assert.equal(result, 'mô tả một bình nước');
  assert.equal(summarizeClaimAnswers({}), 'chưa có câu trả lời phù hợp để tóm tắt');
});
