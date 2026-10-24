# Đề tài và các chức năng chính của hệ thống HRM

## I. Tên đề tài

**Xây dựng hệ thống quản lý nhân sự (Human Resource Management – HRM) cho doanh nghiệp.**

Hệ thống hỗ trợ quản lý tập trung hồ sơ nhân viên, cơ cấu tổ chức, tài khoản và phân quyền, ca làm việc, nghỉ phép, chấm công, tính lương và báo cáo. Người dùng nghiệp vụ làm việc trên **HRM Workspace**; quản trị viên hệ thống dùng **Admin Console**. Hai giao diện sử dụng chung API và cơ sở dữ liệu của một doanh nghiệp.

## II. Các chức năng chính

1. **Xác thực và quản lý phiên đăng nhập:** Đăng nhập theo cổng sử dụng, kích hoạt tài khoản, đổi mật khẩu, làm mới phiên và đăng xuất.
2. **Quản lý tài khoản:** Cấp tài khoản cho nhân viên, xem thông tin tài khoản, gửi lại lời mời, đặt lại mật khẩu, khóa và mở khóa.
3. **Quản lý vai trò và phân quyền:** Tạo vai trò tùy chỉnh, gán quyền, gán hoặc thu hồi vai trò và kiểm soát phạm vi dữ liệu được phép truy cập.
4. **Quản lý hồ sơ nhân viên:** Tạo, tra cứu, cập nhật hồ sơ; theo dõi phân công, điều chuyển và vòng đời làm việc.
5. **Quản lý cơ cấu tổ chức:** Quản lý đơn vị, địa điểm làm việc, chức danh và mối liên hệ giữa các bộ phận.
6. **Quản lý ca làm việc và ngày lễ:** Khai báo ca, thời gian làm việc và ngày nghỉ lễ của công ty.
7. **Quản lý nghỉ phép:** Tạo, gửi, duyệt, từ chối hoặc hủy đơn nghỉ; theo dõi số dư phép năm.
8. **Quản lý chấm công và tăng ca:** Ghi nhận giờ vào/ra từ máy chấm công, chuẩn bị bảng công, xử lý dữ liệu thiếu và duyệt tăng ca.
9. **Quản lý lương, bảo hiểm và thuế:** Lưu dữ liệu đầu vào, nhập khoản thưởng/phụ cấp/khấu trừ theo kỳ, tính và duyệt kỳ lương, lập phiếu lương, xác nhận thanh toán, khóa kỳ và xuất bảng lương.
10. **Báo cáo nhân sự và lương:** Tổng hợp phân bố nhân sự theo trình độ, thâm niên và phân bố lương cơ bản theo phạm vi được cấp quyền.

## III. Mô tả chi tiết các chức năng chính

### 1. Xác thực và quản lý phiên đăng nhập

- **Người sử dụng:** Nhân viên, HR, kế toán lương, người duyệt lương, chủ doanh nghiệp và quản trị viên hệ thống.
- **Luồng chính:** Người dùng đăng nhập tại cổng phù hợp; hệ thống kiểm tra thông tin đăng nhập và trạng thái tài khoản, sau đó tạo phiên. Người dùng có thể xem thông tin phiên, đổi mật khẩu và đăng xuất. Tài khoản mới được kích hoạt bằng token mời để chủ tài khoản tự đặt mật khẩu.
- **Quy tắc:** HRM Workspace và Admin Console tách phiên. Token được lưu trong cookie `HttpOnly` ở phía ứng dụng web; phiên được làm mới bằng refresh token và refresh token bị thu hồi khi đăng xuất.
- **Kết quả:** Chỉ người dùng có phiên hợp lệ mới truy cập được chức năng và dữ liệu thuộc quyền của mình.

**Quy trình sử dụng:**

1. Người dùng nhập tên đăng nhập, email hoặc mã nhân viên cùng mật khẩu tại `/login` hoặc `/admin/login`. API kiểm tra thông tin xác thực, trạng thái tài khoản và cổng được phép sử dụng.
2. Khi đăng nhập thành công, hệ thống trả thông tin tài khoản, vai trò, quyền và phạm vi hiện có. Giao diện dùng những thông tin này để hiển thị các chức năng phù hợp; API tiếp tục kiểm tra quyền cho từng yêu cầu.
3. Khi access token hết hạn, ứng dụng dùng refresh token để lấy phiên mới theo cơ chế xoay vòng. Người dùng có thể chủ động đăng xuất hoặc đổi mật khẩu từ phiên hợp lệ.
4. Với tài khoản mới hoặc cần đặt lại mật khẩu, người dùng mở liên kết chứa token kích hoạt còn hiệu lực, tự tạo mật khẩu và chuyển tài khoản từ `PENDING` sang `ACTIVE`.

