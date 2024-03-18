/*
 * Copyright (C) 2023-2024 Fengz Ning (windywany@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.apzda.cloud.audit.facade;

import com.apzda.cloud.audit.domain.repository.AuditLogRepository;
import com.apzda.cloud.audit.proto.*;
import com.apzda.cloud.gsvc.domain.Pager;
import com.apzda.cloud.gsvc.ext.GsvcExt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author fengz (windywany@gmail.com)
 * @version 1.0.0
 * @since 1.0.0
 **/
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final TypeReference<List<Arg>> ARG_TYPE_HINT = new TypeReference<List<Arg>>() {
    };

    private final ObjectMapper objectMapper;

    private final AuditLogRepository auditLogRepository;

    @Override
    public GsvcExt.CommonRes log(AuditLog request) {
        val builder = GsvcExt.CommonRes.newBuilder();
        builder.setErrCode(0);
        val entity = new com.apzda.cloud.audit.domain.entity.AuditLog();
        entity.setTenantId(StringUtils.defaultIfBlank(request.getTenantId(), "0"));
        entity.setUserId(request.getUserid());
        entity.setLogTime(request.getTimestamp());
        entity.setActivity(request.getActivity());
        entity.setIp(request.getIp());
        entity.setMessage(request.getMessage());
        entity.setTemplate(request.getTemplate());
        entity.setLevel(StringUtils.defaultIfBlank(request.getLevel(), "info"));
        if (request.getArgCount() > 0) {
            try {
                entity.setArgs(objectMapper.writeValueAsString(request.getArgList()));
            }
            catch (Exception e) {
                log.warn("Cannot serialize args: {}", request.getArgList());
            }
        }
        entity.setOldValue(request.getOldJsonValue());
        entity.setNewValue(request.getNewJsonValue());
        val mEntity = auditLogRepository.save(entity);
        if (mEntity.getId() == null) {
            builder.setErrCode(503);
            builder.setErrMsg("Cannot save audit log");
            log.error("Cannot save audit log: {}", entity);
        }
        return builder.build();
    }

    @Override
    @PreAuthorize("hasAuthority('view:auditlog')")
    public QueryRes logs(Query request) {
        val pager = request.getPager();
        val pr = Pager.of(pager);
        val logs = auditLogRepository
            .findAll((Specification<com.apzda.cloud.audit.domain.entity.AuditLog>) (root, query, builder) -> {
                val cons = new ArrayList<Predicate>();
                cons.add(builder.equal(root.<Boolean>get("deleted"), false));
                if (request.hasUserId()) {
                    cons.add(builder.equal(root.<String>get("userId"), request.getUserId()));
                }
                if (request.hasActivity()) {
                    cons.add(builder.equal(root.<String>get("activity"), request.getActivity()));
                }
                if (request.hasTenantId()) {
                    cons.add(builder.equal(root.<String>get("tenantId"), request.getTenantId()));
                }
                if (request.hasStartTime()) {
                    cons.add(builder.ge(root.<Long>get("logTime"), request.getStartTime()));
                }
                if (request.hasEndTime()) {
                    cons.add(builder.lt(root.<Long>get("logTime"), request.getEndTime()));
                }
                return builder.and(cons.toArray(new Predicate[0]));
            }, pr);

        val pageInfo = Pager.of(logs);
        val builder = QueryRes.newBuilder();
        builder.setPager(pageInfo);
        builder.addAllLog(logs.getContent().stream().map((lg) -> {
            val args = lg.getArgs();
            val newValue = lg.getNewValue();
            val oldValue = lg.getOldValue();
            val bd = AuditLog.newBuilder();
            bd.setId(lg.getId());
            bd.setTimestamp(lg.getLogTime());
            bd.setUserid(lg.getUserId());
            bd.setActivity(lg.getActivity());
            bd.setMessage(lg.getMessage());
            bd.setTenantId(lg.getTenantId());
            bd.setIp(lg.getIp());
            bd.setLevel(lg.getLevel());
            bd.setTemplate(lg.isTemplate());
            if (newValue != null) {
                bd.setNewJsonValue(newValue);
            }
            if (oldValue != null) {
                bd.setOldJsonValue(oldValue);
            }
            if (StringUtils.isNotBlank(args)) {
                try {
                    bd.addAllArg(objectMapper.readValue(args, ARG_TYPE_HINT));
                }
                catch (JsonProcessingException e) {
                    log.warn("Cannot deserialize args: {} - {}", args, e.getMessage());
                }
            }
            return bd.build();
        }).toList());

        return builder.build();
    }

}