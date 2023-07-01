package tools.dslr.hdcamera.repository.model.weather;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Hour {

    @SerializedName("time_epoch")
    @Expose
    private Long timeEpoch;
    @SerializedName("time")
    @Expose
    private String time;
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
    private Condition__2 condition;
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
    @SerializedName("windchill_c")
    @Expose
    private Float windchillC;
    @SerializedName("windchill_f")
    @Expose
    private Float windchillF;
    @SerializedName("heatindex_c")
    @Expose
    private Float heatindexC;
    @SerializedName("heatindex_f")
    @Expose
    private Float heatindexF;
    @SerializedName("dewpoint_c")
    @Expose
    private Float dewpointC;
    @SerializedName("dewpoint_f")
    @Expose
    private Float dewpointF;
    @SerializedName("will_it_rain")
    @Expose
    private Long willItRain;
    @SerializedName("chance_of_rain")
    @Expose
    private Long chanceOfRain;
    @SerializedName("will_it_snow")
    @Expose
    private Long willItSnow;
    @SerializedName("chance_of_snow")
    @Expose
    private Long chanceOfSnow;
    @SerializedName("vis_km")
    @Expose
    private Float visKm;
    @SerializedName("vis_miles")
    @Expose
    private Float visMiles;
    @SerializedName("gust_mph")
    @Expose
    private Float gustMph;
    @SerializedName("gust_kph")
    @Expose
    private Float gustKph;
    @SerializedName("uv")
    @Expose
    private Float uv;

    public Long getTimeEpoch() {
        return timeEpoch;
    }

    public void setTimeEpoch(Long timeEpoch) {
        this.timeEpoch = timeEpoch;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
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

    public Condition__2 getCondition() {
        return condition;
    }

    public void setCondition(Condition__2 condition) {
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

    public Float getWindchillC() {
        return windchillC;
    }

    public void setWindchillC(Float windchillC) {
        this.windchillC = windchillC;
    }

    public Float getWindchillF() {
        return windchillF;
    }

    public void setWindchillF(Float windchillF) {
        this.windchillF = windchillF;
    }

    public Float getHeatindexC() {
        return heatindexC;
    }

    public void setHeatindexC(Float heatindexC) {
        this.heatindexC = heatindexC;
    }

    public Float getHeatindexF() {
        return heatindexF;
    }

    public void setHeatindexF(Float heatindexF) {
        this.heatindexF = heatindexF;
    }

    public Float getDewpointC() {
        return dewpointC;
    }

    public void setDewpointC(Float dewpointC) {
        this.dewpointC = dewpointC;
    }

    public Float getDewpointF() {
        return dewpointF;
    }

    public void setDewpointF(Float dewpointF) {
        this.dewpointF = dewpointF;
    }

    public Long getWillItRain() {
        return willItRain;
    }

    public void setWillItRain(Long willItRain) {
        this.willItRain = willItRain;
    }

    public Long getChanceOfRain() {
        return chanceOfRain;
    }

    public void setChanceOfRain(Long chanceOfRain) {
        this.chanceOfRain = chanceOfRain;
    }

    public Long getWillItSnow() {
        return willItSnow;
    }

    public void setWillItSnow(Long willItSnow) {
        this.willItSnow = willItSnow;
    }

    public Long getChanceOfSnow() {
        return chanceOfSnow;
    }

    public void setChanceOfSnow(Long chanceOfSnow) {
        this.chanceOfSnow = chanceOfSnow;
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

    public Float getUv() {
        return uv;
    }

    public void setUv(Float uv) {
        this.uv = uv;
    }

}
