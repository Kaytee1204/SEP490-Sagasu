package com.example.base.service.claim;

import com.example.base.dto.office.OfficeDtos.Supplement;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.report.WorkflowImages;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service @RequiredArgsConstructor
public class OfficeSupplementService {
    private final OfficeClaimService claims;
    private final WorkflowImages images;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;

    public void submit(long claimId, Supplement request, List<MultipartFile> files, CustomUserDetails user) {
        // Authorize before uploading; the locked claim/version is checked again inside the transaction.
        var claim = claims.detail(claimId, user);
        if (!claim.ownRequest()) throw new com.example.base.exception.AppException(com.example.base.exception.ErrorCode.FORBIDDEN);
        var uploaded = images.upload("claims/supplements/" + claimId, images.prepare(request.information(), files));
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                claims.supplement(claimId, request, user);
                // Flush JPA's activity insert before reading it through JDBC in the same transaction.
                entityManager.flush();
                Long activity = jdbc.queryForObject("select claim_activity_id from claim_activity where claim_id=? and action='RESUBMIT' order by claim_activity_id desc limit 1", Long.class, claimId);
                for (var image : uploaded) jdbc.update("insert into office_claim_supplement_image(activity_id,storage_key,original_name,content_hash,sort_order) values(?,?,?,?,?)",
                    activity, image.key(), image.name(), image.hash(), image.order());
            });
        } catch (RuntimeException failure) { images.cleanup(uploaded); throw failure; }
    }
    private final jakarta.persistence.EntityManager entityManager;

    @Transactional(readOnly=true)
    public List<Map<String,Object>> history(long claimId, CustomUserDetails user) {
        claims.detail(claimId, user);
        var rows = jdbc.queryForList("select a.claim_activity_id as activity_id,a.reason as information,a.created_at,p.full_name as actor," +
            "(select q.reason from claim_activity q where q.claim_id=a.claim_id and q.action='REQUEST_INFO' and q.claim_activity_id<a.claim_activity_id order by q.claim_activity_id desc limit 1) as question " +
            "from claim_activity a join accounts p on p.id=a.actor_id where a.claim_id=? and a.action='RESUBMIT' order by a.claim_activity_id desc", claimId);
        for (var row : rows) {
            var evidence = jdbc.queryForList("select image_id,storage_key,original_name from office_claim_supplement_image where activity_id=? order by sort_order", row.get("activity_id"));
            evidence.forEach(image -> image.put("url", images.read((String)image.remove("storage_key"))));
            row.put("images", evidence);
        }
        return rows;
    }
}
