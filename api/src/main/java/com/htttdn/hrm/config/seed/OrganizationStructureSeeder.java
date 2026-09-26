package com.htttdn.hrm.config.seed;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.OrganizationUnit;
import com.htttdn.hrm.entity.WorkLocation;
import com.htttdn.hrm.entity.enums.LocationType;
import com.htttdn.hrm.entity.enums.OrganizationUnitType;
import com.htttdn.hrm.repository.OrganizationUnitRepository;
import com.htttdn.hrm.repository.WorkLocationRepository;

/**
 * Khởi tạo cơ cấu tổ chức và địa điểm làm việc tối thiểu cho doanh nghiệp mới.
 *
 * <p>Seeder chỉ tạo code còn thiếu và không ghi đè dữ liệu đã được doanh nghiệp chỉnh sửa.
 * Parent luôn được bảo đảm trước child để quan hệ cây hợp lệ ngay từ lần khởi tạo đầu tiên.
 */
@Component
@Order(60)
public class OrganizationStructureSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrganizationStructureSeeder.class);

    private final WorkLocationRepository workLocationRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final boolean enabled;

    public OrganizationStructureSeeder(
        WorkLocationRepository workLocationRepository,
        OrganizationUnitRepository organizationUnitRepository,
        @Value("${company.seed.enabled:true}") boolean enabled
    ) {
        this.workLocationRepository = workLocationRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        seedWorkLocations();
        seedOrganizationUnits();
        log.info("Organization structure seed completed: 5 work locations and 4 organization units ensured");
    }

    private void seedWorkLocations() {
        WorkLocation headOffice = findOrCreateLocation(
            "HO",
            "Trụ sở chính",
            LocationType.HEAD_OFFICE,
            "Thành phố Hồ Chí Minh",
            null
        );
        WorkLocation branchOne = findOrCreateLocation(
            "BRANCH-01",
            "Chi nhánh Hà Nội",
            LocationType.BRANCH,
            "Thành phố Hà Nội",
            null
        );
        findOrCreateLocation(
            "BRANCH-02",
            "Chi nhánh Đà Nẵng",
            LocationType.BRANCH,
            "Thành phố Đà Nẵng",
            null
        );
        findOrCreateLocation(
            "WAREHOUSE-01",
            "Kho trụ sở chính",
            LocationType.WAREHOUSE,
            "Thành phố Hồ Chí Minh",
            headOffice
        );
        findOrCreateLocation(
            "WAREHOUSE-02",
            "Kho chi nhánh Hà Nội",
            LocationType.WAREHOUSE,
            "Thành phố Hà Nội",
            branchOne
        );
    }

    private void seedOrganizationUnits() {
        OrganizationUnit board = findOrCreateUnit(
            "BOARD",
            "Ban giám đốc",
            OrganizationUnitType.BOARD,
            null
        );
        findOrCreateUnit("HR", "Phòng Nhân sự", OrganizationUnitType.DEPARTMENT, board);
        findOrCreateUnit("ACCOUNTING", "Phòng Kế toán", OrganizationUnitType.DEPARTMENT, board);
        findOrCreateUnit("OPERATIONS", "Phòng Vận hành", OrganizationUnitType.DEPARTMENT, board);
    }

    private WorkLocation findOrCreateLocation(
        String code,
        String name,
        LocationType type,
        String address,
        WorkLocation parent
    ) {
        return workLocationRepository.findByCodeAndDeletedAtIsNull(code)
            .orElseGet(() -> {
                Instant now = Instant.now();
                return workLocationRepository.save(WorkLocation.builder()
                    .code(code)
                    .name(name)
                    .locationType(type)
                    .address(address)
                    .parentLocation(parent)
                    .isActive(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            });
    }

    private OrganizationUnit findOrCreateUnit(
        String code,
        String name,
        OrganizationUnitType type,
        OrganizationUnit parent
    ) {
        return organizationUnitRepository.findByCodeAndDeletedAtIsNull(code)
            .orElseGet(() -> {
                Instant now = Instant.now();
                return organizationUnitRepository.save(OrganizationUnit.builder()
                    .code(code)
                    .name(name)
                    .unitType(type)
                    .parentUnit(parent)
                    .isActive(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            });
    }
}
