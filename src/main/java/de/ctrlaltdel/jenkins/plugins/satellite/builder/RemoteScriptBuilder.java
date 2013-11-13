package de.ctrlaltdel.jenkins.plugins.satellite.builder;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import de.ctrlaltdel.jenkins.plugins.satellite.SatelliteConnection;
import de.ctrlaltdel.jenkins.plugins.satellite.PluginConfiguration;

/**
 * RemoteScriptBuilder
 * @author ds
 */
public class RemoteScriptBuilder extends Builder {

    private final String group;
    private final String script;
    private final boolean useSSH;

    private transient JSch jsch;

    @DataBoundConstructor
    public RemoteScriptBuilder(String group, String script, boolean useSSH) {
        super();
        this.group = group;
        this.script = script;
        this.useSSH = useSSH;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        logBuild(listener);
        String runtimeScript = setScriptVariables(listener.getLogger(), build.getBuildVariables());
        if (useSSH) {
            for (String host : SatelliteConnection.create().forOneCall().listHosts(group)) {
                int result = executeSSH(host, listener.getLogger(), runtimeScript);
                if (result != 0) {
                    build.setResult(Result.FAILURE);
                }
            }
        } else {
            SatelliteConnection.create().forOneCall().logger(listener).remoteScript(group, runtimeScript);
        }
        return true;
    }

    /**
     * setScriptVariables
     */
    private String setScriptVariables(PrintStream ps, Map<String, String> vars) {
        StringBuilder sb = new StringBuilder();
        for (String variable : vars.keySet()) {
            if (variable.startsWith("_")) {
                continue;
            }
            if (script.contains("$" + variable)) {
                ps.println("[INFO] insert '" + variable + '=' + vars.get(variable) + '\''); 
                sb.append(variable + "=\"" + vars.get(variable) + "\"\n");
            }
        }
        sb.append("\n");
        sb.append(script);
        return sb.toString();
    }

    public String getGroup() {
        return group;
    }

    public String getScript() {
        return script;
    }

    public boolean isUseSSH() {
        return useSSH;
    }

    /**
     * logCmd
     */
    private void logBuild(BuildListener listener) {
        PrintStream ps = listener.getLogger();
        ps.println("[INFO] ------------------------------------------------------------------------");
        ps.println("[INFO] Run remote script on '" + group + '\'');
        ps.println("[INFO] ------------------------------------------------------------------------");
    }

    /**
     * executeSSH stolen from SSHBuild-Plugin
     */
    private int executeSSH(String hostname, PrintStream logger, String command) {

        logger.println("[SSH] connect " + hostname);
        PluginConfiguration configuration = (PluginConfiguration) Jenkins.getInstance().getDescriptorOrDie(PluginConfiguration.class);
        if (jsch == null) {
            jsch = new JSch();
        }

        int port = 22;

        ChannelExec channel = null;
        Session session = null;
        int status = -1;
        try {

            session = jsch.getSession(configuration.getSshUser(), hostname, port);
            if (StringUtils.isNotEmpty(configuration.getSshKeyPath())) {
                jsch.addIdentity(configuration.getSshKeyPath(), configuration.getSshPassword());
            } else {
                session.setPassword(configuration.getSshPassword());
            }

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();

            channel = (ChannelExec) session.openChannel("exec");
            channel.setOutputStream(logger, true);
            channel.setExtOutputStream(logger, true);
            channel.setInputStream(null);
            // channel.setPty(pty == null ? Boolean.FALSE : pty );

            logger.println("[SSH] execute script");
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            channel.connect();

            byte[] tmp = new byte[1024];
            int read = 0;
            while (true) {
                while ((read = in.read(tmp)) > 0) {
                    logger.print(new String(tmp, 0, read));
                }
                if (channel.isClosed()) {
                    status = channel.getExitStatus();
                    logger.println("[SSH] exit-status: " + status);
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception ee) {
                    //
                }
            }

        } catch (Exception e) {
            logger.println("[SSH] Exception:" + e.getMessage());
            e.printStackTrace(logger);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        return status;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public String getDisplayName() {
            return "Satellite Remote Script";
        }

        @Override
        public RemoteScriptBuilder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(RemoteScriptBuilder.class, formData);
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        /**
         * doFillGroupItems
         */
        public ListBoxModel doFillGroupItems() {
            List<String> groups = SatelliteConnection.create().forOneCall().listGroups();
            ListBoxModel listBoxModel = new ListBoxModel();
            listBoxModel.add("");
            for (String group : groups) {
                listBoxModel.add(group);
            }
            return listBoxModel;
        }

    }

}
