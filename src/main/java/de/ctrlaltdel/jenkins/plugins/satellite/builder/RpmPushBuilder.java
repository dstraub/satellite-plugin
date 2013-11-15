package de.ctrlaltdel.jenkins.plugins.satellite.builder;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import de.ctrlaltdel.jenkins.plugins.satellite.NVR;
import de.ctrlaltdel.jenkins.plugins.satellite.SatelliteConnection;

/**
 * RpmPublisher
 * @author ds
 */
public class RpmPushBuilder extends Builder {

    private static final String DEFAULT_ARTEFACTS = "**/RPMS/noarch/**/*.rpm";

    private final String artifacts;
    private final String channel;

    @DataBoundConstructor
    public RpmPushBuilder(String artifacts, String channel) {
        this.artifacts = artifacts == null ? DEFAULT_ARTEFACTS : artifacts;
        this.channel = channel;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        logBuild(listener);

        FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            return true;
        }

        SatelliteConnection connection = null;
        try {
            String artifacts = build.getEnvironment(listener).expand(this.artifacts);
            Map<String, String> files = workspace.act(new ListFiles(artifacts));
            if (files.isEmpty()) {
                Result result = build.getResult();
                if (result != null && result.isBetterOrEqualTo(Result.UNSTABLE)) {
                    try {
                        String msg = workspace.validateAntFileMask(artifacts);
                        listener.getLogger().println(String.format("WARN: %s", msg));
                    } catch (Exception e) {
                        listener.getLogger().println(String.format("ERROR: %s", e));
                    }
                }
                return true;
            }

            connection = SatelliteConnection.create().logger(listener).login();

            StringBuilder sb = new StringBuilder();
            for (String fileName : files.keySet()) {
                FilePath filePath = new FilePath(workspace, fileName);
                NVR nvr = connection.push(filePath, channel);
                if (nvr == null) {
                    build.setResult(Result.FAILURE);
                    continue;
                }
                sb.append(nvr.getName()).append(',');
            }
            build.getBuildVariables().put("RPM_NAME", sb.substring(0, sb.length() - 1));

        } catch (IOException e) {
            Util.displayIOException(e, listener);
            build.setResult(Result.FAILURE);
            return true;
        } finally {
            if (connection != null) {
                connection.logout();
            }
        }
        return true;
    }

    /**
     * ListFiles
     */
    private static final class ListFiles implements FilePath.FileCallable<Map<String, String>> {
        private final String includes;

        ListFiles(String includes) {
            this.includes = includes;
        }

        public Map<String, String> invoke(File basedir, VirtualChannel channel) throws IOException, InterruptedException {
            Map<String, String> result = new HashMap<String, String>();
            for (String fileName : Util.createFileSet(basedir, includes).getDirectoryScanner().getIncludedFiles()) {
                fileName = fileName.replace(File.separatorChar, '/');
                result.put(fileName, fileName);
            }
            return result;
        }
    }

    /**
     * logCmd
     */
    private void logBuild(BuildListener listener) {
        PrintStream ps = listener.getLogger();
        ps.println("[INFO] ------------------------------------------------------------------------");
        ps.println("[INFO] Push to '" + channel + '\'');
        ps.println("[INFO] ------------------------------------------------------------------------");
    }

    public String getArtifacts() {
        return artifacts;
    }

    public String getChannel() {
        return channel;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        /**
         * getDisplayName
         */
        public String getDisplayName() {
            return "Satellite RPM Push";
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
        public RpmPushBuilder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(RpmPushBuilder.class, formData);
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

    }

}