**Trường hợp cần xử lý:** Đăng nhập sai bị từ chối và số lần sai được ghi nhận; tài khoản `PENDING`, `LOCKED` hoặc `DISABLED` không đăng nhập được. Hệ thống hiện chưa tự khóa sau một số lần nhập sai, vì vậy trạng thái `LOCKED` do thao tác quản trị thiết lập. Token kích hoạt chỉ dùng một lần và phải còn hạn. Quyền vừa thay đổi được phản ánh trong token mới, không tự sửa token đã phát.

### 2. Quản lý tài khoản

- **Người sử dụng:** Người có quyền cấp hoặc quản lý tài khoản; nhân viên là người nhận tài khoản.
- **Luồng chính:** Sau khi có hồ sơ nhân viên hợp lệ, người quản lý cấp tài khoản gắn với hồ sơ đó. Hệ thống tạo trạng thái chờ kích hoạt và token mời. Người dùng kích hoạt rồi tự đặt mật khẩu. Người quản lý có thể xem danh sách, xem chi tiết, gửi lại lời mời, khởi tạo quy trình đặt lại mật khẩu, khóa hoặc mở khóa tài khoản.
- **Trạng thái:** `PENDING` khi chờ kích hoạt, `ACTIVE` khi sử dụng được, `LOCKED` khi bị khóa và `DISABLED` khi ngừng sử dụng.
- **Quy tắc:** Không đặt mật khẩu hộ người dùng. Khi nhân viên kết thúc làm việc, tài khoản liên quan chuyển sang `DISABLED` và các refresh token bị thu hồi.
- **Kết quả:** Mỗi nhân viên được cấp quyền có tài khoản để truy cập đúng phần việc của mình.

**Thông tin đầu vào khi cấp tài khoản:** ID của hồ sơ nhân viên và tên đăng nhập. Email đăng nhập được lấy từ email công việc trong hồ sơ, không nhập lại ở biểu mẫu cấp tài khoản. Nhân viên phải đang thử việc hoặc đang làm việc, chưa có tài khoản và có email công việc hợp lệ. Tên đăng nhập phải duy nhất.

**Các thao tác quản trị:**

| Thao tác | Điều kiện và tác động |
| --- | --- |
| Cấp tài khoản | Tạo account `PENDING`, gán vai trò nền `EMPLOYEE` với phạm vi `SELF` và phát token kích hoạt. |
| Gửi lại lời mời | Chỉ dành cho account `PENDING`; token cũ bị thu hồi và token mới được phát. |
| Đặt lại mật khẩu | Đưa account `ACTIVE` hoặc `LOCKED` về `PENDING` để chủ tài khoản tự đặt mật khẩu bằng token mới. |
| Khóa hoặc vô hiệu hóa | Chặn đăng nhập và thu hồi các refresh token còn hiệu lực. |
| Mở lại tài khoản | Áp dụng theo trạng thái hiện tại và điều kiện account đã có mật khẩu. |

**Phân biệt trách nhiệm:** Quyền `account.provision` cho phép cấp tài khoản nhưng không mặc nhiên cho phép xem toàn bộ tài khoản, đặt lại mật khẩu hoặc khóa tài khoản. Việc cấp vai trò nghiệp vụ bổ sung được thực hiện sau bước cấp account, qua quy trình phân quyền riêng. API hiện phát token kích hoạt cho luồng quản trị; không nên mô tả hệ thống là đã tự gửi email mời.

### 3. Quản lý vai trò và phân quyền

