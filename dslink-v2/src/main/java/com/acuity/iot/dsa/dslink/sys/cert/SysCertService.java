package com.acuity.iot.dsa.dslink.sys.cert;

import com.acuity.iot.dsa.dslink.sys.cert.HostnameWhitelist.WhitelistValue;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map.Entry;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import org.iot.dsa.dslink.Action.ResultsType;
import org.iot.dsa.dslink.ActionResults;
import org.iot.dsa.node.DSBool;
import org.iot.dsa.node.DSInfo;
import org.iot.dsa.node.DSMap;
import org.iot.dsa.node.DSNode;
import org.iot.dsa.node.DSString;
import org.iot.dsa.node.action.DSAction;
import org.iot.dsa.node.action.DSIActionRequest;
import org.iot.dsa.security.DSPasswordAes128;
import org.iot.dsa.util.DSException;

/**
 * Certificate management for the whole process.  This is basically a stub for future
 * functionality.  Right now it generates a self signed cert for the link as well
 * as accepts self signed (anonymous) certs from the broker.
 *
 * @author Aaron Hansen
 * @author Daniel Shapiro
 */
public class SysCertService extends DSNode {

    // Constants
    // ---------

    private static final String ALLOW_CLIENTS = "Allow Anonymous Clients";
    private static final String ALLOW_SERVERS = "Allow Anonymous Servers";
    private static final String VERIFY_HOSTNAMES = "Enable Hostname-Certificate Verification";
    private static final String HOSTNAME_WHITELIST = "Hostname Whitelist";
    private static final String CERTFILE = "Cert File";
    private static final String CERTFILE_PASS = "Cert File Pass";
    private static final String CERTFILE_TYPE = "Cert File Type";
    private static final String LOCAL_TRUSTSTORE = "Local Truststore";
    private static final String QUARANTINE = "Quarantine";
    private static final String GENERATE_CSR = "Generate Certificate Signing Request";
    private static final String IMPORT_CA_CERT = "Import CA Certificate";
    private static final String IMPORT_PRIMARY_CERT = "Import Primary Certificate";
    private static final String GENERATE_SELF_SIGNED = "Generate Self-Signed Certificate";
    private static final String DELETE_KS_ENTRY = "Delete Keystore Entry";
    private static final String GET_KS_ENTRY = "Get Keystore Entry";
    private static final String DEFAULT_CERTFILE = "dslink.jks";

    // Fields
    // ------
    private static SysCertService inst;
    private static HostnameVerifier oldHostnameVerifier = HttpsURLConnection
            .getDefaultHostnameVerifier();
    private DSInfo<?> allowClients = getInfo(ALLOW_CLIENTS);
    private DSInfo<?> allowServers = getInfo(ALLOW_SERVERS);
    private DSInfo<?> deleteKSEntry = getInfo(DELETE_KS_ENTRY);
    private DSInfo<?> generateCsr = getInfo(GENERATE_CSR);
    private DSInfo<?> generateSSCert = getInfo(GENERATE_SELF_SIGNED);
    private DSInfo<?> getKSEntry = getInfo(GET_KS_ENTRY);
    private HostnameVerifier hostnameVerifier = new SysHostnameVerifier();
    private DSInfo<?> importCaCert = getInfo(IMPORT_CA_CERT);
    private DSInfo<?> importPrimaryCert = getInfo(IMPORT_PRIMARY_CERT);
    private DSInfo<?> keystorePass = getInfo(CERTFILE_PASS);
    private DSInfo<?> keystorePath = getInfo(CERTFILE);
    private DSInfo<?> keystoreType = getInfo(CERTFILE_TYPE);
    private KeyStore localTruststore;
    private CertCollection localTruststoreNode;
    private CertCollection quarantine;
    private DSInfo<?> verifyHostnames = getInfo(VERIFY_HOSTNAMES);
    private HostnameWhitelist whitelist;

    public SysCertService() {
    }

    public void addToQuarantine(X509Certificate cert) {
        try {
            getQuarantine().addCertificate(cert);
        } catch (CertificateEncodingException e) {
            error("", e);
        }
    }

    public void allow(DSInfo<?> certInfo) {
        String name = certInfo.getName();
        CertNode certNode = (CertNode) certInfo.getNode();
        String certStr = certNode.toElement().toString();
        getQuarantine().remove(certInfo);
        getLocalTruststoreNode().addCertificate(name, certStr);
    }

