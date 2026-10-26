// src/data/mockData.js
// Centralized mock data for all pages.
// When backend is ready, services in src/services/ will replace these with API calls.
// DO NOT import this file directly in components — use src/services/ instead.

import { Package, CheckCircle, AlertTriangle, Ban, FileText, Clock, Star } from 'lucide-react';

// ─────────────────────────────────────────────
// AUTH / CURRENT USER
// ─────────────────────────────────────────────
export const CURRENT_USER = {
  id: 'USR-ADMIN',
  name: 'Nguyễn Văn An',
  email: 'an.nguyen@gmail.com',
  phone: '0912 345 678',
  job: 'Kỹ sư Phần mềm',
  address: 'Hai Bà Trưng, Hà Nội',
  joinDate: 'Tháng 9, 2023',
  verified: true,
  twoFA: true,
  avatar: 'https://api.dicebear.com/7.x/initials/svg?seed=NVA&backgroundColor=1e6b6b&textColor=ffffff',
  stats: [
    { label: 'Tin đã đăng', value: '12', sub: 'tổng cộng', icon: FileText },
    { label: 'Đã trao trả', value: '7', sub: 'thành công', icon: CheckCircle },
    { label: 'Đang xử lý', value: '3', sub: 'chờ xác minh', icon: Clock },
    { label: 'Điểm uy tín', value: '4.9', sub: '/ 5.0', icon: Star },
  ],
};

// ─────────────────────────────────────────────
// ADMIN DASHBOARD STATS
// ─────────────────────────────────────────────
export const MOCK_STATS = [
  { id: 1, title: 'Tổng tin đăng', value: '1,842', change: '+12.5%', isUp: true, icon: Package, color: 'var(--teal-600)', bg: 'rgba(30,107,107,0.1)' },
  { id: 2, title: 'Đã trao trả thành công', value: '1,420', change: '+18.2%', isUp: true, icon: CheckCircle, color: '#10b981', bg: 'rgba(16,185,129,0.1)' },
  { id: 3, title: 'Báo cáo vi phạm', value: '15', change: '5 ca khẩn', isUp: false, icon: AlertTriangle, color: '#ef4444', bg: 'rgba(239,68,68,0.1)' },
  { id: 4, title: 'Tài khoản bị khóa', value: '24', change: '+2 tuần này', isUp: false, icon: Ban, color: '#dc2626', bg: 'rgba(220,38,38,0.1)' },
];

// ─────────────────────────────────────────────
// ITEMS (Admin & public)
// ─────────────────────────────────────────────
export const INITIAL_ITEMS = [
  {
    id: 'TD-9021',
    title: 'Ví da nam Pedro & CCCD',
    type: 'lost',
    category: 'Giấy tờ & Ví',
    reporter: 'Trần Văn Bình',
    reporterPhone: '0912 345 456',
    location: 'Bãi xe H1, ĐH Bách Khoa Hà Nội',
    date: '10 phút trước',
    status: 'active',
    aiScore: 98,
    img: 'https://images.unsplash.com/photo-1627123424574-724758594913?w=300&q=80',
    desc: 'Bên trong có 1 thẻ CCCD mang tên Trần Văn Bình, 2 thẻ ngân hàng Techcombank và tiền mặt.',
  },
  {
    id: 'TD-9020',
    title: 'MacBook Pro M2 Space Gray 14"',
    type: 'found',
    category: 'Thiết bị điện tử',
    reporter: 'Lê Minh Cường',
    reporterPhone: '0988 567 112',
    location: 'Bàn số 42 tầng 3 Thư viện Tạ Quang Bửu',
    date: '35 phút trước',
    status: 'matched',
    aiScore: 99,
    img: 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=300&q=80',
    desc: 'Nhặt được ở bàn 42, máy có dán sticker Github ở mặt lưng. Đã bàn giao quầy thủ thư.',
  },
  {
    id: 'TD-9019',
    title: 'Chìa khóa smartkey Honda SH',
    type: 'found',
    category: 'Chìa khóa',
    reporter: 'Bác Nguyễn Thành (Bảo vệ)',
    reporterPhone: '0903 234 789',
    location: 'Sảnh tòa Landmark 81, TP.HCM',
    date: '1 giờ trước',
    status: 'active',
    aiScore: null,
    img: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=300&q=80',
    desc: 'Có móc khóa hình gấu bông màu nâu, chìa 3 nút điều khiển.',
  },
  {
    id: 'TD-9018',
    title: 'Điện thoại iPhone 15 Pro Titan',
    type: 'lost',
    category: 'Thiết bị điện tử',
    reporter: 'Phạm Thu Trang',
    reporterPhone: '0977 888 654',
    location: 'Tuyến Bus 32 (Nguyễn Trãi - Cầu Giấy)',
    date: '2 giờ trước',
    status: 'active',
    aiScore: 95,
    img: 'https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=300&q=80',
    desc: 'Ốp lưng trong suốt có dán ảnh polaroid, màn hình có mật khẩu 6 số.',
  },
];

