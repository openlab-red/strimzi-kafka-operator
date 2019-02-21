#!/bin/bash

# path were the Secret with broker certificates is mounted
KAFKA_CERTS_KEYS=/etc/tls-sidecar/burry
CURRENT=${KAFKA_CERTS_NAME:-burry}

echo "pid = /usr/local/var/run/stunnel.pid"
echo "foreground = yes"
echo "debug = $TLS_SIDECAR_LOG_LEVEL"

cat <<-EOF
[zookeeper-2181]
client = yes
CAfile =  ${KAFKA_CERTS_KEYS}/ca.crt
cert = ${KAFKA_CERTS_KEYS}/${CURRENT}.crt
key = ${KAFKA_CERTS_KEYS}/${CURRENT}.key
accept = 127.0.0.1:2181
connect = ${KAFKA_ZOOKEEPER_CONNECT:-zookeeper-client:2181}
delay = yes
verify = 2

EOF