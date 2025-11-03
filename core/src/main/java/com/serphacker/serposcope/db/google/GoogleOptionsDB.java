/* 
 * Serposcope - SEO rank checker https://serposcope.serphacker.com/
 * 
 * Copyright (c) 2016 SERP Hacker
 * @author Pierre Nogues <support@serphacker.com>
 * @license https://opensource.org/licenses/MIT MIT License
 */
package com.serphacker.serposcope.db.google;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.serphacker.serposcope.db.base.ConfigDB;
import com.serphacker.serposcope.models.google.GoogleSettings;

@Singleton
public class GoogleOptionsDB {
    
    private final static String PAGES = "google.pages";
    private final static String RESULT_PER_PAGE = "google.result_per_page";
    private final static String MIN_PAUSE_BETWEEN_PAGE_SEC = "google.min_pause_between_page_sec";
    private final static String MAX_PAUSE_BETWEEN_PAGE_SEC = "google.max_pause_between_page_sec";    
    private final static String MAX_THREADS = "google.maxThreads";
    private final static String FETCH_RETRY = "google.fetchRetry";    
    
    private final static String DEFAULT_DATACENTER = "google.default_datacenter";
    private final static String DEFAULT_DEVICE = "google.default.device";
    private final static String DEFAULT_LOCAL = "google.default.local";
    private final static String DEFAULT_COUNTRY = "google.default.country";
    private final static String DEFAULT_CUSTOM_PARAMETERS = "google.default.custom";
    
    private final static String GOOGLE_API_KEY = "google.api_key";
    private final static String GOOGLE_CUSTOM_SEARCH_ENGINE_ID = "google.custom_search_engine_id";
    private final static String USE_CUSTOM_SEARCH_API = "google.use_custom_search_api";
    private final static String MAX_DAILY_API_QUERIES = "google.max_daily_api_queries";
    private final static String API_QUERIES_COUNT_DATE = "google.api_queries_count_date";
    private final static String API_QUERIES_COUNT = "google.api_queries_count";
    
    @Inject
    ConfigDB configDB;
    
    public GoogleSettings get(){
        GoogleSettings options = new GoogleSettings();
        
        options.setPages(configDB.getInt(PAGES, options.getPages()));
        options.setResultPerPage(configDB.getInt(RESULT_PER_PAGE, options.getResultPerPage()));
        options.setMinPauseBetweenPageSec(configDB.getInt(MIN_PAUSE_BETWEEN_PAGE_SEC, options.getMinPauseBetweenPageSec()));
        options.setMaxPauseBetweenPageSec(configDB.getInt(MAX_PAUSE_BETWEEN_PAGE_SEC, options.getMaxPauseBetweenPageSec()));        
        options.setMaxThreads(configDB.getInt(MAX_THREADS, options.getMaxThreads()));
        options.setFetchRetry(configDB.getInt(FETCH_RETRY, options.getFetchRetry()));
        
        options.setDefaultDatacenter(configDB.get(DEFAULT_DATACENTER, options.getDefaultDatacenter()));
        options.setDefaultDevice(configDB.get(DEFAULT_DEVICE, null));
        options.setDefaultLocal(configDB.get(DEFAULT_LOCAL, options.getDefaultLocal()));
        options.setDefaultCountry(configDB.get(DEFAULT_COUNTRY, null));
        options.setDefaultCustomParameters(configDB.get(DEFAULT_CUSTOM_PARAMETERS, options.getDefaultCustomParameters()));
        
        // Google Custom Search API settings
        options.setGoogleApiKey(configDB.get(GOOGLE_API_KEY, options.getGoogleApiKey()));
        options.setGoogleCustomSearchEngineId(configDB.get(GOOGLE_CUSTOM_SEARCH_ENGINE_ID, options.getGoogleCustomSearchEngineId()));
        options.setUseCustomSearchAPI(configDB.getBoolean(USE_CUSTOM_SEARCH_API, options.isUseCustomSearchAPI()));
        options.setMaxDailyApiQueries(configDB.getInt(MAX_DAILY_API_QUERIES, options.getMaxDailyApiQueries()));
        
        return options;
    }
    
