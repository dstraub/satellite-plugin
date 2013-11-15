package de.ctrlaltdel.jenkins.plugins.satellite;

import hudson.Extension;
import hudson.model.RootAction;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

/**
 * FunctionLinks
 * @author ds
 */
@Extension
public class FunctionLinks implements RootAction {

    @Exported
    public void doLoadConfigFiles(StaplerRequest req, StaplerResponse rsp, @QueryParameter("value") String value) throws IOException, ServletException {
        List<String> channels = SatelliteConnection.create().forOneCall().listConfigPaths(value);
        ListBoxModel listBoxModel = new ListBoxModel();
        for (String channel : channels) {
            listBoxModel.add(channel);
        }
        listBoxModel.writeTo(req, rsp);
    }

    public String getIconFileName() {
        return null;
    }

    public String getUrlName() {
        return "/satellite";
    }

    public String getDisplayName() {
        return "satellite-functions";
    }

}
