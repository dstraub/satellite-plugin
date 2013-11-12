package de.ctrlaltdel.jenkins.plugins.satellite.parameter;

import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.util.ListBoxModel;

import java.util.List;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import de.ctrlaltdel.jenkins.plugins.satellite.SatelliteConnection;
import de.ctrlaltdel.jenkins.plugins.satellite.builder.SatelliteTaskBuilder.SatelliteTask;
import de.ctrlaltdel.jenkins.plugins.satellite.builder.SatelliteTaskBuilder.UpdateConfigTaskParameter;

/**
 * UpdateConfigParameter
 * @author ds
 */
public class UpdateConfigParameter extends SimpleParameterDefinition {

    private final String configChannel;
    private final String configPath;

    @DataBoundConstructor
    public UpdateConfigParameter(String configChannel, String configPath) {
        super(SatelliteTask.UPDATE_CONFIG.name(), "");
        this.configChannel = configChannel;
        this.configPath = configPath;
    }

    @Override
    public ParameterValue createValue(String value) {
        return new StringParameterValue(getName(), value);
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        StringParameterValue value = req.bindJSON(StringParameterValue.class, jo);
        return new StringParameterValue(value.getName(), new UpdateConfigTaskParameter(configChannel, configPath, value.value).toString());
    }

    public String getConfigChannel() {
        return configChannel;
    }

    public String getConfigPath() {
        return configPath;
    }

    public String getValue() {
        return SatelliteConnection.create().forOneCall().readConfig(configChannel, configPath);
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        private String firstChannel;

        @Override
        public String getDisplayName() {
            return "Satellite Update Configuration";
        }

        public ListBoxModel doFillConfigChannelItems() {
            List<String> channels = SatelliteConnection.create().forOneCall().listConfigChannels();
            firstChannel = channels.get(0);
            ListBoxModel listBoxModel = new ListBoxModel();
            for (String channel : channels) {
                listBoxModel.add(channel);
            }
            return listBoxModel;
        }

        public ListBoxModel doFillConfigPathItems() {
            if (firstChannel == null) {
                return null;
            }
            List<String> channels = SatelliteConnection.create().forOneCall().listConfigPaths(firstChannel);
            ListBoxModel listBoxModel = new ListBoxModel();
            for (String channel : channels) {
                listBoxModel.add(channel);
            }
            return listBoxModel;
        }

    }

}
