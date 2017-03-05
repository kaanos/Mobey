package com.mobeymarker;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Headers;
import retrofit2.http.POST;

@SuppressWarnings("ALL") // Annoying spell-checks (signin)
public class client {
//    public static String API_ROOT = "http://api.muber.com.au/";
    public static String API_ROOT = "http://debug.mobey.com.au/";

    private static ApiInterface apiService;

    // Get the HTTP Client, with API_ROOT
    public static ApiInterface getC() {
        if (apiService == null) {
            // Logging
            HttpLoggingInterceptor lg = new HttpLoggingInterceptor();
            lg.setLevel(HttpLoggingInterceptor.Level.BASIC);

            // OkHttp
            OkHttpClient c = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .writeTimeout(8, TimeUnit.SECONDS)
                    .addInterceptor(lg) // Logging
                    .build();

            // Retrofit
            // TODO: as above, no initiation w/ apiService
            apiService = new Retrofit.Builder()
                    .baseUrl(API_ROOT)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(c).
                            build()
                    .create(ApiInterface.class);
        }

        return apiService;
    }


    public interface ApiInterface {
        @FormUrlEncoded
        @POST("controllers/getDriverLocation/")
        Call<retLoc> getDriverLocation(@Field("trip_id") String trip_id, @Field("index") int index);
    }
}


