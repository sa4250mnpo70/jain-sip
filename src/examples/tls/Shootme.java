    package examples.tls;
import gov.nist.javax.sip.TransactionExt;

import javax.sip.*;
import javax.sip.address.*;
import javax.sip.header.*;
import javax.sip.message.*;

import java.security.cert.Certificate;
import java.util.*;

/**
 * This class is a UAC template. Shootist is the guy that shoots and shootme
 * is the guy that gets shot.
 *
 *@author Daniel Martinez
 */

public class Shootme implements SipListener {

    private static AddressFactory addressFactory;
    private static MessageFactory messageFactory;
    private static HeaderFactory headerFactory;
    private static SipStack sipStack;
    private static final String myAddress = "127.0.0.1";
    private static final int myPort    = 5071;

    protected ServerTransaction inviteTid;

    Dialog dialog;

    class ApplicationData {
        protected int ackCount;
    }

    protected static final String usageString =
        "java "
            + "examples.shootistTLS.Shootist \n"
            + ">>>> is your class path set to the root?";

    private static void usage() {
        System.out.println(usageString);
        System.exit(0);

    }

    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        ServerTransaction serverTransactionId =
            requestEvent.getServerTransaction();

        System.out.println(
            "\n\nRequest "
                + request.getMethod()
                + " received at "
                + sipStack.getStackName()
                + " with server transaction id "
                + serverTransactionId);

