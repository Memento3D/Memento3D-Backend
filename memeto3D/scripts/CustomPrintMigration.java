import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
public class CustomPrintMigration {
    static String env(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException("Missing "+key);return value;}
    public static void main(String[] args) {
        try {
            String host=env("DB_HOST"),port=env("DB_PORT"),name=env("DB_NAME");
            if(!host.matches("[A-Za-z0-9.-]+")||!port.matches("[0-9]+")||!name.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Invalid database settings");
            var ds=new DriverManagerDataSource("jdbc:postgresql://"+host+":"+port+"/"+name+"?sslmode=require",env("DB_USERNAME"),env("DB_PASSWORD"));
            var flyway=Flyway.configure().dataSource(ds).locations("classpath:db/migration").defaultSchema("public").schemas("public").ignoreMigrationPatterns("*:pending").load();
            var validation=flyway.validateWithResult();if(!validation.validationSuccessful)throw new IllegalStateException("Flyway history validation failed; no repair allowed");
            for(var migration:flyway.info().pending())if(migration.getVersion()==null||!migration.getVersion().getVersion().equals("30"))throw new IllegalStateException("Unexpected pending migration");
            System.out.println("CUSTOM_PRINT_PREFLIGHT_OK pending="+flyway.info().pending().length);
            if(args.length==1&&args[0].equals("--apply")) {
                System.out.println("CUSTOM_PRINT_MIGRATIONS_APPLIED="+flyway.migrate().migrationsExecuted);
                var jdbc=new JdbcTemplate(ds);
                System.out.println("IMMUTABILITY_TRIGGER="+jdbc.queryForObject("SELECT count(*) FROM pg_trigger WHERE tgname='guard_custom_order_snapshot' AND NOT tgisinternal",Long.class));
            }
        }catch(Exception error){System.err.println("CUSTOM_PRINT_MIGRATION_FAILED type="+error.getClass().getSimpleName()+"; no repair/reset performed");System.exit(1);}
    }
}