// ─────────────────────────────────────────────
// VIOLATION REPORTS (Admin)
// ─────────────────────────────────────────────
export const INITIAL_REPORTS = [
  {
    id: 'REP-701',
    claimId: 'YC-401',
    itemId: 'TD-9020',
    itemName: 'MacBook Pro M2 Space Gray 14"',
    itemImg: 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=300&q=80',
    reportType: 'fraud_claim',
    typeLabel: 'Nghi vấn mạo nhận',
    severity: 'high',
    reporter: 'Lê Minh Cường (Người nhặt được)',
    reportedUser: 'Hoàng Văn X (Tài khoản nghi vấn)',
    reportedUserPhone: '0933 444 888',
    reportedUserId: 'USR-882',
    reason: 'Người này liên tục gửi 4 yêu cầu nhận máy với các câu trả lời số seri khác nhau để thử vận may và gửi tin nhắn quấy rối đe dọa ép giao máy.',
    evidence: 'Log hệ thống ghi nhận 4 lần trả lời sai câu hỏi bí mật + ảnh chụp tin nhắn ép giao máy.',
    time: '15 phút trước',
    status: 'pending',
  },
  {
    id: 'REP-702',
    claimId: 'YC-402',
    itemId: 'TD-9018',
    itemName: 'Điện thoại iPhone 15 Pro Titan',
    itemImg: 'https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=300&q=80',
    reportType: 'extortion',
    typeLabel: 'Đòi tiền chuộc bất hợp pháp',
    severity: 'critical',
    reporter: 'Phạm Thu Trang (Chủ sở hữu mất đồ)',
    reportedUser: 'Nguyễn T. (Người giữ đồ)',
    reportedUserPhone: '0905 444 123',
    reportedUserId: 'USR-904',
    reason: 'Người nhặt ép chủ nhân phải chuyển khoản trước 5 triệu tiền "chuộc máy" mới trả.',
    evidence: 'Ảnh chụp màn hình tin nhắn Zalo kèm số tài khoản ngân hàng yêu cầu chuyển tiền chuộc.',
    time: '1 giờ trước',
    status: 'pending',
  },
];

// ─────────────────────────────────────────────
// USERS (Admin)
// ─────────────────────────────────────────────
export const USERS_LIST = [
  { id: 'USR-101', name: 'Nguyễn Văn An', email: 'an.nguyen@example.com', trustScore: 98, returnsCount: 5, verified: true, role: 'Thành viên Tích Cực', status: 'Hoạt động' },
  { id: 'USR-102', name: 'Trần Văn Bình', email: 'binh.tran@example.com', trustScore: 92, returnsCount: 1, verified: true, role: 'Thành viên', status: 'Hoạt động' },
  { id: 'USR-105', name: 'Tài khoản nghi vấn #882', email: 'spam_bot88@tempmail.com', trustScore: 24, returnsCount: 0, verified: false, role: 'Cảnh báo', status: 'Đã tạm khóa' },
];

