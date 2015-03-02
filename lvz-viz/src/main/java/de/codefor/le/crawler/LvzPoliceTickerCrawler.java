package de.codefor.le.crawler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import com.google.common.base.Stopwatch;

import de.codefor.le.model.PoliceTicker;
import de.codefor.le.repositories.PoliceTickerRepository;
import de.codefor.le.utilities.Utils;

/**
 * @author spinner0815
 * @author sepe81
 */
@Component
public class LvzPoliceTickerCrawler {

    private static final Logger logger = LoggerFactory.getLogger(LvzPoliceTickerCrawler.class);

    protected static final String USER_AGENT = "leipzig crawler";

    protected static final int REQUEST_TIMEOUT = 10000;

    protected static final String FILE_ENDING_HTML = ".html";

    protected static final String LVZ_BASE_URL = "http://www.lvz-online.de";

    protected static final String LVZ_POLICE_TICKER_BASE_URL = LVZ_BASE_URL
            + "/leipzig/polizeiticker/polizeiticker-leipzig";

    protected static final String REF_TOKEN = "r-polizeiticker-leipzig";

    protected static final String LVZ_POLICE_TICKER_PAGE_URL = LVZ_POLICE_TICKER_BASE_URL + "/" + REF_TOKEN + "-seite-";

    @Autowired
    PoliceTickerRepository policeTickerRepository;

    private boolean crawlMore = false;

    @Async
    public Future<Iterable<String>> execute(final int page) {
        final Stopwatch watch = Stopwatch.createStarted();
        logger.info("Start crawling page {}", page);
        final List<String> crawledNews = new ArrayList<>();
        try {
            crawlMore = crawlNewsFromPage(crawledNews, page);
        } catch (final IOException e) {
            logger.error(e.toString(), e);
        }
        watch.stop();
        logger.info("Finished crawling page {} in {} ms", page, watch.elapsed(TimeUnit.MILLISECONDS));
        return new AsyncResult<Iterable<String>>(crawledNews);
    }

    /**
     * @param page the page which to crawl
     * @return true if all content of the current page is new. Hint for also crawling the next site
     * @throws IOException if there are problems while writing the detail links to a file
     */
    private boolean crawlNewsFromPage(final List<String> crawledNews, final int page) throws IOException {
        final Document doc = Jsoup.connect(generateUrl(page)).userAgent(USER_AGENT).timeout(REQUEST_TIMEOUT).get();
        final Elements links = doc.select("a:contains(mehr...)");
        for (final Element link : links) {
            final String detailLink = LVZ_BASE_URL + link.attr("href");
            final String id = Utils.generateHashForUrl(detailLink);
            if (!id.isEmpty()) {
                PoliceTicker article = null;
                if (policeTickerRepository != null) {
                    article = policeTickerRepository.findOne(id);
                }
                if (article == null) {
                    logger.debug("article not stored yet: {}", detailLink);
                    crawledNews.add(detailLink);
                } else {
                    logger.debug("article already stored: {}", detailLink);
                }
            }
        }
        boolean result = true;
        if (crawledNews.isEmpty()) {
            logger.info("No new articles found on this page");
            result = true;
        }
        if (links.isEmpty()) {
            logger.info("No links found on this page, this should be the last available page");
            result = false;
        }
        return result;
    }

    private String generateUrl(final int page) {
        final StringBuilder sb = new StringBuilder(LVZ_POLICE_TICKER_PAGE_URL);
        sb.append(page);
        sb.append(FILE_ENDING_HTML);
        final String url = sb.toString();
        logger.debug("page url {}", url);
        return url;
    }

    public void resetCrawler() {
        this.crawlMore = true;
    }

    public boolean isMoreToCrawl() {
        return crawlMore;
    }

}
