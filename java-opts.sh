#!/usr/bin/env sh

if test -r "${NAV_TRUSTSTORE_PATH}";
then
    echo "Using truststore:"
    ls -l "${NAV_TRUSTSTORE_PATH}"
    if ! keytool -list -keystore ${NAV_TRUSTSTORE_PATH} -storepass "${NAV_TRUSTSTORE_PASSWORD}" > /dev/null;
    then
        echo Truststore is corrupt, or bad password
        exit 1
    fi

    JAVA_OPTS="${JAVA_OPTS} -Djavax.net.ssl.trustStore=${NAV_TRUSTSTORE_PATH}"
    JAVA_OPTS="${JAVA_OPTS} -Djavax.net.ssl.trustStorePassword=${NAV_TRUSTSTORE_PASSWORD}"
fi

JAVA_OPTS="${JAVA_OPTS} -XX:+HeapDumpOnOutOfMemoryError"
JAVA_OPTS="${JAVA_OPTS} -XX:HeapDumpPath=/tmp/"
JAVA_OPTS="${JAVA_OPTS} -Xmx256m"
JAVA_OPTS="${JAVA_OPTS} -Djdk.tls.client.protocols=TLSv1.2"

# inject proxy settings set by the nais platform
JAVA_OPTS="${JAVA_OPTS} ${JAVA_PROXY_OPTIONS}"

export JAVA_OPTS
