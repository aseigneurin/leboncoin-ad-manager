# Leboncoin-ad-manager

This program allows managing on website http://leboncoin.fr

## Accepting the certificate

To accept the certificate:

    $ openssl s_client -connect compteperso.leboncoin.fr:443 -showcerts </dev/null 2>/dev/null|openssl x509 -outform PEM >leboncoin.pem
    $ sudo $JAVA_HOME/bin/keytool -importcert -alias leboncoin -file leboncoin.pem -keystore $JAVA_HOME/jre/lib/security/cacerts

When asked for the keystore's password, type 'changeit'.

