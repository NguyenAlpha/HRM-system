package com.htttdn.hrm.service.employee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.htttdn.hrm.entity.Employee;
import com.htttdn.hrm.repository.LeaveRequestRepository;
import com.htttdn.hrm.repository.ResignationRequestRepository;

import lombok.RequiredArgsConstructor;

/**
 * Moves the pending leave and resignation requests of an approver who can no longer act on them (their
 * employment ended, or their account was suspended or deleted) to the approver {@link ApproverResolver}
 * picks now. Only the stored approver can decide a request, so without this the requests would wait
 * for nobody. Call it after the change that stops the approver, so the resolver already skips them.
 *
 * <p>A request whose employee has nobody else to approve it keeps its approver and is logged; the
 * employee can cancel and resubmit it once someone can approve.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PendingApprovalRerouter {

    private static final Logger log = LoggerFactory.getLogger(PendingApprovalRerouter.class);

    private final LeaveRequestRepository leaveRequestRepository;
    private final ResignationRequestRepository resignationRequestRepository;
    private final ApproverResolver approverResolver;

    public void rerouteFrom(Employee approver) {
        leaveRequestRepository.findPendingByApproverForUpdate(approver.getId()).forEach(request ->
            approverResolver.approverFor(request.getEmployee()).ifPresentOrElse(
                request::setApproverEmployee,
                () -> log.warn("Leave request {} keeps approver {}: nobody else can approve it",
                    request.getId(), approver.getId())));
        resignationRequestRepository.findPendingByApproverForUpdate(approver.getId()).forEach(request ->
            approverResolver.approverFor(request.getEmployee()).ifPresentOrElse(
                request::setApproverEmployee,
                () -> log.warn("Resignation request {} keeps approver {}: nobody else can approve it",
                    request.getId(), approver.getId())));
    }
}
