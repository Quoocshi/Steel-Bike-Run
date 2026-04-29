# Steel Bike Run — Tài liệu Nghiệp vụ & Luồng Ứng dụng (Business Flow)

Tài liệu này mô tả chi tiết các luồng hoạt động của hệ thống Steel Bike Run, tập trung vào trải nghiệm người dùng (UX) và logic nghiệp vụ. Mục tiêu là cung cấp thông tin đầu vào rõ ràng cho đội ngũ UI/UX Designer thiết kế giao diện ứng dụng Mobile (Customer App & Driver App).

---

## 1. Tổng quan hệ sinh thái Steel Bike Run

Hệ thống bao gồm 2 ứng dụng riêng biệt (hoặc 2 phân hệ luồng riêng biệt trong cùng 1 app):
1. **Customer App (Ứng dụng Khách hàng):** Đặt xe, xem giá, theo dõi tài xế.
2. **Driver App (Ứng dụng Tài xế):** Quét khuôn mặt an toàn, nhận cuốc, dẫn đường.

---

## 2. Luồng Ứng dụng Khách hàng (Customer Flow)

### 2.1. Đăng nhập / Đăng ký (Auth Flow)
* **Mục tiêu:** Xác thực danh tính khách hàng.
* **Các màn hình (Screens):**
    * **Màn hình Chào mừng (Splash/Onboarding):** Giới thiệu tính năng an toàn, đặt xe nhanh.
    * **Màn hình Đăng nhập:** Nhập Số điện thoại/Email và Mật khẩu. Có nút chuyển sang Đăng ký.
    * **Màn hình Đăng ký:** Nhập Họ tên, Số điện thoại, Email, Mật khẩu.
* **Logic/Xử lý:**
    * Sau khi đăng nhập thành công, lưu JWT token cục bộ.
    * Mặc định luôn chuyển hướng đến Màn hình chính của Khách hàng (Customer Home Screen). Vai trò tài xế sẽ được kích hoạt thông qua tính năng chuyển đổi (Switch Role) trong mục Cài đặt.

### 2.2. Màn hình Chính (Home Screen)
* **Mục tiêu:** Hiển thị bản đồ, vị trí hiện tại và các tài xế xung quanh.
* **Các thành phần giao diện (UI Components):**
    * **Bản đồ toàn màn hình (Google Maps):** Cần thiết kế Marker cho điểm đón (mặc định là vị trí hiện tại) và Marker cho các tài xế đang online xung quanh (xe ô tô/xe máy).
    * **Ô nhập liệu (Search Bar):** "Bạn muốn đi đâu?" nằm ở nửa dưới hoặc trên cùng màn hình.
    * **Nút định vị lại (My Location Button):** Đưa camera bản đồ về vị trí người dùng.
    * **Menu/Profile:** Nút mở Sidebar hoặc Profile.
* **Logic/Xử lý:**
    * Ứng dụng gọi API `GET /api/v1/driver/nearby` dựa trên vị trí hiện tại của user để hiển thị tài xế.
    * (Tùy chọn) Hiển thị H3 Hexagon overlay trên bản đồ để cho thấy vùng nào đang có surge pricing (giá cao).

### 2.3. Đặt xe & Xem giá (Booking Flow)
* **Mục tiêu:** Khách hàng chọn điểm đến, xem ước tính giá và xác nhận đặt xe.
* **Các màn hình (Screens):**
    * **Màn hình Chọn Điểm đến:** Cửa sổ tìm kiếm địa chỉ (Autocomplete).
    * **Màn hình Xem trước Chuyến đi (Trip Preview):**
        * Bản đồ thu phóng để hiển thị cả Điểm đón và Điểm đến. Vẽ tuyến đường (Route) dự kiến.
        * **Thẻ thông tin chuyến đi (Bottom Sheet):**
            * Địa chỉ Đón & Đến.
            * Giá cước ước tính (Có thể highlight nếu đang bị Surge Pricing - ví dụ: "Đang giờ cao điểm, giá tăng x1.5").
            * Phương thức thanh toán (Tiền mặt/Thẻ).
            * Nút **"Đặt xe ngay"** (Call to Action chính).
