package com.memento3d.printers.domain.exception;

import com.memento3d.shared.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 401 – the agent token is missing, unknown or revoked. Deliberately vague message. */
public class AgentUnauthorizedException extends BusinessException {
    public AgentUnauthorizedException() {
        super(HttpStatus.UNAUTHORIZED, "AGENT_UNAUTHORIZED", "Invalid agent credentials");
    }
}
