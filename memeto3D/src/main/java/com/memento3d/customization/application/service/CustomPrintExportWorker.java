package com.memento3d.customization.application.service;
import com.memento3d.assets.domain.port.ModelStoragePort;
import com.memento3d.models.application.service.ModelDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

@Component @RequiredArgsConstructor @Slf4j
public class CustomPrintExportWorker {
    private final PrintDesignService designs;
    private final ModelDeliveryService delivery;
    private final ModelStoragePort storage;
    private final GlbDesignAssembler assembler;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    @Configuration static class Scheduler {
        @Bean("taskScheduler") ThreadPoolTaskScheduler defaultScheduler(){var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(2);scheduler.setThreadNamePrefix("memento-scheduling-");return scheduler;}
        @Bean("customPrintExportScheduler") ThreadPoolTaskScheduler scheduler(){var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("custom-export-");return scheduler;}
    }
    @Scheduled(fixedDelayString="${customization.print-export.poll-ms:5000}",scheduler="customPrintExportScheduler")
    public void tick() {
        var job=designs.claim();if(job==null)return;
        try {
            var snapshot=job.snapshot();List<GlbDesignAssembler.Part> parts=new ArrayList<>();
            parts.add(new GlbDesignAssembler.Part(download(snapshot.base()),null));
            for(var asset:snapshot.assets())parts.add(new GlbDesignAssembler.Part(download(asset),asset.matrix()));
            byte[] file=assembler.assemble(parts,job.heightCm());
            var stored=storage.uploadGlbArtifact(file,snapshot.userId(),snapshot.scanId(),snapshot.jobId(),snapshot.attemptId(),"customization/order-"+job.id()+".glb");
            designs.finish(job,stored,PrintDesignService.sha(file));
            log.info("Custom print export ready: orderId={}, designId={}, bytes={}",job.orderId(),job.id(),file.length);
        }catch(Exception ex){designs.fail(job,"CUSTOM_PRINT_EXPORT_FAILED");log.warn("Custom print export failed: orderId={}, designId={}, type={}",job.orderId(),job.id(),ex.getClass().getSimpleName());}
    }
    private byte[] download(FrozenDesign.Artifact asset) throws Exception {
        if(!"b2".equals(asset.provider())||asset.sizeBytes()<12||asset.sizeBytes()>128L*1024*1024)throw new IOException("Unsupported pinned artifact");
        URI uri=URI.create(delivery.url(asset.provider(),asset.objectKey(),asset.location()));
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||!uri.getHost().endsWith(".backblazeb2.com"))throw new IOException("Invalid storage origin");
        // A complete byte-array response has an end-to-end deadline; no unbounded streaming socket.
        var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2)).GET().build(),info->new BoundedBody(asset.sizeBytes()));
        if(response.statusCode()!=200)throw new IOException("Storage unavailable");
        byte[] bytes=response.body();
        if(bytes.length!=asset.sizeBytes()||!PrintDesignService.sha(bytes).equals(asset.sha256()))throw new IOException("Pinned artifact checksum mismatch");
        return bytes;
    }
    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final long max;private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();private Flow.Subscription subscription;
        BoundedBody(long max){this.max=max;}
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){int size=buffer.remaining();if((long)bytes.size()+size>max){subscription.cancel();result.completeExceptionally(new IOException("Artifact exceeds pinned size"));return;}
                byte[] block=new byte[size];buffer.get(block);bytes.writeBytes(block);
            }subscription.request(1);
        }
        public void onError(Throwable error){result.completeExceptionally(error);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
}