* **Logic/Xử lý:**
    * Khi chọn xong điểm đến, app gọi `POST /api/v1/trip/estimate` để lấy giá.
    * Nhấn "Đặt xe ngay", app gọi `POST /api/v1/trip` tạo cuốc xe và bắt đầu chờ tài xế (thông qua kết nối WebSocket).

### 2.4. Tìm tài xế & Theo dõi chuyến đi (Matching & Tracking Flow)
* **Mục tiêu:** Chờ hệ thống ghép cuốc và theo dõi tài xế di chuyển theo thời gian thực.
* **Các màn hình (Screens):**
    * **Màn hình Đang tìm tài xế (Finding Driver):** Hiển thị animation radar hoặc loading, thông báo "Đang tìm tài xế gần nhất...". Có nút "Hủy".
    * **Màn hình Theo dõi (Tracking) - Khi tài xế đã nhận cuốc:**
        * Bản đồ hiển thị vị trí realtime của tài xế đang di chuyển về phía điểm đón.
        * **Thẻ thông tin tài xế:** Avatar, Tên, Đánh giá (Sao), Biển số xe, Loại xe, Màu xe.
        * Nút "Gọi điện" / "Nhắn tin".
        * Thông báo trạng thái: "Tài xế đang đến", "Tài xế đã đến nơi", "Đang di chuyển đến đích".
* **Logic/Xử lý:**
    * Nhận sự kiện WebSocket `TripFoundMessage` -> Chuyển từ "Đang tìm" sang "Đã tìm thấy tài xế".
    * Nhận liên tục `DriverLocationUpdate` qua WebSocket -> Cập nhật vị trí icon ô tô trên bản đồ mượt mà (animate marker).

### 2.5. Kết thúc & Đánh giá (Completion Flow)
* **Mục tiêu:** Thanh toán và đánh giá chất lượng tài xế.
* **Các màn hình (Screens):**
    * **Màn hình Biên lai (Receipt):** Hiển thị tổng tiền, quãng đường, thời gian, phương thức thanh toán.
    * **Màn hình Đánh giá (Rating):** Vote 1-5 sao, ô nhập phản hồi, nút "Gửi đánh giá". Nút "Bỏ qua".

---

## 3. Luồng Ứng dụng Tài xế (Driver Flow)

### 3.1. Chuyển đổi Vai trò & Đăng ký Tài xế (Role Switch & Onboarding)
* **Mục tiêu:** Cho phép người dùng bình thường chuyển sang chế độ tài xế để nhận cuốc.
* **Các màn hình (Screens):**
    * **Màn hình Cài đặt (Settings - ở Customer App):** Có nút "Chuyển sang chế độ Tài xế" (Switch to Driver Mode).
    * **Màn hình Đăng ký Tài xế (Driver Onboarding):** Dành cho người dùng lần đầu tiên chuyển sang làm tài xế. Yêu cầu nhập các thông tin bắt buộc: Biển số xe, Loại xe, Màu xe, Bằng lái.
* **Logic/Xử lý:**
    * **Trường hợp 1 (Lần đầu chuyển đổi):** Khi nhấn "Chuyển sang chế độ Tài xế", hệ thống kiểm tra xem user đã có profile driver (thông tin phương tiện) chưa. Nếu chưa có, ứng dụng điều hướng đến màn hình Đăng ký Tài xế. Sau khi điền và lưu thông tin thành công, chuyển sang giao diện Driver App.
    * **Trường hợp 2 (Các lần sau):** Nếu đã có profile driver, khi nhấn nút, ngay lập tức chuyển đổi giao diện sang Driver App (Màn hình Home của tài xế) mà không cần điền lại thông tin.
    * Trong Menu/Cài đặt của Driver App cũng sẽ có nút "Chuyển về chế độ Khách hàng" để user dễ dàng quay lại trải nghiệm đặt xe ban đầu.