- **Người sử dụng:** Chủ doanh nghiệp, quản trị viên và người được giao quyền quản lý vai trò.
- **Luồng chính:** Hệ thống cung cấp danh mục quyền. Người có thẩm quyền tạo vai trò tùy chỉnh, chọn các quyền được phép ủy quyền, gán hoặc thu hồi vai trò cho tài khoản. Một số vai trò được đề xuất và phê duyệt qua yêu cầu cấp quyền; hệ thống cũng hỗ trợ ngoại lệ quyền trên từng lần gán.
- **Quy tắc:** Quyền truy cập phụ thuộc đồng thời vào quyền của vai trò và phạm vi dữ liệu `SELF`, `ORG_UNIT`, `LOCATION` hoặc `COMPANY`. Chính sách cấp vai trò có thể là tự động, HR gán, chủ doanh nghiệp phê duyệt hoặc chỉ hệ thống cấp. Thay đổi quyền có hiệu lực trong token mới sau khi đăng nhập hoặc làm mới phiên.
- **Kết quả:** Người dùng chỉ nhìn thấy và thao tác trên nghiệp vụ, nhân sự thuộc phạm vi được cấp.

**Thành phần phân quyền:**

| Thành phần | Ý nghĩa |
| --- | --- |
| Permission | Hành động cụ thể, chẳng hạn đọc hồ sơ, cập nhật nhân viên hoặc tính lương. Danh mục này do mã nguồn quản lý. |
| Role | Nhóm quyền gắn với một nhiệm vụ; gồm vai trò hệ thống và vai trò tùy chỉnh. |
| Role assignment | Lần gán một role cho account, kèm phạm vi và thời gian hiệu lực. |
| Permission override | Ngoại lệ thêm hoặc bớt quyền trên một lần gán, có thể được lên lịch, kích hoạt, hết hạn hoặc thu hồi. |

**Cách hệ thống quyết định quyền truy cập:** Trước hết kiểm tra người dùng có permission cần thiết hay không; sau đó kiểm tra đối tượng dữ liệu có nằm trong phạm vi được gán hay không. Ví dụ, người có quyền cập nhật hồ sơ ở phạm vi `ORG_UNIT` chỉ sửa được nhân viên thuộc đơn vị tương ứng. `SELF` giới hạn ở bản thân, `LOCATION` theo địa điểm làm việc và `COMPANY` theo toàn doanh nghiệp.

**Luồng cấp vai trò:** Người có thẩm quyền chọn account, vai trò và phạm vi. Hệ thống áp dụng chính sách cấp của vai trò (`AUTO`, `HR_ASSIGNABLE`, `OWNER_APPROVAL` hoặc `SYSTEM_ONLY`); trường hợp cần chủ doanh nghiệp duyệt đi qua yêu cầu cấp quyền. Khi thu hồi vai trò hoặc ngoại lệ, quyền truy cập tương ứng không còn trong phiên mới. Chức danh quản lý trong hồ sơ nhân sự không tự tạo role.

### 4. Quản lý hồ sơ nhân viên

- **Người sử dụng:** HR, quản lý được phân quyền và nhân viên có quyền xem hồ sơ tương ứng.
- **Luồng chính:** HR tạo hồ sơ nhân viên; hệ thống sinh mã nhân viên theo tiền tố vị trí. Người có quyền xem danh sách, xem chi tiết, cập nhật hồ sơ, xác nhận hết thử việc, ghi nhận điều chuyển hoặc bổ nhiệm. Thông tin phân công lưu đơn vị, địa điểm, chức danh, ca làm và quản lý trực tiếp theo thời gian hiệu lực.
- **Vòng đời:** Nhân viên có thể ở trạng thái thử việc (`PROBATION`), đang làm việc (`ACTIVE`), nghỉ việc (`RESIGNED`), chấm dứt hợp đồng (`TERMINATED`) hoặc nghỉ hưu (`RETIRED`). Xác nhận hết thử việc chuyển sang `ACTIVE`; thao tác kết thúc làm việc đóng phân công đang mở và vô hiệu hóa tài khoản.
- **Dữ liệu nhạy cảm:** CCCD và tài khoản ngân hàng được đọc, cập nhật qua nhóm quyền riêng, tách khỏi quyền xem hồ sơ thông thường.
- **Quy tắc:** Xóa mềm chỉ áp dụng cho hồ sơ tạo nhầm và chưa phát sinh dữ liệu nghiệp vụ liên quan như tài khoản, lương, đơn nghỉ, chấm công hoặc phiếu lương.
- **Kết quả:** Doanh nghiệp có hồ sơ nhân sự và lịch sử phân công nhất quán để phục vụ chấm công, tính lương và báo cáo.

