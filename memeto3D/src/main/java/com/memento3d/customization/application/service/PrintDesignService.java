package com.memento3d.customization.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.models.application.service.ModelDeliveryService;
import com.memento3d.models.infrastructure.persistence.repository.ModelOutputRepository;
import com.memento3d.orders.domain.model.*;
import com.memento3d.orders.infrastructure.persistence.repository.OrderRepository;
import com.memento3d.modelreview.domain.model.ModelVersion;
import com.memento3d.modelreview.infrastructure.persistence.repository.ModelVersionRepository;
import com.memento3d.assets.domain.port.ModelStoragePort;
import com.memento3d.scans.domain.model.Scan;
import com.memento3d.shared.exception.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class PrintDesignService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final OrderRepository orders;
    private final ModelOutputRepository outputs;
    private final ModelVersionRepository versions;
    private final ModelDeliveryService delivery;
    private final EntityManager em;

    public record Export(UUID id,UUID orderId,UUID itemId,FrozenDesign snapshot,int heightCm,UUID lease) {}
    public record View(UUID id,UUID orderId,UUID orderItemId,UUID modelOutputId,UUID fitResultId,long revision,
        String status,int requestedHeightCm,Map<String,UUID> selection,List<Map<String,Object>> assets,
        String snapshotSha256,String artifactSha256,Long artifactSizeBytes,String downloadUrl,UUID modelVersionId,
        boolean approvedForProduction,boolean readyForMachine,String errorCode,String reviewNotes) {}

    public static BusinessException conflict(String code,String message) {return new BusinessException(HttpStatus.CONFLICT,code,message);}
    public static String sha(byte[] bytes) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public String json(Object value) {try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private FrozenDesign read(String json) {try{return mapper.readValue(json,FrozenDesign.class);}catch(Exception e){throw conflict("CUSTOM_PRINT_SNAPSHOT_INVALID","Invalid frozen design");}}

    /** Called inside the order transaction, after the order item has its permanent ID. */
    public void attach(Order order, OrderItem item, FrozenDesign snapshot, int height) {

        // Validate persisted Order
        if (order == null || order.getId() == null) {
            throw new IllegalStateException(
                    "Cannot attach custom design: Order has not been persisted"
            );
        }

        // Validate persisted OrderItem
        if (item == null || item.getId() == null) {
            throw new IllegalStateException(
                    "Cannot attach custom design: OrderItem has not been persisted"
            );
        }

        // Ensure OrderItem belongs to this Order
        if (item.getOrder() == null ||
                !order.getId().equals(item.getOrder().getId())) {
            throw new IllegalStateException(
                    "Cannot attach custom design: OrderItem belongs to another Order"
            );
        }

        String content = json(snapshot);

        jdbc.update(
                """
                INSERT INTO order_custom_designs (
                    id,
                    order_id,
                    order_item_id,
                    model_output_id,
                    snapshot_json,
                    snapshot_sha256,
                    requested_height_cm
                )
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                """,
                UUID.randomUUID(),
                order.getId(),
                item.getId(),
                snapshot.modelOutputId(),
                content,
                sha(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                height
        );
    }
    public boolean isCustom(UUID orderId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM order_custom_designs WHERE order_id=?)",Boolean.class,orderId));
    }
    public void lockCheckout(UUID userId) {jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",UUID.class,userId);}
    public void assertReplay(Order order,com.memento3d.orders.api.request.CreateOrderRequest request) {
        var r=row(order.getId());var frozen=read(r.get("snapshot_json").toString());
        if(!frozen.modelOutputId().equals(request.getModelOutputId()) || !frozen.fitResultId().equals(request.getFitResultId())
            || !Objects.equals(frozen.revision(),request.getCustomizationRevision())
            || !Objects.equals(((Number)r.get("requested_height_cm")).intValue(),request.getRequestedHeightCm())
            || !Objects.equals(order.getRecipientName(),request.getRecipientName()) || !Objects.equals(order.getPhone(),request.getPhone())
            || !Objects.equals(order.getShippingAddress(),request.getShippingAddress()) || !Objects.equals(order.getNote(),request.getNote()))
            throw conflict("IDEMPOTENCY_PAYLOAD_MISMATCH","This checkout key was already used for another request");
    }
    private Map<String,Object> row(UUID orderId) {
        var rows=jdbc.queryForList("SELECT * FROM order_custom_designs WHERE order_id=?",orderId);
        if(rows.isEmpty())throw new ResourceNotFoundException("Custom order design not found");return rows.get(0);
    }
    private Order authorize(UUID orderId,UUID user,Role role) {
        var order=orders.findById(orderId).orElseThrow(()->new ResourceNotFoundException("Order not found"));
        if(!order.getUserId().equals(user)&&!staff(role))throw new BusinessException(HttpStatus.FORBIDDEN,"ORDER_ACCESS_DENIED","Order belongs to another customer");
        return order;
    }
    private boolean staff(Role role) {return role==Role.ROLE_ADMIN||role==Role.ROLE_MANAGER||role==Role.ROLE_OPERATOR;}
    @Transactional(readOnly=true)
    public View view(UUID orderId,UUID user,Role role) {
        authorize(orderId,user,role);var row=row(orderId);var frozen=read(row.get("snapshot_json").toString());
        String status=(String)row.get("status");String url=row.get("location")==null?null:delivery.url((String)row.get("provider"),(String)row.get("object_key"),(String)row.get("location"));
        var assets=frozen.assets().stream().map(a->Map.<String,Object>of("assetVersionId",a.assetVersionId(),"name",a.name(),"slot",a.slot())).toList();
        return new View((UUID)row.get("id"),orderId,(UUID)row.get("order_item_id"),frozen.modelOutputId(),frozen.fitResultId(),frozen.revision(),
            status,((Number)row.get("requested_height_cm")).intValue(),frozen.selection(),assets,(String)row.get("snapshot_sha256"),
            (String)row.get("artifact_sha256"),row.get("artifact_size_bytes")==null?null:((Number)row.get("artifact_size_bytes")).longValue(),url,
            (UUID)row.get("model_version_id"),"APPROVED".equals(status),false,(String)row.get("error_code"),(String)row.get("review_notes"));
    }
    @Transactional
    public View retry(UUID orderId,UUID user,Role role) {
        var order=authorize(orderId,user,role);
        if(order.getStatus()==OrderStatus.CANCELLED||order.getStatus()==OrderStatus.REFUNDED)throw conflict("ORDER_CLOSED","Order is closed");
        jdbc.update("UPDATE order_custom_designs SET status='QUEUED',error_code=NULL,updated_at=now() WHERE order_id=? AND status='FAILED'",orderId);
        return view(orderId,user,role);
    }
    @Transactional
    public View review(UUID orderId,boolean approved,String notes,UUID user,Role role) {
        if(!staff(role))throw new BusinessException(HttpStatus.FORBIDDEN,"STAFF_REQUIRED","Workshop review is required");
        var order=authorize(orderId,user,role);
        if(order.getStatus()==OrderStatus.CANCELLED||order.getStatus()==OrderStatus.REFUNDED)throw conflict("ORDER_CLOSED","Order is closed");
        var row=jdbc.queryForMap("SELECT * FROM order_custom_designs WHERE order_id=? FOR UPDATE",orderId);
        if(!List.of("READY","REJECTED","APPROVED").contains(row.get("status")))throw conflict("CUSTOM_PRINT_NOT_READY","Export must finish before review");
        if("APPROVED".equals(row.get("status")))return view(orderId,user,role);
        if(notes==null||notes.trim().length()<10)throw conflict("PRINT_REVIEW_NOTES_REQUIRED","Record fit, geometry, scale and printability checks");
        var version=versions.findById((UUID)row.get("model_version_id")).orElseThrow();
        version.setStatus(approved?"APPROVED":"REVISION_REQUESTED");versions.save(version);
        jdbc.update("UPDATE order_custom_designs SET status=?,review_notes=?,reviewed_by=?,reviewed_at=now(),updated_at=now() WHERE order_id=?",
            approved?"APPROVED":"REJECTED",notes.trim(),user,orderId);
        return view(orderId,user,role);
    }
    public void requireReviewed(UUID orderId) {
        if(isCustom(orderId)&&!"APPROVED".equals(row(orderId).get("status")))throw conflict("CUSTOM_PRINT_REVIEW_REQUIRED","Approve the exact exported order design before quoting or printing");
    }
    public UUID requirePrintable(UUID orderId,UUID itemId,UUID requestedVersion) {
        if(!isCustom(orderId))return null;
        var r=row(orderId);requireReviewed(orderId);
        if(!Objects.equals(itemId,r.get("order_item_id")))throw conflict("CUSTOM_PRINT_ITEM_MISMATCH","Select the frozen custom order item");
        UUID version=(UUID)r.get("model_version_id");
        if(version==null || !versions.findById(version).map(ModelVersion::isApproved).orElse(false))throw conflict("CUSTOM_PRINT_REVIEW_REQUIRED","The frozen model version is not approved");
        if(requestedVersion!=null&&!requestedVersion.equals(version))throw conflict("CUSTOM_PRINT_VERSION_MISMATCH","Print the exact version frozen for this order");
        return version;
    }
    public void validateVersionBinding(UUID versionId,UUID orderId) {
        if(versionId==null)return;
        var rows=jdbc.queryForList("SELECT order_id FROM order_custom_designs WHERE model_version_id=?",versionId);
        if(!rows.isEmpty()&&!Objects.equals(rows.get(0).get("order_id"),orderId))throw conflict("CUSTOM_PRINT_VERSION_MISMATCH","This file belongs to another frozen order");
    }
    public boolean isFrozenVersion(UUID versionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM order_custom_designs WHERE model_version_id=?)",Boolean.class,versionId));
    }
    @Transactional
    public Export claim() {
        UUID token=UUID.randomUUID();
        var rows=jdbc.queryForList("""
            WITH candidate AS (
              SELECT d.id FROM order_custom_designs d JOIN print_orders o ON o.id=d.order_id
              WHERE (d.status='QUEUED' OR (d.status='EXPORTING' AND d.updated_at < now()-interval '20 minutes'))
                AND o.status NOT IN ('CANCELLED','REFUNDED') ORDER BY d.created_at FOR UPDATE OF d SKIP LOCKED LIMIT 1
            ) UPDATE order_custom_designs SET status='EXPORTING',lease_token=?,updated_at=now()
            WHERE id IN (SELECT id FROM candidate) RETURNING *
            """,token);
        if(rows.isEmpty())return null;var r=rows.get(0);
        return new Export((UUID)r.get("id"),(UUID)r.get("order_id"),(UUID)r.get("order_item_id"),read(r.get("snapshot_json").toString()),((Number)r.get("requested_height_cm")).intValue(),token);
    }
    @Transactional
    public void finish(Export job,ModelStoragePort.StoredModel stored,String digest) {
        var r=jdbc.queryForMap("SELECT * FROM order_custom_designs WHERE id=? FOR UPDATE",job.id());
        if(!job.lease().equals(r.get("lease_token"))||!"EXPORTING".equals(r.get("status")))return;
        // Serialize version numbering across exports of the same scan.
        var scan=em.find(Scan.class,job.snapshot().scanId(),LockModeType.PESSIMISTIC_WRITE);
        var output=outputs.findSceneOutput(job.snapshot().modelOutputId()).orElseThrow();
        int next=versions.findFirstByScan_IdOrderByVersionNumberDesc(scan.getId()).map(v->v.getVersionNumber()+1).orElse(1);
        var version=versions.saveAndFlush(ModelVersion.builder().scan(scan).job(output.getJob()).versionNumber(next)
            .modelUrl(stored.location()).status("AWAITING_APPROVAL").build());
        jdbc.update("UPDATE order_custom_designs SET status='READY',provider=?,object_key=?,location=?,artifact_sha256=?,artifact_size_bytes=?,model_version_id=?,error_code=NULL,updated_at=now() WHERE id=?",
            stored.provider(),stored.objectKey(),stored.location(),digest,stored.sizeBytes(),version.getId(),job.id());
    }
    @Transactional
    public void fail(Export job,String code) {
        jdbc.update("UPDATE order_custom_designs SET status='FAILED',error_code=?,updated_at=now() WHERE id=? AND lease_token=? AND status='EXPORTING'",code,job.id(),job.lease());
    }
}