### 3.2. Màn hình Chính & Trạng thái Offline (Home - Offline)
* **Mục tiêu:** Màn hình chờ của tài xế khi chưa sẵn sàng nhận cuốc.
* **Các màn hình (Screens):**
    * **Bản đồ:** Vị trí hiện tại của tài xế.
    * **Lớp phủ H3 Surge Zones:** Trên bản đồ cần vẽ các hình lục giác (Hexagon) màu đỏ/cam chỉ thị các khu vực đang có nhu cầu cao (nhằm điều hướng tài xế lái xe ra đó để nhận cuốc giá cao).
    * **Nút Trạng thái (Toggle Button):** Đang ở trạng thái "OFFLINE" / "CHƯA SẴN SÀNG".
    * **Thống kê nhanh:** Số chuyến trong ngày, Thu nhập hôm nay.

### 3.3. Xác thực An toàn & Trạng thái Online (Safety Check & Go Online)
* **Mục tiêu:** Kiểm tra độ tỉnh táo của tài xế trước khi cho phép trực tuyến nhận cuốc. Đây là **tính năng cốt lõi (USP)** của dự án.
* **Các màn hình (Screens):**
    * Khi tài xế bật nút sang "ONLINE", màn hình quét khuôn mặt xuất hiện.
    * **Màn hình Quét khuôn mặt (Face Scan):**
        * Camera trước bật. Khung hướng dẫn đưa mặt vào chính giữa (CameraX Preview).
        * UI yêu cầu: "Vui lòng giữ điện thoại ngang tầm mắt. Nhìn thẳng vào camera".
        * Hiển thị trạng thái phân tích: "Đang kiểm tra...", "Phát hiện mệt mỏi! Vui lòng chớp mắt để xác nhận tỉnh táo".
* **Logic/Xử lý:**
    * Sử dụng ML Kit tính EAR (Eye Aspect Ratio). Nếu mắt nhắm quá lâu/có dấu hiệu buồn ngủ -> Cảnh báo, từ chối cho Online.
    * Pass (Vượt qua) -> Cập nhật trạng thái thành ONLINE, gọi API `PUT /api/v1/driver/status`, hệ thống bắt đầu gửi tọa độ (heartbeat) lên server qua WebSocket mỗi 3 giây.
    * Giao diện Home chuyển sang chế độ "ONLINE", nút Toggle chuyển màu (xanh lá), sẵn sàng nhận chuyến.

### 3.4. Nhận cuốc xe (Incoming Trip)
* **Mục tiêu:** Tài xế nhận thông báo có chuyến mới và quyết định nhận/từ chối.
* **Các màn hình (Screens):**
    * **Popup / Màn hình Nhận cuốc (Ringing):**
        * Âm thanh thông báo lớn (Ringing).
        * Vòng đếm ngược thời gian (Ví dụ: 10 giây).
        * Thông tin chuyến đi: Khoảng cách từ vị trí hiện tại đến điểm đón, Điểm đón, Điểm đến, Ước tính doanh thu cuốc xe, Trạng thái Surge (x1.5 giá).
        * Nút vuốt/chạm kích thước lớn: **"CHẤP NHẬN"** (Nổi bật) và nút "Bỏ qua" (Nhỏ hơn/Dạng Text).
* **Logic/Xử lý:**
    * App nhận thông điệp có chuyến qua WebSocket từ Matching Engine.
    * Nếu tài xế chấp nhận -> Gọi API `PUT /api/v1/trip/{id}/accept`.