**Thông tin được quản lý:** Hồ sơ thông thường gồm họ tên, ngày sinh, giới tính, trình độ, chuyên ngành, nơi đào tạo, email công việc, số điện thoại, ngày tuyển dụng và trạng thái làm việc. Nhóm nhạy cảm gồm CCCD, email cá nhân, địa chỉ, mã số thuế và thông tin ngân hàng. Mỗi nhân viên còn có lịch sử phân công và có thể có lịch sử lương, đơn nghỉ, bảng công, phiếu lương.

**Quy trình tạo và cập nhật:**

1. HR chọn đơn vị, địa điểm, chức danh, loại lao động, ngày hiệu lực; có thể chọn ca và người quản lý trực tiếp.
2. Hệ thống kiểm tra danh mục đang hoạt động, phạm vi quản lý, ngày hiệu lực và dữ liệu cá nhân bắt buộc. Hồ sơ và phân công ban đầu được tạo trong cùng một giao dịch; lỗi ở một phần làm toàn bộ thao tác thất bại.
3. Mã nhân viên được sinh theo tiền tố chức danh và không đổi khi điều chuyển. Hồ sơ mới ở trạng thái `PROBATION`; thao tác tạo hồ sơ chưa tự tạo tài khoản đăng nhập.
4. Khi thay đổi bộ phận, địa điểm, chức danh, ca hoặc người quản lý, hệ thống ghi một phân công mới có thời gian hiệu lực thay vì ghi đè lịch sử. Người có quyền có thể xem phân công tại từng thời điểm.

**Kiểm soát vòng đời:** Kết thúc làm việc là thao tác thực thi cho nhân viên còn đang thử việc hoặc đang làm việc; hệ thống đóng phân công đang mở, chuyển trạng thái nhân viên và vô hiệu hóa account. Hồ sơ đã phát sinh nghiệp vụ phải đi theo luồng kết thúc làm việc, không được xóa mềm như hồ sơ tạo nhầm. Dữ liệu nhạy cảm luôn đòi hỏi permission riêng ngoài quyền xem hoặc sửa hồ sơ thường.

### 5. Quản lý cơ cấu tổ chức

- **Người sử dụng:** Người phụ trách tổ chức nhân sự và quản trị viên có quyền phù hợp.
- **Luồng chính:** Tạo, xem, sửa và xóa mềm đơn vị tổ chức, địa điểm làm việc, chức danh. Đơn vị và địa điểm được trình bày theo cây để dễ theo dõi quan hệ cấp trên, cấp dưới.
- **Quy tắc:** Đơn vị tổ chức và địa điểm làm việc là hai thuộc tính độc lập của phân công nhân viên. Chức danh được đánh dấu là quản lý không tự động cấp quyền quản lý trong hệ thống.
- **Kết quả:** Cơ cấu công ty làm cơ sở cho phân công nhân viên và giới hạn phạm vi truy cập dữ liệu.

**Các danh mục và quan hệ:** Đơn vị tổ chức có ba cấp `BOARD` → `DEPARTMENT` → `TEAM`. Địa điểm làm việc là cây riêng, có thể gồm trụ sở, chi nhánh và kho. Chức danh mô tả công việc và có thể đánh dấu vị trí quản lý. Mỗi danh mục có mã, tên, trạng thái hoạt động và thông tin quan hệ phù hợp.

**Quy trình quản lý:** Người có `organization.read` xem danh sách, chi tiết và cây tổ chức. Người có `organization.manage` tạo, sửa, ngừng hoạt động hoặc xóa mềm các danh mục. Khi lập phân công nhân viên, HR chọn ID của đơn vị, địa điểm và chức danh đang hoạt động, thay vì nhập tên tự do.

**Ràng buộc dữ liệu:** Mã danh mục là duy nhất trong các bản ghi chưa xóa mềm. Cây tổ chức không được tạo chu trình, một đơn vị không thể làm cha của chính nó; đơn vị cha không được ngừng hoạt động khi còn đơn vị con đang hoạt động. Danh mục đã được dùng phải được xử lý theo ràng buộc của hệ thống để không làm sai dữ liệu phân công lịch sử.

### 6. Quản lý ca làm việc và ngày lễ

