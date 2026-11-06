package com.memento3d.customization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.memento3d.customization.application.dto.CatalogImportManifest;
import com.memento3d.customization.application.service.*;
import com.memento3d.customization.domain.model.*;
import com.memento3d.customization.domain.port.CatalogObjectVerifier;
import com.memento3d.customization.infrastructure.persistence.repository.*;
import com.memento3d.models.application.service.ModelDeliveryService;
import com.memento3d.shared.exception.BusinessException;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Uses only an explicitly supplied localhost database named memento_catalog_test. */
@EnabledIfEnvironmentVariable(named="CATALOG_TEST_JDBC",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/memento_catalog_test")
class CatalogPostgresIntegrationTest {
    static AnnotationConfigApplicationContext context;static JdbcTemplate jdbc;
    static AssetCatalogService service;static CatalogImportWriter writer;
    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses=CustomizationCatalogRepository.class,
        excludeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes={CustomizationOptionRepository.class,CustomizationFitResultRepository.class,CustomizationFitAssetRepository.class,CustomizationDraftRepository.class}))
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(System.getenv("CATALOG_TEST_JDBC"),"catalog_test","catalog_test_only"); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);
            factory.setManagedTypes(PersistenceManagedTypes.of(CustomizationCatalog.class.getName(),CustomizationAssetVersion.class.getName(),CustomizationCatalogMember.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.generate_statistics","true"));return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean CatalogManifestLoader loader(ObjectMapper m) { return new CatalogManifestLoader(m); }
        @Bean CatalogObjectVerifier verifier() { return mock(CatalogObjectVerifier.class); }
        @Bean ModelDeliveryService delivery() {
            var delivery=mock(ModelDeliveryService.class);when(delivery.url(any(),any(),any())).thenReturn("https://example.test/signed");
            when(delivery.urlTtlSeconds()).thenReturn(3600L);return delivery;
        }
        @Bean CatalogImportWriter writer(CustomizationCatalogRepository c,CustomizationAssetVersionRepository v,CustomizationCatalogMemberRepository m,ObjectMapper mapper) { return new CatalogImportWriter(c,v,m,mapper); }
        @Bean AssetCatalogService service(CatalogManifestLoader l,CatalogObjectVerifier v,CatalogImportWriter w,CustomizationCatalogRepository c,CustomizationCatalogMemberRepository m,ModelDeliveryService d,ObjectMapper mapper) { return new AssetCatalogService(l,v,w,c,m,d,mapper); }
    }
    @BeforeAll static void migrateAndStart() throws Exception {
        var ds=new DriverManagerDataSource(System.getenv("CATALOG_TEST_JDBC"),"catalog_test","catalog_test_only");
        var dir=Files.createTempDirectory("memento-catalog-migration-");
        try(var source=CatalogPostgresIntegrationTest.class.getResourceAsStream("/db/migration/V27__create_versioned_customization_catalog.sql")) {
            assertNotNull(source);Files.copy(source,dir.resolve("V27__create_versioned_customization_catalog.sql"));
        }
        try { Flyway.configure().dataSource(ds).locations("filesystem:"+dir.toAbsolutePath()).load().migrate(); }
        finally { Files.deleteIfExists(dir.resolve("V27__create_versioned_customization_catalog.sql"));Files.deleteIfExists(dir); }
        context=new AnnotationConfigApplicationContext(Config.class);jdbc=new JdbcTemplate(context.getBean(DataSource.class));
        service=context.getBean(AssetCatalogService.class);writer=context.getBean(CatalogImportWriter.class);
    }
    @AfterAll static void close() { if(context!=null)context.close(); }
    @BeforeEach void resetOnlyIsolatedTables() { jdbc.execute("TRUNCATE public.customization_catalog_members, public.customization_asset_versions, public.customization_catalogs CASCADE"); }
    long count(String table) { return jdbc.queryForObject("select count(*) from public."+table,Long.class); }

    @Test void migrationJsonbHibernateMappingIdempotencyAndReadQueryCount() {
        assertFalse(service.importCatalog("mvp-v1").unchanged());assertTrue(service.importCatalog("mvp-v1").unchanged());
        assertEquals(1,count("customization_catalogs"));assertEquals(3,count("customization_asset_versions"));assertEquals(3,count("customization_catalog_members"));
        assertEquals("array",jdbc.queryForObject("select jsonb_typeof(groups_json) from customization_catalogs",String.class));
        assertEquals("object",jdbc.queryForObject("select jsonb_typeof(compatibility_json) from customization_asset_versions limit 1",String.class));
        var statistics=context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();statistics.clear();
        var result=service.getCatalog("mvp-v1");assertEquals(3,result.assets().size());assertEquals(2,statistics.getPrepareStatementCount(),"Catalog read must not issue one query per asset");
        assertTrue(result.assets().stream().noneMatch(a->a.printReady()));
    }
    @Test void conflictingVersionRollsBackNewCatalogAndAllMemberships() throws Exception {
        service.importCatalog("mvp-v1");var mapper=context.getBean(ObjectMapper.class);
        var original=context.getBean(CatalogManifestLoader.class).load("mvp-v1");ObjectNode tree=mapper.valueToTree(original.manifest());
        tree.put("code","rollback-v1");((ObjectNode)tree.withArray("assets").get(2)).put("name","Different version data");
        var changed=mapper.treeToValue(tree,CatalogImportManifest.class);
        assertThrows(BusinessException.class,()->writer.persistVerified(new CatalogManifestLoader.Loaded(changed,"f".repeat(64))));
        assertEquals(1,count("customization_catalogs"));assertEquals(3,count("customization_catalog_members"));assertEquals(3,count("customization_asset_versions"));
    }
    @Test void concurrentImportUsesDatabaseLockAndNeverDuplicatesVersions() throws Exception {
        var executor=Executors.newFixedThreadPool(2);
        try {
            var first=executor.submit(()->service.importCatalog("mvp-v1"));var second=executor.submit(()->service.importCatalog("mvp-v1"));
            var a=first.get(30,TimeUnit.SECONDS);var b=second.get(30,TimeUnit.SECONDS);
            assertNotEquals(a.unchanged(),b.unchanged());assertEquals(3,count("customization_asset_versions"));assertEquals(3,count("customization_catalog_members"));
        } finally { executor.shutdownNow(); }
    }
    @Test void databaseUniqueConstraintRejectsDuplicateVersion() {
        service.importCatalog("mvp-v1");
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into customization_catalog_members(id,catalog_id,asset_version_id,sort_order) select ?,catalog_id,asset_version_id,sort_order from customization_catalog_members limit 1",UUID.randomUUID()));
        assertEquals(3,count("customization_catalog_members"));
    }
}
