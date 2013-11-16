package de.ctrlaltdel.jenkins.plugins.satellite.builder;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import de.ctrlaltdel.jenkins.plugins.satellite.SatelliteConnection;
import de.ctrlaltdel.jenkins.plugins.satellite.PluginConfiguration;

/**
 * CleanPackagesBuilder
 * @author ds
 */
public class CleanPackagesBuilder extends Builder {

    private final String packagePattern;
    private final String channel;
    private final int maxAge;

    @DataBoundConstructor
    public CleanPackagesBuilder(String packagePattern, String channel, int maxAge) {
        this.packagePattern = packagePattern;
        this.channel = channel;
        this.maxAge = maxAge;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        logBuild(listener);
        SatelliteConnection connection = null;
        Pattern pattern = packagePattern != null ? Pattern.compile(packagePattern) : null;
        
        PluginConfiguration configuration = (PluginConfiguration) Jenkins.getInstance().getDescriptorOrDie(PluginConfiguration.class);
        TimeZone timeZone = TimeZone.getTimeZone(configuration.getTimezone()); 
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        dateFormat.setTimeZone(timeZone);
        
        Date today = Calendar.getInstance(timeZone).getTime();
        
        connection = SatelliteConnection.create().logger(listener).login();
        List<Map<String, Object>> packages = connection.listPackages(channel);
        List<Integer> pkgIds = new ArrayList<Integer>();
        
        StringBuilder sb = new StringBuilder("[INFO] packages to remove:\n");
        
        for (Map<String, Object> pkgData : packages) {
            String packageName = (String) pkgData.get(SatelliteConnection.ATTR_PKG_NAME);
            if (pattern != null && !pattern.matcher(packageName).matches()) {
                continue;
            }
            try {
                String ds = (String) pkgData.get("last_modified_date");
                Date date = dateFormat.parse(ds);
                int diffInDays = (int)(today.getTime() - date.getTime()) / 86400000 ; // 1000 * 60 * 60 * 24
                if (diffInDays < maxAge) {
                    continue;
                }
            } catch (Exception x) {
                listener.getLogger().println("[ERROR] " + x.getMessage() + " package " + packageName);
            }
            pkgIds.add((Integer) pkgData.get("id"));
            sb.append("       ").append(packageName).append(" [").append(pkgData.get("id")).append("]\n");
        }
        
        boolean result = false;
        try {
            if (pkgIds.isEmpty()) {
                listener.getLogger().println("[INFO] found no packages to remove");
                return true;
            }
            listener.getLogger().print(sb.toString());
            result = connection.removePackages(channel, pkgIds);
            if (!result) {
                listener.getLogger().println("[ERROR] remove packages failed");
            } else {
                listener.getLogger().println("[INFO] remove packages successful");
            }
            
        } finally {
            connection.logout();
        }
        return result;
    }


    /**
     * logCmd
     */
    private void logBuild(BuildListener listener) {
        PrintStream ps = listener.getLogger();
        ps.println("[INFO] ------------------------------------------------------------------------");
        ps.println("[INFO] Cleanup channel '" + channel + '\'');
        ps.println("[INFO] ------------------------------------------------------------------------");
    }

    public String getPackagePattern() {
        return packagePattern;
    }
    public String getChannel() {
        return channel;
    }
    public int getMaxAge() {
        return maxAge;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        /**
         * getDisplayName
         */
        public String getDisplayName() {
            return "Satellite Clean Channel";
        }

        /**
         * doCheckPathPattern
         */
        public FormValidation doCheckPathPattern(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("Please enter a artifact pattern");
            }
            if (value.length() != 0) {
                return FilePath.validateFileMask(project.getSomeWorkspace(), value);
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillChannelItems() {
            List<String> channels = SatelliteConnection.create().forOneCall().listChannels();
            ListBoxModel listBoxModel = new ListBoxModel();
            for (String channel : channels) {
                listBoxModel.add(channel);
            }
            return listBoxModel;
        }

        @Override
        public CleanPackagesBuilder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(CleanPackagesBuilder.class, formData);
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
        
        public FormValidation doCheckMaxAge(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("Please enter a max. age > 0");
            }
            try {
                int maxAge = Integer.valueOf(value);
//                if (maxAge < 1) {
//                    return FormValidation.error("Please enter a valid max. age > 0");
//                }
            } catch (Exception x) {
                return FormValidation.error("Please enter a valid max. age > 0");
            }
            return FormValidation.ok();
        }


    }

}
