package com.costco.gb.controller;

import com.costco.gb.security.JwtService;
import com.costco.gb.service.AuthService;
import com.costco.gb.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @InjectMocks
    private AuthController authController;

    @Test
    void devLoginIsUnavailableWhenDisabled() {
        ReflectionTestUtils.setField(authController, "devLoginEnabled", false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authController.devLogin(1L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(jwtService, refreshTokenService);
    }
}
