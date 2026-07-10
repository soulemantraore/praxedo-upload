package com.praxedo.upload.infrastructure.id;

import com.praxedo.upload.domain.port.IdGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidIdGenerator implements IdGenerator {
    @Override
    public UUID newId() {
        return UUID.randomUUID();
    }
}
