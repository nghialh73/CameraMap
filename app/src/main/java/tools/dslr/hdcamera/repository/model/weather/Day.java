package tools.dslr.hdcamera.repository.model.weather;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Day {

    @SerializedName("maxtemp_c")
    @Expose
    private Float maxtempC;
    @SerializedName("maxtemp_f")
    @Expose
    private Float maxtempF;
    @SerializedName("mintemp_c")
    @Expose
    private Float mintempC;
    @SerializedName("mintemp_f")
    @Expose
    private Float mintempF;
    @SerializedName("avgtemp_c")
    @Expose
    private Float avgtempC;
    @SerializedName("avgtemp_f")
    @Expose
    private Float avgtempF;
    @SerializedName("maxwind_mph")
    @Expose
    private Float maxwindMph;
    @SerializedName("maxwind_kph")
    @Expose
    private Float maxwindKph;
    @SerializedName("totalprecip_mm")
    @Expose
    private Float totalprecipMm;
    @SerializedName("totalprecip_in")
    @Expose
    private Float totalprecipIn;
    @SerializedName("totalsnow_cm")
    @Expose
    private Float totalsnowCm;
    @SerializedName("avgvis_km")
    @Expose
    private Float avgvisKm;
    @SerializedName("avgvis_miles")
    @Expose
    private Float avgvisMiles;
    @SerializedName("avghumidity")
    @Expose
    private Float avghumidity;
    @SerializedName("daily_will_it_rain")
    @Expose
    private Long dailyWillItRain;
    @SerializedName("daily_chance_of_rain")
    @Expose
    private Long dailyChanceOfRain;
    @SerializedName("daily_will_it_snow")
    @Expose
    private Long dailyWillItSnow;
    @SerializedName("daily_chance_of_snow")
    @Expose
    private Long dailyChanceOfSnow;
    @SerializedName("condition")
    @Expose
    private Condition__1 condition;
    @SerializedName("uv")
    @Expose
    private Float uv;

    public Float getMaxtempC() {
        return maxtempC;
    }

    public void setMaxtempC(Float maxtempC) {
        this.maxtempC = maxtempC;
    }

    public Float getMaxtempF() {
        return maxtempF;
    }

    public void setMaxtempF(Float maxtempF) {
        this.maxtempF = maxtempF;
    }

    public Float getMintempC() {
        return mintempC;
    }

    public void setMintempC(Float mintempC) {
        this.mintempC = mintempC;
    }

    public Float getMintempF() {
        return mintempF;
    }

    public void setMintempF(Float mintempF) {
        this.mintempF = mintempF;
    }

    public Float getAvgtempC() {
        return avgtempC;
    }

    public void setAvgtempC(Float avgtempC) {
        this.avgtempC = avgtempC;
    }

    public Float getAvgtempF() {
        return avgtempF;
    }

    public void setAvgtempF(Float avgtempF) {
        this.avgtempF = avgtempF;
    }

    public Float getMaxwindMph() {
        return maxwindMph;
    }

    public void setMaxwindMph(Float maxwindMph) {
        this.maxwindMph = maxwindMph;
    }

    public Float getMaxwindKph() {
        return maxwindKph;
    }

    public void setMaxwindKph(Float maxwindKph) {
        this.maxwindKph = maxwindKph;
    }

    public Float getTotalprecipMm() {
        return totalprecipMm;
    }

    public void setTotalprecipMm(Float totalprecipMm) {
        this.totalprecipMm = totalprecipMm;
    }

    public Float getTotalprecipIn() {
        return totalprecipIn;
    }

    public void setTotalprecipIn(Float totalprecipIn) {
        this.totalprecipIn = totalprecipIn;
    }

    public Float getTotalsnowCm() {
        return totalsnowCm;
    }

    public void setTotalsnowCm(Float totalsnowCm) {
        this.totalsnowCm = totalsnowCm;
    }

    public Float getAvgvisKm() {
        return avgvisKm;
    }

    public void setAvgvisKm(Float avgvisKm) {
        this.avgvisKm = avgvisKm;
    }

    public Float getAvgvisMiles() {
        return avgvisMiles;
    }

    public void setAvgvisMiles(Float avgvisMiles) {
        this.avgvisMiles = avgvisMiles;
    }

    public Float getAvghumidity() {
        return avghumidity;
    }

    public void setAvghumidity(Float avghumidity) {
        this.avghumidity = avghumidity;
    }

    public Long getDailyWillItRain() {
        return dailyWillItRain;
    }

    public void setDailyWillItRain(Long dailyWillItRain) {
        this.dailyWillItRain = dailyWillItRain;
    }

    public Long getDailyChanceOfRain() {
        return dailyChanceOfRain;
    }

    public void setDailyChanceOfRain(Long dailyChanceOfRain) {
        this.dailyChanceOfRain = dailyChanceOfRain;
    }

    public Long getDailyWillItSnow() {
        return dailyWillItSnow;
    }

    public void setDailyWillItSnow(Long dailyWillItSnow) {
        this.dailyWillItSnow = dailyWillItSnow;
    }

    public Long getDailyChanceOfSnow() {
        return dailyChanceOfSnow;
    }

    public void setDailyChanceOfSnow(Long dailyChanceOfSnow) {
        this.dailyChanceOfSnow = dailyChanceOfSnow;
    }

    public Condition__1 getCondition() {
        return condition;
    }

    public void setCondition(Condition__1 condition) {
        this.condition = condition;
    }

    public Float getUv() {
        return uv;
    }

    public void setUv(Float uv) {
        this.uv = uv;
    }

}