### 3.5. Thực hiện Chuyến đi (Trip Execution / Navigation)
* **Mục tiêu:** Dẫn đường cho tài xế đến điểm đón và sau đó đến điểm đến.
* **Các màn hình (Screens):**
    * **Trạng thái 1: Đang đến điểm đón:**
        * Bản đồ vẽ tuyến đường từ vị trí hiện tại đến điểm đón khách.
        * Thẻ thông tin khách hàng: Tên, Nút gọi/nhắn tin.
        * Nút thao tác: **"Đã đến điểm đón"**.
    * **Trạng thái 2: Khách lên xe / Bắt đầu chuyến:**
        * Nhấn "Đã đến điểm đón" -> Chờ khách lên xe -> UI chuyển đổi nút thành **"Bắt đầu chuyến đi"** (Gọi API `PUT /api/v1/trip/{id}/start`).
    * **Trạng thái 3: Đang di chuyển đến đích:**
        * Bản đồ thay đổi dẫn đường, hướng tới điểm đến cuối cùng của chuyến đi.
        * Nút thao tác lớn: **"Hoàn thành chuyến đi"** (Có thể thiết kế dạng Vuốt "Swipe to complete" để tránh chạm nhầm khi đang lái).
* **Logic/Xử lý:**
    * Khi hoàn thành, gọi API `PUT /api/v1/trip/{id}/complete`.
    * Ứng dụng đưa tài xế trở lại màn hình Home (ONLINE) để chờ cuốc tiếp theo, và hiển thị thông báo thu nhập vừa nhận được.

---

## 4. Bảng Trạng thái Chuyến đi (Trip Status Machine)

Designer cần thiết kế UI tương ứng cho từng trạng thái sau của một chuyến đi (Trip):

| Trạng thái (Status) | Giao diện Khách hàng (Customer UI) | Giao diện Tài xế (Driver UI) |
| :--- | :--- | :--- |
| `REQUESTED` | Màn hình "Đang tìm tài xế..." với radar animation. | Màn hình popup nháy sáng nhận cuốc (Đếm ngược). |
| `ACCEPTED` | Hiện thông tin xe, vẽ đường xe chạy đến điểm đón. | Hiện màn hình dẫn đường (Navigation) đến điểm đón. |
| `IN_PROGRESS` | Bản đồ hiển thị lộ trình tài xế đang chạy đến đích. | Dẫn đường đến điểm đến cuối cùng. |
| `COMPLETED` | Hiện màn hình Biên lai Thanh toán, Đánh giá (Rate) tài xế. | Hiện bảng Tổng kết doanh thu cuốc xe vừa hoàn thành. |
| `CANCELLED` | Thông báo chuyến đi bị hủy, tự động quay về Home. | Thông báo chuyến đi bị hủy, tự động quay về Home. |

---

## 5. Lưu ý quan trọng cho Designer (UI/UX)

1. **Hiển thị H3 Hexagon:** Trên app tài xế, cần có thiết kế lớp phủ (overlay) bản đồ hình tổ ong (lục giác). Các ô lục giác có nhu cầu cao sẽ có màu đỏ đậm/cam nhạt tùy theo hệ số nhân giá (Surge Multiplier).
2. **Realtime Feedback:** Ở app khách hàng, xe của tài xế phải có hiệu ứng di chuyển trên bản đồ trơn tru (animate icon marker), không được nhảy giật cục (do vị trí được cập nhật qua WebSocket mỗi 3 giây).
3. **Face Scan UI:** Màn hình quét khuôn mặt cần giao diện mang hơi hướng công nghệ/an toàn (futuristic/safety-focused - ví dụ: vòng tròn quét đổi màu từ trắng sang xanh lá khi pass), vì đây là **điểm khác biệt chính (USP)** của ứng dụng so với các đối thủ trên thị trường.
4. **Phân biệt 2 App:** Cần sử dụng tông màu khác biệt hoặc hệ thống nhận diện rõ ràng để phân biệt giữa Customer App (ví dụ: Tông màu sáng, xanh dương/xanh lá mang lại cảm giác an toàn) và Driver App (ví dụ: Hỗ trợ Dark mode, tông đen/cam tối ưu để dễ nhìn khi lái xe ban đêm, chống lóa mắt).
5. **Thao tác 1 tay (One-handed Operation):** Đặc biệt với Driver App, các nút bấm thao tác chính (Nhận cuốc, Đã đến điểm đón, Hoàn thành) cần được thiết kế to, rõ ràng, dễ dàng thao tác bằng 1 tay hoặc sử dụng cử chỉ vuốt (Swipe).
