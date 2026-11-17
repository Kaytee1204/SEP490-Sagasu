// Turn grounded facts into readable sentences; never infer ownership or missing evidence.
const ITEM_PHRASES = new Map([
  ['Điện thoại', 'một chiếc điện thoại'], ['Laptop', 'một chiếc laptop'],
  ['Máy tính bảng', 'một chiếc máy tính bảng'], ['Tai nghe', 'tai nghe'],
  ['Chuột', 'một con chuột máy tính'], ['Bàn phím', 'một bàn phím'],
  ['Balo', 'một chiếc balo'], ['Túi xách', 'một chiếc túi xách'],
  ['Túi đeo chéo', 'một chiếc túi đeo chéo'], ['Ví', 'một chiếc ví'],
  ['Bình nước', 'một bình nước'], ['Hộp cơm', 'một hộp cơm'], ['Ô', 'một chiếc ô'],
  ['Thú bông', 'một con thú bông'], ['Đồ chơi', 'một món đồ chơi'],
  ['Quần áo', 'quần áo'], ['Mũ', 'một chiếc mũ'], ['Giày', 'giày'], ['Dép', 'dép'],
  ['Kính', 'một chiếc kính'], ['Đồng hồ', 'một chiếc đồng hồ'], ['Trang sức', 'trang sức'],
  ['Sách', 'một cuốn sách'], ['Vở', 'một cuốn vở'], ['Bút', 'một cây bút'], ['Hộp bút', 'một hộp bút'],
]);

const lowerFirst = value => value.charAt(0).toLocaleLowerCase('vi-VN') + value.slice(1);
const joinWords = values => values.length < 2 ? values.join('') : `${values.slice(0, -1).join(', ')} và ${values.at(-1)}`;
const pointsFor = (row, criterion) => [...new Set((row.cells || []).find(cell => cell.criterion === criterion)?.points || [])];

export function describeClaimSummary(row) {
  const identity = pointsFor(row, 'identity');
  const appearance = pointsFor(row, 'appearance');
  const accessories = pointsFor(row, 'accessories');
  if (!identity.length && !appearance.length && !accessories.length) return 'chưa có mô tả phù hợp để tóm tắt';

  const types = identity.filter(point => ITEM_PHRASES.has(point));
  const brands = identity.filter(point => !ITEM_PHRASES.has(point));
  let description = types.length === 1 ? ITEM_PHRASES.get(types[0]) : types.length > 1
    ? `món đồ được nhắc đến là ${types.map(lowerFirst).join(' hoặc ')}` : 'một món đồ';
  if (brands.length === 1) description += ` hãng ${brands[0]}`;
  else if (brands.length > 1) description += `, nhắc đến các hãng ${joinWords(brands)}`;

  const colors = appearance.filter(point => point.startsWith('Màu ')).map(point => point.slice(4));
  const materials = appearance.filter(point => point.startsWith('Chất liệu ')).map(point => point.slice(10));
  if (colors.length) description += ` màu ${joinWords(colors)}`;
  if (materials.length) description += `, chất liệu ${joinWords(materials)}`;
  const otherAppearance = appearance.filter(point => !point.startsWith('Màu ') && !point.startsWith('Chất liệu '));
  if (otherAppearance.length) description += `, có đặc điểm ${joinWords(otherAppearance.map(lowerFirst))}`;

  const accessoryColors = accessories.filter(point => point.startsWith('Màu ')).map(point => point.slice(4));
  const accessoryFacts = accessories.filter(point => !point.startsWith('Màu '));
  const absent = accessoryFacts.filter(point => point.startsWith('Không '));
  const present = accessoryFacts.filter(point => !point.startsWith('Không '));
  if (absent.length) description += `, ${joinWords(absent.map(lowerFirst))}`;
  if (present.includes('Có phụ kiện, chưa mô tả loại')) description += ', có phụ kiện nhưng chưa mô tả rõ loại';
  const namedAccessories = present.filter(point => point !== 'Có phụ kiện, chưa mô tả loại');
  if (namedAccessories.length) description += `, kèm ${joinWords(namedAccessories.map(lowerFirst))}`;
  if (accessoryColors.length) description += namedAccessories.length
    ? ` màu ${joinWords(accessoryColors)}` : `, phụ kiện được mô tả có màu ${joinWords(accessoryColors)}`;
  return `mô tả ${description}`;
}

export function claimSummaryClosing(rows) {
  const unambiguousTypes = rows.map(row => pointsFor(row, 'identity').filter(point => ITEM_PHRASES.has(point)))
    .filter(types => types.length === 1).flat();
  return new Set(unambiguousTypes).size > 1
    ? 'Các hồ sơ đang mô tả những loại đồ khác nhau; hãy đối chiếu với món đồ bạn đang giữ trước khi duyệt.'
    : 'Bấm tên từng người để đối chiếu hồ sơ gốc trước khi duyệt.';
}
