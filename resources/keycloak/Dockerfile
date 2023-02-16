FROM quay.io/keycloak/keycloak:19.0.3 as builder

ENV KC_FEATURES=token-exchange
ENV KC_DB=mysql

# Build the customized Keycloak setup with MySQL, token exchange, and the metrics service provider interface
RUN /opt/keycloak/bin/kc.sh build


FROM quay.io/keycloak/keycloak:19.0.3
COPY --from=builder /opt/keycloak/ /opt/keycloak/

# Install custom themes
COPY themes/saas-boost-theme/ /opt/keycloak/themes/saas-boost-theme/

# Don't need a self signed cert since we're running behind a TLS terminating proxy
#WORKDIR /opt/keycloak
#RUN keytool -genkeypair -storepass password -storetype PKCS12 -keyalg RSA -keysize 2048 -dname "CN=server" -alias server -ext "SAN:c=DNS:localhost,IP:127.0.0.1" -keystore conf/server.keystore

EXPOSE 8080
#EXPOSE 8443

# Environment variables aren't shared between Docker build stages
# All of these can also be defined as command switches to kc.sh

#--health-enabled=true
ENV KC_HEALTH_ENABLED=true
#--metrics-enabled=true
ENV KC_METRICS_ENABLED=true
#--http-enabled=true
ENV KC_HTTP_ENABLED=true
#--hostname-strict=false
ENV KC_HOSTNAME_STRICT=false
#--hostname-strict-https=false
ENV KC_HOSTNAME_STRICT_HTTPS=false
#--hostname-strict-backchannel=false
ENV KC_HOSTNAME_STRICT_BACKCHANNEL=false
#--proxy edge (no equal symbol)
ENV KC_PROXY=edge

# These are overwritten by the Task Definition when the container is launched
# Because we don't know these values yet, we can't run --optimized or we 
# won't be able to conect to the database and Keycloak will fail to start
#ENV KC_HOSTNAME=localhost
#ENV KC_DB=mysql
#ENV KC_DB_URL=<DBURL>
#ENV KC_DB_USERNAME=<DBUSERNAME>
#ENV KC_DB_PASSWORD=<DBPASSWORD>

RUN /opt/keycloak/bin/kc.sh build

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start"]
#CMD ["start", "--log-level=debug"]
#CMD ["start-dev"]