package com.github.aznamier.keycloak.event.provider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.fasterxml.jackson.core.type.TypeReference;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerTransaction;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.BasicProperties.Builder;
import com.rabbitmq.client.Channel;
import org.keycloak.util.JsonSerialization;

public class RabbitMqEventListenerProvider implements EventListenerProvider {

	private static final Logger log = Logger.getLogger(RabbitMqEventListenerProvider.class);
	
	private final RabbitMqConfig cfg;
	private final Channel channel;

	private final KeycloakSession session;

	private final EventListenerTransaction tx = new EventListenerTransaction(this::publishAdminEvent, this::publishEvent);

	public RabbitMqEventListenerProvider(Channel channel, KeycloakSession session, RabbitMqConfig cfg) {
		this.cfg = cfg;
		this.channel = channel;
		this.session = session;
		session.getTransactionManager().enlistAfterCompletion(tx);
	}

	@Override
	public void close() {

	}

	@Override
	public void onEvent(Event event) {
		tx.addEvent(event.clone());
	}

	@Override
	public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
		tx.addAdminEvent(adminEvent, includeRepresentation);
	}
	
	private void publishEvent(Event event) {
		EventClientNotificationMqMsg msg = EventClientNotificationMqMsg.create(event);
		String routingKey = RabbitMqConfig.calculateRoutingKey(event, session);
		String messageString = RabbitMqConfig.writeAsJson(msg, true);
		
		BasicProperties msgProps = RabbitMqEventListenerProvider.getMessageProps(EventClientNotificationMqMsg.class.getName());
		this.publishNotification(messageString, msgProps, routingKey);
	}
	
	private void publishAdminEvent(AdminEvent adminEvent, boolean includeRepresentation) {
		EventAdminNotificationMqMsg msg = EventAdminNotificationMqMsg.create(adminEvent);
		String routingKey = RabbitMqConfig.calculateRoutingKey(adminEvent, session);
		String messageString = RabbitMqConfig.writeAsJson(msg, true);

		BasicProperties msgProps = RabbitMqEventListenerProvider.getMessageProps(EventAdminNotificationMqMsg.class.getName(), adminEvent);
		this.publishNotification(messageString,msgProps, routingKey);
	}
	
	private static BasicProperties getMessageProps(String className) {
		
		Map<String,Object> headers = new HashMap<>();
		headers.put("__TypeId__", className);
		headers.put("aw_modified", true);

		Builder propsBuilder = new AMQP.BasicProperties.Builder()
				.appId("Keycloak")
				.headers(headers)
				.contentType("application/json")
				.contentEncoding("UTF-8");
		return propsBuilder.build();
	}

	private static BasicProperties getMessageProps(String className, AdminEvent adminEvent) {
		Map<String,Object> headers = new HashMap<>();
		headers.put("__TypeId__", className);
		headers.put("aw_modified", true);

		if (ScimInducedEventDetector.isScimInducedEvent(adminEvent)) {
			headers.put("scim", true);
		}

		Builder propsBuilder = new AMQP.BasicProperties.Builder()
				.appId("Keycloak")
				.headers(headers)
				.contentType("application/json")
				.contentEncoding("UTF-8");
		return propsBuilder.build();
	}

	private void publishNotification(String messageString, BasicProperties props, String routingKey) {
		try {
			channel.basicPublish(cfg.getExchange(), routingKey, props, messageString.getBytes(StandardCharsets.UTF_8));
			log.tracef("keycloak-to-rabbitmq SUCCESS sending message: %s%n", routingKey);
		} catch (Exception ex) {
			log.errorf(ex, "keycloak-to-rabbitmq ERROR sending message: %s%n", routingKey);
		}
	}

	public static class ScimInducedEventDetector {
		private static final List<?> SCIM_NAMESPACES = Arrays.asList(
			"urn:ietf:params:scim:schemas:core:2.0:User",
			"urn:ietf:params:scim:schemas:core:2.0:Group"
		);

		public static boolean isScimInducedEvent(AdminEvent adminEvent) {
			if (adminEvent.getRepresentation() == null) {
				return false;
			}

			Map<String,Object> representationMap;
			try {
				representationMap = JsonSerialization.readValue(adminEvent.getRepresentation(), new TypeReference<Map<String,Object>>() {});
			} catch (IOException e) {
				return false;
			}

			if (representationMap == null) {
				return false;
			}

			Object schemas = representationMap.get("schemas");
			return schemas instanceof ArrayList &&
				((ArrayList<?>) schemas).stream().anyMatch(SCIM_NAMESPACES::contains);
		}
	}
}