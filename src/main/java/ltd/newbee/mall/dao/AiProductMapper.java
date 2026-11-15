package ltd.newbee.mall.dao;

import ltd.newbee.mall.entity.NewBeeMallGoods;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 导购模块专用的商品查询 Mapper。
 * 与后台分页查询解耦，只暴露上架商品，避免 AI 工具拿到下架/后台数据。
 */
public interface AiProductMapper {

    /**
     * 查询上架商品。
     *
     * @param keyword 为空时返回全部上架商品；否则按名称/简介/标签模糊匹配
     * @param limit   最大返回条数
     */
    List<NewBeeMallGoods> selectOnSale(@Param("keyword") String keyword, @Param("limit") int limit);
}
