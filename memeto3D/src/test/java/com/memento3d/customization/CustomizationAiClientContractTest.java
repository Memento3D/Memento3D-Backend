package com.memento3d.customization;

import com.memento3d.reconstruction.infrastructure.ai.HttpAiGenerationClient;
import com.memento3d.reconstruction.domain.exception.AiClientException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomizationAiClientContractTest {
    HttpServer server;HttpAiGenerationClient client;final UUID job=UUID.randomUUID(),attempt=UUID.randomUUID();
    final AtomicReference<byte[]> response=new AtomicReference<>();final AtomicInteger status=new AtomicInteger(200),requests=new AtomicInteger();
    final AtomicReference<String> path=new AtomicReference<>(),key=new AtomicReference<>();
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{requests.incrementAndGet();path.set(exchange.getRequestURI().toString());key.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            byte[] bytes=response.get();exchange.sendResponseHeaders(status.get(),bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);} });
        server.start();client=new HttpAiGenerationClient("http://127.0.0.1:"+server.getAddress().getPort(),"fixture-backend-key");
    }
    @AfterEach void stop() { server.stop(0); }
    void json(String value) { response.set(value.getBytes(StandardCharsets.UTF_8)); }
    @Test void statusRetainsNewFlagsWhileLegacyResponsesRemainCompatible() {
        json("{\"job_id\":\""+job+"\",\"attempt_id\":\""+attempt+"\",\"status\":\"done\",\"output_kind\":\"customization_base\",\"customization_ready\":true,\"ready_for_machine\":false}");
        var actual=client.getJob(job);assertEquals("customization_base",actual.outputKind());assertTrue(actual.customizationReady());assertFalse(actual.readyForMachine());
        json("{\"job_id\":\""+job+"\",\"attempt_id\":\""+attempt+"\",\"status\":\"done\"}");assertNull(client.getJob(job).outputKind());
    }
    @Test void manifestAndAssetDownloadUseAuthenticatedFixedRoutesBoundToAttempt() throws Exception {
        var source=FitFixtures.download(job,attempt,"a".repeat(64));json(source.json());
        var actual=client.getCustomizationManifest(job,attempt);assertEquals(job,actual.manifest().jobId());assertEquals(source.json(),actual.json());
        assertEquals("/jobs/"+job+"/customization?attempt_id="+attempt,path.get());assertEquals("fixture-backend-key",key.get());
        response.set(FitFixtures.glb("glasses-round"));assertArrayEquals(response.get(),client.downloadCustomizationAsset(job,attempt,"glasses-round"));
        assertEquals("/jobs/"+job+"/customization/assets/glasses-round?attempt_id="+attempt,path.get());
    }
    @Test void oversizedOrMalformedManifestIsRejected() {
        response.set(new byte[64*1024+1]);assertThrows(AiClientException.class,()->client.getCustomizationManifest(job,attempt));
        json("not JSON");assertThrows(AiClientException.class,()->client.getCustomizationManifest(job,attempt));
    }
    @Test void missingArtifactAndInvalidGlbFailWithoutReturningFallback() {
        json("private upstream error");status.set(404);
        assertEquals(404,assertThrows(AiClientException.class,()->client.getCustomizationManifest(job,attempt)).getUpstreamStatus());
        status.set(200);assertThrows(AiClientException.class,()->client.downloadCustomizationAsset(job,attempt,"hair-side-part"));
    }
    @Test void unsafeIdentifiersAndMissingApiKeyFailBeforeAnyRequest() {
        assertThrows(IllegalArgumentException.class,()->client.downloadCustomizationAsset(job,attempt,"../other-user"));
        var noKey=new HttpAiGenerationClient("http://127.0.0.1:"+server.getAddress().getPort(),"");
        assertThrows(AiClientException.class,()->noKey.getCustomizationManifest(job,attempt));assertEquals(0,requests.get());
    }
}