- **Người sử dụng:** HR hoặc người được cấp quyền quản lý tổ chức.
- **Luồng chính:** Khai báo ca với giờ vào, giờ ra, thời gian nghỉ và công chuẩn; quản lý các ngày lễ của công ty. Ca được gắn vào phân công nhân viên để phục vụ chấm công.
- **Quy tắc:** Hệ thống hiện dùng lịch làm việc chuẩn từ thứ Hai đến thứ Bảy, trừ ngày lễ đã khai báo. Ca có thể đi qua nửa đêm. Lịch cá nhân hoặc lịch xoay ca theo từng ngày chưa thuộc phạm vi hiện tại.
- **Kết quả:** Hệ thống có căn cứ xác định ngày làm việc, ngày lễ và số phút công khi xử lý bảng công.

**Dữ liệu ca làm:** Một ca có mã và tên, giờ bắt đầu, giờ kết thúc, vị trí và số phút nghỉ, số phút công chuẩn, số phút cho phép đi muộn, cờ ca qua đêm và trạng thái hoạt động. Ca qua đêm được tính theo ngày bắt đầu ca; thời điểm lưu trữ là UTC còn ngày công hiển thị theo múi giờ Việt Nam.

**Quy trình sử dụng:** Người quản trị tạo ca, HR chọn ca cho phân công nhân viên, hệ thống dùng ca có hiệu lực trong từng ngày để xác định công chuẩn. Ngày lễ được khai báo bằng ngày và tên; bảng công và tính lương loại những ngày này khỏi lịch làm việc chuẩn. Việc chuẩn bị bảng công tháng kiểm tra nhân viên có phân công và ca cần thiết trong các ngày làm việc.

**Kiểm tra quan trọng:** Thời gian nghỉ phải nằm trong ca và nhỏ hơn thời lượng ca; số phút công chuẩn không vượt thời lượng còn lại sau nghỉ. Khi ca đã phát sinh chấm công, các thông số thời gian cốt lõi không được sửa để giữ đúng lịch sử; cần tạo ca mới. Thay đổi ngày lễ có thể buộc kỳ lương đã tính nhưng chưa duyệt quay về `DRAFT` để tính lại; kỳ đã duyệt thì không được thay đổi dữ liệu ảnh hưởng kỳ đó.

### 7. Quản lý nghỉ phép

- **Người sử dụng:** Nhân viên lập đơn, người có thẩm quyền duyệt và HR theo dõi số dư phép.
- **Luồng chính:** Nhân viên tạo đơn nháp, gửi duyệt hoặc hủy. Người duyệt xem đơn chờ, chấp thuận hoặc từ chối. Nhân viên và người có quyền xem lịch sử đơn và số dư phép năm.
- **Vòng đời:** `DRAFT` → `PENDING` → `APPROVED` hoặc `REJECTED`; đơn cũng có thể chuyển sang `CANCELLED` theo quy tắc nghiệp vụ.
- **Quy tắc:** Phép năm có hạn mức 12 ngày, tính tỷ lệ theo số tháng làm việc nếu vào làm giữa năm. Khi xét số dư, các đơn đang chờ cũng được tính để tránh duyệt vượt hạn mức. Đơn được duyệt cập nhật số phút nghỉ vào bảng công; cách trả lương phụ thuộc loại nghỉ.
- **Kết quả:** Đơn nghỉ và số dư phép được theo dõi thống nhất với chấm công, tính lương.

**Thông tin của đơn nghỉ:** Nhân viên, loại nghỉ, khoảng thời gian, lý do, trạng thái và cách xử lý lương. Các loại hiện có là `ANNUAL` (phép năm), `SICK` (ốm), `MATERNITY` (thai sản), `UNPAID` (không lương) và `OTHER` (khác).

**Các bước xử lý:** Nhân viên tạo đơn `DRAFT`, kiểm tra nội dung rồi gửi thành `PENDING`. Người duyệt xem các đơn đang chờ trong phạm vi được giao và chọn duyệt hoặc từ chối. Khi duyệt, hệ thống kiểm tra số dư nếu là phép năm, sau đó ghi số phút nghỉ lên bản ghi công tương ứng. Đơn hủy hoặc từ chối không tiếp tục được tính như ngày nghỉ đã duyệt.

