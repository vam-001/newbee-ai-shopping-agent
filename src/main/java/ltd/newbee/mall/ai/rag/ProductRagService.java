package ltd.newbee.mall.ai.rag;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import ltd.newbee.mall.ai.config.AiProperties;
import ltd.newbee.mall.dao.AiProductMapper;
import ltd.newbee.mall.entity.NewBeeMallGoods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 商品知识库的向量检索（RAG）。
 *
 * <p>应用启动后异步把上架商品构建成向量索引，避免阻塞启动；
 * 索引未就绪时检索返回空列表，由工具层回退到 MySQL 关键词查询。</p>
 */
@Service
public class ProductRagService {

    private static final Logger log = LoggerFactory.getLogger(ProductRagService.class);
    private static final int MAX_PRODUCTS = 1000;
    private static final int DETAIL_CONTENT_LIMIT = 300;

    private final AiProductMapper productMapper;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final AiProperties properties;

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicInteger indexedCount = new AtomicInteger(0);
    private volatile long lastBuildMillis = 0L;

    public ProductRagService(AiProductMapper productMapper,
                             EmbeddingModel embeddingModel,
                             EmbeddingStore<TextSegment> embeddingStore,
                             AiProperties properties) {
        this.productMapper = productMapper;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void buildIndexAsync() {
        Thread thread = new Thread(this::buildIndex, "product-rag-indexer");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 全量重建商品向量索引。也可以由运维接口手动触发。
     */
    public synchronized void buildIndex() {
        long start = System.currentTimeMillis();
        try {
            List<NewBeeMallGoods> goodsList = productMapper.selectOnSale(null, MAX_PRODUCTS);
            List<TextSegment> segments = new ArrayList<>(goodsList.size());
            for (NewBeeMallGoods goods : goodsList) {
                segments.add(toSegment(goods));
            }

            embeddingStore.removeAll();
            int batchSize = Math.max(1, properties.getIndexBatchSize());
            for (int i = 0; i < segments.size(); i += batchSize) {
                List<TextSegment> batch = segments.subList(i, Math.min(i + batchSize, segments.size()));
                List<Embedding> embeddings = embeddingModel.embedAll(batch).content();
                embeddingStore.addAll(embeddings, batch);
            }

            indexedCount.set(segments.size());
            lastBuildMillis = System.currentTimeMillis() - start;
            ready.set(true);
            log.info("Product RAG index built: {} products in {} ms", segments.size(), lastBuildMillis);
        } catch (Exception e) {
            log.error("Failed to build product RAG index", e);
        }
    }

    /**
     * 语义召回商品 ID。索引未就绪或没有达标结果时返回空列表。
     */
    public List<Long> searchGoodsIds(String query, int topK) {
        if (!ready.get() || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(Math.max(1, topK))
                    .minScore(properties.getRagMinScore())
                    .build();
            return embeddingStore.search(request).matches().stream()
                    .map(EmbeddingMatch::embedded)
                    .filter(Objects::nonNull)
                    .map(segment -> segment.metadata().getLong("goodsId"))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.warn("Product RAG search failed: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean isReady() {
        return ready.get();
    }

    public int getIndexedCount() {
        return indexedCount.get();
    }

    public long getLastBuildMillis() {
        return lastBuildMillis;
    }

    private TextSegment toSegment(NewBeeMallGoods goods) {
        String text = "商品ID: " + goods.getGoodsId()
                + "\n商品名称: " + safe(goods.getGoodsName())
                + "\n商品简介: " + safe(goods.getGoodsIntro())
                + "\n标签: " + safe(goods.getTag())
                + "\n价格: " + yuan(goods.getSellingPrice()) + " 元"
                + "\n库存: " + (goods.getStockNum() == null ? 0 : goods.getStockNum())
                + "\n分类ID: " + goods.getGoodsCategoryId()
                + "\n详情: " + truncate(goods.getGoodsDetailContent(), DETAIL_CONTENT_LIMIT);
        return TextSegment.from(text, Metadata.from(Map.of("goodsId", goods.getGoodsId())));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private String yuan(Integer cents) {
        return cents == null ? "-" : String.format("%.2f", cents / 100.0);
    }
}
