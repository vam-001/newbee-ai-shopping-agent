package ltd.newbee.mall.ai.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import ltd.newbee.mall.ai.config.AiProperties;
import ltd.newbee.mall.dao.AiProductMapper;
import ltd.newbee.mall.entity.NewBeeMallGoods;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductRagServiceTest {

    @Test
    void buildsIndexAndReturnsMatchingGoodsIds() {
        AiProductMapper productMapper = mock(AiProductMapper.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        NewBeeMallGoods keyboard = goods(1L, "机械键盘");
        NewBeeMallGoods headset = goods(2L, "蓝牙耳机");
        when(productMapper.selectOnSale(null, 1000)).thenReturn(List.of(keyboard, headset));
        when(embeddingModel.embedAll(anyList()))
                .thenAnswer(invocation -> Response.from(List.of(
                        Embedding.from(new float[]{1.0f, 0.0f}),
                        Embedding.from(new float[]{1.0f, 0.0f}))));
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[]{1.0f, 0.0f})));

        AiProperties properties = new AiProperties();
        properties.setIndexBatchSize(2);
        properties.setRagMinScore(0.0);
        ProductRagService service = new ProductRagService(
                productMapper, embeddingModel, new InMemoryEmbeddingStore<>(), properties);

        service.buildIndex();

        assertTrue(service.isReady());
        assertEquals(2, service.getIndexedCount());
        List<Long> ids = service.searchGoodsIds("键盘", 2);
        assertEquals(2, ids.size());
        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(2L));
    }

    private NewBeeMallGoods goods(Long id, String name) {
        NewBeeMallGoods goods = new NewBeeMallGoods();
        goods.setGoodsId(id);
        goods.setGoodsName(name);
        goods.setGoodsIntro("简介");
        goods.setSellingPrice(9900);
        goods.setStockNum(10);
        goods.setGoodsCategoryId(1L);
        goods.setGoodsSellStatus((byte) 0);
        return goods;
    }
}
