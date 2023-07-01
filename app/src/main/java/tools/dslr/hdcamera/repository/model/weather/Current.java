package tools.dslr.hdcamera.repository.model.weather;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Current {

    @SerializedName("last_updated_epoch")
    @Expose
    private Long lastUpdatedEpoch;
    @SerializedName("last_updated")
    @Expose
    private String lastUpdated;
    @SerializedName("temp_c")
    @Expose
    private Float tempC;
    @SerializedName("temp_f")
    @Expose
    private Float tempF;
    @SerializedName("is_day")
    @Expose
    private Long isDay;
    @SerializedName("condition")
    @Expose
    private Condition condition;
    @SerializedName("wind_mph")
    @Expose
    private Float windMph;
    @SerializedName("wind_kph")
    @Expose
    private Float windKph;
    @SerializedName("wind_degree")
    @Expose
    private Long windDegree;
    @SerializedName("wind_dir")
    @Expose
    private String windDir;
    @SerializedName("pressure_mb")
    @Expose
    private Float pressureMb;
    @SerializedName("pressure_in")
    @Expose
    private Float pressureIn;
    @SerializedName("precip_mm")
    @Expose
    private Float precipMm;
    @SerializedName("precip_in")
    @Expose
    private Float precipIn;
    @SerializedName("humidity")
    @Expose
    private Long humidity;
    @SerializedName("cloud")
    @Expose
    private Long cloud;
    @SerializedName("feelslike_c")
    @Expose
    private Float feelslikeC;
    @SerializedName("feelslike_f")
    @Expose
    private Float feelslikeF;
    @SerializedName("vis_km")
    @Expose
    private Float visKm;
    @SerializedName("vis_miles")
    @Expose
    private Float visMiles;
    @SerializedName("uv")
    @Expose
    private Float uv;
    @SerializedName("gust_mph")
    @Expose
    private Float gustMph;
    @SerializedName("gust_kph")
    @Expose
    private Float gustKph;

    public Long getLastUpdatedEpoch() {
        return lastUpdatedEpoch;
    }

    public void setLastUpdatedEpoch(Long lastUpdatedEpoch) {
        this.lastUpdatedEpoch = lastUpdatedEpoch;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Float getTempC() {
        return tempC;
    }

    public void setTempC(Float tempC) {
        this.tempC = tempC;
    }

    public Float getTempF() {
        return tempF;
    }

    public void setTempF(Float tempF) {
        this.tempF = tempF;
    }

    public Long getIsDay() {
        return isDay;
    }

    public void setIsDay(Long isDay) {
        this.isDay = isDay;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(Condition condition) {
        this.condition = condition;
    }

    public Float getWindMph() {
        return windMph;
    }

    public void setWindMph(Float windMph) {
        this.windMph = windMph;
    }

    public Float getWindKph() {
        return windKph;
    }

    public void setWindKph(Float windKph) {
        this.windKph = windKph;
    }

    public Long getWindDegree() {
        return windDegree;
    }

    public void setWindDegree(Long windDegree) {
        this.windDegree = windDegree;
    }

    public String getWindDir() {
        return windDir;
    }

    public void setWindDir(String windDir) {
        this.windDir = windDir;
    }

    public Float getPressureMb() {
        return pressureMb;
    }

    public void setPressureMb(Float pressureMb) {
        this.pressureMb = pressureMb;
    }

    public Float getPressureIn() {
        return pressureIn;
    }

    public void setPressureIn(Float pressureIn) {
        this.pressureIn = pressureIn;
    }

    public Float getPrecipMm() {
        return precipMm;
    }

    public void setPrecipMm(Float precipMm) {
        this.precipMm = precipMm;
    }

    public Float getPrecipIn() {
        return precipIn;
    }

    public void setPrecipIn(Float precipIn) {
        this.precipIn = precipIn;
    }

    public Long getHumidity() {
        return humidity;
    }

    public void setHumidity(Long humidity) {
        this.humidity = humidity;
    }

    public Long getCloud() {
        return cloud;
    }

    public void setCloud(Long cloud) {
        this.cloud = cloud;
    }

    public Float getFeelslikeC() {
        return feelslikeC;
    }

    public void setFeelslikeC(Float feelslikeC) {
        this.feelslikeC = feelslikeC;
    }

    public Float getFeelslikeF() {
        return feelslikeF;
    }

    public void setFeelslikeF(Float feelslikeF) {
        this.feelslikeF = feelslikeF;
    }

    public Float getVisKm() {
        return visKm;
    }

    public void setVisKm(Float visKm) {
        this.visKm = visKm;
    }

    public Float getVisMiles() {
        return visMiles;
    }

    public void setVisMiles(Float visMiles) {
        this.visMiles = visMiles;
    }

    public Float getUv() {
        return uv;
    }

    public void setUv(Float uv) {
        this.uv = uv;
    }

    public Float getGustMph() {
        return gustMph;
    }

    public void setGustMph(Float gustMph) {
        this.gustMph = gustMph;
    }

    public Float getGustKph() {
        return gustKph;
    }

    public void setGustKph(Float gustKph) {
        this.gustKph = gustKph;
    }

}
