package de.ctrlaltdel.jenkins.plugins.satellite;

/**
 * Holder for NameVersionRelease
 * @author ds
 */
public class NVR {

	private final String name;
	private final String version;
	private final String release;

	/**
	 * NVR
	 */
	public NVR(String name, String version, String release) {
		this.name = name;
		this.version = version;
		this.release = release;
	}
	
	/**
	 * NVR
	 */
	public NVR(String rpmName) {
		String[] nvr = new String[2];
		char delimiter = '.';
		for (int token=1; 0<=token; token--) { 
			int idx = rpmName.lastIndexOf('-');
			StringBuilder tmp = new StringBuilder();
			for (int i=idx+1; i<rpmName.length(); i++) {
				char c = rpmName.charAt(i);
				if (c == delimiter) {
					break;
				}
				tmp.append(c);
			}
			nvr[token] = tmp.toString();
			tmp.setLength(0);
			rpmName = rpmName.substring(0, idx);
			delimiter = '-';
		}
		name = rpmName;
		version = nvr[0];
		release = nvr[1];
	}

	public String getName() {
		return name;
	}
	public String getRelease() {
		return release;
	}
	public String getVersion() {
		return version;
	}
	@Override
	public String toString() {
		return name + '-' + version + '-' + release;

	}

}
