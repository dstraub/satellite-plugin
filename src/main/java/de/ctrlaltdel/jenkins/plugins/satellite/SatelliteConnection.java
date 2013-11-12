package de.ctrlaltdel.jenkins.plugins.satellite;

import hudson.FilePath;
import hudson.model.BuildListener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

/**
 * SatelliteConnection
 * @author ds
 */
public class SatelliteConnection {

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
	public SatelliteConnection login()  {
		if (logger == null) {
			logger = new PrintStream(System.out);
		}
		
		XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
		config.setServerURL(configuration.getRpcUrl());
		config.setEnabledForExtensions(true);

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
	 * packagesFindByNvr
	 */
	public Map<String, Object> listPackages(NVR nvr) {
		Object result = call("packages.findByNvrea", nvr.getName(), nvr.getVersion(), nvr.getRelease(), "", "noarch");
		return from(result);
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
		Object[] channels = call("channel.listMyChannels");
		List<String> result = new ArrayList<String>(channels.length);
		for (Object o: channels) {
			Map<String, Object> map = (Map<String, Object>) o;
			result.add((String)map.get("label"));
		}
		return result;
	}

	/**
	 * listConfigChannels
	 */
	public List<String> listConfigChannels() {
		Object[] channels = call("configchannel.listGlobals");
		List<String> result = new ArrayList<String>(channels.length);
		for (Object o: channels) {
			Map<String, Object> map = (Map<String, Object>) o;
			result.add((String)map.get("label"));
		}
		return result;
	}
	
	/**
	 * listGroups
	 */
	public List<String> listGroups() {
		Object[] groups = call("systemgroup.listAllGroups");
		List<String> result = new ArrayList<String>(groups.length);
		for (Object o: groups) {
			Map<String, Object> map = (Map<String, Object>) o;
			if (0 < (Integer) map.get("system_count")) {
				result.add((String)map.get("name"));
			}
		}
		return result;
	}

	/**
	 * listPackages
	 */
	public Map<String, Integer> listPackages(String channel) {
		Object[] packages = call("channel.software.listAllPackages", channel);
		Map<String, Integer> result = new HashMap<String, Integer>();
		for (Object o: packages) {
			Map<String, Object> map = (Map<String, Object>) o;
			String pkgName = (String) map.get("name") + '-' + map.get("version") + '-' + map.get("release"); 
			result.put(pkgName, (Integer) map.get("id"));
		}
		return result;
	}
	
	/**
	 * listConfigPaths
	 */
	public List<String> listConfigPaths(String configChannel) {
		Object[] channels = call("configchannel.listFiles", configChannel);
		Pattern pattern = StringUtils.isEmpty(configuration.getConfigPathPattern()) ? null : Pattern.compile(configuration.getConfigPathPattern());
		
		List<String> result = new ArrayList<String>(channels.length);
		for (Object o: channels) {
			Map<String, Object> map = (Map<String, Object>) o;
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
		Object[] fileInfos = call("configchannel.lookupFileInfo", configChannel, Arrays.asList(configPath));
		Map<String, Object> revision =  (Map<String, Object>) fileInfos[0];
		String contents = (String) revision.get("contents");
		return ((Boolean) revision.get("contents_enc64")) ? new String(Base64.decodeBase64(contents)) : contents;
	}

	/**
	 * updateConfig
	 */
	public boolean updateConfig(String configChannel, String configPath, String contents) {
		boolean wasOneCall = oneCall;
		oneCall = false;
		Object[] fileInfos = call("configchannel.lookupFileInfo", configChannel, Arrays.asList(configPath));
		Map<String, Object> revision =  (Map<String, Object>) fileInfos[0];
		Boolean encoded = (Boolean) revision.get("contents_enc64");
		revision.put("contents", encoded ? Base64.encodeBase64String(contents.getBytes()) : contents);
		revision.put("revision", ((Integer) revision.get("revision")) +1);
		revision.put("permissions", revision.get("permissions_mode"));
		
		for (String key: new String[] { "channel", "path", "modified", "type", "md5", "permissions_mode", "creation" }) {
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
	 * push
	 */
	public NVR push(FilePath filePath, String channel) {
		
		NVR nvr = new NVR(filePath.getName());
	    try {
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
	    	
	    	HttpResponse response = new DefaultHttpClient().execute(httpPost);
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
		
		Map<String, Object> data = listPackages(nvr);
		int id = (Integer) data.get("id");
		info("package-id: " + id);
		
		boolean result = addPackage(channel, id);
		info("push to '" + channel + "' was " + (result ? "successful " : "not successful"));
		
		return nvr;
	}

	/**
	 * from
	 */
	private Map<String, Object> from(Object result) {
		if (result instanceof Map) {
			return (Map<String, Object>) result;
		}
		if (result.getClass().isArray()) {
			return from(((Object[]) result)[0]);
		}
		return new HashMap<String, Object>();
	}

	/**
	 * dump
	 */
	private void dump(Map<String, Object> result) {
		for (Map.Entry<String, Object> me : result.entrySet()) {
			System.out.println(me.getKey() + ' ' + me.getValue());
		}

	}

	/**
	 * remoteScript
	 */
	public void remoteScript(String group, String script) {
		boolean wasOneCall = oneCall;
		oneCall = false;
		
		Object[] groups = call("systemgroup.listSystems", group);
		List<Integer> systemIds = new ArrayList<Integer>();
		StringBuilder sb = new StringBuilder("schedule update for ");
		for (Object o: groups) {
			Map<String, Object> system = (Map<String, Object>) o;
			systemIds.add((Integer)system.get("id"));
			sb.append(system.get("hostname")).append(' ');
		}
		long startTime = new Date().getTime() + 60 * 1000;
		String runScript =  script.startsWith("#!/") ? script : "#!/bin/sh\n" + script;
		Integer scriptId = call("system.scheduleScriptRun", systemIds, "root", "root", new Integer(300), runScript, new Date(startTime));
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
		Object[] systems = call("systemgroup.listSystems", group);
		List<String> hosts = new ArrayList<String>(systems.length);
		for (Object o: systems) {
			Map<String, Object> system = (Map<String, Object>) o;
			hosts.add((String) system.get("hostname"));
		}
		return hosts;
	}
	
	/**
	 * dump
	 */
	private void dump(HttpResponse response) {
		StringBuilder sb = new StringBuilder(response.getStatusLine().toString());
		for (Header header: response.getAllHeaders()) {
			String headerName = header.getName();
			String headerValue = header.getValue();
			if (headerName.equals("X-RHN-Upload-Error-String")) {
				sb.append(headerName).append(':')
				  .append(new String(Base64.decodeBase64(headerValue)))
				  .append('\n');
			}
		}
		if (200 != response.getStatusLine().getStatusCode() && 0 < response.getEntity().getContentLength()) try {
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
			result = (T) client.execute(method, params);
		} catch (Exception x) {
			throw new IllegalStateException(x);
		}
		
		if (oneCall && auth != null) {
			try {
				client.execute("auth.logout", new Object[]{ auth });
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
	
	
	public static void main(String[] args) {
		try {
			PluginConfiguration configuration = new PluginConfiguration().url("http://satellite.local").user("jenkins").password("jenkins");
			SatelliteConnection connection = SatelliteConnection.from(configuration).login();

//			Object o = connection.configContents("jboss-dev-config", "/etc/jbossas/sample-app.properties");
//			System.out.println(o);
			List<String> channels = connection.listChannels();
			for (String ch : channels) {
				System.out.println(ch);
			}
//
//			String rpm     = "/Users/ds/jmx4perl-1.07-1.noarch.rpm";
//			String channel = "jboss-dev";
//			connection.logout();
			

			
		} catch (Exception x) {
			x.printStackTrace();
		}
	}






}
