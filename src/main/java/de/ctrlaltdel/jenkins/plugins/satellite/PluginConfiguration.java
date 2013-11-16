package de.ctrlaltdel.jenkins.plugins.satellite;

import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;


/**
 * SatelliteConfiguration
 * @author ds
 */
@Extension
public class PluginConfiguration extends GlobalConfiguration {

    private String user;
    private String password;
    private String url;
    private String configPathPattern;
    private String sshUser;
    private String sshPassword;
    private String sshKeyPath;
    private String timezone;
    private boolean rootAllowed;

    private transient URL satelliteUrl;
    private transient URL rpcUrl;

    /**
     */
    public PluginConfiguration() {
        try {
            load();
            initialize();
        } catch (Exception x) {
            //
        }
    }

    private void initialize() {
        try {
            satelliteUrl = new URL(url);
            rpcUrl = new URL(url + "/rpc/api");
        } catch (Exception x) {
            throw new IllegalStateException(x);
        }
    }

    /**
     * doCheckUrl
     */
    public FormValidation doCheckUrl(@QueryParameter String value) throws IOException, ServletException {
        if (StringUtils.isEmpty(value)) {
            return FormValidation.error("Please enter a url");
        }
        try {
            new URL(value);
        } catch (Exception x) {
            return FormValidation.error("Invalid url");
        }
        return FormValidation.ok();
    }

    /**
     * doCheckUser
     */
    public FormValidation doCheckUser(@QueryParameter String value) throws IOException, ServletException {
        if (StringUtils.isEmpty(value)) {
            return FormValidation.error("Please enter a user");
        }
        return FormValidation.ok();
    }

    /**
     * doCheckPassword
     */
    public FormValidation doCheckPassword(@QueryParameter String value) throws IOException, ServletException {
        if (StringUtils.isEmpty(value)) {
            return FormValidation.error("Please enter a password");
        }
        return FormValidation.ok();
    }

    /**
     * doCheckConfigPathPattern
     */
    public FormValidation doCheckConfigPathPattern(@QueryParameter String value) throws IOException, ServletException {
        if (!StringUtils.isEmpty(value)) {
            try {
                Pattern.compile(value);
            } catch (Exception x) {
                return FormValidation.error("Invalid regular expression");
            }
        }
        return FormValidation.ok();
    }

    /**
     * doTestConnection
     */
    public FormValidation doTestConnection(@QueryParameter("url") String url, @QueryParameter("user") String user, @QueryParameter("password") String password) throws IOException, ServletException {
        try {
        	this.url = url;
        	this.user = user;
        	this.password = password;
        	initialize();
            SatelliteConnection connection = SatelliteConnection.from(this);
            connection.login();
            connection.logout();
            return FormValidation.ok("Success");
        } catch (Exception x) {
        	StringWriter sw = new StringWriter();
        	x.printStackTrace(new PrintWriter(sw));
            return FormValidation.error(sw.toString());
        }
    }

    /**
     * doTestSshUser
     */
    public FormValidation doCheckSshUser(@QueryParameter("sshUser") String sshUser, @QueryParameter("sshKeyPath") String sshKeyPath, @QueryParameter("sshPassword") String sshPassword)
            throws IOException, ServletException {
        boolean required = StringUtils.isNotEmpty(sshKeyPath) || StringUtils.isNotEmpty(sshPassword);
        if (!required) {
            return FormValidation.ok();
        }
        return StringUtils.isEmpty(sshUser) ? FormValidation.error("User required") : FormValidation.ok();
    }

    /**
     * doTestSshKeyPath
     */
    public FormValidation doCheckSshKeyPath(@QueryParameter String value) throws IOException, ServletException {
        if (StringUtils.isEmpty(value)) {
            return FormValidation.ok();
        }
        try {
            File file = new File(value);
            return file.exists() && file.canRead() && file.isFile() ? FormValidation.ok() : FormValidation.error("File not found or readable !");
        } catch (Exception x) {
            return FormValidation.error(x.getMessage());
        }
    }
    
    public ListBoxModel doFillTimezoneItems() {
        ListBoxModel listBoxModel = new ListBoxModel();
        List<String> tzStrings = new ArrayList<String>();
        for (String tz : TimeZone.getAvailableIDs()) {
            tzStrings.add(tz);
        }
        Collections.sort(tzStrings);
        for (String tz : tzStrings) {
            listBoxModel.add(tz);
        }
        return listBoxModel;
    }
    
    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        url               = formData.getString("url");
        user              = formData.getString("user");
        password          = formData.getString("password");
        configPathPattern = formData.getString("configPathPattern");
        sshUser           = formData.getString("sshUser");
        sshPassword       = formData.getString("sshPassword");
        sshKeyPath        = formData.getString("sshKeyPath");
        rootAllowed       = formData.getBoolean("rootAllowed");
        timezone          = formData.getString("timezone");
        
        initialize();
        save();
        return super.configure(req, formData);
    }

    @Override
    public String getDisplayName() {
        return "Satellite";
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getUrl() {
        return url;
    }

    public URL getSatelliteUrl() {
        return satelliteUrl;
    }

    public URL getRpcUrl() {
        return rpcUrl;
    }

    public String getConfigPathPattern() {
        return configPathPattern;
    }

    public String getSshUser() {
        return sshUser;
    }
    
    public String getTimezone() {
        return timezone;
    }
    
    public boolean isRootAllowed() {
		return rootAllowed;
	}

    public String getSshPassword() {
        return sshPassword;
    }

    public String getSshKeyPath() {
        return sshKeyPath;
    }

    public PluginConfiguration user(String user) {
        this.user = user;
        return this;
    }

    public PluginConfiguration password(String password) {
        this.password = password;
        return this;
    }

    public PluginConfiguration url(String url) {
        this.url = url;
        initialize();
        return this;
    }

    public boolean isSSL() {
        if (satelliteUrl != null) {
            initialize();
        }
        return satelliteUrl.getProtocol().equals("https");
    }

}
