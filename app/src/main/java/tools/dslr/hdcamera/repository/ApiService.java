package tools.dslr.hdcamera.repository;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import tools.dslr.hdcamera.repository.model.weather.Weather;

public interface ApiService {
    @GET("forecast.json")
    Call<Weather> getWeatherByDay(@Query("q") String query, @Query("days") int days, @Query("key") String key);
}