// ─────────────────────────────────────────────
// HOMEPAGE items (public listing)
// ─────────────────────────────────────────────
export const HOME_ITEMS = [
  { id: 1, status: 'lost', category: 'Giấy tờ & Ví', title: 'Ví da nam Pedro kèm CCCD', desc: 'Rơi tại khu vực gửi xe tòa H1 ĐH Bách Khoa kèm thẻ ngân hàng.', location: 'ĐH Bách Khoa, Hà Nội', time: '10p trước', img: 'https://images.unsplash.com/photo-1627123424574-724758594913?w=400&q=80' },
  { id: 2, status: 'found', category: 'Thiết bị điện tử', title: 'MacBook Pro M2 Space Gray', desc: 'Bàn số 42 tầng 3 thư viện Tạ Quang Bửu, đã gửi lễ tân.', location: 'Thư viện Tạ Quang Bửu, HN', time: '35p trước', img: 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=400&q=80' },
  { id: 3, status: 'found', category: 'Chìa khóa', title: 'Chìa khóa smartkey Honda', desc: 'Nhặt được tại Vinhomes Landmark 81 kèm móc khóa gấu bông.', location: 'Vinhomes Central Park, TP.HCM', time: '1 giờ trước', img: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=400&q=80' },
  { id: 4, status: 'done', category: 'Thú cưng', title: 'Mèo Anh lông ngắn Golden', desc: 'Bé Mochi đã được cư dân toà Sarimi hỗ trợ chăm sóc và trao trả.', location: 'KĐT Sala, TP. Thủ Đức', time: 'Hôm qua', img: 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=400&q=80' },
  { id: 5, status: 'lost', category: 'Thiết bị điện tử', title: 'Điện thoại iPhone 15 Pro xanh', desc: 'Để quên trên xe bus tuyến 32, khu vực Nguyễn Trãi. Có ốp lưng trong suốt.', location: 'Tuyến 32, Hà Nội', time: '2 giờ trước', img: 'https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=400&q=80' },
  { id: 6, status: 'found', category: 'Ba lô & Túi', title: 'Ba lô Xiaomi đen 15L', desc: 'Nhặt tại sân D3 ĐHBK, bên trong có sách vở và bút viết.', location: 'ĐH Bách Khoa, Hà Nội', time: '3 giờ trước', img: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=400&q=80' },
  { id: 7, status: 'lost', category: 'Đồng hồ & Trang sức', title: 'Đồng hồ Casio G-Shock đỏ', desc: 'Thất lạc tại sân vận động ĐHQG, buổi tối ngày thứ 6.', location: 'ĐHQG TP.HCM', time: 'Hôm qua', img: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=400&q=80' },
  { id: 8, status: 'found', category: 'Kính mắt', title: 'Kính cận gọng titan mảnh', desc: 'Nhặt được tại bàn D12 thư viện, kính độ cao có hộp đựng đen.', location: 'Thư viện ĐH Khoa học Tự nhiên', time: '5 giờ trước', img: 'https://images.unsplash.com/photo-1574258495973-f010dfbb5371?w=400&q=80' },
];

// ─────────────────────────────────────────────
// SEARCH PAGE results
// ─────────────────────────────────────────────
export const TOP_MATCHES_MOCK = [
  { id: 1, title: 'Ví da nam Pedro đen', location: 'Toà H1, ĐHBK Hà Nội', category: 'Ví & Giấy tờ', date: '18/09/2025', time: '14:30', matchScore: 94.5, matchBreakdown: { visual: 98, location: 90, time: 92 }, img: 'https://images.unsplash.com/photo-1627123424574-724758594913?w=400&q=80', status: 'found' },
  { id: 2, title: 'Ví nam Pedro viền nâu', location: 'Tuyến bus 32, Cầu Giấy', category: 'Ví & Giấy tờ', date: '17/09/2025', time: '08:15', matchScore: 82.1, matchBreakdown: { visual: 85, location: 60, time: 90 }, img: 'https://images.unsplash.com/photo-1605333396914-232f50c05b8a?w=400&q=80', status: 'found' },
  { id: 3, title: 'Bóp da đen nam', location: 'Khu A, ĐH Kinh tế Quốc dân', category: 'Ví & Giấy tờ', date: '16/09/2025', time: '17:00', img: 'https://images.unsplash.com/photo-1559591937-bea52fc059ba?w=400&q=80', status: 'found' },
];

export const MORE_RESULTS_MOCK = [
  { id: 4, title: 'Ví đen không rõ hiệu', location: 'Hồ Hoàn Kiếm', category: 'Ví & Giấy tờ', date: '10/09/2025', time: '20:00', matchScore: 58.2, img: 'https://images.unsplash.com/photo-1628151015968-3a4429e9ef04?w=400&q=80', status: 'found' },
  { id: 5, title: 'Ví da cá sấu đen', location: 'Aeon Mall Hà Đông', category: 'Ví & Giấy tờ', date: '18/09/2025', time: '19:45', matchScore: 55.4, img: 'https://images.unsplash.com/photo-1543163521-1bf539c55dd2?w=400&q=80', status: 'found' },
  { id: 6, title: 'iPhone 15 Pro Titan', location: 'Tuyến bus 32, Cầu Giấy', category: 'Thiết bị điện tử', date: '19/09/2025', time: '10:15', matchScore: 89.0, img: 'https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=400&q=80', status: 'lost' },
  { id: 7, title: 'Chìa khóa smartkey Honda', location: 'Vinhomes Central Park, TP.HCM', category: 'Chìa khóa', date: '18/09/2025', time: '15:20', matchScore: 78.5, img: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=400&q=80', status: 'found' },
  { id: 8, title: 'Mèo Anh lông ngắn xám', location: 'KĐT Sala, TP. Thủ Đức', category: 'Thú cưng', date: '17/09/2025', time: '09:00', matchScore: 72.0, img: 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=400&q=80', status: 'lost' },
];

// ─────────────────────────────────────────────
// SMART MATCH PAGE
// ─────────────────────────────────────────────
export const POSTED_ITEM_MOCK = {
  id: 'REQ-8821',
  title: 'Ví da nam Pedro màu đen',
  type: 'lost',
  date: '18/09/2025',
  time: '14:30',
  location: 'Khu vực Bách Khoa, Hai Bà Trưng',
  img: 'https://images.unsplash.com/photo-1627123424574-724758594913?w=300&q=80',
};

export const AI_MATCHES_MOCK = [
  { id: 1, title: 'Ví da nam Pedro đen', location: 'Toà H1, ĐHBK Hà Nội', date: '18/09/2025', time: '15:10', matchScore: 94.5, matchBreakdown: { visual: 98, location: 90, time: 92 }, img: 'https://images.unsplash.com/photo-1627123424574-724758594913?w=400&q=80', status: 'found' },
  { id: 2, title: 'Ví nam Pedro viền nâu', location: 'Tuyến bus 32, Cầu Giấy', date: '17/09/2025', time: '08:15', matchScore: 82.1, matchBreakdown: { visual: 85, location: 60, time: 90 }, img: 'https://images.unsplash.com/photo-1605333396914-232f50c05b8a?w=400&q=80', status: 'found' },
  { id: 3, title: 'Bóp da đen nam', location: 'Khu A, ĐH Kinh tế Quốc dân', date: '16/09/2025', time: '17:00', matchScore: 76.8, matchBreakdown: { visual: 70, location: 80, time: 75 }, img: 'https://images.unsplash.com/photo-1559591937-bea52fc059ba?w=400&q=80', status: 'found' },
];

// ─────────────────────────────────────────────
// ITEM DETAIL PAGE
// ─────────────────────────────────────────────
export const ITEM_DETAIL_MOCK = {
  id: '1',
  title: 'Ví da nam Pedro màu đen',
  type: 'found',
  category: 'Ví & Giấy tờ',
  date: '18/09/2025',
  time: '14:30',
  location: 'Toà H1, Đại học Bách Khoa Hà Nội',
  lat: 21.0041, lng: 105.8437,
  description: 'Nhặt được một ví da nam hiệu Pedro màu đen trên ghế đá gần sảnh Toà H1. Trong ví có một số giấy tờ tuỳ thân nhưng không tiện công khai. Mong chủ nhân liên hệ để nhận lại.',
  characteristics: ['Màu sắc: Đen', 'Thương hiệu: Pedro', 'Chất liệu: Da thật', 'Đặc điểm phụ: Có vết xước nhỏ ở góc phải dưới'],
  finder: { name: 'Thành viên uy tín', verified: true, avatar: 'https://api.dicebear.com/7.x/initials/svg?seed=TV&backgroundColor=1e6b6b' },
  images: ['https://images.unsplash.com/photo-1627123424574-724758594913?w=800&q=80', 'https://images.unsplash.com/photo-1605333396914-232f50c05b8a?w=800&q=80'],
  matchData: { score: 94.5, visual: 98, location: 90, time: 92 },
};

// ─────────────────────────────────────────────
// PROFILE PAGE
// ─────────────────────────────────────────────
export const MY_POSTS_MOCK = [
  { id: 1, status: 'found', title: 'Ví da nam Pedro', category: 'Giấy tờ & Ví', date: '18/09/2025', location: 'Toà H1, ĐHBK', img: 'https://images.unsplash.com/photo-1627123424574-724758594913?w=300&q=80', matched: true },
  { id: 2, status: 'lost', title: 'Điện thoại iPhone 15 Pro xanh', category: 'Thiết bị điện tử', date: '15/09/2025', location: 'Tuyến 32, Hà Nội', img: 'https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=300&q=80', matched: false },
  { id: 3, status: 'done', title: 'MacBook Pro M2 Space Gray', category: 'Thiết bị điện tử', date: '08/09/2025', location: 'Thư viện TQB', img: 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=300&q=80', matched: true },
];

export const ACTIVITY_LOG_MOCK = [
  { iconName: 'CheckCircle', color: 'var(--status-found)', label: 'Xác minh quyền sở hữu thành công', detail: 'Ví Pedro — Chủ nhân đã nhận đồ', time: '2 ngày trước' },
  { iconName: 'Bell', color: 'var(--accent)', label: 'AI so khớp mới', detail: 'iPhone 15 Pro có 3 kết quả khớp mới', time: '4 ngày trước' },
  { iconName: 'UploadCloud', color: '#6366f1', label: 'Đăng tin thất lạc', detail: 'Điện thoại iPhone 15 Pro xanh', time: '6 ngày trước' },
  { iconName: 'Shield', color: 'var(--accent)', label: 'Xác thực 2FA kích hoạt', detail: 'Tài khoản được bảo mật 2 lớp', time: '10 ngày trước' },
];

// ─────────────────────────────────────────────
// MY POSTS PAGE
// ─────────────────────────────────────────────
export const MOCK_POSTS = [
  { id: 1, type: 'found', status: 'active', title: 'Ví da nam Pedro màu đen', location: 'Toà H1, ĐH Bách Khoa', date: '18/09/2025', time: '14:30', views: 47, aiMatched: true, matchScore: 92, img: 'https://images.unsplash.com/photo-1627123424574-724758594913?w=300&q=80' },
  { id: 2, type: 'lost', status: 'active', title: 'iPhone 15 Pro Max Xanh Titan', location: 'Tuyến xe 32 – Hà Nội', date: '15/09/2025', time: '09:15', views: 124, aiMatched: false, matchScore: null, img: 'https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=300&q=80' },
  { id: 3, type: 'found', status: 'resolved', title: 'MacBook Pro M2 Space Gray 14 inch', location: 'Thư viện Tạ Quang Bửu', date: '08/09/2025', time: '16:00', views: 88, aiMatched: true, matchScore: 97, img: 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=300&q=80' },
  { id: 4, type: 'lost', status: 'discussing', title: 'Chìa khoá xe Honda Wave Alpha', location: 'Căng tin C2, ĐH Bách Khoa', date: '20/09/2025', time: '11:00', views: 12, aiMatched: false, matchScore: null, img: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=300&q=80' },
  { id: 5, type: 'found', status: 'active', title: 'Thẻ sinh viên & Thẻ ATM Vietcombank', location: 'Sân bóng khu A, ĐH Bách Khoa', date: '21/09/2025', time: '18:45', views: 5, aiMatched: false, matchScore: null, img: 'https://images.unsplash.com/photo-1614680376408-81e91ffe3db7?w=300&q=80' },
];

// ─────────────────────────────────────────────
// CLAIM ITEM PAGE
// ─────────────────────────────────────────────
export const FOUND_ITEM_MOCK = {
  id: '1',
  title: 'Ví da nam Pedro màu đen',
  location: 'Toà H1, Đại học Bách Khoa Hà Nội',
  finder: 'Thành viên uy tín',
};
