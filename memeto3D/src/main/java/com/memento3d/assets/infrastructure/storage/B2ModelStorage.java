package com.memento3d.assets.infrastructure.storage;

import com.memento3d.assets.domain.port.ModelStoragePort;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** Private B2 objects with stable DB references and fresh SigV4 download URLs. */
public class B2ModelStorage implements ModelStoragePort, AutoCloseable {
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration ttl;

    public B2ModelStorage(S3Client client,S3Presigner presigner,String bucket,Duration ttl) {
        this.client=client;this.presigner=presigner;this.bucket=bucket;this.ttl=ttl;
    }
    public static B2ModelStorage connect(String endpoint,String region,String bucket,String keyId,String key,long seconds) {
        if (keyId == null || keyId.isBlank() || key == null || key.isBlank())
            throw new IllegalArgumentException("B2_KEY_ID and B2_APPLICATION_KEY are required for B2 storage");
        if (bucket == null || !bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))
            throw new IllegalArgumentException("Invalid B2 bucket name");
        URI uri=URI.create(endpoint);
        if (!"https".equals(uri.getScheme()) || !("s3."+region+".backblazeb2.com").equals(uri.getHost())
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || uri.getPort()!=-1 || (uri.getPath()!=null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())))
            throw new IllegalArgumentException("B2 endpoint must be the HTTPS S3 endpoint for B2_REGION");
        if (seconds<60 || seconds>604800) throw new IllegalArgumentException("B2 URL TTL must be 60..604800 seconds");
        var credentials=StaticCredentialsProvider.create(AwsBasicCredentials.create(keyId,key));
        var s3Config=S3Configuration.builder().pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false).checksumValidationEnabled(false).build();
        S3Client client=S3Client.builder().endpointOverride(uri).region(Region.of(region))
                .credentialsProvider(credentials).serviceConfiguration(s3Config)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofMinutes(3)).apiCallAttemptTimeout(Duration.ofSeconds(90)).build()).build();
        S3Presigner presigner=S3Presigner.builder().endpointOverride(uri).region(Region.of(region))
                .credentialsProvider(credentials).serviceConfiguration(s3Config).build();
        return new B2ModelStorage(client,presigner,bucket,Duration.ofSeconds(seconds));
    }
    public String provider() { return "b2"; }
    public long urlTtlSeconds() { return ttl.toSeconds(); }
    public StoredModel uploadGlb(byte[] bytes,UUID user,UUID scan,UUID job,UUID attempt) {
        return uploadGlbArtifact(bytes,user,scan,job,attempt,"preview.glb");
    }
    @Override public StoredModel uploadGlbArtifact(byte[] bytes,UUID user,UUID scan,UUID job,UUID attempt,String artifactName) {
        if (artifactName==null || !artifactName.matches("(?:preview|customization/[a-z0-9][a-z0-9-]{0,99})\\.glb"))
            throw new IllegalArgumentException("Unsupported model artifact name");
        if (bytes.length<12 || bytes[0]!='g' || bytes[1]!='l' || bytes[2]!='T' || bytes[3]!='F')
            throw new IllegalArgumentException("Invalid GLB artifact");
        String objectKey="memento3d/users/"+user+"/scans/"+scan+"/jobs/"+job+"/attempts/"+attempt+"/"+artifactName;
        String hash=sha256(bytes);
        HeadObjectResponse head=null;
        try { head=client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build()); }
        catch (S3Exception ex) { if (ex.statusCode()!=404) throw ex; }
        if (head==null) {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(objectKey)
                    .contentType("model/gltf-binary").contentLength((long)bytes.length)
                    .metadata(Map.of("sha256",hash)).build(),RequestBody.fromBytes(bytes));
            head=client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
        }
        if (head.contentLength()==null || head.contentLength()!=bytes.length || !hash.equals(head.metadata().get("sha256")))
            throw new IllegalStateException("B2 artifact conflicts with this job/attempt checksum or size");
        return new StoredModel(provider(),objectKey,head.eTag(),"b2://"+bucket+"/"+objectKey,head.contentLength());
    }
    /** Read-only verification for console-uploaded catalog files; never trust ETag as SHA-256. */
    public void verifyObject(String expectedBucket,String objectKey,long sizeBytes,String expectedSha256) throws java.io.IOException {
        if (!bucket.equals(expectedBucket) || objectKey==null || !objectKey.startsWith("library/")
                || objectKey.contains("..") || objectKey.contains("\\") || sizeBytes<=0 || sizeBytes>50L*1024*1024
                || expectedSha256==null || !expectedSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid pinned catalog object");
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
            try (var input=client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build())) {
                byte[] buffer=new byte[64*1024];int count;
                while ((count=input.read(buffer))!=-1) {
                    total+=count;
                    if (total>sizeBytes) throw new IllegalStateException("B2 catalog object exceeds its pinned size");
                    digest.update(buffer,0,count);
                }
            }
            if (total!=sizeBytes || !expectedSha256.equals(HexFormat.of().formatHex(digest.digest())))
                throw new IllegalStateException("B2 catalog object checksum or size mismatch");
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public String downloadUrl(String objectKey,String location) {
        if (!location.equals("b2://"+bucket+"/"+objectKey))
            throw new IllegalArgumentException("B2 reference does not match configured bucket and key");
        return presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(objectKey).build()).build()).url().toString();
    }
    /** Explicit setup operation: preserve other rules and replace only our viewer rule. */
    public void configureViewerCors(List<String> origins) {
        if (origins.isEmpty()) throw new IllegalArgumentException("At least one FE origin is required");
        for (String origin:origins) {
            URI uri=URI.create(origin);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost()==null
                    || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                    || (uri.getPath()!=null && !uri.getPath().isEmpty()))
                throw new IllegalArgumentException("CORS origins must contain scheme, host and optional port only");
        }
        List<CORSRule> rules=new ArrayList<>();
        try { rules.addAll(client.getBucketCors(GetBucketCorsRequest.builder().bucket(bucket).build()).corsRules()); }
        catch (S3Exception ex) { if(ex.statusCode()!=404) throw ex; }
        rules.removeIf(rule->"memento3d-model-viewer".equals(rule.id()));
        rules.add(CORSRule.builder().id("memento3d-model-viewer").allowedOrigins(origins)
                .allowedMethods("GET","HEAD").allowedHeaders("range")
                .exposeHeaders("ETag","Content-Length","Content-Range","Accept-Ranges").maxAgeSeconds(3600).build());
        client.putBucketCors(PutBucketCorsRequest.builder().bucket(bucket)
                .corsConfiguration(CORSConfiguration.builder().corsRules(rules).build()).build());
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public void close() { presigner.close();client.close(); }
}
