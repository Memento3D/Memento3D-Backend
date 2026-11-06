import com.fasterxml.jackson.databind.ObjectMapper;
import com.memento3d.assets.infrastructure.storage.B2ModelStorage;
import com.memento3d.customization.application.service.*;
import com.memento3d.customization.domain.model.*;
import com.memento3d.customization.infrastructure.persistence.repository.*;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.*;

/** Explicit local operator tool: no HTTP endpoint, repair, reset, or credential output. */
public class CustomizationMaintenance {
    static String env(String key) {String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException("Missing "+key);return value;}
    static DataSource database() {
        String host=env("DB_HOST"),port=env("DB_PORT"),name=env("DB_NAME");
        if(!host.matches("[A-Za-z0-9.-]+")||!port.matches("[0-9]+")||!name.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Invalid database settings");
        return new DriverManagerDataSource("jdbc:postgresql://"+host+":"+port+"/"+name+"?sslmode=require",env("DB_USERNAME"),env("DB_PASSWORD"));
    }
    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses=CustomizationCatalogRepository.class,
        excludeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes={CustomizationOptionRepository.class,CustomizationFitResultRepository.class,CustomizationFitAssetRepository.class,CustomizationDraftRepository.class}))
    static class Config {
        @Bean DataSource dataSource(){return database();}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){
            var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);
            f.setManagedTypes(PersistenceManagedTypes.of(CustomizationCatalog.class.getName(),CustomizationAssetVersion.class.getName(),CustomizationCatalogMember.class.getName()));
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate"));return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf){return new JpaTransactionManager(emf);}
        @Bean ObjectMapper mapper(){return new ObjectMapper();}
        @Bean CatalogImportWriter writer(CustomizationCatalogRepository c,CustomizationAssetVersionRepository v,CustomizationCatalogMemberRepository m,ObjectMapper mapper){return new CatalogImportWriter(c,v,m,mapper);}
    }
    public static void main(String[] args) {
        try {
            boolean apply=args.length==1&&args[0].equals("--apply");
            var flyway=Flyway.configure().dataSource(database()).locations("classpath:db/migration").defaultSchema("public").schemas("public").ignoreMigrationPatterns("*:pending").load();
            var validation=flyway.validateWithResult();
            if(!validation.validationSuccessful){for(var invalid:validation.invalidMigrations)System.out.println("VALIDATION_ERROR version="+invalid.version+" code="+invalid.errorDetails.errorCode+" detail="+invalid.errorDetails.errorMessage);throw new IllegalStateException("Flyway validation failed; no repair will be performed");}
            var info=flyway.info();var pending=info.pending();
            for(var m:pending){String v=m.getVersion()==null?"":m.getVersion().getVersion();if(!Set.of("27","28","29").contains(v))throw new IllegalStateException("Pending migration outside approved V27-V29: "+v);System.out.println("PENDING V"+v+" "+m.getDescription());}
            for(var m:info.applied())if(m.getVersion()!=null&&m.getVersion().compareTo(org.flywaydb.core.api.MigrationVersion.fromVersion("29"))>0)throw new IllegalStateException("Database is newer than this tool");
            System.out.println("PREFLIGHT_OK pending="+pending.length);
            if(!apply)return;
            var result=flyway.migrate();System.out.println("MIGRATIONS_APPLIED="+result.migrationsExecuted);
            var loaded=new CatalogManifestLoader(new ObjectMapper()).load("mvp-v1");
            try(var b2=B2ModelStorage.connect(env("B2_ENDPOINT"),env("B2_REGION"),env("B2_BUCKET"),env("B2_KEY_ID"),env("B2_APPLICATION_KEY"),3600)) {
                for(var file:loaded.manifest().files())b2.verifyObject(loaded.manifest().bucket(),file.key(),file.bytes(),file.sha256());
                System.out.println("B2_VERIFIED="+loaded.manifest().files().size());
            }
            try(var context=new AnnotationConfigApplicationContext(Config.class)) {
                System.out.println("CATALOG_IMPORT="+context.getBean(CatalogImportWriter.class).persistVerified(loaded));
                var jdbc=new JdbcTemplate(context.getBean(DataSource.class));
                System.out.println("SCHEMA_VERSION="+jdbc.queryForObject("select version from public.flyway_schema_history where success=true and version is not null order by installed_rank desc limit 1",String.class));
                System.out.println("CATALOG_ASSETS="+jdbc.queryForObject("select count(*) from customization_catalog_members m join customization_catalogs c on c.id=m.catalog_id where c.code='mvp-v1' and c.status='ACTIVE'",Long.class));
            }
        } catch(Exception ex) {System.err.println("MAINTENANCE_FAILED type="+ex.getClass().getSimpleName()+"; inspect local configuration/history; no repair/reset attempted.");System.exit(1);}
    }
}
