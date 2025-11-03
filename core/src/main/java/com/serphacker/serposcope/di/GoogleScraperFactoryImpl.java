/* 
 * Serposcope - SEO rank checker https://serposcope.serphacker.com/
 * 
 * Copyright (c) 2016 SERP Hacker
 * @author Pierre Nogues <support@serphacker.com>
 * @license https://opensource.org/licenses/MIT MIT License
 */
package com.serphacker.serposcope.di;

import com.google.inject.Inject;
import com.serphacker.serposcope.db.google.GoogleOptionsDB;
import com.serphacker.serposcope.models.google.GoogleSettings;
import com.serphacker.serposcope.scraper.captcha.solver.CaptchaSolver;
import com.serphacker.serposcope.scraper.google.scraper.GoogleCustomSearchAPIScraper;
import com.serphacker.serposcope.scraper.google.scraper.GoogleScraper;
import com.serphacker.serposcope.scraper.http.ScrapClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class GoogleScraperFactoryImpl implements GoogleScraperFactory{

    private static final Logger LOG = LoggerFactory.getLogger(GoogleScraperFactoryImpl.class);
    
    private GoogleOptionsDB googleOptionsDB;

    @Inject
    public GoogleScraperFactoryImpl(GoogleOptionsDB googleOptionsDB) {
        this.googleOptionsDB = googleOptionsDB;
    }

    @Override
    public GoogleScraper get(ScrapClient http, CaptchaSolver solver) {
        return new GoogleScraper(http, solver);
    }

    @Override
    public GoogleScraper get(ScrapClient http, CaptchaSolver solver, GoogleSettings settings) {
        if (settings != null && settings.isUseCustomSearchAPI() && 
            settings.getGoogleApiKey() != null && !settings.getGoogleApiKey().isEmpty() &&
            settings.getGoogleCustomSearchEngineId() != null && !settings.getGoogleCustomSearchEngineId().isEmpty()) {
            
            // 使用回数制限をチェック
            if (googleOptionsDB != null && googleOptionsDB.isApiQueriesLimitReached(settings.getMaxDailyApiQueries())) {
                int todayCount = googleOptionsDB.getTodayApiQueriesCount();
                LOG.warn("Daily API queries limit reached ({} / {}). Falling back to traditional scraping.", 
                    todayCount, settings.getMaxDailyApiQueries());
                return new GoogleScraper(http, solver);
            }
            
            LOG.info("Using Google Custom Search API instead of scraping");
            
            // ApiQueriesCounterの実装を作成
            GoogleCustomSearchAPIScraper.ApiQueriesCounter counter = new GoogleCustomSearchAPIScraper.ApiQueriesCounter() {
                @Override
                public int getTodayCount() {
                    return googleOptionsDB.getTodayApiQueriesCount();
                }
                
                @Override
                public int increment() {
                    return googleOptionsDB.incrementApiQueriesCount();
                }
                
                @Override
                public boolean isLimitReached(int maxDailyQueries) {
                    return googleOptionsDB.isApiQueriesLimitReached(maxDailyQueries);
                }
            };
            
            // 最大使用回数をスクレイパーに設定するためのラッパーを作成
            final int maxQueries = settings.getMaxDailyApiQueries();
            GoogleCustomSearchAPIScraper scraper = new GoogleCustomSearchAPIScraper(http, solver, 
                settings.getGoogleApiKey(), 
                settings.getGoogleCustomSearchEngineId(),
                counter) {
                @Override
                protected int getMaxDailyQueries() {
                    return maxQueries;
                }
            };
            
            return scraper;
        } else {
            LOG.debug("Using traditional scraping method (API not configured or disabled)");
            return new GoogleScraper(http, solver);
        }
    }

}
