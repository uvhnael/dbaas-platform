package com.dbaas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson configuration for Spring MVC.
 * This provides a standard ObjectMapper for HTTP request/response
 * serialization.
 */
@Configuration
public class JacksonConfig {

    /**
     * Primary ObjectMapper for Spring MVC.
     * This does NOT have polymorphic type handling enabled,
     * which is required for standard REST API JSON parsing.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
