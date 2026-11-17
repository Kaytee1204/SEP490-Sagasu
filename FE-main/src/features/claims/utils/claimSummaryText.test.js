import { test } from 'node:test';
import assert from 'node:assert/strict';
import { claimSummaryClosing, describeClaimSummary } from './claimSummaryText.js';

const row = (identity = [], appearance = [], accessories = []) => ({ cells: [
  { criterion: 'identity', points: identity }, { criterion: 'appearance', points: appearance },
  { criterion: 'accessories', points: accessories },
] });

test('writes a sentence regardless of the order selected by Gemini', () => {
  assert.equal(describeClaimSummary(row(['Xiaomi', 'Điện thoại'], ['Màu trắng', 'Màu xanh (chưa rõ sắc độ)'])),
    'mô tả một chiếc điện thoại hãng Xiaomi màu trắng và xanh (chưa rõ sắc độ)');
});
test('keeps the color of an accessory separate from the color of the item', () => {
  assert.equal(describeClaimSummary(row(['Điện thoại', 'Samsung'], ['Màu đen'], ['Ốp bảo vệ', 'Màu hồng'])),
    'mô tả một chiếc điện thoại hãng Samsung màu đen, kèm ốp bảo vệ màu hồng');
});
test('preserves explicit negative answers without claiming that an omitted answer means no accessories', () => {
  assert.equal(describeClaimSummary(row(['Bình nước'])), 'mô tả một bình nước');
  assert.equal(describeClaimSummary(row(['Điện thoại'], [], ['Không dùng ốp / phụ kiện gắn máy'])),
    'mô tả một chiếc điện thoại, không dùng ốp / phụ kiện gắn máy');
});
test('does not turn missing or filtered facts into an ownership conclusion', () => {
  assert.equal(describeClaimSummary(row()), 'chưa có mô tả phù hợp để tóm tắt');
  assert.equal(claimSummaryClosing([row(), row()]), 'Bấm tên từng người để đối chiếu hồ sơ gốc trước khi duyệt.');
});
test('retains materials and accessory uncertainty', () => {
  assert.equal(describeClaimSummary(row(['Ví'], ['Màu nâu', 'Chất liệu da'], ['Có phụ kiện, chưa mô tả loại'])),
    'mô tả một chiếc ví màu nâu, chất liệu da, có phụ kiện nhưng chưa mô tả rõ loại');
});
test('does not pick one of multiple mentioned types or brands', () => {
  assert.equal(describeClaimSummary(row(['Điện thoại', 'Máy tính bảng'], [], [])),
    'mô tả món đồ được nhắc đến là điện thoại hoặc máy tính bảng');
  assert.equal(describeClaimSummary(row(['Samsung', 'Xiaomi'])),
    'mô tả một món đồ, nhắc đến các hãng Samsung và Xiaomi');
});
test('only highlights conflicting item types when separate dossiers each identify a type', () => {
  assert.match(claimSummaryClosing([row(['Bình nước']), row(['Ô']), row(['Điện thoại', 'Xiaomi'])]), /những loại đồ khác nhau/u);
  assert.doesNotMatch(claimSummaryClosing([row(['Điện thoại']), row(['Điện thoại', 'Samsung'])]), /loại đồ khác nhau/u);
  assert.doesNotMatch(claimSummaryClosing([row(['Điện thoại', 'Máy tính bảng']), row(['Điện thoại'])]), /loại đồ khác nhau/u);
});
test('never mixes facts between dossiers or mutates the input', () => {
  const phone = row(['Điện thoại', 'Xiaomi'], ['Màu đen']);
  const bottle = row(['Bình nước'], ['Màu hồng']);
  const original = JSON.stringify([phone, bottle]);
  assert.doesNotMatch(describeClaimSummary(phone), /hồng|bình nước/u);
  assert.doesNotMatch(describeClaimSummary(bottle), /Xiaomi|đen/u);
  assert.equal(JSON.stringify([phone, bottle]), original);
});
