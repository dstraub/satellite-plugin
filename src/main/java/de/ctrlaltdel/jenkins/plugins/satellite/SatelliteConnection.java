package de.ctrlaltdel.jenkins.plugins.satellite;

import hudson.FilePath;
import hudson.model.BuildListener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import jenkins.model.Jenkins;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

/**
 * SatelliteConnection
 * 
 * @author ds
 */
public class SatelliteConnection {

    public static final String ATTR_PKG_NAME = "packageName"; 
    
    private final PluginConfiguration configuration;
    private String auth;
    private XmlRpcClient client;
    private PrintStream logger;
    private boolean oneCall;

    private SatelliteConnection(PluginConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * from
     */
    public static SatelliteConnection from(PluginConfiguration configuration) {
        return new SatelliteConnection(configuration);
    }

    public static SatelliteConnection create() {
        PluginConfiguration configuration = (PluginConfiguration) Jenkins.getInstance().getDescriptorOrDie(PluginConfiguration.class);
        return new SatelliteConnection(configuration);
    }

    /**
     * forOneCall
     */
    public SatelliteConnection forOneCall() {
        oneCall = true;
        return login();
    }

    public SatelliteConnection logger(BuildListener listener) {
        this.logger = listener.getLogger();
        return this;
    }

    /**
     * login
     */
    public SatelliteConnection login() {
        if (logger == null) {
            logger = new PrintStream(System.out);
        }

        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(configuration.getRpcUrl());
        config.setEnabledForExtensions(true);

        if (configuration.isSSL()) {
            initializeSSLContext();
        }

        client = new XmlRpcClient();
        client.setConfig(config);

        auth = call("auth.login", configuration.getUser(), configuration.getPassword());

        return this;
    }

    /**
     * logout
     */
    public void logout() {
        try {
            call("auth.logout");
        } finally {
            reset();
        }
    }

    /**
     * checkAuth
     */
    public SatelliteConnection checkAuth() {
        return auth == null ? login() : this;
    }

    /**
     * addPackages
     */
    public boolean addPackage(String channel, Integer id) {
        Integer result = call("channel.software.addPackages", channel, Arrays.asList(id));
        return result == 1;
    }

    /**
     * listChannels
     */
    public List<String> listChannels() {
        Map<String, Object>[] channels = call("channel.listMyChannels");
        List<String> result = new ArrayList<String>(channels.length);
        for (Map<String, Object> map : channels) {
            result.add((String) map.get("label"));
        }
        return result;
    }

    /**
     * listConfigChannels
     */
    public List<String> listConfigChannels() {
        Map<String, Object>[] channels = call("configchannel.listGlobals");
        List<String> result = new ArrayList<String>(channels.length);
        for (Map<String, Object> map : channels) {
            result.add((String) map.get("label"));
        }
        return result;
    }

    /**
     * listGroups
     */
    public List<String> listGroups() {
        Map<String, Object>[] groups = call("systemgroup.listAllGroups");
        List<String> result = new ArrayList<String>(groups.length);
        for (Map<String, Object> map : groups) {
            if (0 < (Integer) map.get("system_count")) {
                result.add((String) map.get("name"));
            }
        }
        return result;
    }

    /**
     * listPackages
     */
    public List<Map<String, Object>> listPackages(String channel) {
        Map<String, Object>[] packages = call("channel.software.listAllPackages", channel);
        for (Map<String, Object> map : packages) {
            String pkgName = (String) map.get("name") + '-' + map.get("version") + '-' + map.get("release");
            map.put("packageName", pkgName);
        }
        return Arrays.asList(packages);
    }
    
    /**
     * removePackages
     */
    public boolean removePackages(String channel, List<Integer> pkgIds) {
        Integer result = call("channel.software.removePackages", channel, pkgIds);
        if (result != 1) {
            return false;
        }
        info(pkgIds.size() + " packages removed from channel '" + channel + "'");
        for (Integer id : pkgIds) {
            try {
                int removeResult = call("packages.removePackage", id);
                if (removeResult != 1) {
                    error("deletion of package " + id + "failed");
                }
            } catch (Exception x) {
                error("deletion of package " + id + " failed: " + x.getMessage());
            }
        }
        return result == 1;
    }
    
    /**
     * listConfigPaths
     */
    public List<String> listConfigPaths(String configChannel) {
        Map<String, Object>[] channels = call("configchannel.listFiles", configChannel);
        Pattern pattern = StringUtils.isEmpty(configuration.getConfigPathPattern()) ? null : Pattern.compile(configuration.getConfigPathPattern());

        List<String> result = new ArrayList<String>(channels.length);
        for (Map<String, Object> map : channels) {
            String path = (String) map.get("path");
            boolean match = pattern == null ? true : pattern.matcher(path).matches();
            if (match) {
                result.add(path);
            }
        }
        return result;
    }

    /**
     * readConfig
     */
    public String readConfig(String configChannel, String configPath) {
        Map<String, Object>[] fileInfos = call("configchannel.lookupFileInfo", configChannel, Arrays.asList(configPath));
        Map<String, Object> revision = fileInfos[0];
        String contents = (String) revision.get("contents");
        return ((Boolean) revision.get("contents_enc64")) ? new String(Base64.decodeBase64(contents)) : contents;
    }

    /**
     * updateConfig
     */
    public boolean updateConfig(String configChannel, String configPath, String contents) {
        boolean wasOneCall = oneCall;
        oneCall = false;
        Map<String, Object>[] fileInfos = call("configchannel.lookupFileInfo", configChannel, Arrays.asList(configPath));
        Map<String, Object> revision = fileInfos[0];
        Boolean encoded = (Boolean) revision.get("contents_enc64");
        revision.put("contents", encoded ? Base64.encodeBase64String(contents.getBytes()) : contents);
        revision.put("revision", ((Integer) revision.get("revision")) + 1);
        revision.put("permissions", revision.get("permissions_mode"));

        for (String key : new String[] { "channel", "path", "modified", "type", "md5", "permissions_mode", "creation" }) {
            revision.remove(key);
        }

        Map<String, Object> newRevision = call("configchannel.createOrUpdatePath", configChannel, configPath, Boolean.FALSE, revision);
        boolean changed = revision.get("revision").equals(newRevision.get("revision"));
        if (changed) {
            info("contents updated, new revision=" + newRevision.get("revision"));

            Integer deploy = call("configchannel.deployAllSystems", configChannel);
            info("call deployAllSystems: " + (deploy == 1 ? "successful" : "error"));
        } else {
            warn("contents not updated !");
        }

        if (wasOneCall) {
            logout();
        }
        return true;
    }

    /**
     * remoteScript
     */
    public void remoteScript(String group, String user, String script) {
        boolean wasOneCall = oneCall;
        oneCall = false;

        Map<String, Object>[] groups = call("systemgroup.listSystems", group);
        List<Integer> systemIds = new ArrayList<Integer>();
        StringBuilder sb = new StringBuilder("schedule script for ");
        for (Map<String, Object> system : groups) {
            systemIds.add((Integer) system.get("id"));
            sb.append(system.get("hostname")).append(' ');
        }
        long startTime = new Date().getTime(); // + 60 * 1000;
        String runScript = script.startsWith("#!/") ? script : "#!/bin/sh\n" + script;
        Integer scriptId = call("system.scheduleScriptRun", systemIds, user, user, new Integer(300), runScript, new Date(startTime));
        sb.append(", script-id=").append(scriptId);
        info(sb.toString());

        if (wasOneCall) {
            logout();
        }
    }

    /**
     * listHosts
     */
    public List<String> listHosts(String group) {
        Map<String, Object>[] systems = call("systemgroup.listSystems", group);
        List<String> hosts = new ArrayList<String>(systems.length);
        for (Map<String, Object> system : systems) {
            hosts.add((String) system.get("hostname"));
        }
        return hosts;
    }
    
    /**
     * push
     */
    public NVR push(FilePath filePath, String channel) {

        NVR nvr = new NVR(filePath.getName());
        try {
            initializeSSLContext();

            HttpPost httpPost = new HttpPost(configuration.getUrl() + "/PACKAGE-PUSH");
            httpPost.setHeader("X-RHN-Upload-Auth-Session", auth);
            httpPost.setHeader("X-RHN-Upload-File-Checksum-Type", "md5");
            httpPost.setHeader("X-RHN-Upload-Force", "0");
            httpPost.setHeader("X-RHN-Upload-Package-Arch", "noarch");
            httpPost.setHeader("X-RHN-Upload-Package-Name", "sample-app");
            httpPost.setHeader("X-RHN-Upload-Package-Release", "1");
            httpPost.setHeader("X-RHN-Upload-Package-Version", "1.0");
            httpPost.setHeader("X-RHN-Upload-Packaging", "rpm");
            httpPost.setHeader("X-RHN-Upload-File-Checksum", filePath.digest());

            httpPost.setEntity(new InputStreamEntity(filePath.read(), filePath.length(), ContentType.create("application/x-rpm")));

            info("upload " + filePath);
            
            DefaultHttpClient httpClient = new DefaultHttpClient();
            if (configuration.isSSL()) {
                configureHttps(httpClient);
            }
            HttpResponse response = httpClient.execute(httpPost);
            if (response.getStatusLine().getStatusCode() == 200) {
                info("upload was successful");
            } else {
                dump(response);
                return null;
            }

        } catch (Exception x) {
            error(x.getClass().getSimpleName() + ": " + x.getMessage());
            throw new IllegalStateException(x);
        }

        Map<String, Object>[] packages = call("packages.findByNvrea", nvr.getName(), nvr.getVersion(), nvr.getRelease(), "", "noarch");
        if (packages.length != 1) {
            throw new IllegalStateException("non unique nvr " + nvr);
        }
        int id = (Integer) packages[0].get("id");
        info("package-id: " + id);

        boolean result = addPackage(channel, id);
        info("push to '" + channel + "' was " + (result ? "successful " : "not successful"));

        return nvr;
    }

    /**
     * initializeSSLContext
     */
    private void initializeSSLContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            } };

            SSLContext sslContext = SSLContext.getInstance("SSL");
            HostnameVerifier hostnameVerifier = new HostnameVerifier() {
                public boolean verify(String host, SSLSession session) {
                    return true;
                }
            };

            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
        } catch (Exception x) {
            throw new IllegalStateException(x);
        }

    }

    /**
     * configureHttps
     */
    private void configureHttps(DefaultHttpClient httpClient) {
        TrustStrategy trustStrategy = new TrustStrategy() {
            public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                return true;
            }
        };
        try {
            SSLSocketFactory sslSocketFactory = new SSLSocketFactory(trustStrategy, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            Scheme https = new Scheme("https", 443, sslSocketFactory);
            httpClient.getConnectionManager().getSchemeRegistry().register(https);
        } catch (Exception x) {
            throw new IllegalStateException(x);
        }
    }

    /**
     * dump
     */
    private void dump(HttpResponse response) {
        StringBuilder sb = new StringBuilder(response.getStatusLine().toString());
        for (Header header : response.getAllHeaders()) {
            String headerName = header.getName();
            String headerValue = header.getValue();
            if (headerName.equals("X-RHN-Upload-Error-String")) {
                sb.append(headerName).append(':').append(new String(Base64.decodeBase64(headerValue))).append('\n');
            }
        }
        if (200 != response.getStatusLine().getStatusCode() && 0 < response.getEntity().getContentLength())
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read = 0;
                InputStream is = response.getEntity().getContent();
                while ((read = is.read(buffer)) > 0) {
                    baos.write(buffer, 0, read);
                }
                sb.append(baos.toString());
            } catch (Exception x) {
                // ignore
            }

        error(sb.toString());
    }

    /**
     * logging
     */
    public void info(String msg) {
        logger.println(String.format("[INFO] %s", msg));
    }

    public void warn(String msg) {
        logger.println(String.format("[WARN] %s", msg));
    }

    public void error(String msg) {
        logger.println(String.format("[ERROR] %s", msg));
    }

    /**
     * call
     */
    private <T> T call(String method, Object... args) {
        Object[] params = null;
        if (auth != null) {
            params = new Object[args.length + 1];
            params[0] = auth;
            System.arraycopy(args, 0, params, 1, args.length);
        } else {
            params = args;
        }
        T result = null;
        try {
            Object obj = client.execute(method, params);
            if (obj.getClass().isArray()) {
                Object[] objArray = (Object[]) obj;
                if (0 < objArray.length && objArray[0] instanceof Map) {
                    Map<?, ?>[] mapArray = new Map<?, ?>[objArray.length];
                    System.arraycopy(objArray, 0, mapArray, 0, objArray.length);
                    result = (T) mapArray;
                }
            } else {
                result = (T) obj;
            }
            
        } catch (Exception x) {
            throw new IllegalStateException(x);
        }

        if (oneCall && auth != null) {
            try {
                client.execute("auth.logout", new Object[] { auth });
            } catch (Exception x) {
                // ignore
            } finally {
                reset();
            }
        }

        return result;
    }

    /**
     * reset
     */
    private void reset() {
        client = null;
        auth = null;
        oneCall = false;
    }

}
