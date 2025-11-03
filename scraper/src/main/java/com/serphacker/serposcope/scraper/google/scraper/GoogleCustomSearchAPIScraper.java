/*
 * Serposcope - SEO rank checker https://serposcope.serphacker.com/
 *
 * Copyright (c) 2016 SERP Hacker
 * @author Pierre Nogues <support@serphacker.com>
 * @license https://opensource.org/licenses/MIT MIT License
 */
package com.serphacker.serposcope.scraper.google.scraper;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.customsearch.v1.CustomSearchAPI;
import com.google.api.services.customsearch.v1.model.Result;
import com.google.api.services.customsearch.v1.model.Search;
import com.serphacker.serposcope.scraper.captcha.solver.CaptchaSolver;
import com.serphacker.serposcope.scraper.google.GoogleCountryCode;
import com.serphacker.serposcope.scraper.google.GoogleScrapResult;
import com.serphacker.serposcope.scraper.google.GoogleScrapResult.Status;
import com.serphacker.serposcope.scraper.google.GoogleScrapSearch;
import com.serphacker.serposcope.scraper.http.ScrapClient;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Google Custom Search APIを使用したスクレイパー
 * スクレイピングの代わりに公式APIを使用します
 * @author admin
 */
public class GoogleCustomSearchAPIScraper extends GoogleScraper {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCustomSearchAPIScraper.class);
    
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "Serposcope";
    
    private final String apiKey;
    private final String customSearchEngineId;
    private final ApiQueriesCounter apiQueriesCounter;
    private CustomSearchAPI customSearchAPI;
    
    /**
     * API使用回数をカウントするためのコールバックインターフェース
     * coreモジュールで実装されます
     */
    public interface ApiQueriesCounter {
        /**
         * 現在の使用回数を取得
         * @return 今日のAPI使用回数
         */
        int getTodayCount();
        
        /**
         * 使用回数を1回増やす
         * @return 更新後の今日の使用回数
         */
        int increment();
        
        /**
         * 制限に達しているかチェック
         * @param maxDailyQueries 1日の最大使用回数
         * @return 制限に達している場合true
         */
        boolean isLimitReached(int maxDailyQueries);
    }
    
    public GoogleCustomSearchAPIScraper(ScrapClient client, CaptchaSolver solver, String apiKey, String customSearchEngineId, 
            ApiQueriesCounter apiQueriesCounter) {
        super(client, solver);
        this.apiKey = apiKey;
        this.customSearchEngineId = customSearchEngineId;
        this.apiQueriesCounter = apiQueriesCounter;
        
        try {
            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            this.customSearchAPI = new CustomSearchAPI.Builder(httpTransport, JSON_FACTORY, null)
                .setApplicationName(APPLICATION_NAME)
                .build();
        } catch (GeneralSecurityException | IOException ex) {
            LOG.error("Failed to initialize Google Custom Search API", ex);
            this.customSearchAPI = null;
        }
    }

    @Override
    public GoogleScrapResult scrap(GoogleScrapSearch search) throws InterruptedException {
        if (customSearchAPI == null || apiKey == null || apiKey.isEmpty() || 
            customSearchEngineId == null || customSearchEngineId.isEmpty()) {
            LOG.error("Google Custom Search API is not properly configured");
            return new GoogleScrapResult(Status.ERROR_NETWORK, new ArrayList<>(), 0);
        }

        // 使用回数制限のチェックはGoogleScraperFactoryImplで行われます
        // ここに到達した時点で、使用可能であることが保証されています

        List<String> urls = new ArrayList<>();
        long resultsNumber = 0;
        
        try {
            int pages = search.getPages();
            int resultPerPage = Math.min(search.getResultPerPage(), 10); // API limit is 10 per page
            
            for (int page = 0; page < pages; page++) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                
                // 各ページのAPI呼び出し前に使用回数制限をチェック
                if (apiQueriesCounter != null && apiQueriesCounter.isLimitReached(getMaxDailyQueries())) {
                    int todayCount = apiQueriesCounter.getTodayCount();
                    LOG.warn("Daily API queries limit reached during pagination ({} / {}). Stopping at page {}.", 
                        todayCount, getMaxDailyQueries(), page);
                    break; // 制限に達したらループを抜ける
                }
                
                long startIndex = (long)page * resultPerPage + 1L; // API uses 1-based indexing
                
                CustomSearchAPI.Cse.List list = customSearchAPI.cse().list()
                    .setKey(apiKey)
                    .setCx(customSearchEngineId)
                    .setQ(search.getKeyword())
                    .setNum(resultPerPage)
                    .setStart(startIndex);
                
                // Set country/region (gl parameter)
                if (search.getCountry() != null && !GoogleCountryCode.__.equals(search.getCountry())) {
                    list.setGl(search.getCountry().name().toLowerCase());
                }
                
                // Set language (lr parameter) - approximate mapping from country
                if (search.getLocal() != null && !search.getLocal().isEmpty()) {
                    // Custom Search API doesn't directly support uule parameter
                    // We can try to set language based on locale
                    String language = extractLanguageFromLocale(search.getLocal());
                    if (language != null) {
                        list.setLr(language);
                    }
                }
                
                LOG.debug("Querying Google Custom Search API: keyword='{}', page={}, start={}", 
                    search.getKeyword(), page, startIndex);
                
                // API呼び出し前に使用回数をインクリメント
                if (apiQueriesCounter != null) {
                    int currentCount = apiQueriesCounter.increment();
                    LOG.debug("API queries count incremented to: {} / {}", currentCount, getMaxDailyQueries());
                }
                
                Search results = list.execute();
                
                if (results.getItems() != null) {
                    for (Result item : results.getItems()) {
                        urls.add(item.getLink());
                    }
                }
                
                // Get total results count from first page
                if (page == 0 && results.getSearchInformation() != null) {
                    try {
                        resultsNumber = Long.parseLong(results.getSearchInformation().getTotalResults());
                    } catch (NumberFormatException ex) {
                        LOG.warn("Could not parse total results count", ex);
                    }
                }
                
                // Check if there are more results
                if (results.getItems() == null || results.getItems().isEmpty() || 
                    results.getItems().size() < resultPerPage) {
                    break; // No more results
                }
                
                // Rate limiting: add a small delay between pages
                if (page < pages - 1) {
                    long pause = search.getRandomPagePauseMS();
                    if (pause > 0) {
                        try {
                            LOG.trace("sleeping {} milliseconds", pause);
                            Thread.sleep(pause);
                        } catch (InterruptedException ex) {
                            throw ex;
                        }
                    }
                }
            }
            
            return new GoogleScrapResult(Status.OK, urls, 0, resultsNumber);
            
        } catch (InterruptedException ex) {
            throw ex;
        } catch (IOException ex) {
            LOG.error("Error calling Google Custom Search API", ex);
            return new GoogleScrapResult(Status.ERROR_NETWORK, urls, 0);
        } catch (Exception ex) {
            LOG.error("Unexpected error in Google Custom Search API", ex);
            return new GoogleScrapResult(Status.ERROR_NETWORK, urls, 0);
        }
    }
    
    /**
     * Extract language code from locale string (approximate)
     * This is a simple mapping - you may need to enhance this based on your needs
     */
    private String extractLanguageFromLocale(String locale) {
        if (locale == null || locale.isEmpty()) {
            return null;
        }
        
        // Simple mapping: try to extract language from common locale formats
        // e.g., "Tokyo, Japan" -> "lang_ja"
        String lower = locale.toLowerCase();
        if (lower.contains("japan") || lower.contains("tokyo")) {
            return "lang_ja";
        } else if (lower.contains("france") || lower.contains("paris")) {
            return "lang_fr";
        } else if (lower.contains("germany") || lower.contains("berlin")) {
            return "lang_de";
        } else if (lower.contains("spain") || lower.contains("madrid")) {
            return "lang_es";
        } else if (lower.contains("italy") || lower.contains("rome")) {
            return "lang_it";
        }
        
        // Default: return null (no language restriction)
        return null;
    }
    
    @Override
    protected Status downloadSerp(String url, String referrer, GoogleScrapSearch search, int retry) {
        // Not used in API-based implementation
        return Status.OK;
    }
    
    @Override
    protected Status parseSerp(List<String> urls) {
        // Not used in API-based implementation
        return Status.OK;
    }
    
    /**
     * 最大使用回数を取得（デフォルト50）
     * 実装はcoreモジュールで行われます
     */
    protected int getMaxDailyQueries() {
        return 50; // デフォルト値、coreモジュールで上書き可能
    }
}

