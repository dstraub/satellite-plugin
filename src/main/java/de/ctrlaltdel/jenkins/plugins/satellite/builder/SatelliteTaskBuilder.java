package de.ctrlaltdel.jenkins.plugins.satellite.builder;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.XmlRootElement;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import de.ctrlaltdel.jenkins.plugins.satellite.SatelliteConnection;

/**
 * SatelliteTaskBuilder
 * @author ds
 */
public class SatelliteTaskBuilder extends Builder {
	
	public enum SatelliteTask {
		ADD_PACKAGE, 
		UPDATE_CONFIG
		;
		
		static List<SatelliteTask> from(Map<String, String> buildVariables) {
			List<SatelliteTask> result = new ArrayList<SatelliteTaskBuilder.SatelliteTask>();
			for (SatelliteTask sc: values()) {
				if (buildVariables.containsKey(sc.name())) {
					result.add(sc);
				}
			}
			return result;
		}
	}
	
    @DataBoundConstructor
    public SatelliteTaskBuilder() {
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
    	Map<String, String> buildVariables = build.getBuildVariables();
    	for (SatelliteTask task: SatelliteTask.from(buildVariables)) {
    		String parameter = buildVariables.get(task.name());
    		if (parameter == null) {
    			continue;
    		}
    		logTask(task, listener);
    		boolean result = false;
    		switch (task) {
			case ADD_PACKAGE:
				AddPackageTaskParameter addPackageParameter = XMLStuff.from(parameter);
				result = addPackge(listener, addPackageParameter);
				break;
			case UPDATE_CONFIG:
				UpdateConfigTaskParameter updateConfigParameter = XMLStuff.from(parameter);
				result = updateConfig(listener, updateConfigParameter);
				break;
			default:
				break;
			}
    		if (!result) {
    			listener.getLogger().println("[INFO] Task was not successful");
    			return false;
    		}
    		listener.getLogger().println("[INFO] Task was successful");
    	}
    	return true;
    }

    
    /**
     * channelAddPackges
     */
    private boolean addPackge(BuildListener listener, AddPackageTaskParameter parameter) {
    	listener.getLogger().println("[INFO] add to '" + parameter.channel + "' : " + parameter.packageName);
    	return SatelliteConnection.create().forOneCall().logger(listener)
    	                          .addPackage(parameter.channel, parameter.packageId);
    }
    
    /**
     * updateConfig
     */
    private boolean updateConfig(BuildListener listener, UpdateConfigTaskParameter parameter) {
    	listener.getLogger().println("[INFO] update " + parameter.configPath + " [" + parameter.configChannel + ']');
    	return SatelliteConnection.create().forOneCall().logger(listener)
    	                          .updateConfig(parameter.configChannel, parameter.configPath, parameter.contents);
    }
    
    /**
     * logCmd
     */
    private void logTask(SatelliteTask task, BuildListener listener) {
    	PrintStream ps = listener.getLogger();
    	ps.println("\n[INFO] ------------------------------------------------------------------------");
    	ps.println("[INFO] SatelliteTask " + task.toString());
    	ps.println("[INFO] ------------------------------------------------------------------------");
    }
    
    @XmlRootElement
    public static class AddPackageTaskParameter {
    	public String channel;
    	public String packageName;
    	public Integer packageId;
    	
    	public AddPackageTaskParameter() {
    	}
    	public AddPackageTaskParameter(String channel, String packageName, Integer packageId) {
			this.channel     = channel;
			this.packageName = packageName;
			this.packageId   = packageId;
		}
    	@Override
    	public String toString() {
    		return XMLStuff.toString(this);
    	}
    }

    @XmlRootElement
    public static class UpdateConfigTaskParameter {
    	public String configChannel;
    	public String configPath;
    	public String contents;
    	
    	public UpdateConfigTaskParameter() {
    	}
    	public UpdateConfigTaskParameter(String configChannel, String configPath, String contents) {
			this.configChannel = configChannel;
			this.configPath    = configPath;
			this.contents      = contents;
		}
    	@Override
    	public String toString() {
    		return XMLStuff.toString(this);
    	}
    }

    /**
     * XMLStuff
     */
    static class XMLStuff {
    	static String toString(Object obj) {
    		try {
				JAXBContext jaxbContext = JAXBContext.newInstance(obj.getClass());
				StringWriter writer = new StringWriter();
				Marshaller marshaller = jaxbContext.createMarshaller();
				marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
				marshaller.marshal(obj, writer);
				return writer.toString();
			} catch (Exception x) {
				throw new IllegalStateException(x);
			}
    	}
    	
    	static <T> T from(String xml) {
    		try {
    			JAXBContext jaxbContext = JAXBContext.newInstance(UpdateConfigTaskParameter.class, AddPackageTaskParameter.class);
    			return (T) jaxbContext.createUnmarshaller().unmarshal(new StringReader(xml));
			} catch (Exception x) {
				throw new IllegalStateException(x);
			}
    	}
    }
    
    /**
     * Descriptor
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    	public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public SatelliteTaskBuilder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(SatelliteTaskBuilder.class, formData);
        }

        public String getDisplayName() {
            return "Satellite Task";
        }
    }
 
}

