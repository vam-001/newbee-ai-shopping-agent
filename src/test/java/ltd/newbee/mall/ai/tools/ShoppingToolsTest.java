package ltd.newbee.mall.ai.tools;

import ltd.newbee.mall.ai.config.AiProperties;
import ltd.newbee.mall.ai.rag.ProductRagService;
import ltd.newbee.mall.common.NewBeeMallException;
import ltd.newbee.mall.controller.vo.NewBeeMallOrderListVO;
import ltd.newbee.mall.dao.AiProductMapper;
import ltd.newbee.mall.entity.NewBeeMallGoods;
import ltd.newbee.mall.service.NewBeeMallGoodsService;
import ltd.newbee.mall.service.NewBeeMallOrderService;
import ltd.newbee.mall.util.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShoppingToolsTest {

    private NewBeeMallGoodsService goodsService;
    private NewBeeMallOrderService orderService;
    private AiProductMapper productMapper;
    private ProductRagService ragService;
    private AiProperties properties;

    @BeforeEach
    void setUp() {
        goodsService = mock(NewBeeMallGoodsService.class);
        orderService = mock(NewBeeMallOrderService.class);
        productMapper = mock(AiProductMapper.class);
        ragService = mock(ProductRagService.class);
        properties = new AiProperties();
        properties.setRagTopK(3);
    }

    @Test
    void searchGoodsFallsBackToKeywordAndFormatsLink() {
        when(ragService.searchGoodsIds(anyString(), anyInt())).thenReturn(List.of());
        NewBeeMallGoods goods = goods(1L, "机械键盘", 29900, 5, "青轴键盘");
        when(productMapper.selectOnSale("键盘", 3)).thenReturn(List.of(goods));

        ShoppingTools tools = new ShoppingTools(goodsService, orderService, productMapper, ragService, properties, 42L);
        String result = tools.searchGoods("键盘");

        assertTrue(result.contains("商品ID：1"));
        assertTrue(result.contains("299.00"));
        assertTrue(result.contains("/goods/detail/1"));
    }

    @Test
    void anonymousUserCannotQueryOrders() {
        ShoppingTools tools = new ShoppingTools(goodsService, orderService, productMapper, ragService, properties, null);

        assertTrue(tools.getMyOrders().contains("未登录"));
        assertTrue(tools.getOrderDetail("ORDER-1").contains("未登录"));
        verifyNoInteractions(orderService);
    }

    @Test
    void orderDetailPermissionErrorIsReturnedToModel() {
        when(orderService.getOrderDetailByOrderNo("ORDER-1", 42L))
                .thenThrow(new NewBeeMallException("无权限操作该订单"));
        ShoppingTools tools = new ShoppingTools(goodsService, orderService, productMapper, ragService, properties, 42L);

        assertTrue(tools.getOrderDetail("ORDER-1").contains("无权限"));
    }

    @Test
    void myOrdersAreFormattedForTheCurrentUser() {
        NewBeeMallOrderListVO order = new NewBeeMallOrderListVO();
        order.setOrderNo("202611150001");
        order.setTotalPrice(19900);
        order.setOrderStatusString("已支付");
        order.setCreateTime(new Date());
        PageResult pageResult = new PageResult(List.of(order), 1, 5, 1);
        when(orderService.getMyOrders(any())).thenReturn(pageResult);

        ShoppingTools tools = new ShoppingTools(goodsService, orderService, productMapper, ragService, properties, 42L);
        String result = tools.getMyOrders();

        assertTrue(result.contains("202611150001"));
        assertTrue(result.contains("199.00"));
        assertTrue(result.contains("已支付"));
    }

    private NewBeeMallGoods goods(Long id, String name, Integer price, Integer stock, String tag) {
        NewBeeMallGoods goods = new NewBeeMallGoods();
        goods.setGoodsId(id);
        goods.setGoodsName(name);
        goods.setSellingPrice(price);
        goods.setStockNum(stock);
        goods.setTag(tag);
        goods.setGoodsIntro("测试商品简介");
        goods.setGoodsDetailContent("测试商品详情");
        goods.setGoodsSellStatus((byte) 0);
        return goods;
    }
}