    /**
     * True if self signed anonymous client certs are allowed.
     */
    public boolean allowAnonymousClients() {
        return allowClients.getElement().toBoolean();
    }

    /**
     * True if self signed anonymous server certs are allowed.
     */
    public boolean allowAnonymousServers() {
        return allowServers.getElement().toBoolean();
    }

    @Override
    public void declareDefaults() {
        declareDefault(ALLOW_CLIENTS, DSBool.FALSE);
        declareDefault(ALLOW_SERVERS, DSBool.TRUE);
        declareDefault(VERIFY_HOSTNAMES, DSBool.TRUE);
        declareDefault(HOSTNAME_WHITELIST, new HostnameWhitelist());
        declareDefault(CERTFILE, DSString.valueOf(DEFAULT_CERTFILE));
        declareDefault(CERTFILE_TYPE, DSString.valueOf("JKS"));
        declareDefault(CERTFILE_PASS, DSPasswordAes128.valueOf("dsarocks"));
        declareDefault(LOCAL_TRUSTSTORE, new CertCollection());
        declareDefault(QUARANTINE, new CertCollection()).setTransient(true);
        declareDefault(GENERATE_CSR, getGenerateCSRAction());
        declareDefault(IMPORT_CA_CERT, getImportCACertAction());
        declareDefault(IMPORT_PRIMARY_CERT, getImportPrimaryCertAction());
        declareDefault(GENERATE_SELF_SIGNED, getGenerateSelfSignedAction());
        declareDefault(GET_KS_ENTRY, getGetKSEntryAction());
        declareDefault(DELETE_KS_ENTRY, getDeleteKSEntryAction());
        declareDefault("Help", DSString.valueOf(
                "https://iot-dsa-v2.github.io/sdk-dslink-java-v2/Certificate%20Service"))
                .setTransient(true).setReadOnly(true);
    }

    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    public static SysCertService getInstance() {
        return inst;
    }

