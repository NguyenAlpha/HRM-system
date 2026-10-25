package com.htttdn.hrm.config.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình {@code admin.seed.*} cho {@link SystemAdminSeeder}.
 *
 * <h3>Cách {@code @ConfigurationProperties} hoạt động</h3>
 * Đây là cách Spring Boot khuyến nghị để đọc cấu hình, thay cho việc rải {@code @Value("${...}")}
 * lên từng tham số constructor:
 * <ol>
 *   <li>{@code @ConfigurationProperties("admin.seed")} khai báo prefix. Mỗi thành phần của record
 *       ứng với một key dưới prefix đó; tên camelCase khớp với kebab-case trong file cấu hình:
 *       <pre>
 *   admin.seed.enabled  → enabled()
 *   admin.seed.username → username()
 *   jwt.access-token-expiration-seconds → accessTokenExpirationSeconds()  (xem JwtProperties)
 *       </pre></li>
 *   <li>{@code @ConfigurationPropertiesScan} trên {@code HrmApplication} tự tìm mọi class có
 *       annotation này, đọc giá trị từ {@code application.properties} / biến môi trường, tạo record
 *       và đăng ký nó thành một bean. Không cần {@code @Component} hay {@code @Bean}.</li>
 *   <li>Class cần cấu hình chỉ việc nhận record như mọi bean khác (ở đây qua
 *       {@code @RequiredArgsConstructor}) rồi gọi {@code admin.username()}.</li>
 *   <li>{@code @DefaultValue} là giá trị khi key không có trong cấu hình, tương đương phần sau dấu
 *       {@code :} của {@code @Value("${admin.seed.enabled:false}")}. Thành phần không có
 *       {@code @DefaultValue} mà thiếu key thì nhận giá trị rỗng của kiểu: {@code null},
 *       {@code false} hoặc {@code 0}.</li>
 * </ol>
 * Giá trị sai kiểu (ví dụ {@code admin.seed.enabled=abc}) làm ứng dụng dừng ngay khi khởi động,
 * kèm thông báo chỉ rõ key nào sai. Trong test không có Spring, tạo record trực tiếp:
 * {@code new AdminSeedProperties(true, "admin", "admin@hrm.local", "Admin@123")}.
 *
 * <p>Giá trị thật lấy từ {@code application.properties}, ở đó mỗi key lại đọc biến môi trường,
 * ví dụ {@code admin.seed.password=${ADMIN_SEED_PASSWORD:Admin@123}}.
 *
 * @param enabled  có tạo tài khoản quản trị hệ thống khi khởi động hay không
 * @param username tên đăng nhập của tài khoản quản trị
 * @param email    email của tài khoản quản trị
 * @param password mật khẩu ban đầu; bắt buộc khi {@code enabled}
 */
@ConfigurationProperties("admin.seed")
public record AdminSeedProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("admin") String username,
    @DefaultValue("admin@hrm.local") String email,
    @DefaultValue("") String password
) { }
