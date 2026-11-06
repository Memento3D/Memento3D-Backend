package com.memento3d.customization;

import com.memento3d.shared.config.FlywayConfig;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlywayMigrationSafetyTest {
    @Test void defaultStartupDoesNotRewriteMigrationChecksums() {
        var flyway=mock(Flyway.class);new FlywayConfig().flywayMigrationStrategy().migrate(flyway);
        verify(flyway).migrate();verify(flyway,never()).repair();
    }
    @Test void migrationFailureIsNotSilentlyRepaired() {
        var flyway=mock(Flyway.class);when(flyway.migrate()).thenThrow(new IllegalStateException("fixture validation failure"));
        assertThrows(IllegalStateException.class,()->new FlywayConfig().flywayMigrationStrategy().migrate(flyway));verify(flyway,never()).repair();
    }
    @Test void explicitOperatorOptInRepairsBeforeMigration() {
        var config=new FlywayConfig();ReflectionTestUtils.setField(config,"repairBeforeMigrate",true);var flyway=mock(Flyway.class);
        config.flywayMigrationStrategy().migrate(flyway);var order=inOrder(flyway);order.verify(flyway).repair();order.verify(flyway).migrate();
    }
}
