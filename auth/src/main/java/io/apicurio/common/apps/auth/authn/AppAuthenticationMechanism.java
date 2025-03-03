/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.common.apps.auth.authn;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import io.apicurio.common.apps.logging.audit.AuditHttpRequestContext;
import io.apicurio.common.apps.logging.audit.AuditHttpRequestInfo;
import io.apicurio.common.apps.logging.audit.AuditLogService;
import io.apicurio.common.apps.util.Pair;
import io.apicurio.rest.client.JdkHttpClientProvider;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.oidc.runtime.BearerAuthenticationMechanism;
import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

public abstract class AppAuthenticationMechanism implements HttpAuthenticationMechanism {

    @Inject
    Logger log;

    @ConfigProperty(name = "app.authn.enabled", defaultValue = "false")
    boolean authEnabled;

    @ConfigProperty(name = "app.authn.basic-auth-client-credentials.enabled", defaultValue = "false")
    boolean basicAuthEnabled;

    @ConfigProperty(name = "app.authn.token.endpoint", defaultValue = "")
    String authServerUrl;

    @ConfigProperty(name = "app.authn.client-secret")
    Optional<String> clientSecret;

    @ConfigProperty(name = "quarkus.oidc.client-id", defaultValue = "")
    String clientId;

    @Inject
    OidcAuthenticationMechanism oidcAuthenticationMechanism;

    @Inject
    AuditLogService auditLog;

    private BearerAuthenticationMechanism bearerAuth;

    private ApicurioHttpClient httpClient;

    @PostConstruct
    public void init() {
        if (authEnabled) {
            httpClient = new JdkHttpClientProvider().create(authServerUrl, Collections.emptyMap(), null, new AuthErrorHandler());
            bearerAuth = new BearerAuthenticationMechanism();
        }
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (authEnabled) {
            setAuditLogger(context);
            if (basicAuthEnabled) {
                final Pair<String, String> clientCredentials = CredentialsHelper.extractCredentialsFromContext(context);
                if (null != clientCredentials) {
                    try {
                        return authenticateWithClientCredentials(clientCredentials, context, identityProviderManager);
                    } catch (NotAuthorizedException ex) {
                        //Ignore exception, wrong credentials passed
                        throw new AuthenticationFailedException();
                    }
                } else {
                    return customAuthentication(context, identityProviderManager);
                }
            } else {
                return customAuthentication(context, identityProviderManager);
            }
        } else {
            return Uni.createFrom().nullItem();
        }
    }

    public Uni<SecurityIdentity> customAuthentication(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (clientSecret.isEmpty()) {
            //if no secret is present, try to authenticate with oidc provider
            return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
        } else {
            final Pair<String, String> credentialsFromContext = CredentialsHelper.extractCredentialsFromContext(context);
            if (credentialsFromContext != null) {
                try (OidcAuth oidcAuth = new OidcAuth(httpClient, clientId, clientSecret.get())) {
                    String jwtToken = oidcAuth.obtainAccessTokenPasswordGrant(credentialsFromContext.getLeft(), credentialsFromContext.getRight());
                    if (jwtToken != null) {
                        //If we manage to get a token from basic credentials, try to authenticate it using the fetched token using the identity provider manager
                        return identityProviderManager
                                .authenticate(new TokenAuthenticationRequest(new AccessTokenCredential(jwtToken, context)));
                    }
                }
            } else {
                //If we cannot get a token, then try to authenticate using oidc provider as last resource
                return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
            }
        }
        return Uni.createFrom().nullItem();
    }

    private void setAuditLogger(RoutingContext context) {
        BiConsumer<RoutingContext, Throwable> failureHandler = context.get(QuarkusHttpUser.AUTH_FAILURE_HANDLER);
        BiConsumer<RoutingContext, Throwable> auditWrapper = (ctx, ex) -> {
            //this sends the http response
            failureHandler.accept(ctx, ex);
            //if it was an error response log it
            if (ctx.response().getStatusCode() >= 400) {
                Map<String, String> metadata = new HashMap<>();
                metadata.put("method", ctx.request().method().name());
                metadata.put("path", ctx.request().path());
                metadata.put("response_code", String.valueOf(ctx.response().getStatusCode()));
                if (ex != null) {
                    metadata.put("error_msg", ex.getMessage());
                }

                //request context for AuditHttpRequestContext does not exist at this point
                auditLog.log("app.audit", "authenticate", AuditHttpRequestContext.FAILURE, metadata, new AuditHttpRequestInfo() {
                    @Override
                    public String getSourceIp() {
                        return ctx.request().remoteAddress().toString();
                    }

                    @Override
                    public String getForwardedFor() {
                        return ctx.request().getHeader(AuditHttpRequestContext.X_FORWARDED_FOR_HEADER);
                    }
                });
            }
        };

        context.put(QuarkusHttpUser.AUTH_FAILURE_HANDLER, auditWrapper);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return bearerAuth.getChallenge(context);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Collections.singleton(TokenAuthenticationRequest.class);
    }

    @Override
    public HttpCredentialTransport getCredentialTransport() {
        return new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, "bearer");
    }

    private Uni<SecurityIdentity> authenticateWithClientCredentials(Pair<String, String> clientCredentials, RoutingContext context, IdentityProviderManager identityProviderManager) {
        try (OidcAuth oidcAuth = new OidcAuth(httpClient, clientCredentials.getLeft(), clientCredentials.getRight())) {
            final String jwtToken = oidcAuth.authenticate();//If we manage to get a token from basic credentials, try to authenticate it using the fetched token using the identity provider manager
            return identityProviderManager
                    .authenticate(new TokenAuthenticationRequest(new AccessTokenCredential(jwtToken, context)));
        }
    }
}
