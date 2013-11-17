import hudson.FilePath;

import java.io.File;
import java.util.List;

import org.junit.Ignore;

import de.ctrlaltdel.jenkins.plugins.satellite.PluginConfiguration;
import de.ctrlaltdel.jenkins.plugins.satellite.SatelliteConnection;

@Ignore
public class AddTestPackages {

    public static void main(String[] args) {
        try {
            PluginConfiguration configuration = new PluginConfiguration().url("https://satellite.local").user("jenkins").password("jenkins");
            SatelliteConnection connection = SatelliteConnection.from(configuration).login();

            List<String> channels = connection.listChannels();
            for (String ch : channels) {
                System.out.println(ch);
            }

            String channel = "jboss-dev";
            for (File file : new File("src/test/resources").listFiles()) {
                if (!file.getName().endsWith(".rpm")) {
                    continue;
                }
                FilePath filePath = new FilePath(file);
                connection.push(filePath, channel);
            }
            connection.logout();

        } catch (Exception x) {
            x.printStackTrace();
        }
    }

}
