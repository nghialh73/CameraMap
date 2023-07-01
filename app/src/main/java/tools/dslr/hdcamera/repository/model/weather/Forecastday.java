package tools.dslr.hdcamera.repository.model.weather;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Forecastday {

    @SerializedName("date")
    @Expose
    private String date;
    @SerializedName("date_epoch")
    @Expose
    private Long dateEpoch;
    @SerializedName("day")
    @Expose
    private Day day;
    @SerializedName("astro")
    @Expose
    private Astro astro;
    @SerializedName("hour")
    @Expose
    private List<Hour> hour;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Long getDateEpoch() {
        return dateEpoch;
    }

    public void setDateEpoch(Long dateEpoch) {
        this.dateEpoch = dateEpoch;
    }

    public Day getDay() {
        return day;
    }

    public void setDay(Day day) {
        this.day = day;
    }

    public Astro getAstro() {
        return astro;
    }

    public void setAstro(Astro astro) {
        this.astro = astro;
    }

    public List<Hour> getHour() {
        return hour;
    }

    public void setHour(List<Hour> hour) {
        this.hour = hour;
    }

}
