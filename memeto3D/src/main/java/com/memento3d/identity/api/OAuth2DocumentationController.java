package com.memento3d.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@Tag(name = "Authentication")
public class OAuth2DocumentationController {

    @GetMapping("/oauth2/authorization/google")
    @Operation(
            summary = "Login with Google (OAuth2 / OIDC Entrypoint)",
            description = "Endpoint chính thức của Spring Security để đăng nhập bằng Google. <br/><br/>"
                    + "⚠️ <b>KHÔNG dùng nút 'Execute' của Swagger</b> vì Google chặn request ngầm (Fetch CORS). <br/>"
                    + "👉 Hãy mở trực tiếp trên tab mới của trình duyệt: <a href=\"http://localhost:8080/oauth2/authorization/google\" target=\"_blank\"><b>http://localhost:8080/oauth2/authorization/google</b></a>"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirect to accounts.google.com consent screen")
    })
    public void googleOAuth2Login(
            @Parameter(description = "Optional dynamic redirect URI to return after login", example = "http://localhost:3000/oauth2/redirect")
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            HttpServletResponse response
    ) throws IOException {
        // Fallback in case request bypasses filter: redirect directly to Google OAuth2 entrypoint
        String target = "/oauth2/authorization/google" + (redirectUri != null ? "?redirect_uri=" + redirectUri : "");
        response.sendRedirect(target);
    }
}
