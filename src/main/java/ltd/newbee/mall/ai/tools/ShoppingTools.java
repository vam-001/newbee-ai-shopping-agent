package ltd.newbee.mall.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import ltd.newbee.mall.ai.config.AiProperties;
import ltd.newbee.mall.ai.rag.ProductRagService;
import ltd.newbee.mall.common.Constants;
import ltd.newbee.mall.common.NewBeeMallException;
import ltd.newbee.mall.controller.vo.NewBeeMallOrderDetailVO;
import ltd.newbee.mall.controller.vo.NewBeeMallOrderItemVO;
import ltd.newbee.mall.controller.vo.NewBeeMallOrderListVO;
import ltd.newbee.mall.dao.AiProductMapper;
import ltd.newbee.mall.entity.NewBeeMallGoods;
import ltd.newbee.mall.service.NewBeeMallGoodsService;
import ltd.newbee.mall.service.NewBeeMallOrderService;
import ltd.newbee.mall.util.PageQueryUtil;
import ltd.newbee.mall.util.PageResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 导购 Agent 可调用的工具集合。
 *
 * <p>每次请求创建一个实例，userId 由服务端会话注入，模型无法伪造；
 * 订单类工具只查询当前登录用户自己的数据，从根上避免越权。</p>
 */
public class ShoppingTools {

    private final NewBeeMallGoodsService goodsService;
    private final NewBeeMallOrderService orderService;
    private final AiProductMapper productMapper;
    private final ProductRagService ragService;
    private final AiProperties properties;
    private final Long userId;

    public ShoppingTools(NewBeeMallGoodsService goodsService,
                         NewBeeMallOrderService orderService,
                         AiProductMapper productMapper,
                         ProductRagService ragService,
                         AiProperties properties,
                         Long userId) {
        this.goodsService = goodsService;
        this.orderService = orderService;
        this.productMapper = productMapper;
        this.ragService = ragService;
        this.properties = properties;
        this.userId = userId;
    }

    @Tool("根据用户需求搜索在售商品。输入自然语言描述，返回最相关的商品列表，包含商品ID、名称、价格、库存、简介和商品详情链接。")
    public String searchGoods(String query) {
        if (query == null || query.isBlank()) {
            return "请告诉我你想找的商品类型或需求。";
        }
        int topK = Math.max(1, properties.getRagTopK());

        List<NewBeeMallGoods> goodsList = new ArrayList<>();
        for (Long goodsId : ragService.searchGoodsIds(query, topK)) {
            NewBeeMallGoods goods = goodsService.getNewBeeMallGoodsById(goodsId);
            if (isOnSale(goods)) {
                goodsList.add(goods);
            }
        }
        // 向量索引未就绪或召回为空时，回退到关键词检索
        if (goodsList.isEmpty()) {
            goodsList = productMapper.selectOnSale(query, topK);
        }
        if (goodsList.isEmpty()) {
            return "没有找到相关商品，可以换一个关键词，或者告诉我预算和使用场景。";
        }

        StringBuilder sb = new StringBuilder("找到以下在售商品：\n\n");
        for (int i = 0; i < goodsList.size(); i++) {
            sb.append(formatGoods(goodsList.get(i)));
            if (i < goodsList.size() - 1) {
                sb.append("\n\n");
            }
        }
        return sb.toString();
    }

    @Tool("根据商品ID查询商品详情。输入商品ID，返回名称、价格、库存、标签、简介、详情和链接。")
    public String getGoodsDetail(Long goodsId) {
        if (goodsId == null) {
            return "请提供商品ID。";
        }
        NewBeeMallGoods goods = goodsService.getNewBeeMallGoodsById(goodsId);
        if (!isOnSale(goods)) {
            return "商品不存在或已下架。";
        }
        return "商品详情：\n"
                + "- 商品ID：" + goods.getGoodsId() + "\n"
                + "- 名称：" + safe(goods.getGoodsName()) + "\n"
                + "- 价格：" + yuan(goods.getSellingPrice()) + " 元\n"
                + "- 库存：" + (goods.getStockNum() == null ? 0 : goods.getStockNum()) + "\n"
                + "- 标签：" + safe(goods.getTag()) + "\n"
                + "- 简介：" + safe(goods.getGoodsIntro()) + "\n"
                + "- 详情：" + truncate(goods.getGoodsDetailContent(), 500) + "\n"
                + "- 链接：/goods/detail/" + goods.getGoodsId();
    }