**Cách tính số dư:** Mỗi năm có 12 ngày phép; người vào làm giữa năm được chia tỷ lệ theo số tháng làm việc, tháng vào làm tính trọn tháng (Bộ luật Lao động 2019, Điều 113). Hạn mức không lưu trong database mà được tính lại mỗi lần từ ngày vào làm và các đơn nghỉ, đối chiếu theo phút công. Chỉ đơn `ANNUAL` trừ hạn mức; đơn `PENDING` cũng được tạm tính khi kiểm tra khả năng duyệt các đơn khác, và duyệt đơn vượt số dư bị chặn. Phép không cộng theo thâm niên, không chuyển phần còn dư sang năm sau và HR không điều chỉnh tay hạn mức.

**Liên hệ tính lương:** Hệ thống tự suy ra cách trả lương từ loại nghỉ: phép năm do doanh nghiệp trả, nghỉ ốm hoặc thai sản theo bảo hiểm xã hội, nghỉ không lương hoặc loại khác mặc định không trả. Người có `request.manage` mới được ghi đè khi có trường hợp nghiệp vụ phù hợp. Nghỉ có lương tăng số phút được trả lương trong bảng công; nghỉ không lương hoặc do bảo hiểm chi trả không tăng số phút này.

### 8. Quản lý chấm công và tăng ca

- **Người sử dụng:** Nhân viên xem bảng công của mình; HR ghi nhận và đối soát giờ chấm; quản lý trực tiếp duyệt tăng ca.
- **Luồng chính:** Hệ thống chuẩn bị bản ghi công cho tháng. Giờ vào/ra lấy từ máy chấm công của công ty và được ghi vào hệ thống qua API ghi nhận giờ chấm; nhân viên không tự chấm trên web mà chỉ xem bảng công của mình. HR xử lý ngày thiếu lần chấm, xem và điều chỉnh dữ liệu thuộc phạm vi quản lý. Người duyệt xác nhận số phút tăng ca và hệ số tương ứng.
- **Quy tắc:** Ngày làm việc chưa có dữ liệu được đánh dấu `MISSING_PUNCH`; tính lương không thể hoàn tất khi kỳ còn trạng thái này. Giờ làm vào Chủ nhật hoặc ngày lễ chỉ được tính trả theo quy trình duyệt tăng ca. Khi kỳ lương đã duyệt, việc sửa dữ liệu công liên quan bị chặn.
- **Kết quả:** Bảng công phản ánh ngày làm, nghỉ và tăng ca đã được đối soát để làm đầu vào cho kỳ lương.

**Các bước trong tháng:**

1. HR chuẩn bị bảng công tháng. Với ngày làm việc chưa có dữ liệu, hệ thống tạo bản ghi `MISSING_PUNCH`; gọi lại thao tác chuẩn bị không tạo trùng.
2. Giờ vào và giờ ra của từng ngày được ghi nhận từ máy chấm công qua `POST /api/attendance/punches` (quyền `attendance.manage`); HR cũng dùng đường này để nhập hoặc sửa giờ chấm kèm ghi chú. Hệ thống đối chiếu ca hiệu lực trong ngày để tính phút đã làm, phút được trả lương, phút đến muộn và phút về sớm.
3. HR xem bản ghi theo nhân viên và thời gian, đối soát những ngày thiếu lần chấm hoặc vắng mặt, nhập số phút và ghi chú hợp lệ. Đơn nghỉ được duyệt có thể tự cập nhật phút nghỉ trên bảng công.
4. Quản lý trực tiếp của nhân viên (nếu không có thì Giám đốc, rồi Chủ doanh nghiệp) duyệt tăng ca, xác nhận số phút, hệ số và việc khoản tăng ca có được đánh dấu miễn thuế hay không. Phút tăng ca được tách khỏi phút công thường.

**Trạng thái ngày công:** Bảng công có thể ghi đi làm, nghỉ có lương, nghỉ không lương, nghỉ thai sản, nghỉ ốm, vắng không phép, ngày lễ hoặc thiếu dữ liệu chấm công. `MISSING_PUNCH` phải được giải quyết trước khi tính lương. Một ngày Chủ nhật hoặc ngày lễ không tự sinh phút công thường dù có chấm vào/ra; tiền tăng ca chỉ phát sinh sau bước duyệt với hệ số tối thiểu tương ứng.