    public KeyStore getLocalTruststore() {
        if (localTruststore == null) {
            try {
                localTruststore = KeyStore.getInstance(KeyStore.getDefaultType());
                localTruststore.load(null);
                for (Entry<String, X509Certificate> entry : getLocalTruststoreNode()
                        .getCertificates().entrySet()) {
                    X509Certificate cert = entry.getValue();
                    addCertToLocalTruststore(cert);
                }
            } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
                warn("Failed to create local truststore object", e);
            }
        }
        return localTruststore;
    }

    public boolean hostnameVerificationEnabled() {
        return verifyHostnames.getElement().toBoolean();
    }

    public boolean isInTrustStore(X509Certificate cert) {
        return getLocalTruststoreNode().containsCertificate(cert);
    }

    // Methods
    // -------

    public void onCertAddedToCollection(CertCollection collection, X509Certificate cert) {
        if (collection == localTruststoreNode) {
            addCertToLocalTruststore(cert);
            try {
                AnonymousTrustFactory.initLocalTrustManager();
            } catch (NoSuchAlgorithmException | KeyStoreException e) {
                warn("", e);
            }
        }
    }

    public void onCertRemovedFromCollection(CertCollection collection, X509Certificate cert) {
        if (collection == localTruststoreNode) {
            removeCertFromLocalTruststore(cert);
            try {
                AnonymousTrustFactory.initLocalTrustManager();
            } catch (NoSuchAlgorithmException | KeyStoreException e) {
                warn("", e);
            }
        }
    }

    @Override
    public void onStarted() {
        inst = this;
        AnonymousTrustFactory.init(this);
        String keystore = getKeystorePath();
        File f = new File(keystore);
        if (isKeytoolAvailable()) {
            if (!f.exists()) {
                keytoolGenkey();
            }
        } else {
            info("Keytool not available. Disabling keytool functionality and attempting to use existing keystore");
            if (!f.exists()) {
                InputStream inpStream = null;
                FileOutputStream outStream = null;
                try {
                    inpStream = SysCertService.class.getResourceAsStream(DEFAULT_CERTFILE);
                    if (inpStream != null) {
                        int readBytes;
                        byte[] buffer = new byte[4096];
                        outStream = new FileOutputStream(f);
                        while ((readBytes = inpStream.read(buffer)) > 0) {
                            outStream.write(buffer, 0, readBytes);
                        }
                    }
                } catch (Exception e) {
                    debug("", e);
                } finally {
                    try {
                        if (inpStream != null) {
                            inpStream.close();
                        }
                        if (outStream != null) {
                            outStream.close();
                        }
                    } catch (Exception e) {
                        debug("", e);
                    }
                }
                if (!f.exists()) {
                    error("Existing keystore not found and new one could not be generated");
                }
            }
            generateCsr.setPrivate(true);
            importCaCert.setPrivate(true);
            importPrimaryCert.setPrivate(true);
            generateSSCert.setPrivate(true);
            getKSEntry.setPrivate(true);
            deleteKSEntry.setPrivate(true);
        }

        try {
            System.setProperty("javax.net.ssl.keyStore", keystore);
            System.setProperty("javax.net.ssl.keyStoreType",
                               keystoreType.getElement().toString());
            System.setProperty("javax.net.ssl.keyStorePassword", getCertFilePass());
        } catch (Exception x) {
            error(getParent(), x);
        }
        HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
    }

    @Override
    protected void onChildChanged(DSInfo<?> info) {
        if (info == keystorePath) {
            System.setProperty("javax.net.ssl.keyStore", getKeystorePath());
        } else if (info == keystoreType) {
            System.setProperty("javax.net.ssl.keyStoreType",
                               keystoreType.getElement().toString());
        } else if (info == keystorePass) {
            System.setProperty("javax.net.ssl.keyStorePassword", getCertFilePass());
        }
    }

    private void addCertToLocalTruststore(X509Certificate cert) {
        try {
            String alias = generateCertAlias(cert);
            getLocalTruststore().setCertificateEntry(alias, cert);
        } catch (CertificateEncodingException | NoSuchAlgorithmException | KeyStoreException e) {
            warn("", e);
        }
    }

    private String deleteKSEntry() {
        return KeyToolUtil.deleteEntry(getKeystorePath(), getCertFilePass());
    }

    private String[] generateCSR() {
        try {
            return KeyToolUtil.generateCSR(getKeystorePath(), getCertFilePass());
        } catch (IOException e) {
            DSException.throwRuntime(e);
            return null;
        }
    }

    private static String generateCertAlias(X509Certificate cert)
            throws CertificateEncodingException, NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("MD5").digest(cert.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            sb.append(Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    private String getCertFilePass() {
        DSPasswordAes128 pass = (DSPasswordAes128) keystorePass.get();
        return pass.decode();
    }

    private DSAction getDeleteKSEntryAction() {
        DSAction act = new DSAction() {

            @Override
            public ActionResults invoke(DSIActionRequest req) {
                String result = ((SysCertService) req.getTargetInfo().get()).deleteKSEntry();
                return makeResults(req, DSString.valueOf(result));
            }

        };
        act.setResultsType(ResultsType.VALUES);
        act.addColumnMetadata("Result", DSString.NULL);
        return act;
    }

    private DSAction getGenerateCSRAction() {
        DSAction act = new DSAction() {
            @Override
            public ActionResults invoke(DSIActionRequest req) {
                String[] results = ((SysCertService) req.getTargetInfo().get()).generateCSR();
                if (results != null && results.length > 1) {
                    return makeResults(req,
                                       DSString.valueOf(results[0]),
                                       DSString.valueOf(results[1]));
                } else {
                    return null;
                }
            }
        };
        act.setResultsType(ResultsType.VALUES);
        act.addColumnMetadata("Result", DSString.NULL);
        act.addColumnMetadata("CSR", DSString.NULL).setEditor("textarea");
        return act;
    }

    private DSAction getGenerateSelfSignedAction() {
        DSAction act = new DSAction() {

            @Override
            public ActionResults invoke(DSIActionRequest req) {
                String result = ((SysCertService) req.getTargetInfo().get()).keytoolGenkey();
                return makeResults(req, DSString.valueOf(result));
            }

        };
        act.setResultsType(ResultsType.VALUES);
        act.addColumnMetadata("Result", DSString.NULL);
        return act;
    }

    private DSAction getGetKSEntryAction() {
        DSAction act = new DSAction() {

            @Override
            public ActionResults invoke(DSIActionRequest req) {
                String result = ((SysCertService) req.getTargetInfo().get()).getKSEntry();
                return makeResults(req, DSString.valueOf(result));
            }

        };
        act.setResultsType(ResultsType.VALUES);
        act.addColumnMetadata("Entry", DSString.NULL).setEditor("textarea");
        return act;
    }

    private HostnameWhitelist getHostnameWhitelist() {
        if (whitelist == null) {
            whitelist = (HostnameWhitelist) getInfo(HOSTNAME_WHITELIST).get();
        }
        return whitelist;
    }

    private DSAction getImportCACertAction() {
        DSAction act = new DSAction() {

            @Override
            public ActionResults invoke(DSIActionRequest req) {
                DSMap parameters = req.getParameters();
                String result = ((SysCertService) req.getTargetInfo().get())
                        .importCACert(parameters);
                return makeResults(req, DSString.valueOf(result));
            }

        };
        act.addParameter("Alias", DSString.NULL, null);
        act.addParameter("Certificate", DSString.NULL, null).setEditor("textarea");
        act.setResultsType(ResultsType.VALUES);
        act.addColumnMetadata("Result", DSString.NULL);
        return act;
    }

    private DSAction getImportPrimaryCertAction() {
        DSAction act = new DSAction() {

            @Override
            public ActionResults invoke(DSIActionRequest req) {
                DSMap parms = req.getParameters();
                String result = ((SysCertService) req.getTargetInfo().get())
                        .importPrimaryCert(parms);
                return makeResults(req, DSString.valueOf(result));
            }

        };
        act.addParameter("Certificate", DSString.NULL, null).setEditor("textarea");
        act.setResultsType(ResultsType.VALUES);
        act.addColumnMetadata("Result", DSString.NULL);
        return act;
    }

    private String getKSEntry() {
        return KeyToolUtil.getEntry(getKeystorePath(), getCertFilePass());
    }

    private String getKeystorePath() {
        return keystorePath.getElement().toString();
    }

    private CertCollection getLocalTruststoreNode() {
        if (localTruststoreNode == null) {
            localTruststoreNode = (CertCollection) getInfo(LOCAL_TRUSTSTORE).get();
        }
        return localTruststoreNode;
    }

    private CertCollection getQuarantine() {
        if (quarantine == null) {
            quarantine = (CertCollection) getInfo(QUARANTINE).get();
        }
        return quarantine;
    }

    private String importCACert(DSMap parameters) {
        String alias = parameters.getString("Alias");
        String certStr = parameters.getString("Certificate");
        try {
            return KeyToolUtil.importCACert(getKeystorePath(), certStr, alias, getCertFilePass());
        } catch (IOException e) {
            DSException.throwRuntime(e);
            return null;
        }
    }

    private String importPrimaryCert(DSMap parameters) {
        String certStr = parameters.getString("Certificate");
        try {
            return KeyToolUtil.importPrimaryCert(getKeystorePath(), certStr, getCertFilePass());
        } catch (IOException e) {
            DSException.throwRuntime(e);
            return null;
        }
    }

    private boolean isKeytoolAvailable() {
        String result = KeyToolUtil.help();
        return result != null && !result.isEmpty();
    }

    /**
     * Executes the java keytool to generate a new self signed cert.
     */
    private String keytoolGenkey() {
        return KeyToolUtil.generateSelfSigned(getKeystorePath(), getCertFilePass());
    }

    private void removeCertFromLocalTruststore(X509Certificate cert) {
        try {
            String alias = generateCertAlias(cert);
            getLocalTruststore().deleteEntry(alias);
        } catch (CertificateEncodingException | NoSuchAlgorithmException | KeyStoreException e) {
            warn("", e);
        }
    }

    private class SysHostnameVerifier implements HostnameVerifier {

        @Override
        public boolean verify(String hostname, SSLSession session) {
            if (getHostnameWhitelist().isEnabled()) {
                WhitelistValue wlval = getHostnameWhitelist().checkHostname(hostname);
                if (wlval != null) {
                    switch (wlval) {
                        case ALLOWED:
                            return true;
                        case FORBIDDEN:
                            return false;
                    }
                }
            }
            if (hostnameVerificationEnabled()) {
                return oldHostnameVerifier.verify(hostname, session);
            } else {
                return true;
            }
        }
    }

}
