package gov.nist.javax.sip.stack;

import javax.sip.ClientTransaction;
import javax.sip.TlsSecurityPolicy;

public class DefaultTlsSecurityPolicy implements TlsSecurityPolicy {

    /**
     * Enforce any application-specific security policy for TLS clients.
     * Called when establishing an outgoing TLS connection.
     * @param transaction -- the transaction context for the connection
     * @throws SecurityException -- if the connection should be rejected
     */
    public void enforceTlsPolicy(ClientTransaction transaction) throws SecurityException
    {
       // accept everything: do not throw exception
    }

}