**Giới hạn hiện tại:** Mỗi nhân viên trong một ngày có một cặp chấm vào/ra và tối đa một đơn nghỉ gắn với ngày đó. Việc kết nối trực tiếp với máy chấm công vật lý chưa được triển khai; hiện giờ chấm được đưa vào qua API ghi nhận giờ chấm. Dữ liệu công ảnh hưởng kỳ lương đã `APPROVED` trở đi bị khóa; thay đổi ở kỳ mới tính nhưng chưa duyệt đòi hỏi tính lại.

### 9. Quản lý lương, bảo hiểm và thuế

- **Người sử dụng:** HR nhập dữ liệu nhân sự liên quan đến lương; kế toán lương tính kỳ; người duyệt lương phê duyệt; nhân viên xem phiếu lương của mình.
- **Dữ liệu đầu vào:** Lịch sử lương cơ bản, hồ sơ bảo hiểm và thuế, người phụ thuộc, phân công theo thời gian, bảng công, nghỉ phép, tăng ca được duyệt và các khoản cộng/trừ kế toán nhập theo kỳ.
- **Luồng chính:** Tạo kỳ lương tháng ở trạng thái `DRAFT`; tính lương để sinh phiếu lương (`CALCULATED`); người có quyền riêng phê duyệt (`APPROVED`); xác nhận đã trả (`PAID`); sau đó khóa kỳ (`LOCKED`). Có thể hủy kỳ trước bước duyệt. Hệ thống hỗ trợ xem phiếu lương và xuất bảng lương ra Excel.
- **Cách tính tổng quát:** Tiền lương dựa trên lương cơ bản và số phút được trả lương, cộng phụ cấp chức vụ, phụ cấp thâm niên, tăng ca và các khoản thưởng, phụ cấp khác nhập theo kỳ; sau đó trừ phần bảo hiểm nhân viên phải đóng, thuế thu nhập cá nhân theo quy tắc áp dụng và các khoản khấu trừ. Phiếu lương lưu căn cứ tính và bộ quy tắc đã dùng.
- **Kiểm soát:** Người tính lương và người duyệt lương thuộc hai vai trò tách biệt. Từ khi kỳ được duyệt, phiếu lương là bản chụp không thay đổi và dữ liệu công liên quan không được sửa.
- **Phạm vi hiện tại:** Nhân viên cư trú tại Việt Nam, trả lương bằng VND, kỳ lương tháng từ năm 2020 đến hết năm 2026 theo các quy tắc bảo hiểm và thuế được nạp sẵn; kỳ ngoài khoảng này dừng tính và báo thiếu quy tắc cho tới khi bổ sung.
- **Kết quả:** Kỳ lương, phiếu lương và bảng xuất có thể đối soát theo dữ liệu, quy tắc đã áp dụng.

**Điều kiện trước khi tính:** Mọi nhân viên thuộc kỳ cần có phân công chính và ca hợp lệ trên các ngày làm việc, bảng công đã xử lý hết `MISSING_PUNCH`, mức lương cơ bản có hiệu lực, hồ sơ bảo hiểm và thuế phù hợp. Thiếu dữ liệu ở nhân viên nào, hệ thống báo lỗi để HR hoặc kế toán bổ sung; kỳ không được hoàn tất bằng dữ liệu giả định.

**Nội dung phiếu lương:** Phiếu thể hiện lương cơ bản theo phút được trả lương, phụ cấp chức vụ, phụ cấp thâm niên, tiền tăng ca, thưởng và phụ cấp khác, tổng thu nhập, phần BHXH/BHYT/BHTN nhân viên đóng, thu nhập tính thuế, thuế TNCN, các khoản khấu trừ và số thực nhận. Căn cứ đóng bảo hiểm do HR nhập theo hồ sơ, không tự lấy từ tổng thu nhập. Thuế áp dụng theo quy tắc hiệu lực tại ngày trả lương dự kiến.

**Khoản cộng/trừ theo kỳ:** Kế toán lương (`payroll.calculate`) nhập tay cho từng nhân viên trong kỳ các khoản thưởng (`BONUS`, chịu thuế), phụ cấp khác chịu thuế (`TAXABLE_ALLOWANCE`), phụ cấp khác không chịu thuế (`NON_TAXABLE_ALLOWANCE`) và khấu trừ sau thuế như hoàn tạm ứng hoặc tiền phạt (`DEDUCTION`). Các khoản này chỉ vào phiếu lương qua bước tính lương: thêm hoặc xóa khi kỳ đã `CALCULATED` đưa kỳ về `DRAFT` để tính lại; từ `APPROVED` trở đi chúng bị khóa cùng phiếu lương.

