package com.memento3d.customization.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.customization.api.request.SaveCustomizationRequest;
import com.memento3d.customization.api.response.*;
import com.memento3d.customization.domain.model.*;
import com.memento3d.customization.infrastructure.persistence.repository.*;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.models.application.service.ModelDeliveryService;
import com.memento3d.models.domain.model.ModelOutput;
import com.memento3d.models.infrastructure.persistence.repository.ModelOutputRepository;
import com.memento3d.reconstruction.application.dto.AiCustomizationManifest;
import com.memento3d.scans.application.service.ScanAccessGuard;
import com.memento3d.scans.domain.model.ScanStatus;
import com.memento3d.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class ModelCustomizationService {
    private final ModelOutputRepository outputs;
    private final CustomizationFitResultRepository fits;
    private final CustomizationFitAssetRepository fitAssets;
    private final CustomizationCatalogRepository catalogs;
    private final CustomizationCatalogMemberRepository members;
    private final CustomizationDraftRepository drafts;
    private final ScanAccessGuard access;
    private final ModelDeliveryService delivery;
    private final ObjectMapper mapper;

    private record Context(ModelOutput output,CustomizationFitResult fit,CustomizationCatalog catalog,
        AiCustomizationManifest snapshot,Map<UUID,CustomizationAssetVersion> versions,
        Map<UUID,CustomizationFitAsset> placements,List<ModelCustomizationSceneResponse.Group> groups,
        Map<String,UUID> defaults,boolean owner) {}

    @Transactional
    public FrozenDesign freeze(UUID outputId,UUID fitId,Long revision,UUID userId) {
        var c=context(outputId,userId,Role.ROLE_USER,true);
        if(!c.owner())throw error(HttpStatus.FORBIDDEN,"CUSTOMIZATION_OWNER_REQUIRED","Only the owner can order this design");
        var saved=draftResponse(c,drafts.findByModelOutputId(outputId).orElse(null));
        if(!saved.exists())throw error(HttpStatus.CONFLICT,"CUSTOMIZATION_SAVE_REQUIRED","Save the design before ordering");
        if(!Objects.equals(fitId,c.fit().getId()) || revision==null || revision!=saved.revision())throw conflict();
        var output=c.output();
        var base=new FrozenDesign.Artifact(null,"Body and personalized head","base",output.getStorageProvider(),
            output.getStoragePublicId(),output.getDeliveryUrl(),output.getSha256(),output.getFileSizeBytes(),null);
        List<FrozenDesign.Artifact> selected=new ArrayList<>();
        for(var id:saved.selection().values())if(id!=null) {
            var version=c.versions().get(id);var fit=c.placements().get(id);
            selected.add(new FrozenDesign.Artifact(id,version.getName(),version.getSlot(),fit.getStorageProvider(),
                fit.getObjectKey(),fit.getLocation(),fit.getSha256(),fit.getFileSizeBytes(),matrix(fit.getMatrixJson())));
        }
        return new FrozenDesign(outputId,c.fit().getId(),userId,c.fit().getScanId(),output.getJob().getId(),output.getAttemptId(),
            saved.revision(),c.catalog().getCode(),c.catalog().getBodyPreset(),new LinkedHashMap<>(saved.selection()),base,List.copyOf(selected));
    }

    @Transactional(readOnly=true)
    public ModelCustomizationSceneResponse scene(UUID outputId,UUID userId,Role role) {
        var c=context(outputId,userId,role,false);
        List<ModelCustomizationSceneResponse.Asset> choices=new ArrayList<>();
        for(var group:c.groups())for(UUID id:group.assetVersionIds()) {
            var version=c.versions().get(id);var placement=c.placements().get(id);
            choices.add(new ModelCustomizationSceneResponse.Asset(id,version.getAssetCode(),version.getVersion(),version.getName(),
                version.getKind(),version.getSlot(),placement.getGeometryMode(),delivery.url(placement.getStorageProvider(),placement.getObjectKey(),placement.getLocation()),
                placement.getSha256(),version.getSha256(),placement.getFileSizeBytes(),version.getThumbnailUrl(),matrix(placement.getMatrixJson())));
        }
        long ttl=delivery.urlTtlSeconds();
        return new ModelCustomizationSceneResponse(outputId,c.output().getJob().getId(),c.output().getAttemptId(),c.fit().getId(),
            c.catalog().getCode(),c.catalog().getBodyPreset(),c.snapshot().qualityStatus(),false,c.snapshot().coordinateSystem(),
            new ModelCustomizationSceneResponse.BaseModel(delivery.url(c.output()),c.output().getSha256(),c.output().getFileSizeBytes()),
            c.groups(),choices,c.defaults(),draftResponse(c,drafts.findByModelOutputId(outputId).orElse(null)),ttl,Instant.now().plusSeconds(ttl));
    }

    @Transactional(readOnly=true)
    public CustomizationDraftResponse getDraft(UUID outputId,UUID userId,Role role) {
        var c=context(outputId,userId,role,false);
        return draftResponse(c,drafts.findByModelOutputId(outputId).orElse(null));
    }

    @Transactional
    public CustomizationDraftResponse save(UUID outputId,SaveCustomizationRequest request,UUID userId,Role role) {
        // Lock the immutable output to serialize both first-insert races and updates.
        var c=context(outputId,userId,role,true);
        if(!c.owner())throw error(HttpStatus.FORBIDDEN,"CUSTOMIZATION_OWNER_REQUIRED","Only the owner can change this design");
        if(request==null || request.fitResultId()==null || request.revision()==null || request.revision()<0 || request.selection()==null)
            throw invalidSelection();
        if(!c.fit().getId().equals(request.fitResultId()))throw conflict();
        Map<String,UUID> selection=validateSelection(c,request.selection());
        var draft=drafts.findByModelOutputId(outputId).orElse(null);
        var current=draftResponse(c,draft);
        if(draft!=null && selection.equals(current.selection()) && request.revision()<=current.revision())return current;
        if(request.revision()!=current.revision())throw conflict();
        if(!"ACTIVE".equals(c.catalog().getStatus()))throw error(HttpStatus.CONFLICT,"CATALOG_ARCHIVED","This catalog can be viewed but no longer edited");
        Instant now=Instant.now();
        if(draft==null) {
            draft=new CustomizationDraft();draft.setModelOutputId(outputId);draft.setFitResultId(c.fit().getId());
            draft.setUserId(userId);draft.setCreatedAt(now);
        }
        draft.setRevision(current.revision()+1);draft.setSelectionJson(json(selection));draft.setUpdatedAt(now);
        drafts.saveAndFlush(draft);
        return draftResponse(c,draft);
    }

    private Context context(UUID outputId,UUID userId,Role role,boolean write) {
        var output=(write?outputs.findSceneOutputForUpdate(outputId):outputs.findSceneOutput(outputId))
            .orElseThrow(()->new ResourceNotFoundException("Model output not found"));
        var scan=output.getJob().getScan();access.requireAccess(scan,userId,role);
        if(scan.getDeletedAt()!=null || scan.getStatus()==ScanStatus.deleted)throw new ResourceNotFoundException("Model output not found");
        if(!"glb".equalsIgnoreCase(output.getFormat()))throw unavailable();
        // Bind to the output's attempt, never the current/latest job attempt.
        var fit=fits.findByJobIdAndAttemptId(output.getJob().getId(),output.getAttemptId()).orElseThrow(this::unavailable);
        if(!Objects.equals(fit.getUserId(),scan.getUser().getId()) || !Objects.equals(fit.getScanId(),scan.getId())
            || !Objects.equals(fit.getBaseSha256(),output.getSha256()))throw invalidData();
        var catalog=catalogs.findById(fit.getCatalogId()).orElseThrow(this::unavailable);
        AiCustomizationManifest snapshot=read(fit.getSceneJson(),AiCustomizationManifest.class);
        if(!Objects.equals(snapshot.jobId(),output.getJob().getId()) || !Objects.equals(snapshot.attemptId(),output.getAttemptId())
            || snapshot.baseModel()==null || !Objects.equals(snapshot.baseModel().sha256(),output.getSha256()))throw invalidData();
        Map<UUID,CustomizationAssetVersion> versions=new LinkedHashMap<>();Map<String,UUID> byCode=new HashMap<>();
        for(var member:members.findMembers(catalog.getId())) {
            var version=member.getAssetVersion();versions.put(version.getId(),version);
            if(byCode.put(version.getAssetCode(),version.getId())!=null)throw invalidData();
        }
        Map<UUID,CustomizationFitAsset> placements=new HashMap<>();
        for(var placement:fitAssets.findByFitResultId(fit.getId())) {
            if(!versions.containsKey(placement.getAssetVersionId()) || placements.put(placement.getAssetVersionId(),placement)!=null)throw invalidData();
        }
        if(versions.isEmpty() || !placements.keySet().equals(versions.keySet()) || snapshot.groups()==null || snapshot.defaultSelection()==null)throw invalidData();
        List<ModelCustomizationSceneResponse.Group> groups=new ArrayList<>();Map<String,UUID> defaults=new LinkedHashMap<>();Set<UUID> grouped=new HashSet<>();
        for(var source:snapshot.groups()) {
            Object name=source.get("id"),choices=source.get("assets");
            if(!(name instanceof String id) || !(choices instanceof List<?> codes) || defaults.containsKey(id))throw invalidData();
            List<UUID> ids=new ArrayList<>();
            for(Object code:codes) {
                UUID versionId=byCode.get(code);
                if(versionId==null || !grouped.add(versionId) || !id.equals(versions.get(versionId).getSlot()))throw invalidData();
                ids.add(versionId);
            }
            boolean required=Boolean.TRUE.equals(source.get("required"));
            boolean noneAllowed=source.containsKey("none_allowed")?Boolean.TRUE.equals(source.get("none_allowed")):!required;
            groups.add(new ModelCustomizationSceneResponse.Group(id,required,!Boolean.FALSE.equals(source.get("exclusive")),noneAllowed,List.copyOf(ids)));
            String chosen=snapshot.defaultSelection().get(id);UUID choice=chosen==null?null:byCode.get(chosen);
            if(chosen!=null && choice==null)throw invalidData();defaults.put(id,choice);
        }
        if(!grouped.equals(versions.keySet()))throw invalidData();
        var context=new Context(output,fit,catalog,snapshot,versions,placements,List.copyOf(groups),defaults,Objects.equals(userId,scan.getUser().getId()));
        validateSelection(context,defaults);return context;
    }

    private CustomizationDraftResponse draftResponse(Context c,CustomizationDraft draft) {
        Map<String,UUID> selection=c.defaults();long revision=0;Instant updated=null;
        if(draft!=null) {
            if(!c.fit().getId().equals(draft.getFitResultId()) || !c.fit().getUserId().equals(draft.getUserId()) || draft.getRevision()<1)throw invalidData();
            selection=validateSelection(c,readSelection(draft.getSelectionJson()));revision=draft.getRevision();updated=draft.getUpdatedAt();
        }
        return new CustomizationDraftResponse(c.output().getId(),c.fit().getId(),draft!=null,revision,selection,c.owner() && "ACTIVE".equals(c.catalog().getStatus()),updated);
    }
    private Map<String,UUID> validateSelection(Context c,Map<String,UUID> input) {
        if(input==null || !input.keySet().equals(c.defaults().keySet()))throw invalidSelection();
        Map<String,UUID> normalized=new LinkedHashMap<>();
        for(var group:c.groups()) {
            UUID choice=input.get(group.id());
            if(choice==null?(group.required() || !group.noneAllowed()):!group.assetVersionIds().contains(choice))throw invalidSelection();
            normalized.put(group.id(),choice);
        }
        return normalized;
    }
    private List<Double> matrix(String source) {
        List<Double> values=read(source,new TypeReference<>() {});
        if(values==null || values.size()!=16 || values.stream().anyMatch(v->v==null || !Double.isFinite(v)))throw invalidData();return values;
    }
    private Map<String,UUID> readSelection(String source) { return read(source,new TypeReference<>() {}); }
    private <T> T read(String source,Class<T> type) { try{return Objects.requireNonNull(mapper.readValue(source,type));}catch(Exception ex){throw invalidData();} }
    private <T> T read(String source,TypeReference<T> type) { try{return Objects.requireNonNull(mapper.readValue(source,type));}catch(Exception ex){throw invalidData();} }
    private String json(Object value) { try{return mapper.writeValueAsString(value);}catch(Exception ex){throw invalidData();} }
    private BusinessException error(HttpStatus status,String code,String message) { return new BusinessException(status,code,message); }
    private BusinessException unavailable() { return error(HttpStatus.CONFLICT,"CUSTOMIZATION_NOT_AVAILABLE","This output does not have a completed custom scene"); }
    private BusinessException invalidData() { return error(HttpStatus.CONFLICT,"CUSTOMIZATION_DATA_INVALID","Stored scene/draft does not match this model output"); }
    private BusinessException invalidSelection() { return error(HttpStatus.BAD_REQUEST,"INVALID_CUSTOMIZATION_SELECTION","Select compatible asset version IDs for every scene group"); }
    private BusinessException conflict() { return error(HttpStatus.CONFLICT,"CUSTOMIZATION_REVISION_CONFLICT","Design changed; reload the saved selection before saving again"); }
}
