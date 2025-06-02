package com.github.aznamier.keycloak.event.provider;

import org.junit.jupiter.api.Test;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScimInducedEventDetectorTest {

    @Test
    void WithNullRepresentationReturnsFalse() {
        AdminEvent event = createAdminEvent(null);
        assertFalse(RabbitMqEventListenerProvider.ScimInducedEventDetector.isScimInducedEvent(event));
    }

    @Test
    void WithInvalidJsonReturnsFalse() {
        AdminEvent event = createAdminEvent("invalid json");
        assertFalse(RabbitMqEventListenerProvider.ScimInducedEventDetector.isScimInducedEvent(event));
    }

    @Test
    void WithNonScimSchemaReturnsFalse() {
        Map<String, Object> representation = new HashMap<>();
        representation.put("schemas", Arrays.asList("some:other:schema"));
        
        AdminEvent event = createAdminEvent(toJson(representation));
        assertFalse(RabbitMqEventListenerProvider.ScimInducedEventDetector.isScimInducedEvent(event));
    }

    @Test
    void WithScimUserSchemaReturnsTrue() {
        Map<String, Object> representation = new HashMap<>();
        representation.put("schemas", Arrays.asList("urn:ietf:params:scim:schemas:core:2.0:User"));
        
        AdminEvent event = createAdminEvent(toJson(representation));
        assertTrue(RabbitMqEventListenerProvider.ScimInducedEventDetector.isScimInducedEvent(event));
    }

    @Test
    void WithScimGroupSchemaReturnsTrue() {
        Map<String, Object> representation = new HashMap<>();
        representation.put("schemas", Arrays.asList("urn:ietf:params:scim:schemas:core:2.0:Group"));
        
        AdminEvent event = createAdminEvent(toJson(representation));
        assertTrue(RabbitMqEventListenerProvider.ScimInducedEventDetector.isScimInducedEvent(event));
    }

    @Test
    void WithMultipleSchemasReturnsTrue() {
        Map<String, Object> representation = new HashMap<>();
        representation.put("schemas", Arrays.asList(
            "some:other:schema",
            "urn:ietf:params:scim:schemas:core:2.0:User",
            "another:schema"
        ));
        
        AdminEvent event = createAdminEvent(toJson(representation));
        assertTrue(RabbitMqEventListenerProvider.ScimInducedEventDetector.isScimInducedEvent(event));
    }

    @Test
    void WithNonArraySchemasReturnsFalse() {
        Map<String, Object> representation = new HashMap<>();
        representation.put("schemas", "urn:ietf:params:scim:schemas:core:2.0:User");
        
        AdminEvent event = createAdminEvent(toJson(representation));
        assertFalse(RabbitMqEventListenerProvider.ScimInducedEventDetector.isScimInducedEvent(event));
    }

    private AdminEvent createAdminEvent(String representation) {
        AdminEvent event = new AdminEvent();
        event.setOperationType(OperationType.CREATE);
        event.setResourceType(ResourceType.USER);
        event.setRepresentation(representation);
        return event;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return org.keycloak.util.JsonSerialization.writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