    @Tool("查询当前登录用户的最近5条订单，返回订单号、金额、状态、下单时间和商品。未登录时返回提示。")
    public String getMyOrders() {
        if (userId == null) {
            return "当前用户未登录，无法查询订单。请先登录商城账号。";
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", userId);
        params.put("page", 1);
        params.put("limit", 5);
        PageResult pageResult = orderService.getMyOrders(new PageQueryUtil(params));
        List<?> list = pageResult.getList();
        if (list == null || list.isEmpty()) {
            return "当前账号还没有订单。";
        }

        StringBuilder sb = new StringBuilder("最近订单：\n\n");
        for (Object item : list) {
            NewBeeMallOrderListVO order = (NewBeeMallOrderListVO) item;
            sb.append("- 订单号：").append(order.getOrderNo())
                    .append("\n  金额：").append(yuan(order.getTotalPrice())).append(" 元")
                    .append("\n  状态：").append(safe(order.getOrderStatusString()))
                    .append("\n  下单时间：").append(order.getCreateTime() == null ? "-" : order.getCreateTime())
                    .append("\n  商品：").append(formatItems(order.getNewBeeMallOrderItemVOS()))
                    .append("\n\n");
        }
        return sb.toString().trim();
    }

    @Tool("根据订单号查询当前登录用户的订单详情。只能查询自己的订单，订单号不存在或无权限时返回提示。")
    public String getOrderDetail(String orderNo) {
        if (userId == null) {
            return "当前用户未登录，无法查询订单。请先登录商城账号。";
        }
        if (orderNo == null || orderNo.isBlank()) {
            return "请提供订单号。";
        }
        try {
            NewBeeMallOrderDetailVO detail = orderService.getOrderDetailByOrderNo(orderNo.trim(), userId);
            return "订单详情：\n"
                    + "- 订单号：" + detail.getOrderNo() + "\n"
                    + "- 金额：" + yuan(detail.getTotalPrice()) + " 元\n"
                    + "- 支付状态：" + safe(detail.getPayStatusString()) + "\n"
                    + "- 订单状态：" + safe(detail.getOrderStatusString()) + "\n"
                    + "- 收货地址：" + safe(detail.getUserAddress()) + "\n"
                    + "- 下单时间：" + (detail.getCreateTime() == null ? "-" : detail.getCreateTime()) + "\n"
                    + "- 商品：" + formatItems(detail.getNewBeeMallOrderItemVOS());
        } catch (NewBeeMallException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "订单查询失败，请确认订单号是否正确。";
        }
    }

    private boolean isOnSale(NewBeeMallGoods goods) {
        return goods != null
                && goods.getGoodsSellStatus() != null
                && goods.getGoodsSellStatus().intValue() == Constants.SELL_STATUS_UP;
    }

    private String formatGoods(NewBeeMallGoods goods) {
        return "- 商品ID：" + goods.getGoodsId()
                + "\n  名称：" + safe(goods.getGoodsName())
                + "\n  价格：" + yuan(goods.getSellingPrice()) + " 元"
                + "\n  库存：" + (goods.getStockNum() == null ? 0 : goods.getStockNum())
                + "\n  标签：" + safe(goods.getTag())
                + "\n  简介：" + truncate(goods.getGoodsIntro(), 120)
                + "\n  链接：/goods/detail/" + goods.getGoodsId();
    }

    private String formatItems(List<NewBeeMallOrderItemVO> items) {
        if (items == null || items.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (NewBeeMallOrderItemVO item : items) {
            sb.append(safe(item.getGoodsName()))
                    .append(" x").append(item.getGoodsCount() == null ? 0 : item.getGoodsCount())
                    .append("（").append(yuan(item.getSellingPrice())).append(" 元）; ");
        }
        return sb.toString().trim();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "-";
        }
        return value.length() <= limit ? value : value.substring(0, limit) + "...";
    }

    private String yuan(Integer price) {
        // newbee-mall 的价格字段单位是元，不是分
        return price == null ? "-" : price + ".00";
    }
}
