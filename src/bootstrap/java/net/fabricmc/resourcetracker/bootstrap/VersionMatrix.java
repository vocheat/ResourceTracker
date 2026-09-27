package net.fabricmc.resourcetracker.bootstrap;

public final class VersionMatrix {
    private VersionMatrix() { }

    public static String platformId(String version) {
        return switch (version) {
            case "1.21", "1.21.0" -> "mc1210";
            case "1.21.1" -> "mc1211";
            case "1.21.2" -> "mc1212";
            case "1.21.3" -> "mc1213";
            case "1.21.4" -> "mc1214";
            case "1.21.5" -> "mc1215";
            case "1.21.6" -> "mc1216";
            case "1.21.7" -> "mc1217";
            case "1.21.8" -> "mc1218";
            case "1.21.9" -> "mc1219";
            case "1.21.10" -> "mc12110";
            case "1.21.11" -> "mc12111";
            case "26.1" -> "mc261";
            case "26.2" -> "mc262";
            default -> throw new UnsupportedOperationException("Unsupported Minecraft version: " + version);
        };
    }

    public static String platformClass(String version) {
        return "net.fabricmc.resourcetracker.platform." + platformId(version) + ".ResourceTrackerPlatform";
    }

    public static String modMenuClass(String version) {
        return "net.fabricmc.resourcetracker.platform." + platformId(version) + ".client.ModMenuIntegration";
    }
}
