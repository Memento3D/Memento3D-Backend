package com.memento3d.customization;

import com.memento3d.customization.api.AssetCatalogController;
import com.memento3d.customization.api.response.*;
import com.memento3d.customization.application.service.AssetCatalogService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssetCatalogControllerTest {
    AnnotationConfigWebApplicationContext context;MockMvc mvc;AssetCatalogService service;
    @Configuration @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean AssetCatalogService service() { return mock(AssetCatalogService.class); }
        @Bean AssetCatalogController controller(AssetCatalogService service) { return new AssetCatalogController(service); }
        @Bean com.memento3d.shared.api.GlobalExceptionHandler errors() { return new com.memento3d.shared.api.GlobalExceptionHandler(); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.anyRequest().authenticated())
                .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->s.sendError(401))).build();
        }
    }
    @BeforeEach void start() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Config.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();service=context.getBean(AssetCatalogService.class);
    }
    @AfterEach void close() { context.close(); }
    @Test void anonymousCannotReadOrImport() throws Exception {
        mvc.perform(get("/api/v1/customization-catalogs/mvp-v1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/customization-catalogs/mvp-v1/import")).andExpect(status().isUnauthorized());verifyNoInteractions(service);
    }
    @Test void userAndManagerCannotActivateCatalog() throws Exception {
        for(String role:List.of("USER","MANAGER"))
            mvc.perform(post("/api/v1/admin/customization-catalogs/mvp-v1/import").with(user("test").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void adminImportAndAuthenticatedReadReturnStructuredJson() throws Exception {
        when(service.importCatalog("mvp-v1")).thenReturn(new CatalogImportResponse("mvp-v1","ACTIVE",3,15,false));
        mvc.perform(post("/api/v1/admin/customization-catalogs/mvp-v1/import").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.verifiedObjects").value(15));
        when(service.getCatalog("mvp-v1")).thenReturn(new CustomizationCatalogResponse("mvp-v1","graduation-standing-base-v1","ACTIVE",
            List.of(Map.of("id","headwear")),Map.of("headwear","graduation-cap"),List.of(),3600));
        mvc.perform(get("/api/v1/customization-catalogs/mvp-v1").with(user("customer").roles("USER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.groups").isArray()).andExpect(jsonPath("$.defaultSelection.headwear").value("graduation-cap"));
    }
    @Test void missingCatalogAndRemoteMismatchUseExistingErrorContract() throws Exception {
        when(service.getCatalog("missing")).thenThrow(new com.memento3d.shared.exception.BusinessException(org.springframework.http.HttpStatus.NOT_FOUND,"CATALOG_NOT_FOUND","Catalog not found"));
        mvc.perform(get("/api/v1/customization-catalogs/missing").with(user("customer").roles("USER")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("CATALOG_NOT_FOUND"));
        when(service.importCatalog("mvp-v1")).thenThrow(new com.memento3d.shared.exception.BusinessException(org.springframework.http.HttpStatus.CONFLICT,"CATALOG_OBJECT_MISMATCH","Pinned object mismatch"));
        mvc.perform(post("/api/v1/admin/customization-catalogs/mvp-v1/import").with(user("admin").roles("ADMIN")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CATALOG_OBJECT_MISMATCH"));
    }
}