        if (request.getMethod().equals(Request.INVITE)) {
            processInvite(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.ACK)) {
            processAck(requestEvent, serverTransactionId);
        } else if (request.getMethod().equals(Request.BYE)) {
            processBye(requestEvent, serverTransactionId);
        }

    }

    /** Process the ACK request. Send the bye and complete the call flow.
    */
    public void processAck(
        RequestEvent requestEvent,
        ServerTransaction serverTransaction) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        try {
            System.out.println("shootme: got an ACK "
                + requestEvent.getRequest());
            int ackCount =
                ((ApplicationData ) dialog.getApplicationData()).ackCount;
            if (ackCount == 1) {
               dialog = inviteTid.getDialog();
               Request byeRequest = dialog.createRequest(Request.BYE);
               ClientTransaction tr =
                sipProvider.getNewClientTransaction(byeRequest);
               System.out.println("shootme: got an ACK -- sending bye! ");
               dialog.sendRequest(tr);
               System.out.println("Dialog State = " + dialog.getState());
            } else ((ApplicationData) dialog.getApplicationData()).ackCount ++;
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);
        }
    }

    /** Process the invite request.
     */
    public void processInvite(
        RequestEvent requestEvent,
        ServerTransaction serverTransaction) {
        SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        System.out.println("Got an INVITE  " + request);
        try {
            System.out.println("shootme: got an Invite sending OK");
            //System.out.println("shootme:  " + request);
            Response response = messageFactory.createResponse(180, request);
            ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
            toHeader.setTag("4321"); // Application is supposed to set.
            Address address =
                addressFactory.createAddress("Shootme <sip:" + myAddress+ ":" + myPort 
                + ";transport=tls>" );
            ContactHeader contactHeader =
                headerFactory.createContactHeader(address);

            response.addHeader(contactHeader);
            ServerTransaction st = requestEvent.getServerTransaction();

            if (st == null) {
                st = sipProvider.getNewServerTransaction(request);
                    if (st.getDialog().getApplicationData() == null) {
                    st.getDialog().setApplicationData(new ApplicationData());
                }
                } else {
                System.out.println("This is a RE INVITE ");
                if (st.getDialog() != dialog) {
                   System.out.println("Whoopsa Daisy Dialog Mismatch");
                   System.exit(0);
                }
            }

            // Thread.sleep(5000);
            System.out.println("got a server tranasaction " + st);
            byte[] content = request.getRawContent();
            if (content != null) {
                ContentTypeHeader contentTypeHeader =
                headerFactory.createContentTypeHeader("application", "sdp");
                System.out.println("response = " + response);
                response.setContent(content, contentTypeHeader);
            }
            dialog = st.getDialog();
            if (dialog != null) {
                System.out.println("Dialog " + dialog);
                System.out.println("Dialog state " + dialog.getState());
            }
            st.sendResponse(response);
            TransactionExt stExt = ( TransactionExt)st  ;
            Certificate[] certs = stExt.getPeerCertificates();
            System.out.println("Certs = " + certs);
            
            for (Certificate cert: certs ) {
                System.out.println("Cert = " + cert);
            }
  
            response = messageFactory.createResponse(200, request);
            toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
            toHeader.setTag("4321"); // Application is supposed to set.
            response.addHeader(contactHeader);
            st.sendResponse(response);
            this.inviteTid = st;
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);
        }
    }

    /** Process the bye request.
     */
    public void processBye(
        RequestEvent requestEvent,
        ServerTransaction serverTransactionId) {
        // SipProvider sipProvider = (SipProvider) requestEvent.getSource();
        Request request = requestEvent.getRequest();
        try {
            System.out.println("shootme:  got a bye sending OK.");
            Response response =
                messageFactory.createResponse(200, request, null, null);
            serverTransactionId.sendResponse(response);
            System.out.println("Dialog State is " + serverTransactionId.getDialog().getState());

        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);

        }
    }

    public void processResponse(ResponseEvent responseReceivedEvent) {
        System.out.println("Got a response");
        Response response = (Response) responseReceivedEvent.getResponse();
        Transaction tid = responseReceivedEvent.getClientTransaction();

        System.out.println(
            "Response received with client transaction id "
                + tid
                + ":\n"
                + response);
        try {
            if (response.getStatusCode() == Response.OK
                && ((CSeqHeader) response.getHeader(CSeqHeader.NAME))
                    .getMethod()
                    .equals(
                    Request.INVITE)) {
                if (tid != this.inviteTid) {
                    new Exception().printStackTrace();
                    System.exit(0);
                }
                Dialog dialog = tid.getDialog();
                // Save the tags for the dialog here.
                Request request = tid.getRequest();
                dialog.sendAck(request);
            }
            Dialog dialog = tid.getDialog();
            System.out.println("Dalog State = " + dialog.getState());
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);
        }

    }

    public void processTimeout(javax.sip.TimeoutEvent timeoutEvent) {
        Transaction transaction;
        if (timeoutEvent.isServerTransaction()) {
            transaction = timeoutEvent.getServerTransaction();
        } else {
            transaction = timeoutEvent.getClientTransaction();
        }
        System.out.println("state = " + transaction.getState());
        System.out.println("dialog = " + transaction.getDialog());
        System.out.println(
            "dialogState = " + transaction.getDialog().getState());
        System.out.println("Transaction Time out");
    }

    public void init() {
        SipFactory sipFactory = null;
        sipStack = null;
        sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "shootme");
        // You need  16 for logging traces. 32 for debug + traces.
        // Your code will limp at 32 but it is best for debugging.
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
        properties.setProperty(
            "gov.nist.javax.sip.DEBUG_LOG",
            "shootmedebug.txt");
        properties.setProperty(
            "gov.nist.javax.sip.SERVER_LOG",
            "shootmelog.txt");
        // Guard against starvation.
        properties.setProperty(
            "gov.nist.javax.sip.READ_TIMEOUT", "1000");
  
        try {
            // Create SipStack object
            sipStack = sipFactory.createSipStack(properties);
            System.out.println("sipStack = " + sipStack);
        } catch (PeerUnavailableException e) {
            // could not find
            // gov.nist.jain.protocol.ip.sip.SipStackImpl
            // in the classpath
            e.printStackTrace();
            System.err.println(e.getMessage());
            if (e.getCause() != null)
                e.getCause().printStackTrace();
            System.exit(0);
        }

        try {
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            messageFactory = sipFactory.createMessageFactory();
            ListeningPoint lpTLS = sipStack.createListeningPoint("127.0.0.1", myPort, "tls");

            Shootme listener = this;

            SipProvider sipProvider = sipStack.createSipProvider(lpTLS);
            System.out.println("tls provider " + sipProvider);
            sipProvider.addSipListener(listener);

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
            usage();
        }

    }

    public static void main(String args[]) {
        new Shootme().init();
    }

    public void processIOException(IOExceptionEvent exceptionEvent) {
        System.out.println("IOException occured while retransmitting requests:" + exceptionEvent);
    }
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        System.out.println("Transaction Terminated event: " + transactionTerminatedEvent );
    }
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        System.out.println("Dialog Terminated event: " + dialogTerminatedEvent);
    }
    
    public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {
		System.out.println("Unexpected DialogTimeout event");
		System.exit(0);
		
	}
	
}