**Quy trình kiểm soát kỳ lương:**

| Trạng thái | Việc thực hiện và ý nghĩa |
| --- | --- |
| `DRAFT` | Kế toán tạo kỳ tháng, nhập ngày trả dự kiến và hoàn thiện dữ liệu nguồn. |
| `CALCULATED` | Hệ thống tính và tạo phiếu; kế toán đối soát số liệu. Dữ liệu đầu vào thay đổi có thể đưa kỳ về `DRAFT`. |
| `APPROVED` | Người có quyền duyệt xác nhận kỳ. Phiếu lương và dữ liệu công liên quan được khóa để giữ bản chụp đã duyệt. |
| `PAID` | Người có quyền ghi nhận ngày thực trả sau khi thanh toán; nếu khác tháng thuế đã duyệt, hệ thống chặn để đối soát. |
| `LOCKED` | Kỳ đã trả được khóa, phục vụ tra cứu và xuất bảng lương. |

**Phân tách trách nhiệm:** `PAYROLL_ACCOUNTANT` được tính kỳ nhưng không duyệt; `PAYROLL_APPROVER` được duyệt nhưng không tính. Nhân viên chỉ xem phiếu của mình từ kỳ đã duyệt trở đi. Người có quyền báo cáo lương ở phạm vi toàn công ty có thể xuất tệp Excel của kỳ đã tính; file lấy từ dữ liệu phiếu, gồm từng nhân viên và dòng tổng tiền. Thưởng và các khoản khác chỉ nhập tay theo kỳ; chức năng hiện chưa tự tính hoa hồng, truy thu, quyết toán thuế năm hoặc xử lý người không cư trú.

### 10. Báo cáo nhân sự và lương

- **Người sử dụng:** Người quản lý, HR và người có quyền xem báo cáo tương ứng.
- **Luồng chính:** Chọn ngày tham chiếu, đơn vị, địa điểm hoặc trạng thái lao động để xem phân bố trình độ, thâm niên và lương cơ bản. Kết quả chỉ tổng hợp những nhân viên thuộc phạm vi vai trò đang có hiệu lực.
- **Quy tắc:** Báo cáo lương trả về số liệu tổng hợp theo khoảng, không hiển thị mức lương của từng cá nhân.
- **Kết quả:** Người quản lý có dữ liệu tổng quan để theo dõi cơ cấu nhân sự và phân bố chi phí lương.

**Bộ lọc và nguồn dữ liệu:** Người dùng có thể chọn ngày thống kê `asOfDate` (mặc định ngày hiện tại), đơn vị, địa điểm và trạng thái lao động. Hệ thống xét hồ sơ, phân công và mức lương cơ bản có hiệu lực tại ngày được chọn, đồng thời áp phạm vi của lần gán vai trò còn hiệu lực. Ngày thống kê không được nằm trong tương lai.

**Hai nhóm báo cáo hiện có:**

| Báo cáo | Chỉ số trả về | Quyền cần có |
| --- | --- | --- |
| Phân bố nhân sự | Tổng số nhân viên, thâm niên trung bình, số lượng theo trình độ và theo nhóm thâm niên. Hồ sơ thiếu trình độ được xếp vào nhóm chưa cập nhật. | `report.hr.read` |
| Phân bố lương cơ bản | Số lượng nhân viên trong từng khoảng lương từ dưới 10 triệu đến từ 30 triệu đồng; hồ sơ thiếu mức lương vào nhóm chưa cấu hình. | `report.payroll.read` |

**Cách sử dụng kết quả:** HR theo dõi cơ cấu trình độ và thâm niên để rà soát chất lượng dữ liệu nhân sự; người quản lý xem cơ cấu các mức lương cơ bản. Báo cáo lương không thay thế bảng lương chi tiết hoặc phiếu lương từng người.

---

**Nguồn đối chiếu:** [Tính năng hiện có](FEATURES.md), [Tổng quan hệ thống](OVERVIEW.md), [Tài liệu API](../api/docs/api/README.md). Nội dung trên mô tả phạm vi hệ thống hiện tại, không bao gồm các chức năng mới chỉ ở mức đề xuất.
