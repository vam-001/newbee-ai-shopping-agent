-- 修复 newbee-mall 演示数据中的失效图片
-- 1) 轮播图改用本地 swiper 图片
UPDATE tb_newbee_mall_carousel
SET carousel_url = '/mall/image/swiper/banner01.jpg'
WHERE carousel_id = 2;

UPDATE tb_newbee_mall_carousel
SET carousel_url = '/mall/image/swiper/banner02.jpg'
WHERE carousel_id = 5;

-- 2) 去掉历史数据里的 localhost 绝对地址，改成相对路径
UPDATE tb_newbee_mall_goods_info
SET goods_cover_img = REPLACE(goods_cover_img, 'http://localhost:28089', '')
WHERE goods_cover_img LIKE 'http://localhost:28089%';

UPDATE tb_newbee_mall_goods_info
SET goods_carousel = REPLACE(goods_carousel, 'http://localhost:28089', '')
WHERE goods_carousel LIKE 'http://localhost:28089%';

-- 3) 失效 OSS 商品图改用本地占位图
UPDATE tb_newbee_mall_goods_info
SET goods_cover_img = '/mall/image/product-placeholder.svg'
WHERE goods_cover_img LIKE 'https://newbee-mall.oss-cn-beijing.aliyuncs.com/%';

UPDATE tb_newbee_mall_goods_info
SET goods_carousel = '/mall/image/product-placeholder.svg'
WHERE goods_carousel LIKE 'https://newbee-mall.oss-cn-beijing.aliyuncs.com/%';

-- 4) 商品详情 HTML 里的失效 OSS 图片改用本地占位图
UPDATE tb_newbee_mall_goods_info
SET goods_detail_content = REGEXP_REPLACE(
    goods_detail_content,
    'https://newbee-mall\\.oss-cn-beijing\\.aliyuncs\\.com/[^"''\\s<>]+',
    '/mall/image/null-content.png'
)
WHERE goods_detail_content LIKE '%newbee-mall.oss-cn-beijing.aliyuncs.com%';
