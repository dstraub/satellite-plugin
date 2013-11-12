package de.ctrlaltdel.jenkins.plugins.satellite.parameter;

import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import de.ctrlaltdel.jenkins.plugins.satellite.SatelliteConnection;
import de.ctrlaltdel.jenkins.plugins.satellite.builder.SatelliteTaskBuilder.AddPackageTaskParameter;
import de.ctrlaltdel.jenkins.plugins.satellite.builder.SatelliteTaskBuilder.SatelliteTask;

/**
 * StagingParameter
 * @author ds
 */
public class StagingParameter extends SimpleParameterDefinition {
	
	private Map<String, Integer> packages; 
	private final String sourceChannel;
	private final String targetChannel;
	private final String packagePattern;
	private boolean includeSnapShots;
	
	
	@DataBoundConstructor
	public StagingParameter(String sourceChannel, String targetChannel, String packagePattern) {
		super(SatelliteTask.ADD_PACKAGE.name(),  "");
		this.sourceChannel  = sourceChannel;
		this.targetChannel  = targetChannel;
		this.packagePattern = packagePattern;
	}

	@Override
	public ParameterValue createValue(String value) {
		return new StringParameterValue(getName(), targetChannel + ':' + value);
	}

	@Override
	public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
		StringParameterValue value = req.bindJSON(StringParameterValue.class, jo);
		for (Map.Entry<String, Integer> me : packages.entrySet()) {
			if (me.getKey().equals(value.value)) {
				return new StringParameterValue(value.getName(), 
						                        new AddPackageTaskParameter(targetChannel, value.value, me.getValue()).toString());
			}
		}
		return null;
	}
	

	@Exported
	public List<String> getPackages() {
		packages = SatelliteConnection.create().forOneCall().listPackages(sourceChannel);
		List<String> result = new ArrayList<String>(packages.size());
		Pattern pattern = StringUtils.isEmpty(packagePattern) ? null : Pattern.compile(packagePattern);
		for (String key: packages.keySet()) {
			if (pattern != null && !pattern.matcher(key).matches()) {
				continue;
			}
			if (!includeSnapShots && key.contains("SNAPSHOT")) {
				continue;
			}
			
			result.add(key);
		}
		return result;
	}	

    public String getPackagesText() {
		packages = SatelliteConnection.create().forOneCall().listPackages(sourceChannel);
		return StringUtils.join(packages.entrySet(), "\n");
    }
    
    public String getSourceChannel() {
		return sourceChannel;
	}
    public String getTargetChannel() {
		return targetChannel;
	}
    public boolean isIncludeSnapShots() {
		return includeSnapShots;
	}
    public String getPackagePattern() {
		return packagePattern;
	}
    
    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
    	 @Override
         public String getDisplayName() {
             return "Satellite Staging";
         }
    	 
     	private List<String> channels;
     	private ListBoxModel listBoxModel;
     	
     	private ListBoxModel getListBoxModel() {
     		if (listBoxModel == null) {
         		channels = SatelliteConnection.create().forOneCall().listChannels();
         		listBoxModel = new ListBoxModel();
                 for (String channel: channels) {
                 	listBoxModel.add(channel);
                 }
     		}
     		return listBoxModel;
     	}
     	
     	public ListBoxModel doFillSourceChannelItems() {
             return getListBoxModel();
         }
     	
     	public ListBoxModel doFillTargetChannelItems() {
             return getListBoxModel();
         }
     	
        public FormValidation doCheckPackagePattern(@QueryParameter String value) throws IOException, ServletException {
            if (!StringUtils.isEmpty(value)) {
            	try {
            		Pattern.compile(value);
            	} catch (Exception x) {
            		return FormValidation.error("Invalid regular expression");
            	}
            }
            return FormValidation.ok();
        }     	

    }

}