    public void update(GoogleSettings opts){
        
        GoogleSettings def = new GoogleSettings();

        // scraping
        configDB.updateInt(PAGES, nullIfDefault(opts.getPages(), def.getPages()));
        configDB.updateInt(RESULT_PER_PAGE, nullIfDefault(opts.getResultPerPage(), def.getResultPerPage()));
        configDB.updateInt(MIN_PAUSE_BETWEEN_PAGE_SEC, nullIfDefault(opts.getMinPauseBetweenPageSec(), def.getMinPauseBetweenPageSec()));
        configDB.updateInt(MAX_PAUSE_BETWEEN_PAGE_SEC, nullIfDefault(opts.getMaxPauseBetweenPageSec(), def.getMaxPauseBetweenPageSec()));
        configDB.updateInt(MAX_THREADS, nullIfDefault(opts.getMaxThreads(), def.getMaxThreads()));
        configDB.updateInt(FETCH_RETRY, nullIfDefault(opts.getFetchRetry(), def.getFetchRetry()));

        // search
        configDB.update(DEFAULT_DATACENTER, nullIfDefault(opts.getDefaultDatacenter(), def.getDefaultDatacenter()));
        configDB.updateInt(DEFAULT_DEVICE, nullIfDefault(opts.getDefaultDevice().ordinal(), def.getDefaultDevice().ordinal()));
        configDB.update(DEFAULT_LOCAL, nullIfDefault(opts.getDefaultLocal(), def.getDefaultLocal()));
        configDB.update(DEFAULT_COUNTRY, nullIfDefault(opts.getDefaultCountry().name(), def.getDefaultCountry().name()));
        configDB.update(DEFAULT_CUSTOM_PARAMETERS, nullIfDefault(opts.getDefaultCustomParameters(),def.getDefaultCustomParameters()));
        
        // Google Custom Search API settings
        configDB.update(GOOGLE_API_KEY, nullIfDefault(opts.getGoogleApiKey(), def.getGoogleApiKey()));
        configDB.update(GOOGLE_CUSTOM_SEARCH_ENGINE_ID, nullIfDefault(opts.getGoogleCustomSearchEngineId(), def.getGoogleCustomSearchEngineId()));
        configDB.updateBoolean(USE_CUSTOM_SEARCH_API, nullIfDefault(opts.isUseCustomSearchAPI(), def.isUseCustomSearchAPI()));
        configDB.updateInt(MAX_DAILY_API_QUERIES, nullIfDefault(opts.getMaxDailyApiQueries(), def.getMaxDailyApiQueries()));
        
    }
    
    /**
     * 今日のAPI使用回数を取得
     * @return 今日のAPI使用回数
     */
    public int getTodayApiQueriesCount() {
        String today = java.time.LocalDate.now().toString();
        String storedDate = configDB.get(API_QUERIES_COUNT_DATE, null);
        
        // 日付が変わっていたら0にリセット
        if (storedDate == null || !today.equals(storedDate)) {
            return 0;
        }
        
        return configDB.getInt(API_QUERIES_COUNT, 0);
    }
    
    /**
     * API使用回数を1回増やす
     * 日付が変わっていたらリセットしてからカウント
     * @return 更新後の今日の使用回数
     */
    public synchronized int incrementApiQueriesCount() {
        String today = java.time.LocalDate.now().toString();
        String storedDate = configDB.get(API_QUERIES_COUNT_DATE, null);
        
        int currentCount;
        if (storedDate == null || !today.equals(storedDate)) {
            // 日付が変わっていたらリセット
            currentCount = 1;
            configDB.update(API_QUERIES_COUNT_DATE, today);
        } else {
            currentCount = configDB.getInt(API_QUERIES_COUNT, 0) + 1;
        }
        
        configDB.updateInt(API_QUERIES_COUNT, currentCount);
        return currentCount;
    }
    
    /**
     * 今日のAPI使用回数が上限に達しているかチェック
     * @param maxDailyQueries 1日の最大使用回数
     * @return 上限に達している場合true
     */
    public boolean isApiQueriesLimitReached(int maxDailyQueries) {
        int todayCount = getTodayApiQueriesCount();
        return todayCount >= maxDailyQueries;
    }
    
    protected Boolean nullIfDefault(Boolean value, Boolean def){
        return (Boolean)nullIfDefaultObject(value, def);
    }
    
    protected Integer nullIfDefault(Integer value, Integer def){
        return (Integer)nullIfDefaultObject(value, def);
    }
    
    protected String nullIfDefault(String value, String def){
        return (String)nullIfDefaultObject(value, def);
    }
    
    protected Object nullIfDefaultObject(Object value, Object def){
        if(def == null && value != null){
            return value;
        }
        
        if(value == null){
            return null;
        }
        
        if(def.equals(value)){
            return null;
        }
        return value;
    }
    
}
