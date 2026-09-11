package local.kdt.gata.appconfig;


public enum AppConfigType {
    APP("App Copnfig"),
    ;

    private final String description;

    AppConfigType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
