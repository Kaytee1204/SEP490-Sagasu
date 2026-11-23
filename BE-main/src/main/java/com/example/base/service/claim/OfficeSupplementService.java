package com.example.base.service.claim;

import com.example.base.security.CustomUserDetails;
import com.example.base.service.report.WorkflowImages;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class OfficeSupplementService {
    private final OfficeClaimService claims;
    private final WorkflowImages images;
    private final JdbcTemplate jdbc;

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
