package com.htttdn.hrm.config.seed;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Hibernate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import com.htttdn.hrm.entity.CreatedAtEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;

import lombok.RequiredArgsConstructor;

/**
 * Lùi {@code created_at} / {@code updated_at} của dữ liệu mẫu về quá khứ.
 *
 * <p>Spring Data auditing (xem {@link CreatedAtEntity}) luôn ghi thời điểm hiện tại khi lưu, đúng cho
 * dữ liệu thật. Dữ liệu mẫu lại mô phỏng nhiều năm sử dụng, nên seeder lưu entity như bình thường rồi
 * báo ngày mong muốn ở đây; {@link #apply()} sau đó ghi đè bằng SQL, đi thẳng xuống database nên
 * auditing không can thiệp.
 *
 * <p>Ngày chỉ được ghi khi gọi {@link #apply()}, không ghi ngay, vì:
 * <ul>
 *   <li>seeder thường còn sửa entity sau khi lưu (ví dụ ngày nghỉ phép ghi đè chấm công); lần flush của
 *       lần sửa đó sẽ ghi {@code updated_at} = hiện tại, nên phải lùi ngày sau lần flush cuối cùng;</li>
 *   <li>hàng chục nghìn dòng chấm công được ghi một lần bằng {@code batchUpdate} thay vì từng câu một.</li>
 * </ul>
 * Mỗi seeder gọi {@link #apply()} ở cuối {@code run()}, trong transaction của nó. Báo nhiều lần cho cùng
 * một entity thì giá trị khác {@code null} báo sau cùng thắng.
 */
@Component
@RequiredArgsConstructor
class SeedTimestamps {

    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;

    /** Ngày đã báo, theo bảng rồi theo id, chờ {@link #apply()}. */
    private final Map<String, Map<Object, Instant[]>> pending = new LinkedHashMap<>();

    /** Entity được tạo (và sửa lần cuối, nếu có cột {@code updated_at}) lúc {@code at}. */
    void at(Object entity, Instant at) {
        set(entity, at, at);
    }

    /**
     * @param createdAt ngày tạo mong muốn, {@code null} để giữ giá trị đã báo trước đó (hoặc của auditing)
     * @param updatedAt ngày sửa cuối mong muốn, {@code null} tương tự; bỏ qua với entity không có cột này
     */
    void set(Object entity, Instant createdAt, Instant updatedAt) {
        Object id = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
        if (id == null) {
            throw new IllegalStateException("Seed timestamps: save " + entity.getClass().getSimpleName() + " first");
        }
        Class<?> type = Hibernate.getClass(entity);
        if (createdAt != null && ReflectionUtils.findField(type, "createdAt") == null) {
            throw new IllegalStateException("Seed timestamps: " + type.getSimpleName() + " has no createdAt");
        }
        Instant[] dates = pending.computeIfAbsent(table(entity), key -> new LinkedHashMap<>())
            .computeIfAbsent(id, key -> new Instant[2]);
        if (createdAt != null) {
            dates[0] = createdAt;
        }
        if (updatedAt != null && ReflectionUtils.findField(type, "updatedAt") != null) {
            dates[1] = updatedAt;
        }
    }

    /** Ngày tạo đã báo cho entity, hoặc ngày auditing đã ghi nếu chưa báo. */
    Instant createdAt(CreatedAtEntity entity) {
        Object id = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
        Instant[] dates = pending.getOrDefault(table(entity), Map.of()).get(id);
        return dates != null && dates[0] != null ? dates[0] : entity.getCreatedAt();
    }

    /** Ghi mọi ngày đã báo xuống database rồi xóa danh sách chờ. */
    void apply() {
        entityManager.flush();
        pending.forEach((table, rows) -> {
            update(table, "created_at", rows, 0);
            update(table, "updated_at", rows, 1);
        });
        pending.clear();
        // Entity trong bộ nhớ vẫn giữ ngày cũ; bỏ chúng để lần đọc sau lấy lại từ database.
        entityManager.clear();
    }

    private void update(String table, String column, Map<Object, Instant[]> rows, int index) {
        List<Object[]> args = new ArrayList<>();
        rows.forEach((id, dates) -> {
            if (dates[index] != null) {
                args.add(new Object[] {Timestamp.from(dates[index]), id});
            }
        });
        if (!args.isEmpty()) {
            jdbc.batchUpdate("UPDATE " + table + " SET " + column + " = ? WHERE id = ?", args);
        }
    }

    private static String table(Object entity) {
        return Hibernate.getClass(entity).getAnnotation(Table.class).name();
    }
}
