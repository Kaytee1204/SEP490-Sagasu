import {
  Package, CheckCircle, Clock, Users,
  UploadCloud, Cpu, ShieldCheck, Handshake
} from 'lucide-react';

export const ITEMS = [
  {
    id: 1,
    status: 'lost',
    category: 'Giấy tờ & Ví',
    title: 'Ví da nam Pedro kèm CCCD',
    desc: 'Rơi tại khu vực gửi xe tòa H1 ĐH Bách Khoa kèm thẻ ngân hàng.',
    location: 'ĐH Bách Khoa, Hà Nội',
    time: '10p trước',
    img: 'https://images.unsplash.com/photo-1627123424574-724758594913?w=400&q=80',
  },
  {
    id: 2,
    status: 'found',
    category: 'Thiết bị điện tử',
    title: 'MacBook Pro M2 Space Gray',
    desc: 'Bàn số 42 tầng 3 thư viện Tạ Quang Bửu, đã gửi lễ tân.',
    location: 'Thư viện Tạ Quang Bửu, HN',
    time: '35p trước',
    img: 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=400&q=80',
  },
  {
    id: 3,
    status: 'found',
    category: 'Chìa khóa',
    title: 'Chìa khóa smartkey Honda',
    desc: 'Nhặt được tại Vinhomes Landmark 81 kèm móc khóa gấu bông.',
    location: 'Vinhomes Central Park, TP.HCM',
    time: '1 giờ trước',
    img: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=400&q=80',
  },
  {
    id: 4,
    status: 'done',
    category: 'Thú cưng',
    title: 'Mèo Anh lông ngắn Golden',
    desc: 'Bé Mochi đã được cư dân toà Sarimi hỗ trợ chăm sóc và trao trả.',
    location: 'KĐT Sala, TP. Thủ Đức',
    time: 'Hôm qua',
    img: 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=400&q=80',
  },
  {
    id: 5,
    status: 'lost',
    category: 'Thiết bị điện tử',
    title: 'Điện thoại iPhone 15 Pro xanh',
    desc: 'Để quên trên xe bus tuyến 32, khu vực Nguyễn Trãi. Có ốp lưng trong suốt.',
    location: 'Tuyến 32, Hà Nội',
    time: '2 giờ trước',
    img: 'https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=400&q=80',
  },
  {
    id: 6,
    status: 'found',
    category: 'Ba lô & Túi',
    title: 'Ba lô Xiaomi đen 15L',
    desc: 'Nhặt tại sân D3 ĐHBK, bên trong có sách vở và bút viết.',
    location: 'ĐH Bách Khoa, Hà Nội',
    time: '3 giờ trước',
    img: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=400&q=80',
  },
  {
    id: 7,
    status: 'lost',
    category: 'Đồng hồ & Trang sức',
    title: 'Đồng hồ Casio G-Shock đỏ',
    desc: 'Thất lạc tại sân vận động ĐHQG, buổi tối ngày thứ 6.',
    location: 'ĐHQG TP.HCM',
    time: 'Hôm qua',
    img: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=400&q=80',
  },
  {
    id: 8,
    status: 'found',
    category: 'Kính mắt',
    title: 'Kính cận gọng titan mảnh',
    desc: 'Nhặt được tại bàn D12 thư viện, kính độ cao có hộp đựng đen.',
    location: 'Thư viện ĐH Khoa học Tự nhiên',
    time: '5 giờ trước',
    img: 'https://images.unsplash.com/photo-1574258495973-f010dfbb5371?w=400&q=80',
  },
];

export const STATS = [
  { number: '1,420+', label: 'Món đồ đã trao trả', icon: Package },
  { number: '98.5%', label: 'Tỷ lệ xác minh chuẩn xác', icon: CheckCircle },
  { number: '15 phút', label: 'Thời gian kết nối bình quân', icon: Clock },
  { number: '12,000+', label: 'Thành viên cộng đồng', icon: Users },
];

export const FILTER_TABS = [
  { key: 'all', label: 'Tất cả (8)' },
  { key: 'lost', label: 'Cần tìm (3)' },
  { key: 'found', label: 'Nhặt được (3)' },
  { key: 'done', label: 'Đã trả (2)' },
];

export const CATEGORIES = [
  'Tất cả danh mục',
  'Thiết bị điện tử',
  'Giấy tờ & Ví',
  'Chìa khóa',
  'Ba lô & Túi',
  'Đồng hồ & Trang sức',
  'Kính mắt',
  'Thú cưng',
];

export const QUICK_TAGS = ['Căn cước công dân', 'Ví Pedro ĐHBK', 'AirPods Pro', 'Chìa khóa smartkey'];

export const STEPS = [
  {
    num: '01',
    label: 'Bước 01',
    icon: UploadCloud,
    title: 'Đăng thông tin',
    desc: 'Tải ảnh, nhập thời gian và vị trí ước tính một cách nhanh chóng.',
  },
  {
    num: '02',
    label: 'Bước 02',
    icon: Cpu,
    title: 'AI so khớp',
    desc: 'Hệ thống tự động đối chiếu hình ảnh và thông tin trong bán kính 10km.',
  },
  {
    num: '03',
    label: 'Bước 03',
    icon: ShieldCheck,
    title: 'Xác minh bí mật',
    desc: 'Chủ nhân trả lời 3 câu hỏi đặc trưng để xác minh quyền sở hữu an toàn.',
  },
  {
    num: '04',
    label: 'Bước 04',
    icon: Handshake,
    title: 'Trao trả an toàn',
    desc: 'Hẹn gặp tại các điểm tiếp nhận cộng đồng (Safe Zone) hoặc bốt bảo vệ.',
  },
];

export const MAP_CLUSTERS = [
  { name: 'Cụm Bách Khoa – Xây Dựng (Hà Nội)', lost: 48, found: 31 },
  { name: 'Làng Đại Học Thủ Đức (TP.HCM)', lost: 64, found: 52 },
  { name: 'ĐHQG Hà Nội – Cầu Giấy', lost: 29, found: 18 },
];
